package com.superboyyy.mosstackedsignal;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.Map;

/**
 * 模块设置。设置页写在模块自己的私有存储里，SystemUI 通过模块的 SettingsProvider 读取；
 * 设置页改动后会通知 SystemUI 重读并重画，一般不用重启。
 * （以前用 LSPosed 的 xposedsharedprefs + MODE_WORLD_READABLE，新版 LSPosed 标为"已废弃"，已换掉。）
 */
final class Prefs {
    static final String PKG = "com.superboyyy.mosstackedsignal";
    static final String FILE = "settings";
    static final Uri URI = Uri.parse("content://" + PKG + ".settings/all");

    static final String KEY_FIVE_A = "five_a";
    static final String KEY_WIFI = "wifi_replace";
    static final String KEY_STYLE = "bar_style";
    static final String KEY_WIFI_STYLE = "wifi_style";

    // 5GA 只显示 5A
    static volatile boolean fiveA = false;
    // 替换系统 WiFi 图标
    static volatile boolean wifiReplace = true;
    // 信号格样式：0 圆润、1 方正
    static volatile int style = 0;
    // WiFi 样式：0 圆润、1 方正
    static volatile int wifiStyle = 0;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Context app;
    private static volatile boolean loaded;
    private static boolean observing;
    private static long lastTry;
    private static String lastLog;

    private Prefs() { }

    /**
     * 在状态栏的 preDraw 里调（主线程）。第一次读到设置之前每 5 秒试一次
     * （开机早期模块的 Provider 可能还起不来），读到后只靠变化通知，不再轮询。
     */
    static void poll(View view) {
        if (loaded && observing) return;
        long now = SystemClock.uptimeMillis();
        if (lastTry != 0 && now - lastTry < 5000) return;
        lastTry = now;
        if (app == null) {
            Context c = view.getContext();
            app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        }
        final Context c = app;
        if (!observing) {
            try {
                c.getContentResolver().registerContentObserver(URI, true, new ContentObserver(MAIN) {
                    @Override
                    public void onChange(boolean selfChange) {
                        loadAsync(c);
                    }
                });
                observing = true;
            } catch (Throwable t) {
                log("prefs observe failed: " + t);
            }
        }
        if (!loaded) loadAsync(c);
    }

    // 查询会拉起模块进程，放到后台线程，读完回主线程重画
    private static void loadAsync(final Context c) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (load(c)) MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        redraw();
                    }
                });
            }
        }, "mosstackedsignal-prefs").start();
    }

    private static boolean load(Context c) {
        boolean a = false, w = true;
        int s = 0, ws = 0;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(URI, null, null, null, null);
            if (cur == null) {
                log("prefs provider unavailable");
                return false;
            }
            while (cur.moveToNext()) {
                String key = cur.getString(0);
                String val = cur.getString(1);
                if (KEY_FIVE_A.equals(key)) a = "1".equals(val);
                else if (KEY_WIFI.equals(key)) w = "1".equals(val);
                else if (KEY_STYLE.equals(key)) s = "1".equals(val) ? 1 : 0;
                else if (KEY_WIFI_STYLE.equals(key)) ws = "1".equals(val) ? 1 : 0;
            }
        } catch (Throwable t) {
            log("prefs load failed: " + t);
            return false;
        } finally {
            if (cur != null) cur.close();
        }
        fiveA = a;
        wifiReplace = w;
        style = s;
        wifiStyle = ws;
        loaded = true;
        log("prefs fiveA=" + a + " wifi=" + w + " style=" + s + " wifiStyle=" + ws);
        return true;
    }

    // 只在内容变化时打日志
    private static synchronized void log(String msg) {
        if (msg.equals(lastLog)) return;
        lastLog = msg;
        Log.w(StatusBarViews.TAG, msg);
    }

    // 设置变了：让堆叠信号重新排版重画；状态栏重绘时 preDraw 里的 syncWifi 会按新样式重画 WiFi
    static void redraw() {
        try {
            for (Map.Entry<FrameLayout, StackedSignalDrawable> e : SignalStacker.OVERLAYS.entrySet()) {
                FrameLayout frame = e.getKey();
                View g = StatusBarViews.findIn(frame, StatusBarViews.idMobileCombo);
                if (g instanceof LinearLayout) SignalStacker.schedule(g);
                frame.invalidate();
            }
            for (View v : SignalStacker.WATCHED) {
                if (v != null) v.invalidate();
            }
        } catch (Throwable t) {
            log("prefs redraw failed: " + t);
        }
    }
}
