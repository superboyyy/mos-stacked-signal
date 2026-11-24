package com.superboyyy.mosstackedsignal;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;

import java.util.Arrays;

/**
 * 两行堆叠的信号：每行 "网络类型 + 信号格"，流量箭头画在信号格左上方的空白里。
 * 感叹号样式（IosSignal）：上排竖条是卡 1、下排的点是卡 2；网络类型画在左上方，下面一列 ^ˇ 是上下行。
 */
final class StackedSignalDrawable extends Drawable {
    private static final float SIDE_PAD_DP = 1.75f;
    private static final float LABEL_GAP_DP = 2f;
    private static final float ROW_GAP_RATIO = 0.12f;
    private static final float ROWS_RATIO = 0.88f;
    private static final float BAR_WIDTH_RATIO = 0.4f;
    private static final float BAR_SPACE_RATIO = 0.45f;
    private static final float LABEL_STROKE_RATIO = 0.035f;
    private static final float LABEL_JUSTIFY_LIMIT = 0.12f;
    private static final float DIM_ALPHA = 0.3f;
    private static final float ARROW_NUDGE_X = -0.6f;
    private static final float MIN_ARROW_HEIGHT = 3f;
    static final int LABEL_WEIGHT = 650;
    /** 感叹号样式的列数。 */
    private static final int IOS_COLUMNS = 5;
    /** 感叹号样式网络类型的字重（不描边，比普通样式的 650 + 描边略粗一点）。 */
    private static final int IOS_LABEL_WEIGHT = 750;
    /** 感叹号样式：网络类型墨迹高 = 信号高度的这么多（再大第一列竖条就要压矮，和下面的点不一样高了）。 */
    private static final float IOS_LABEL_RATIO = 0.45f;
    /** 感叹号样式：网络类型和被它盖住的竖条之间的空隙。 */
    private static final float IOS_LABEL_MARGIN = 0.05f;
    /** 感叹号样式的 ^ˇ：宽、线粗、和第一列竖条的固定间距（都按信号高度算，不随网络类型的宽度变）。 */
    private static final float IOS_ARROW_W = 0.24f;
    private static final float IOS_ARROW_STROKE = 0.085f;
    private static final float IOS_ARROW_GAP = 0.08f;

    /** 标识文字的描边宽度，WiFi 上的 "6" / "7" 跟着用，粗细一致。 */
    static volatile float labelStroke;
    /** 流量箭头高度，WiFi 箭头跟着用。 */
    static volatile float arrowHeight;

    int blockH;
    int viewH;
    int width;
    /** 飞行模式：和系统一样不显示信号，宽度收成 0、什么都不画。 */
    boolean hidden;
    private int pad;
    private int gap;
    private int labelW;
    private float typeCenterY;
    private int color = -1;
    private int[] levels = new int[0];
    private int[] counts = new int[0];
    private String[] labels = new String[0];
    private int[] activity = new int[0];
    private int[] inoutIds = new int[0];
    private float[] labelLeft = new float[0];
    private float[] labelSpacing = new float[0];
    private float[] labelShift = new float[0];

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF rect = new RectF();
    private final RectF inkRect = new RectF();
    private final Path inkPath = new Path();
    private final Rect textBounds = new Rect();
    private final ArrowPainter arrows = new ArrowPainter();

    // 感叹号样式
    private boolean ios;
    private float iosBarsW;
    private float[] iosBarH = new float[0];
    /** 数据卡的网络类型（不显示标识时为 null）和它的墨迹尺寸。 */
    private String iosLabel;
    private float iosLabelW, iosLabelInkLeft, iosLabelInkTop, iosLabelInkH;
    /** 网络类型和 ^ˇ 往信号格左边伸出去的宽度。 */
    private float iosLeftExtra;
    /** 数据卡的上下行状态；系统显示数据卡箭头时（和圆润 / 方正一样）才画 ^ˇ。 */
    private int iosAct = -1;
    private boolean iosArrows;
    private final Paint iosLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path arrowPath = new Path();

    boolean update(LinearLayout combo, int newBlockH, int newViewH) {
        float density = combo.getResources().getDisplayMetrics().density;
        int n = combo.getChildCount();
        int[] newLevels = new int[n];
        int[] newCounts = new int[n];
        String[] newLabels = new String[n];
        for (int i = 0; i < n; i++) {
            View sim = combo.getChildAt(i);
            int[] level = SignalInfo.level(sim);
            int count = level != null ? level[1] : SignalInfo.barCount;
            newCounts[i] = count;
            newLevels[i] = level != null ? Math.max(0, Math.min(level[0], count)) : 0;
            // 关掉「显示网络类型标识」，或打开「仅当使用流量时显示」且当前走 WiFi 时，不画标识，信号格左移、整体变窄
            newLabels[i] = Prefs.labelVisible() ? SignalInfo.label(sim) : null;
        }
        int[] newActivity = SignalInfo.activities(combo);
        int newColor = SignalInfo.color(combo);
        boolean airplane = SignalInfo.airplaneMode(combo);
        // 感叹号样式只显示数据卡（有上下行箭头的那张）的网络类型，同样受「显示网络类型标识」「仅当使用流量时显示」控制
        boolean newIos = Prefs.isIos(Prefs.style);
        String newIosLabel = null;
        int newIosAct = -1;
        if (newIos && !airplane && n > 0) {
            int ds = 0;
            for (int k = 0; k < newActivity.length; k++) {
                if (newActivity[k] >= 0) {
                    ds = k;
                    break;
                }
            }
            ds = Math.min(ds, n - 1);
            newIosLabel = newLabels[ds];
            newIosAct = ds < newActivity.length ? newActivity[ds] : -1;
        }
        boolean newIosArrows = newIosLabel != null && newIosAct >= 0;
        boolean changed = newIos != ios || !TextUtils.equals(newIosLabel, iosLabel) || newIosArrows != iosArrows
                || airplane != hidden || newBlockH != blockH || newViewH != viewH || newColor != color
                || !Arrays.equals(newLevels, levels) || !Arrays.equals(newCounts, counts)
                || !Arrays.equals(newLabels, labels) || !Arrays.equals(newActivity, activity);
        activity = newActivity;
        ios = newIos;
        iosLabel = newIosLabel;
        iosAct = newIosAct;
        iosArrows = newIosArrows;
        blockH = newBlockH;
        viewH = newViewH;
        color = newColor;
        levels = newLevels;
        counts = newCounts;
        labels = newLabels;
        pad = Math.round(SIDE_PAD_DP * density);
        gap = Math.round(LABEL_GAP_DP * density);
        typeCenterY = batteryCenter(combo);

        Typeface clock = StatusBarViews.clockTypeface(combo);
        textPaint.setTypeface(Typeface.create(clock != null ? clock : Typeface.DEFAULT, LABEL_WEIGHT, false));
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setStrokeWidth(0f);
        textPaint.setTextSize(100f);
        textPaint.getTextBounds("5G", 0, 2, textBounds);
        float rowH = rowH();
        float stroke = LABEL_STROKE_RATIO * rowH;
        labelStroke = stroke;
        if (textBounds.height() > 0) textPaint.setTextSize((rowH - stroke) * 100f / textBounds.height());
        textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        textPaint.setStrokeWidth(stroke);
        textPaint.setStrokeJoin(Paint.Join.ROUND);
        textPaint.setLetterSpacing(0f);

        labelW = 0;
        labelLeft = new float[n];
        labelSpacing = new float[n];
        labelShift = new float[n];
        float[] inkWidth = new float[n];
        float maxInk = 0f;
        for (int i = 0; i < n; i++) {
            if (newLabels[i] == null) continue;
            textInk(newLabels[i]);
            labelLeft[i] = inkRect.left;
            inkWidth[i] = inkRect.width();
            maxInk = Math.max(maxInk, inkWidth[i]);
            labelW = Math.max(labelW, (int) Math.ceil(inkRect.width() + stroke));
        }
        for (int i = 0; i < n; i++) {
            String label = newLabels[i];
            if (label == null) continue;
            float diff = maxInk - inkWidth[i];
            if (diff <= 0.05f) continue;
            if (label.length() < 2 || diff > LABEL_JUSTIFY_LIMIT * rowH) {
                labelShift[i] = diff;
                continue;
            }
            float spacing = diff / (label.length() - 1) / textPaint.getTextSize();
            textPaint.setLetterSpacing(spacing);
            textInk(label);
            float grown = inkRect.width() - inkWidth[i];
            if (grown > 0.01f) {
                spacing *= diff / grown;
                textPaint.setLetterSpacing(spacing);
                textInk(label);
            }
            labelSpacing[i] = spacing;
            labelLeft[i] = inkRect.left;
            textPaint.setLetterSpacing(0f);
        }
        hidden = airplane;
        width = airplane ? 0 : pad + (labelW > 0 ? labelW + gap : 0) + barsWidth() + pad;
        if (ios && !airplane) layoutIos(combo);
        if (changed) {
            StatusBarViews.log("labels=" + Arrays.toString(newLabels) + " activity=" + Arrays.toString(newActivity)
                    + " ink=" + Arrays.toString(inkWidth) + " spacing=" + Arrays.toString(labelSpacing)
                    + " shift=" + Arrays.toString(labelShift));
        }
        return changed;
    }

    /** 感叹号样式：5 列，总宽和普通样式不显示标识时一样；网络类型右边和第一列竖条右边对齐，往左伸出去。 */
    private void layoutIos(LinearLayout combo) {
        iosBarsW = IOS_COLUMNS * barW() + (IOS_COLUMNS - 1) * barSpace();
        if (iosBarH.length != IOS_COLUMNS) iosBarH = new float[IOS_COLUMNS];
        float colW = IosSignal.colWidth(iosBarsW, IOS_COLUMNS);
        float labelH = 0f, margin = 0f;
        iosLeftExtra = 0f;
        if (iosLabel != null) {
            Typeface clock = StatusBarViews.clockTypeface(combo);
            iosLabelPaint.setTypeface(Typeface.create(clock != null ? clock : Typeface.DEFAULT, IOS_LABEL_WEIGHT, false));
            iosLabelPaint.setStyle(Paint.Style.FILL);
            iosLabelPaint.setTextAlign(Paint.Align.LEFT);
            iosLabelPaint.setTextSize(100f);
            iosLabelPaint.getTextBounds("5G", 0, 2, textBounds);
            if (textBounds.height() > 0) iosLabelPaint.setTextSize(blockH * IOS_LABEL_RATIO * 100f / textBounds.height());
            inkPath.reset();
            iosLabelPaint.getTextPath(iosLabel, 0, iosLabel.length(), 0f, 0f, inkPath);
            inkPath.computeBounds(inkRect, true);
            iosLabelInkLeft = inkRect.left;
            iosLabelInkTop = inkRect.top;
            iosLabelInkH = inkRect.height();
            iosLabelW = inkRect.width();
            labelH = iosLabelInkH;
            margin = blockH * IOS_LABEL_MARGIN;
            iosLeftExtra = Math.max(iosLabelW - colW, iosArrows ? blockH * (IOS_ARROW_W + IOS_ARROW_STROKE / 2f + IOS_ARROW_GAP) : 0f);
        }
        // 网络类型只盖住第一列；第一列竖条比它的下沿低，一般不用压矮
        IosSignal.barHeights(iosBarH, blockH, iosBarsW, IOS_COLUMNS, iosLabel != null ? colW : 0f, labelH, margin);
        width = Math.round(pad + iosLeftExtra + iosBarsW + pad);
    }

    private void textInk(String text) {
        inkPath.reset();
        textPaint.getTextPath(text, 0, text.length(), 0f, 0f, inkPath);
        inkPath.computeBounds(inkRect, true);
    }

    private float rowH() {
        return blockH * ROWS_RATIO / 2f;
    }

    private float barW() {
        return rowH() * BAR_WIDTH_RATIO;
    }

    private float barSpace() {
        return barW() * BAR_SPACE_RATIO;
    }

    private int barsWidth() {
        int max = 4;
        for (int c : counts) max = Math.max(max, c);
        return Math.round(max * barW() + (max - 1) * barSpace());
    }

    private float minBarH(float rowH) {
        return Prefs.isSquare(Prefs.style) ? rowH * 0.3f : Math.max(barW(), rowH * 0.35f);
    }

    private float barRadius() {
        return Prefs.isSquare(Prefs.style) ? 0f : barW() / 2f;
    }

    /** 电池图形的垂直中心，相对于本 drawable 顶部；标识和信号格以它为中心。 */
    private float batteryCenter(LinearLayout combo) {
        View battery = StatusBarViews.findBattery(combo);
        Object parent = combo.getParent();
        if (battery != null && parent instanceof View) {
            View frame = (View) parent;
            if (frame.getHeight() > 0 && battery.getHeight() > 0) {
                float center = StatusBarViews.centerYOnScreen(battery);
                return center - (StatusBarViews.topOnScreen(frame) + (frame.getHeight() - viewH) / 2f);
            }
        }
        return viewH / 2f;
    }

    boolean refreshTypeCenter(LinearLayout combo) {
        float center = batteryCenter(combo);
        if (Math.abs(center - typeCenterY) < 0.25f) return false;
        typeCenterY = center;
        return true;
    }

    boolean activityChanged(LinearLayout combo) {
        int n = combo.getChildCount();
        if (inoutIds.length != n) inoutIds = new int[n];
        boolean iconsChanged = false;
        for (int i = 0; i < n; i++) {
            int id = SignalInfo.inoutIconId(combo.getChildAt(i));
            if (id != inoutIds[i]) {
                inoutIds[i] = id;
                iconsChanged = true;
            }
        }
        return iconsChanged && !Arrays.equals(SignalInfo.activities(combo), activity);
    }

    String describe() {
        return "levels=" + Arrays.toString(levels) + "/" + Arrays.toString(counts)
                + " labels=" + Arrays.toString(labels) + " color=#" + Integer.toHexString(color);
    }

    @Override
    public void draw(Canvas canvas) {
        if (hidden) return;
        Rect bounds = getBounds();
        float top = bounds.top + typeCenterY - blockH / 2f;
        if (ios) {
            drawIos(canvas, bounds.left + pad + iosLeftExtra, top);
            return;
        }
        float rowH = rowH();
        int rows = Math.min(levels.length, 2);
        int alpha = color >>> 24;
        float labelX = bounds.left + pad;
        float barsX = labelX + (labelW > 0 ? labelW + gap : 0);
        textPaint.setColor(color);
        for (int r = 0; r < rows; r++) {
            float baseline = r == 0 ? top + rowH : top + blockH;
            if (labels[r] != null) {
                float halfStroke = textPaint.getStrokeWidth() / 2f;
                textPaint.setLetterSpacing(r < labelSpacing.length ? labelSpacing[r] : 0f);
                float shift = r < labelShift.length ? labelShift[r] : 0f;
                canvas.drawText(labels[r], labelX - labelLeft[r] + halfStroke + shift, baseline - halfStroke, textPaint);
                textPaint.setLetterSpacing(0f);
            }
            int count = counts[r];
            float x = barsX + barsWidth() - (count * barW() + (count - 1) * barSpace());
            drawArrows(canvas, x, baseline, rowH, count, r < activity.length ? activity[r] : -1, alpha);
            float minH = minBarH(rowH);
            float radius = barRadius();
            for (int i = 0; i < count; i++) {
                float barH = minH + (rowH - minH) * i / (count - 1);
                rect.set(x, baseline - barH, x + barW(), baseline);
                paint.setColor(color);
                paint.setAlpha(i < levels[r] ? alpha : Math.round(alpha * DIM_ALPHA));
                canvas.drawRoundRect(rect, radius, radius, paint);
                x += barW() + barSpace();
            }
        }
    }

    private void drawIos(Canvas canvas, float colLeft, float top) {
        int alpha = color >>> 24, dim = Math.round(alpha * DIM_ALPHA);
        if (iosLabel != null) {
            // 网络类型：墨迹顶边和最高的竖条顶边对齐，右边和第一列竖条的右边对齐
            float colW = IosSignal.colWidth(iosBarsW, IOS_COLUMNS);
            iosLabelPaint.setColor(color);
            canvas.drawText(iosLabel, colLeft + colW - iosLabelW - iosLabelInkLeft, top - iosLabelInkTop, iosLabelPaint);
            if (iosArrows) {
                // 下面一列 ^ˇ（^ 上行、ˇ 下行）：右边和第一列竖条隔开固定的间距；
                // ^ 的顶边和第一列竖条的顶边对齐，ˇ 的底边和下排点的底边（整组底边）对齐
                float cw = blockH * IOS_ARROW_W, ch = cw * 0.55f, aGap = blockH * IOS_ARROW_GAP;
                float half = blockH * IOS_ARROW_STROKE / 2f;
                float cx = colLeft - aGap - half - cw / 2f;
                float barsBottom = top + IosSignal.barsRowHeight(blockH, iosBarsW, IOS_COLUMNS);
                float upTop = barsBottom - (iosBarH.length > 0 ? iosBarH[0] : 0f) + half;
                float downBottom = top + blockH - half;
                boolean out = iosAct == SignalInfo.ACTIVITY_OUT || iosAct == SignalInfo.ACTIVITY_INOUT;
                boolean in = iosAct == SignalInfo.ACTIVITY_IN || iosAct == SignalInfo.ACTIVITY_INOUT;
                drawChevron(canvas, cx, upTop + ch / 2f, cw, ch, true, out ? alpha : dim);
                drawChevron(canvas, cx, downBottom - ch / 2f, cw, ch, false, in ? alpha : dim);
            }
        }
        int n = levels.length;
        IosSignal.draw(canvas, paint, rect, colLeft, top, blockH, iosBarsW, IOS_COLUMNS, iosBarH,
                n > 0 ? IosSignal.scale(levels[0], counts[0], IOS_COLUMNS) : 0,
                n > 1 ? IosSignal.scale(levels[1], counts[1], IOS_COLUMNS) : 0,
                Prefs.isSquare(Prefs.style), color, alpha, dim);
    }

    /** 画 ^ 或 ˇ：cx、cy 是中心，w 宽 h 高。 */
    private void drawChevron(Canvas canvas, float cx, float cy, float w, float h, boolean up, int alpha) {
        arrowPath.reset();
        float tip = up ? cy - h / 2f : cy + h / 2f, foot = up ? cy + h / 2f : cy - h / 2f;
        arrowPath.moveTo(cx - w / 2f, foot);
        arrowPath.lineTo(cx, tip);
        arrowPath.lineTo(cx + w / 2f, foot);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(blockH * IOS_ARROW_STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        paint.setAlpha(alpha);
        canvas.drawPath(arrowPath, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** 箭头放在前两格上方：底边比第二格顶端高一点，顶边对齐这一行的顶端（含半个行距）。 */
    private void drawArrows(Canvas canvas, float barsLeft, float baseline, float rowH, int count, int act, int alpha) {
        float secondBarH = minBarH(rowH);
        if (count > 1) secondBarH += (rowH - secondBarH) / (count - 1);
        float arrowBottom = baseline - secondBarH - Math.max(1f, 0.05f * rowH);
        float h = arrowBottom - (baseline - rowH - blockH * ROW_GAP_RATIO * 0.5f);
        if (h <= MIN_ARROW_HEIGHT) return;
        arrowHeight = h;
        if (act < 0) return;
        arrows.draw(canvas, paint, barsLeft + ARROW_NUDGE_X, arrowBottom - h, arrowBottom, act, color, alpha);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
    }
}
