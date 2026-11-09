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
import android.view.View;
import android.widget.LinearLayout;

import java.util.Arrays;

/** 两行堆叠的信号：每行 "网络类型 + 信号格"，流量箭头画在信号格左上方的空白里。 */
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

    /** 标识文字的描边宽度，WiFi 上的 "6" / "7" 跟着用，粗细一致。 */
    static volatile float labelStroke;
    /** 流量箭头高度，WiFi 箭头跟着用。 */
    static volatile float arrowHeight;

    int blockH;
    int viewH;
    int width;
    private int pad;
    private int gap;
    private int labelW;
    private float typeCenterY;
    private int color = -1;
    private int[] levels = new int[0];
    private int[] counts = new int[0];
    private String[] labels = new String[0];
    private int[] activity = new int[0];
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

    boolean update(LinearLayout combo, int newBlockH, int newViewH) {
        float density = combo.getResources().getDisplayMetrics().density;
        int n = combo.getChildCount();
        int[] newLevels = new int[n];
        int[] newCounts = new int[n];
        String[] newLabels = new String[n];
        for (int i = 0; i < n; i++) {
            View sim = combo.getChildAt(i);
            int[] level = SignalInfo.level(sim);
            int count = level != null ? level[1] : 4;
            newCounts[i] = count;
            newLevels[i] = level != null ? Math.max(0, Math.min(level[0], count)) : 0;
            newLabels[i] = SignalInfo.label(sim);
        }
        int[] newActivity = SignalInfo.activities(combo);
        int newColor = SignalInfo.color(combo);
        boolean changed = newBlockH != blockH || newViewH != viewH || newColor != color
                || !Arrays.equals(newLevels, levels) || !Arrays.equals(newCounts, counts)
                || !Arrays.equals(newLabels, labels) || !Arrays.equals(newActivity, activity);
        activity = newActivity;
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
        textPaint.setTypeface(Typeface.create(clock != null ? clock : Typeface.DEFAULT, 700, false));
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
        width = pad + (labelW > 0 ? labelW + gap : 0) + barsWidth() + pad;
        if (changed) {
            StatusBarViews.log("labels=" + Arrays.toString(newLabels) + " activity=" + Arrays.toString(newActivity)
                    + " ink=" + Arrays.toString(inkWidth) + " spacing=" + Arrays.toString(labelSpacing)
                    + " shift=" + Arrays.toString(labelShift));
        }
        return changed;
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
        return Prefs.style == 1 ? rowH * 0.3f : Math.max(barW(), rowH * 0.35f);
    }

    private float barRadius() {
        return Prefs.style == 1 ? 0f : barW() / 2f;
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
        return !Arrays.equals(SignalInfo.activities(combo), activity);
    }

    String describe() {
        return "levels=" + Arrays.toString(levels) + "/" + Arrays.toString(counts)
                + " labels=" + Arrays.toString(labels) + " color=#" + Integer.toHexString(color);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float top = bounds.top + typeCenterY - blockH / 2f;
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
