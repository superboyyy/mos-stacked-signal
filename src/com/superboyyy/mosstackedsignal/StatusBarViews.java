package com.superboyyy.mosstackedsignal;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

final class StatusBarViews {
    static final String SYSTEMUI = "com.android.systemui";
    static final String TAG = "STACKEDSTATUS";

    static int idMobileCombo;
    static int idNativeStack;
    static int idMobileSignal;
    static int idMobileInout;
    static int idWifiCombo;
    static int idWifiSignal;
    static int idWifiInout;
    static int idWifiHotspot;
    static int idWifiNoInternet;
    static int idBattery;
    static int idClock;
    static int idContents;
    private static boolean idsResolved;

    private static final Map<View, SparseArray<WeakReference<View>>> FIND_CACHE = new WeakHashMap<>();
    private static final Map<View, WeakReference<View>> BATTERY_OF = new WeakHashMap<>();
    private static final Map<String, Map<Class<?>, Object>> FIELDS = new HashMap<>();
    private static final Map<String, String> LAST_LOG = new HashMap<>();
    private static final int[] LOCATION = new int[2];
    private static Typeface clockTypeface;

    private StatusBarViews() { }

    static void resolveIds(Resources res) {
        if (idsResolved) return;
        idMobileCombo = id(res, "mobile_combo");
        idNativeStack = id(res, "hn_stacked_signal_icon");
        idMobileSignal = id(res, "mobile_signal");
        idMobileInout = id(res, "mobile_inout");
        idWifiCombo = id(res, "wifi_combo");
        idWifiSignal = id(res, "wifi_signal");
        idWifiInout = id(res, "wifi_inout");
        idWifiHotspot = id(res, "wifi_hotspot");
        idWifiNoInternet = id(res, "wifi_noInternet");
        idBattery = id(res, "battery");
        idClock = id(res, "clock");
        idContents = id(res, "status_bar_contents");
        idsResolved = true;
        log("ids combo=0x" + Integer.toHexString(idMobileCombo));
    }

    private static int id(Resources res, String name) {
        return res.getIdentifier(name, "id", SYSTEMUI);
    }

    /** 每帧都要找同几个 View，按 (根 View, id) 缓存，用前确认它还挂在这个根下面。 */
    static View findIn(View root, int id) {
        if (id == 0 || root == null) return null;
        SparseArray<WeakReference<View>> cache = FIND_CACHE.get(root);
        if (cache != null) {
            WeakReference<View> ref = cache.get(id);
            View cached = ref == null ? null : ref.get();
            if (cached != null && cached.getId() == id && isUnder(cached, root)) return cached;
        }
        View found = root.findViewById(id);
        if (found != null) {
            if (cache == null) {
                cache = new SparseArray<>();
                FIND_CACHE.put(root, cache);
            }
            cache.put(id, new WeakReference<>(found));
        }
        return found;
    }

    private static boolean isUnder(View view, View root) {
        for (int depth = 0; view != null && depth < 32; depth++) {
            if (view == root) return true;
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    static View ancestorWithId(View view, int id) {
        if (id == 0) return null;
        for (ViewParent p = view.getParent(); p instanceof View; p = p.getParent()) {
            if (((View) p).getId() == id) return (View) p;
        }
        return null;
    }

    static View statusBarContents(View view) {
        if (idContents == 0) resolveIds(view.getResources());
        return ancestorWithId(view, idContents);
    }

    static View findBattery(View view) {
        if (idBattery == 0) return null;
        WeakReference<View> ref = BATTERY_OF.get(view);
        View cached = ref == null ? null : ref.get();
        if (cached != null && cached.getParent() != null && cached.getRootView() == view.getRootView()) return cached;
        View found = searchBattery(view);
        if (found != null) BATTERY_OF.put(view, new WeakReference<>(found));
        return found;
    }

    private static View searchBattery(View view) {
        for (ViewParent p = view.getParent(); p instanceof View; p = p.getParent()) {
            View parent = (View) p;
            View battery = parent.findViewById(idBattery);
            if (battery != null) return battery;
            if (parent.getId() == idContents) break;
        }
        return null;
    }

    static Typeface clockTypeface(View view) {
        if (clockTypeface != null) return clockTypeface;
        View contents = statusBarContents(view);
        View clock = contents == null ? null : findIn(contents, idClock);
        if (clock instanceof TextView) clockTypeface = ((TextView) clock).getTypeface();
        return clockTypeface;
    }

    static float centerYOnScreen(View view) {
        view.getLocationOnScreen(LOCATION);
        return LOCATION[1] + view.getHeight() / 2f;
    }

    static int topOnScreen(View view) {
        view.getLocationOnScreen(LOCATION);
        return LOCATION[1];
    }

    /** 图标的资源 id；读不到时返回 drawable 的身份哈希，只能用来比较是否换了图标。 */
    static int iconId(ImageView view) {
        Drawable d = view.getDrawable();
        return d == null ? 0 : intFieldOr(d, "iconId", System.identityHashCode(d));
    }

    static String iconName(ImageView view) {
        Drawable d = view.getDrawable();
        int id = d == null ? 0 : intFieldOr(d, "iconId", 0);
        if (id == 0) return null;
        try {
            return view.getResources().getResourceEntryName(id);
        } catch (Throwable t) {
            return null;
        }
    }

    static int tintColor(ImageView view, int fallback) {
        ColorStateList tint = view.getImageTintList();
        if (tint != null) return tint.getDefaultColor();
        return intFieldOr(view, "mDefaultColor", fallback);
    }

    /** 反射读 int 字段，字段不存在时返回默认值而不抛异常（每帧调用，异常很贵）。 */
    static int intFieldOr(Object target, String name, int fallback) {
        if (target == null) return fallback;
        Map<Class<?>, Object> byClass = FIELDS.get(name);
        if (byClass == null) {
            byClass = new HashMap<>();
            FIELDS.put(name, byClass);
        }
        Class<?> cls = target.getClass();
        Object field = byClass.get(cls);
        if (field == null) {
            field = Boolean.FALSE;
            for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    field = f;
                    break;
                } catch (Throwable ignored) {
                }
            }
            byClass.put(cls, field);
        }
        if (!(field instanceof Field)) return fallback;
        try {
            return ((Field) field).getInt(target);
        } catch (Throwable t) {
            return fallback;
        }
    }

    static void hideByAlpha(View view) {
        if (view != null && view.getAlpha() != 0f) view.setAlpha(0f);
    }

    static void showByAlpha(View view) {
        if (view != null && view.getAlpha() == 0f) view.setAlpha(1f);
    }

    /** 返回 {top, bottom, left, right}，按 alpha > threshold 的像素算；没有墨迹时返回 null。 */
    static int[] inkBounds(View view, int alphaThreshold) {
        int w = view.getWidth(), h = view.getHeight();
        if (w <= 0 || h <= 0 || w > 2048 || h > 2048) return null;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        int top = -1, bottom = -1, left = w, right = -1;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                if ((row[x] >>> 24) > alphaThreshold) {
                    if (top < 0) top = y;
                    bottom = y;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
        }
        bitmap.recycle();
        return top < 0 ? null : new int[] {top, bottom + 1, left, right + 1};
    }

    static void log(String msg) {
        Log.w(TAG, msg);
    }

    static void logOnChange(String key, String value) {
        if (value.equals(LAST_LOG.get(key))) return;
        LAST_LOG.put(key, value);
        log(key + " " + value);
    }
}
