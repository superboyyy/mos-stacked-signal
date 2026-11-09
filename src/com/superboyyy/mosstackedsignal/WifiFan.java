package com.superboyyy.mosstackedsignal;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * WiFi 扇形（状态栏和设置页示意图共用）。f 是整个扇形的高度。
 * 圆润：圆点 + 三条圆头弧线（和以前一样）；
 * 方正：底部尖角的扇形 + 三条直边扇环，四块共用一个圆心（在扇形底部），整体高度和圆润一致；
 * 张角比圆润窄（左右各 SQ_HALF 度），宽度和圆润差不多，块与块之间的空隙也比圆润大一点，小尺寸下更清楚。
 */
final class WifiFan {
    static final float INV_SQRT2 = (float) (1.0d / Math.sqrt(2.0d));

    private WifiFan() { }

    static float dotR(float f) {
        return f * 0.13f;
    }

    static float stroke(float f) {
        return f * 0.15f;
    }

    static float gap(float f) {
        return (f - dotR(f) * 2f - stroke(f) * 3f) / 3f;
    }

    // 方正：半张角、底部扇形半径、扇环厚度、空隙（合计正好是高度 f：w + 3g + 3t = f）
    static final float SQ_HALF = 41f;
    static final float SQ_SIN = (float) Math.sin(Math.toRadians(SQ_HALF));
    static final float SQ_COS = (float) Math.cos(Math.toRadians(SQ_HALF));

    static float sqWedge(float f) {
        return f * 0.25f;
    }

    static float sqStroke(float f) {
        return f * 0.14f;
    }

    static float sqGap(float f) {
        return f * 0.11f;
    }

    // 第 i 条扇环（1..3）的内外半径，圆心在扇形底部
    static float sqInner(float f, int i) {
        return sqWedge(f) + sqGap(f) * i + sqStroke(f) * (i - 1);
    }

    static float sqOuter(float f, int i) {
        return sqInner(f, i) + sqStroke(f);
    }

    // 方正时无网络 "!" 的 x（第二条扇环右端外侧）和顶部 y（第三条扇环右端下沿往下，取 75% 高度）
    static float sqBangX(float cx, float f) {
        return Math.round(cx) + sqOuter(f, 2) * SQ_SIN + 1f;
    }

    static float sqBangTop(float bottom, float f) {
        return bottom - (sqInner(f, 3) * SQ_COS - 1f) * 0.75f;
    }

    /**
     * cy 是圆润样式的圆心（扇形底部往上 dotR）。level 0..4：亮几块（圆点算第一块）。
     * label 不为空时，最外一条从左端裁掉一段，给 "6" / "7" 让出 margin 的空隙。
     */
    static void draw(Canvas canvas, Paint paint, Path path, RectF oval, boolean square, float cx, float cy, float f,
                     int level, int color, int alphaOn, int alphaOff, RectF label, float margin) {
        float d = dotR(f), t = stroke(f), g = gap(f);
        paint.setColor(color);
        if (!square) {
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(level >= 1 ? alphaOn : alphaOff);
            canvas.drawCircle(cx, cy, d, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(t);
            paint.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 1; i <= 3; i++) {
                float r = d + g * i + (i - 0.5f) * t;
                oval.set(cx - r, cy - r, cx + r, cy + r);
                paint.setAlpha(level >= i + 1 ? alphaOn : alphaOff);
                float start = 225f;
                if (label != null && i == 3) {
                    while (start < 300f) {
                        double rad = Math.toRadians(start);
                        float x = (float) Math.cos(rad) * r + cx;
                        float y = (float) Math.sin(rad) * r + cy;
                        if (dist(label, x, y) >= t / 2f + margin) break;
                        start += 0.5f;
                    }
                }
                canvas.drawArc(oval, start, 315f - start, false, paint);
            }
            return;
        }
        // 圆心 = 扇形底部；x 对齐到整像素，左右斜边的抗锯齿对称
        float ay = cy + d;
        cx = Math.round(cx);
        float t2 = sqStroke(f);
        float a0 = 270f - SQ_HALF, sweepAll = SQ_HALF * 2f;
        paint.setStyle(Paint.Style.FILL);
        float w = sqWedge(f);
        path.reset();
        path.moveTo(cx, ay);
        oval.set(cx - w, ay - w, cx + w, ay + w);
        path.arcTo(oval, a0, sweepAll);
        path.close();
        paint.setAlpha(level >= 1 ? alphaOn : alphaOff);
        canvas.drawPath(path, paint);
        for (int i = 1; i <= 3; i++) {
            float ri = sqInner(f, i), ro = ri + t2;
            float start = a0;
            if (label != null && i == 3) {
                while (start < 270f + SQ_HALF - 15f) {
                    double rad = Math.toRadians(start);
                    float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
                    float min = Float.MAX_VALUE;
                    for (int k = 0; k <= 6; k++) {
                        float r = ri + (ro - ri) * k / 6f;
                        min = Math.min(min, dist(label, cx + cos * r, ay + sin * r));
                    }
                    if (min >= margin) break;
                    start += 0.5f;
                }
            }
            float sweep = a0 + sweepAll - start;
            path.reset();
            oval.set(cx - ro, ay - ro, cx + ro, ay + ro);
            path.arcTo(oval, start, sweep, true);
            oval.set(cx - ri, ay - ri, cx + ri, ay + ri);
            path.arcTo(oval, start + sweep, -sweep);
            path.close();
            paint.setAlpha(level >= i + 1 ? alphaOn : alphaOff);
            canvas.drawPath(path, paint);
        }
    }

    // 点到矩形的距离（在矩形里为 0）
    private static float dist(RectF r, float x, float y) {
        float dx = x - Math.max(r.left, Math.min(x, r.right));
        float dy = y - Math.max(r.top, Math.min(y, r.bottom));
        return (float) Math.hypot(dx, dy);
    }
}
