package com.superboyyy.mosstackedsignal;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import de.robv.android.xposed.XposedHelpers;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

final class StatusBarViews {
    static final String SYSTEMUI = "com.android.systemui";
    static final String TAG = "STACKEDSTATUS";

    static int idMobileCombo;
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

    private static final Map<View, Integer> ORIGINAL_WIDTH = new WeakHashMap<>();
    private static final Map<View, Integer> ORIGINAL_HEIGHT = new WeakHashMap<>();
    private static final Map<String, String> LAST_LOG = new HashMap<>();
    private static final int[] LOCATION = new int[2];
    private static Typeface clockTypeface;

    private StatusBarViews() { }

    static void resolveIds(Resources res) {
        if (idsResolved) return;
        idMobileCombo = id(res, "mobile_combo");
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

    static View findIn(View root, int id) {
        return id == 0 ? null : root.findViewById(id);
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

    static String iconName(ImageView view) {
        Drawable d = view.getDrawable();
        if (d == null) return null;
        try {
            return view.getResources().getResourceEntryName(XposedHelpers.getIntField(d, "iconId"));
        } catch (Throwable t) {
            return null;
        }
    }

    static int tintColor(ImageView view, int fallback) {
        ColorStateList tint = view.getImageTintList();
        if (tint != null) return tint.getDefaultColor();
        try {
            return XposedHelpers.getIntField(view, "mDefaultColor");
        } catch (Throwable t) {
            return fallback;
        }
    }

    static void setWidth(View view, int width) {
        ViewGroup.LayoutParams lp = view == null ? null : view.getLayoutParams();
        if (lp == null) return;
        if (!ORIGINAL_WIDTH.containsKey(view)) ORIGINAL_WIDTH.put(view, lp.width);
        if (lp.width == width) return;
        lp.width = width;
        view.setLayoutParams(lp);
    }

    static void setSize(View view, int width, int height) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) return;
        if (!ORIGINAL_WIDTH.containsKey(view)) ORIGINAL_WIDTH.put(view, lp.width);
        if (!ORIGINAL_HEIGHT.containsKey(view)) ORIGINAL_HEIGHT.put(view, lp.height);
        if (lp.width == width && lp.height == height) return;
        lp.width = width;
        lp.height = height;
        view.setLayoutParams(lp);
    }

    static void restoreSize(View view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) return;
        Integer width = ORIGINAL_WIDTH.get(view);
        Integer height = ORIGINAL_HEIGHT.get(view);
        boolean changed = false;
        if (width != null && lp.width != width) {
            lp.width = width;
            changed = true;
        }
        if (height != null && lp.height != height) {
            lp.height = height;
            changed = true;
        }
        if (changed) view.setLayoutParams(lp);
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
