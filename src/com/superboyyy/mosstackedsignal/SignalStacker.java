package com.superboyyy.mosstackedsignal;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 双卡时把 mobile_combo 里的两张卡宽度压成 0，自己在外层 FrameLayout 的 overlay 上画两行堆叠信号；
 * 只剩一张卡时还原。宽度在测量阶段强制，不改 LayoutParams（新版系统会把它重置回去）。
 */
final class SignalStacker {
    private static final float BATTERY_GLYPH_RATIO = 40f / 54f;
    private static final float FALLBACK_BLOCK_RATIO = 0.72f;

    static final Map<FrameLayout, StackedSignalDrawable> OVERLAYS = new WeakHashMap<>();
    static final Set<View> WATCHED = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final Set<View> PENDING = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final Map<LinearLayout, Integer> BLOCK_HEIGHT = new WeakHashMap<>();

    private SignalStacker() { }

    static void schedule(final View view) {
        if (!PENDING.add(view)) return;
        view.post(new Runnable() {
            @Override
            public void run() {
                PENDING.remove(view);
                apply(view);
            }
        });
    }

    static void beforeMeasure(LinearLayout view, XC_MethodHook.MethodHookParam param) {
        int comboId = StatusBarViews.idMobileCombo;
        if (comboId == 0) return;
        if (view.getId() == comboId) {
            FrameLayout frame = frameOf(view);
            StackedSignalDrawable overlay = frame == null ? null : OVERLAYS.get(frame);
            if (overlay != null && overlay.width > 0 && overlay.viewH > 0) {
                param.args[0] = View.MeasureSpec.makeMeasureSpec(overlay.width, View.MeasureSpec.EXACTLY);
                param.args[1] = View.MeasureSpec.makeMeasureSpec(overlay.viewH, View.MeasureSpec.EXACTLY);
            }
            return;
        }
        ViewParent parent = view.getParent();
        if (parent instanceof LinearLayout && ((View) parent).getId() == comboId) {
            FrameLayout frame = frameOf((LinearLayout) parent);
            if (frame != null && OVERLAYS.containsKey(frame)) {
                param.args[0] = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY);
            }
        }
    }

    /** 子 View 的测量规格没变时系统会跳过 onMeasure，所以两张卡也要一起请求重新布局。 */
    private static void remeasure(LinearLayout combo) {
        for (int i = 0; i < combo.getChildCount(); i++) combo.getChildAt(i).requestLayout();
        combo.requestLayout();
    }

    /** 新版系统自带堆叠信号，开着时会隐藏 mobile_combo、显示自己的图标；以模块为准，把它藏回去。 */
    private static void hideNativeStack(FrameLayout frame, LinearLayout combo) {
        View nativeStack = StatusBarViews.findIn(frame, StatusBarViews.idNativeStack);
        if (nativeStack != null && nativeStack.getVisibility() != View.GONE) {
            nativeStack.setVisibility(View.GONE);
            StatusBarViews.logOnChange("native stack", "hidden");
        }
        if (combo.getVisibility() != View.VISIBLE) combo.setVisibility(View.VISIBLE);
    }

    static View comboAncestor(View view) {
        return StatusBarViews.ancestorWithId(view, StatusBarViews.idMobileCombo);
    }

    private static void apply(View view) {
        try {
            StatusBarViews.resolveIds(view.getResources());
            int comboId = StatusBarViews.idMobileCombo;
            if (comboId == 0) return;
            View found = view.getId() == comboId ? view : view.findViewById(comboId);
            if (!(found instanceof LinearLayout)) return;
            LinearLayout combo = (LinearLayout) found;
            FrameLayout frame = frameOf(combo);
            if (combo.getChildCount() >= 2) {
                stack(combo);
            } else if (frame != null && OVERLAYS.containsKey(frame)) {
                restore(combo);
            }
            View contents = StatusBarViews.statusBarContents(combo);
            if (contents != null) watch(contents);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static FrameLayout frameOf(LinearLayout combo) {
        ViewParent parent = combo.getParent();
        return parent instanceof FrameLayout ? (FrameLayout) parent : null;
    }

    private static void stack(LinearLayout combo) {
        FrameLayout frame = frameOf(combo);
        if (frame == null) return;
        int viewH = referenceHeight(combo);
        if (viewH <= 0) return;
        View battery = StatusBarViews.findBattery(combo);
        int blockH = battery != null && battery.getHeight() > 0
                ? Math.round(battery.getHeight() * BATTERY_GLYPH_RATIO)
                : Math.round(viewH * FALLBACK_BLOCK_RATIO);
        StackedSignalDrawable overlay = OVERLAYS.get(frame);
        boolean added = false;
        if (overlay == null) {
            overlay = new StackedSignalDrawable();
            frame.getOverlay().add(overlay);
            OVERLAYS.put(frame, overlay);
            added = true;
        }
        int oldWidth = overlay.width, oldViewH = overlay.viewH;
        boolean changed = overlay.update(combo, blockH, viewH);
        hideNativeStack(frame, combo);
        if (added || oldWidth != overlay.width || oldViewH != overlay.viewH || combo.getWidth() != overlay.width) {
            remeasure(combo);
        }
        layoutOverlay(frame, overlay);
        if (changed || added) {
            frame.invalidate();
            StatusBarViews.log("draw " + overlay.describe() + " block=" + blockH + "px w=" + overlay.width + "px");
        }
    }

    private static void restore(LinearLayout combo) {
        FrameLayout frame = frameOf(combo);
        StackedSignalDrawable overlay = frame == null ? null : OVERLAYS.remove(frame);
        if (overlay != null) {
            frame.getOverlay().remove(overlay);
            StatusBarViews.log("restored single sim");
        }
        remeasure(combo);
    }

    /** 状态栏图标的行高：取 WiFi 图标的高度，没有 WiFi 时取信号图标的高度。 */
    private static int referenceHeight(LinearLayout combo) {
        Integer cached = BLOCK_HEIGHT.get(combo);
        if (cached != null) return cached;
        Object grand = combo.getParent() == null ? null : combo.getParent().getParent();
        View wifi = grand instanceof View ? StatusBarViews.findIn((View) grand, StatusBarViews.idWifiCombo) : null;
        int h = wifi != null ? wifi.getHeight() : 0;
        if (h <= 0) {
            View signal = combo.getChildCount() > 0
                    ? StatusBarViews.findIn(combo.getChildAt(0), StatusBarViews.idMobileSignal) : null;
            h = signal != null ? signal.getHeight() : 0;
        }
        if (h <= 0) return 0;
        BLOCK_HEIGHT.put(combo, h);
        return h;
    }

    private static void layoutOverlay(FrameLayout frame, StackedSignalDrawable overlay) {
        int top = Math.max(0, (frame.getHeight() - overlay.viewH) / 2);
        Rect b = overlay.getBounds();
        if (b.left == 0 && b.top == top && b.width() == overlay.width && b.height() == overlay.viewH) return;
        overlay.setBounds(0, top, overlay.width, top + overlay.viewH);
        frame.invalidate();
    }

    private static void watch(final View contents) {
        if (!WATCHED.add(contents)) return;
        contents.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                Prefs.poll(contents);
                WifiOverlay.sync(contents);
                try {
                    for (Map.Entry<FrameLayout, StackedSignalDrawable> e : OVERLAYS.entrySet()) {
                        FrameLayout frame = e.getKey();
                        StackedSignalDrawable overlay = e.getValue();
                        layoutOverlay(frame, overlay);
                        View combo = StatusBarViews.findIn(frame, StatusBarViews.idMobileCombo);
                        if (!(combo instanceof LinearLayout)) continue;
                        if (overlay.refreshTypeCenter((LinearLayout) combo)) frame.invalidate();
                        if (overlay.activityChanged((LinearLayout) combo)) schedule(combo);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
                return true;
            }
        });
    }
}
