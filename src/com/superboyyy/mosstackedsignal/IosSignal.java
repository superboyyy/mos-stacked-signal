package com.superboyyy.mosstackedsignal;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * iOS 式的双卡信号（状态栏和设置页示意图共用）：上排由矮到高的竖条是卡 1，下排的点是卡 2，
 * 每一列看起来像一个感叹号。固定 5 列，总宽和普通样式不显示标识时的 5 格竖条一样。
 * h 是整个图形的高度，w 是这几列的总宽度。上下方向的尺寸（竖条高、点高、两排间距）按 h 算，
 * 和 iOS 的比例一致；左右方向（列宽、列间距）按 w 算。
 */
final class IosSignal {
    // 从 iOS 截图量的比例（总高 44.5）：列宽 11、列间距 7；竖条和点之间比 iOS 紧一点（4.5 -> 3）。
    // 下排的点比 iOS 矮一点（11 -> 9.5）；第一根竖条和它下面的点一样高，往右均匀加高到 32（= 总高 - 点 - 间距）
    private static final float COL_W = 11f, COL_GAP = 7f, DOT_H = 9.5f, ROW_GAP = 3f, BAR_MIN = DOT_H;
    private static final float FULL_UNIT = 44.5f;
    private static final float ROUND_RADIUS = 0.45f;

    private IosSignal() { }

    static float colWidth(float w, int columns) {
        return w * COL_W / (columns * COL_W + (columns - 1) * COL_GAP);
    }

    /** 第 i 列的左边缘（相对这几列的左边）。 */
    static float colLeft(int i, float w, int columns) {
        return i * colWidth(w, columns) * (COL_W + COL_GAP) / COL_W;
    }

    /** 上排竖条那一行的高度（最高的竖条）。 */
    static float barsRowHeight(float h, float w, int columns) {
        return h * (FULL_UNIT - DOT_H - ROW_GAP) / FULL_UNIT;
    }

    /**
     * 每根竖条的高度。左上角有网络类型标识时（labelRight 是它的右边缘，相对这几列的左边；labelH 是它的高度），
     * 被它盖住的前几根竖条压矮让位，压矮后仍然由矮到高。
     */
    static void barHeights(float[] out, float h, float w, int columns, float labelRight, float labelH, float margin) {
        float rowH = barsRowHeight(h, w, columns);
        float minH = h * BAR_MIN / FULL_UNIT;
        int covered = 0;
        if (labelH > 0f) {
            while (covered < columns && colLeft(covered, w, columns) < labelRight + margin) covered++;
        }
        float cap = rowH - labelH - margin;
        for (int i = 0; i < columns; i++) {
            // 第一根竖条和下面的点一样大，往右均匀加高到最高
            float natural = columns > 1 ? minH + (rowH - minH) * i / (columns - 1) : rowH;
            out[i] = i < covered ? Math.min(natural, cap * (0.6f + 0.4f * (i + 1) / covered)) : natural;
        }
    }

    /** 这张卡的格数（level / count）换算到 columns 列：总格数相同就原样，有信号至少亮 1 格。 */
    static int scale(int level, int count, int columns) {
        if (level <= 0 || count <= 0) return 0;
        if (count == columns) return Math.min(level, columns);
        return Math.max(1, Math.min(columns, Math.round(level * (float) columns / count)));
    }

    static void draw(Canvas canvas, Paint paint, RectF rect, float left, float top, float h, float w, int columns,
                     float[] barH, int topLevel, int bottomLevel, boolean square, int color, int alphaOn, int alphaOff) {
        float colW = colWidth(w, columns), radius = square ? 0f : colW * ROUND_RADIUS;
        float dotH = h * DOT_H / FULL_UNIT;
        float barsBottom = top + barsRowHeight(h, w, columns);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        for (int i = 0; i < columns; i++) {
            float x = left + colLeft(i, w, columns);
            paint.setAlpha(i < topLevel ? alphaOn : alphaOff);
            rect.set(x, barsBottom - barH[i], x + colW, barsBottom);
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setAlpha(i < bottomLevel ? alphaOn : alphaOff);
            rect.set(x, top + h - dotH, x + colW, top + h);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }
}
