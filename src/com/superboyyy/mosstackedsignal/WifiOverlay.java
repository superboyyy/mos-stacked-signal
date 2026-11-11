package com.superboyyy.mosstackedsignal;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import de.robv.android.xposed.XposedBridge;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用自绘 WiFi 替换系统图标：系统的扇形、上下行、热点小标、无网络小标都用 View 透明度藏起来，
 * 在 wifi_combo 的 overlay 上画 WifiDrawable；认不出系统图标或设置里关掉替换时还原。
 */
final class WifiOverlay {
    private static final Pattern WIFI_ICON = Pattern.compile("wifi(2)?_?(6|7)?(_?plus)?_signal_(\\d)");
    private static final float BATTERY_GLYPH_RATIO = 40f / 54f;
    private static final int WIFI_STANDARD_11AX = 6;
    private static final int WIFI_STANDARD_11BE = 8;
    private static final long STANDARD_CACHE_MS = 3000;

    private static final Map<FrameLayout, WifiDrawable> OVERLAYS = new WeakHashMap<>();
    private static final Map<View, String> INOUT_LAYOUT = new WeakHashMap<>();
    private static final Map<FrameLayout, Long> SIGNATURE = new WeakHashMap<>();
    private static final int[] LOCATION = new int[2];
    private static String cachedGeneration;
    private static long cachedGenerationAt;

    private WifiOverlay() { }

    static void sync(View contents) {
        try {
            if (StatusBarViews.idWifiCombo == 0 || StatusBarViews.idWifiSignal == 0) return;
            View comboView = StatusBarViews.findIn(contents, StatusBarViews.idWifiCombo);
            if (!(comboView instanceof FrameLayout)) return;
            FrameLayout combo = (FrameLayout) comboView;
            View signalView = StatusBarViews.findIn(combo, StatusBarViews.idWifiSignal);
            if (!(signalView instanceof ImageView)) return;
            ImageView signal = (ImageView) signalView;
            long signature = signature(combo, signal);
            Long last = SIGNATURE.get(combo);
            if (last != null && last == signature) return;
            SIGNATURE.put(combo, signature);
            Drawable icon = signal.getDrawable();

            int level = -1;
            String gen = null;
            boolean wide = false;
            String name = StatusBarViews.iconName(signal);
            Matcher m = name == null ? null : WIFI_ICON.matcher(name);
            if (m != null && m.find()) {
                level = Integer.parseInt(m.group(4));
                gen = m.group(2);
                wide = m.group(1) != null || m.group(2) != null;
            }
            if (level >= 0 && gen == null) {
                String fromStandard = generationFromStandard(combo.getContext());
                if (fromStandard != null) {
                    gen = fromStandard;
                    wide = true;
                }
            }
            if (!Prefs.wifiReplace) level = -1;

            WifiDrawable wd = OVERLAYS.get(combo);
            if (level < 0) {
                if (wd != null) {
                    combo.getOverlay().remove(wd);
                    OVERLAYS.remove(combo);
                }
                StatusBarViews.showByAlpha(signal);
                StatusBarViews.showByAlpha(StatusBarViews.findIn(combo, StatusBarViews.idWifiInout));
                StatusBarViews.showByAlpha(StatusBarViews.findIn(combo, StatusBarViews.idWifiHotspot));
                StatusBarViews.showByAlpha(StatusBarViews.findIn(combo, StatusBarViews.idWifiNoInternet));
                return;
            }
            if (wd == null) {
                wd = new WifiDrawable();
                combo.getOverlay().add(wd);
                OVERLAYS.put(combo, wd);
            }

            int color = StatusBarViews.tintColor(signal, 0xFFFFFFFF);
            RectF img = new RectF(icon.getBounds());
            signal.getImageMatrix().mapRect(img);
            img.offset(signal.getLeft() + signal.getPaddingLeft(), signal.getTop() + signal.getPaddingTop());
            Typeface typeface = StatusBarViews.clockTypeface(combo);

            float sevenTop = Float.NaN, fanBottom = Float.NaN;
            View battery = StatusBarViews.findBattery(combo);
            if (battery != null && battery.getHeight() > 0 && combo.getHeight() > 0) {
                float center = StatusBarViews.centerYOnScreen(battery);
                float half = battery.getHeight() * BATTERY_GLYPH_RATIO / 2f;
                int comboTop = StatusBarViews.topOnScreen(combo);
                sevenTop = center - half - comboTop;
                fanBottom = center + half - comboTop;
                wd.glyphH = half * 2f;
            }

            StatusBarViews.hideByAlpha(StatusBarViews.findIn(combo, StatusBarViews.idWifiHotspot));
            View noInternetView = StatusBarViews.findIn(combo, StatusBarViews.idWifiNoInternet);
            boolean noInternet = noInternetView != null && noInternetView.getVisibility() == View.VISIBLE;
            StatusBarViews.hideByAlpha(noInternetView);

            int activity = -1;
            RectF ink = wd.inoutInk;
            View inout = StatusBarViews.findIn(combo, StatusBarViews.idWifiInout);
            if (inout instanceof ImageView && inout.getVisibility() == View.VISIBLE && ((ImageView) inout).getDrawable() != null) {
                String inoutName = StatusBarViews.iconName((ImageView) inout);
                activity = SignalInfo.activityFromIconName(inoutName == null ? "" : inoutName);
                String layout = inout.getLeft() + "," + inout.getTop() + "," + inout.getWidth() + "," + inout.getHeight();
                if (!layout.equals(INOUT_LAYOUT.get(inout))) {
                    int[] b = StatusBarViews.inkBounds(inout, 16);
                    if (b != null) {
                        ink = new RectF(inout.getLeft() + b[2], inout.getTop() + b[0], inout.getLeft() + b[3], inout.getTop() + b[1]);
                        INOUT_LAYOUT.put(inout, layout);
                    }
                }
                StatusBarViews.hideByAlpha(inout);
            }

            boolean square = Prefs.wifiStyle == 1;
            float labelStroke = StackedSignalDrawable.labelStroke;
            float arrowHeight = StackedSignalDrawable.arrowHeight;
            Rect bounds = wd.getBounds();
            boolean changed = !near(sevenTop, wd.sevenTop) || !near(fanBottom, wd.fanBottom)
                    || wd.typeStroke != labelStroke || wd.level != level || !TextUtils.equals(wd.gen, gen)
                    || wd.wide != wide || wd.color != color || wd.typeface != typeface || !wd.img.equals(img)
                    || bounds.right != combo.getWidth() || bounds.bottom != combo.getHeight()
                    || wd.activity != activity || !wd.inoutInk.equals(ink) || wd.arrowHeight != arrowHeight
                    || wd.noInternet != noInternet || wd.square != square;
            if (changed) {
                wd.level = level;
                wd.sevenTop = sevenTop;
                wd.fanBottom = fanBottom;
                wd.typeStroke = labelStroke;
                wd.gen = gen;
                wd.wide = wide;
                wd.noInternet = noInternet;
                wd.square = square;
                wd.color = color;
                wd.typeface = typeface;
                wd.img.set(img);
                wd.activity = activity;
                wd.arrowHeight = arrowHeight;
                if (ink != wd.inoutInk) wd.inoutInk.set(ink);
                wd.setBounds(0, 0, combo.getWidth(), combo.getHeight());
                combo.invalidate();
            }
            StatusBarViews.hideByAlpha(signal);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    /** 每帧都会进来：先算一个便宜的状态签名，没变化就跳过后面的正则、取位置和量墨迹。 */
    private static long signature(FrameLayout combo, ImageView signal) {
        long h = 17;
        h = h * 31 + StatusBarViews.iconId(signal);
        h = h * 31 + Float.floatToIntBits(signal.getAlpha());
        h = h * 31 + signal.getLeft() * 131 + signal.getTop();
        h = h * 31 + signal.getWidth() * 131 + signal.getHeight();
        h = h * 31 + StatusBarViews.tintColor(signal, 0);
        h = h * 31 + combo.getWidth() * 131 + combo.getHeight();
        for (int id : new int[] {StatusBarViews.idWifiInout, StatusBarViews.idWifiHotspot, StatusBarViews.idWifiNoInternet}) {
            View v = StatusBarViews.findIn(combo, id);
            if (v == null) continue;
            h = h * 31 + v.getVisibility();
            h = h * 31 + Float.floatToIntBits(v.getAlpha());
            h = h * 31 + v.getLeft() * 131 + v.getTop();
            h = h * 31 + v.getWidth() * 131 + v.getHeight();
            if (v instanceof ImageView) h = h * 31 + StatusBarViews.iconId((ImageView) v);
        }
        View battery = StatusBarViews.findBattery(combo);
        if (battery != null) {
            battery.getLocationOnScreen(LOCATION);
            h = h * 31 + LOCATION[1] * 131 + battery.getHeight();
            combo.getLocationOnScreen(LOCATION);
            h = h * 31 + LOCATION[1];
        }
        h = h * 31 + (Prefs.wifiReplace ? 1 : 0) + Prefs.wifiStyle * 2;
        h = h * 31 + Float.floatToIntBits(StackedSignalDrawable.labelStroke);
        h = h * 31 + Float.floatToIntBits(StackedSignalDrawable.arrowHeight);
        h = h * 31 + System.identityHashCode(StatusBarViews.clockTypeface(combo));
        String generation = generationFromStandard(combo.getContext());
        h = h * 31 + (generation == null ? 0 : generation.hashCode());
        return h;
    }

    private static boolean near(float a, float b) {
        return Float.isNaN(a) ? Float.isNaN(b) : Math.abs(a - b) < 0.25f;
    }

    /** 系统在计费 WiFi（如手机热点）或不可上网时换成不带数字的图标，这时从 WifiManager 读 WiFi 6 / 7。 */
    private static String generationFromStandard(Context context) {
        long now = SystemClock.uptimeMillis();
        if (now - cachedGenerationAt < STANDARD_CACHE_MS) return cachedGeneration;
        cachedGenerationAt = now;
        cachedGeneration = null;
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm == null ? null : wm.getConnectionInfo();
            int standard = info == null ? 0 : info.getWifiStandard();
            if (standard == WIFI_STANDARD_11AX) cachedGeneration = "6";
            else if (standard == WIFI_STANDARD_11BE) cachedGeneration = "7";
        } catch (Throwable ignored) {
        }
        return cachedGeneration;
    }
}
