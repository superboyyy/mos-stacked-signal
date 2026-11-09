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

/** 自绘 WiFi：扇形（WifiFan）、左上角的 "6" / "7"、无网络 "!"、右下角的上下行箭头。 */
final class WifiDrawable extends Drawable {
    private static final float ICON_UNITS_W = 73f;
    private static final float ICON_UNITS_H = 60f;
    private static final float DIM_ALPHA = 0.3f;
    private static final float LABEL_NUDGE_X = -3f;
    private static final float LABEL_EXTRA_STROKE = 0.15f;
    private static final float INV_SQRT2 = WifiFan.INV_SQRT2;

    int level = -1;
    String gen;
    boolean wide;
    boolean noInternet;
    boolean square;
    int color;
    Typeface typeface;
    float typeStroke;
    float arrowHeight;
    int activity = -1;
    final RectF img = new RectF();
    final RectF inoutInk = new RectF();
    float sevenTop = Float.NaN;
    float fanBottom = Float.NaN;
    float glyphH = Float.NaN;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF oval = new RectF();
    private final Path path = new Path();
    private final Rect textBounds = new Rect();
    private final ArrowPainter arrows = new ArrowPainter();

    @Override
    public void draw(Canvas canvas) {
        if (level < 0 || img.width() <= 0f || img.height() <= 0f) return;
        float ux = img.width() / ICON_UNITS_W;
        float uy = img.height() / ICON_UNITS_H;
        float f = Float.isNaN(glyphH) ? 39.4f * uy : glyphH;
        float dot = WifiFan.dotR(f);
        float stroke = WifiFan.stroke(f);
        float gap = WifiFan.gap(f);
        float cx = wide ? img.left + 36.49f * ux : img.centerX();
        float cy = (Float.isNaN(fanBottom) ? img.top + 50.5f * uy : fanBottom) - dot;
        int alpha = color >>> 24;

        RectF labelRect = new RectF(img.left + 7.5f * ux, img.top + 4.8f * uy, img.left + 20f * ux, img.top + 24f * uy);
        labelRect.offset(LABEL_NUDGE_X, 0f);
        if (!Float.isNaN(sevenTop)) labelRect.offsetTo(labelRect.left, sevenTop - 1f);

        WifiFan.draw(canvas, paint, path, oval, square, cx, cy, f, level, color, alpha, Math.round(alpha * DIM_ALPHA),
                gen != null ? labelRect : null, ux * 1.5f);
        if (gen != null) drawGeneration(canvas, labelRect);
        if (noInternet) {
            drawNoInternet(canvas, cx, cy, f, dot, stroke, gap, alpha);
            return;
        }
        if (activity < 0 || inoutInk.width() <= 0f) return;
        float shared = StackedSignalDrawable.arrowHeight;
        float h = shared > 0f ? shared : inoutInk.width() / ArrowPainter.widthPerHeight();
        float left = 1f + Math.max(inoutInk.right - ArrowPainter.widthPerHeight() * h, cx + dot + 0.25f * h);
        arrows.draw(canvas, paint, left, inoutInk.bottom - h, inoutInk.bottom, activity, color, alpha);
    }

    private void drawGeneration(Canvas canvas, RectF rect) {
        text.setTypeface(Typeface.create(typeface != null ? typeface : Typeface.DEFAULT, 700, false));
        text.setStyle(Paint.Style.FILL);
        text.setStrokeWidth(0f);
        text.setTextSize(100f);
        text.getTextBounds(gen, 0, 1, textBounds);
        float strokeW = (typeStroke > 0f ? typeStroke : rect.height() * 0.035f) + LABEL_EXTRA_STROKE;
        if (textBounds.height() > 0) text.setTextSize((rect.height() - strokeW) * 100f / textBounds.height());
        text.getTextBounds(gen, 0, 1, textBounds);
        text.setStyle(Paint.Style.FILL_AND_STROKE);
        text.setStrokeWidth(strokeW);
        text.setStrokeJoin(Paint.Join.ROUND);
        text.setColor(color);
        float half = strokeW / 2f;
        canvas.drawText(gen, rect.right - textBounds.width() - textBounds.left - half, rect.top - textBounds.top + half, text);
    }

    /** "!" 放在第二条弧线和最外一条弧线的右端之间，高度取那里空白的 75%。 */
    private void drawNoInternet(Canvas canvas, float cx, float cy, float f, float dot, float stroke, float gap, int alpha) {
        float r2 = gap * 2f + dot + stroke * 1.5f;
        float r3 = gap * 3f + dot + stroke * 2.5f;
        float width = stroke * 0.6f;
        float halfStroke = stroke / 2f;
        float halfWidth = width / 2f;
        float x = cx + r2 * INV_SQRT2 + halfStroke + 1f + halfWidth;
        float bottom = dot + cy;
        float top = bottom - (bottom - (cy - r3 * INV_SQRT2 + halfStroke + 1f)) * 0.75f;
        if (square) {
            x = WifiFan.sqBangX(cx, f) + halfWidth;
            top = WifiFan.sqBangTop(bottom, f);
        }
        float dotR = width * 0.6f;
        paint.setColor(color);
        paint.setAlpha(alpha);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(x, top + halfWidth, x, bottom - dotR * 2f - width * 1.1f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, bottom - dotR, dotR, paint);
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
