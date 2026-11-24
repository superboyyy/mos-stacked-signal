package com.superboyyy.mosstackedsignal;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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
    static final String KEY_WIFI_EXTRAS = "wifi_extras";
    static final String KEY_LABEL = "show_label";
    static final String KEY_LABEL_DATA = "label_data_only";

    // 显示网络类型标识（5G、4G 等）
    static volatile boolean showLabel = true;
    // 仅当使用流量（默认网络是蜂窝、不是 WiFi）时显示标识
    static volatile boolean labelDataOnly = false;
    // 当前默认网络是不是蜂窝数据（系统回调更新）
    static volatile boolean onCellular = true;
    // 5GA 只显示 5A
    static volatile boolean fiveA = false;
    // 替换系统 WiFi 图标
    static volatile boolean wifiReplace = true;
    // 信号样式：0 圆润、1 方正、2 感叹号圆润、3 感叹号方正
    static volatile int style = 0;
    // WiFi 样式：0 圆润、1 方正
    static volatile int wifiStyle = 0;
    // WiFi 图标上显示 6 / 7 等级和上下行箭头
    static volatile boolean wifiExtras = true;

    /** 样式值里的"方正"和"感叹号"两个维度。 */
    static boolean isSquare(int style) {
        return style == 1 || style == 3;
    }

    static boolean isIos(int style) {
        return style >= 2;
    }

    static int parseStyle(String val) {
        try {
            int v = Integer.parseInt(val);
            return v >= 0 && v <= 3 ? v : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Context app;
    private static volatile boolean loaded;
    private static boolean observing;
    private static boolean netWatching;
    private static long lastTry;
    private static String lastLog;

    private Prefs() { }

    /**
     * 在状态栏的 preDraw 里调（主线程）。第一次读到设置之前每 5 秒试一次
     * （开机早期模块的 Provider 可能还起不来），读到后只靠变化通知，不再轮询。
     */
    static void poll(View view) {
        if (loaded && observing && netWatching) return;
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
        if (!netWatching) watchNetwork(c);
        if (!loaded) loadAsync(c);
    }

    static boolean labelVisible() {
        return showLabel && (!labelDataOnly || onCellular);
    }

    // 跟着系统默认网络走：切到 WiFi / 切回流量时重画（只在打开了「仅当使用流量时显示」时才需要）
    private static void watchNetwork(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            onCellular = isCellular(cm.getNetworkCapabilities(cm.getActiveNetwork()));
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    setCellular(isCellular(caps));
                }

                @Override
                public void onLost(Network network) {
                    setCellular(false);
                }
            }, MAIN);
            netWatching = true;
        } catch (Throwable t) {
            netWatching = true;
            log("network watch failed: " + t);
        }
    }

    // VPN 的能力里带着底层网络的传输类型，所以开着 VPN 走流量也算蜂窝
    private static boolean isCellular(NetworkCapabilities caps) {
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private static void setCellular(boolean v) {
        if (v == onCellular) return;
        onCellular = v;
        log("default network cellular=" + v);
        if (labelDataOnly && showLabel) redraw();
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
        boolean a = false, w = true, lb = true, ld = false, we = true;
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
                else if (KEY_LABEL.equals(key)) lb = "1".equals(val);
                else if (KEY_LABEL_DATA.equals(key)) ld = "1".equals(val);
                else if (KEY_STYLE.equals(key)) s = parseStyle(val);
                else if (KEY_WIFI_STYLE.equals(key)) ws = "1".equals(val) ? 1 : 0;
                else if (KEY_WIFI_EXTRAS.equals(key)) we = "1".equals(val);
            }
        } catch (Throwable t) {
            log("prefs load failed: " + t);
            return false;
        } finally {
            if (cur != null) cur.close();
        }
        fiveA = a;
        showLabel = lb;
        labelDataOnly = ld;
        wifiReplace = w;
        style = s;
        wifiStyle = ws;
        wifiExtras = we;
        loaded = true;
        log("prefs label=" + lb + " dataOnly=" + ld + " cellular=" + onCellular + " fiveA=" + a + " wifi=" + w + " style=" + s + " wifiStyle=" + ws + " wifiExtras=" + we);
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
