package com.superboyyy.mosstackedsignal;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** 模块设置页（在 LSPosed 里点模块打开）。界面直接用代码搭，不依赖 AndroidX。 */
public class SettingsActivity extends Activity {

    private SharedPreferences prefs;
    private final RadioButton[] styleButtons = new RadioButton[2];
    private final RadioButton[] wifiStyleButtons = new RadioButton[2];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        if (!isNight() && android.os.Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
            int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            getWindow().getInsetsController().setSystemBarsAppearance(light, light);
        }
        // 设置存在模块私有的设备加密存储里，SystemUI 通过 SettingsProvider 读
        prefs = SettingsProvider.prefs(this);
        setContentView(buildUi());
    }

    private View buildUi() {
        int fg = themeColor(android.R.attr.textColorPrimary);
        int fg2 = themeColor(android.R.attr.textColorSecondary);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(root);
        scroll.setBackgroundColor(isNight() ? 0xFF000000 : 0xFFF1F3F5);
        // targetSdk 35+ 强制全面屏绘制：内容要让开状态栏和导航栏
        scroll.setClipToPadding(false);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return android.view.WindowInsets.CONSUMED;
        });

        // 大标题用桌面图标名，副标题是模块名（LSPosed 里显示的那个）
        TextView title = text(String.valueOf(getTitle()), 22, fg, true);
        root.addView(title);
        TextView sub = text(String.valueOf(getApplicationInfo().loadLabel(getPackageManager())), 14, fg2, false);
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        // 系统自带的信号堆叠（部分新版本才有）开着会和本模块叠在一起，提醒关闭
        TextView stackTip = text("如果你的系统设置里有「信号堆叠」开关，请保持关闭。", 13, fg2, false);
        stackTip.setPadding(0, 0, 0, dp(16));
        root.addView(stackTip);

        // 显示
        root.addView(sectionTitle("显示", fg2));
        LinearLayout card1 = card();
        card1.addView(switchRow("5GA 显示为 5A", "网络类型是 5GA 时，标识只显示 5A", Prefs.KEY_FIVE_A, false, fg, fg2));
        card1.addView(divider());
        card1.addView(switchRow("替换 WiFi 图标", "关闭后使用系统原生的 WiFi 图标", Prefs.KEY_WIFI, true, fg, fg2));
        root.addView(card1);

        // 信号样式
        root.addView(sectionTitle("信号样式", fg2));
        int cur = prefs.getInt(Prefs.KEY_STYLE, 0) == 1 ? 1 : 0;
        LinearLayout card2 = card();
        card2.addView(styleRow(new StylePreview(this, 0, fg), Prefs.KEY_STYLE, 0, styleButtons, "圆润（默认）", "圆头竖条", cur == 0, fg, fg2));
        card2.addView(divider());
        card2.addView(styleRow(new StylePreview(this, 1, fg), Prefs.KEY_STYLE, 1, styleButtons, "方正", "直角竖条", cur == 1, fg, fg2));
        root.addView(card2);

        // WiFi 样式（只在「替换 WiFi 图标」打开时生效）
        root.addView(sectionTitle("WiFi 样式", fg2));
        int wcur = prefs.getInt(Prefs.KEY_WIFI_STYLE, 0) == 1 ? 1 : 0;
        LinearLayout card3 = card();
        card3.addView(styleRow(new WifiPreview(this, false, fg), Prefs.KEY_WIFI_STYLE, 0, wifiStyleButtons, "圆润（默认）", "圆点、圆头弧线", wcur == 0, fg, fg2));
        card3.addView(divider());
        card3.addView(styleRow(new WifiPreview(this, true, fg), Prefs.KEY_WIFI_STYLE, 1, wifiStyleButtons, "方正", "尖角扇形、直边扇环", wcur == 1, fg, fg2));
        root.addView(card3);

        TextView note = text("设置改动一般 1 秒内自动生效；如果状态栏没有变化或产生问题，请执行软重启。", 13, fg2, false);
        note.setPadding(dp(4), dp(8), dp(4), 0);
        root.addView(note);
        return scroll;
    }

    private View switchRow(String label, String desc, final String key, boolean def, int fg, int fg2) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(12), dp(14));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(label, 16, fg, false));
        TextView d = text(desc, 13, fg2, false);
        d.setPadding(0, dp(2), 0, 0);
        texts.addView(d);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean(key, on).apply();
            notifyChanged();
        });
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        return row;
    }

    private View styleRow(View preview, final String key, final int value, final RadioButton[] group, String label, String desc,
                          boolean checked, int fg, int fg2) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(12), dp(12));
        row.addView(preview, new LinearLayout.LayoutParams(dp(76), dp(38)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(14), 0, 0, 0);
        texts.addView(text(label, 16, fg, false));
        TextView d = text(desc, 13, fg2, false);
        d.setPadding(0, dp(2), 0, 0);
        texts.addView(d);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        RadioButton rb = new RadioButton(this);
        rb.setChecked(checked);
        rb.setClickable(false);
        group[value] = rb;
        row.addView(rb);
        row.setOnClickListener(v -> {
            for (int i = 0; i < group.length; i++) group[i].setChecked(i == value);
            prefs.edit().putInt(key, value).apply();
            notifyChanged();
        });
        return row;
    }

    // 通知 SystemUI 重读设置
    private void notifyChanged() {
        try {
            getContentResolver().notifyChange(Prefs.URI, null);
        } catch (Throwable ignored) {
        }
    }

    private TextView sectionTitle(String s, int color) {
        TextView t = text(s, 13, color, false);
        t.setPadding(dp(4), dp(8), 0, dp(8));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(isNight() ? 0xFF1F1F22 : 0xFFFFFFFF);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(isNight() ? 0x22FFFFFF : 0x14000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private int themeColor(int attr) {
        TypedArray a = obtainStyledAttributes(new int[] {attr});
        int c = a.getColor(0, isNight() ? 0xFFFFFFFF : 0xFF000000);
        a.recycle();
        return c;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** 样式预览：两行 "5G + 信号格"，画法和状态栏里一致（0 圆润、1 方正）。 */
    static final class StylePreview extends View {
        private final int style;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        StylePreview(Context c, int style, int color) {
            super(c);
            this.style = style;
            paint.setColor(color);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight(), w = getWidth();
            // 图形缩小到 80%，左对齐、垂直居中；控件本身大小不变，不影响行高和右边文字的位置
            canvas.save();
            canvas.scale(0.8f, 0.8f, 0f, h / 2f);
            float gapRow = h * 0.12f, rowH = (h - gapRow) / 2f;
            float barW = rowH * 0.4f, space = barW * 0.45f;
            paint.setTextSize(rowH * 1.3f);
            float labelW = paint.measureText("5G");
            float barsW = 5 * barW + 4 * space;
            float x0 = (w - (labelW + rowH * 0.25f + barsW)) / 2f;
            int[] levels = {4, 3};
            for (int r = 0; r < 2; r++) {
                float bottom = rowH * (r + 1) + gapRow * r;
                paint.setAlpha(255);
                canvas.drawText("5G", x0, bottom, paint);
                float x = x0 + labelW + rowH * 0.25f;
                float minH = style == 1 ? rowH * 0.3f : Math.max(barW, rowH * 0.35f);
                float radius = style == 1 ? 0f : barW / 2f;
                for (int i = 0; i < 5; i++) {
                    float bh = minH + (rowH - minH) * i / 4f;
                    rect.set(x, bottom - bh, x + barW, bottom);
                    paint.setAlpha(i < levels[r] ? 255 : 77);
                    canvas.drawRoundRect(rect, radius, radius, paint);
                    x += barW + space;
                }
            }
            canvas.restore();
        }
    }

    /** WiFi 样式预览：亮三格、最外一条变暗，画法和状态栏里一致（WifiFan）。 */
    static final class WifiPreview extends View {
        private final boolean square;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private final RectF oval = new RectF();
        private final int color;

        WifiPreview(Context c, boolean square, int color) {
            super(c);
            this.square = square;
            this.color = color;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight();
            // 和信号示意图一样：缩到 80%，左对齐、垂直居中
            canvas.save();
            canvas.scale(0.8f, 0.8f, 0f, h / 2f);
            float f = h * 0.8f;
            float bottom = (h + f) / 2f;
            float halfW = square ? f * WifiFan.SQ_SIN : f * 0.64f;
            float cx = getWidth() * 0.03f + halfW;
            WifiFan.draw(canvas, paint, path, oval, square, cx, bottom - WifiFan.dotR(f), f, 3, color,
                    Color.alpha(color), Math.round(Color.alpha(color) * 0.3f), null, 0f);
            canvas.restore();
        }
    }
}
