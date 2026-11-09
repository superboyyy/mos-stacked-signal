package com.superboyyy.mosstackedsignal;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** 上下行箭头（流量和 WiFi 共用）：左边上行、右边下行，竖柄圆头，箭头是等腰直角三角形。 */
final class ArrowPainter {
    private static final float STEM = 0.33f;
    private static final float HEAD_LEG = 0.55f;
    private static final float GAP = 0.2f;
    private static final float OUTER_CORNER = 0.3f;
    private static final float JOIN_FILLET = 0.35f;
    private static final float IDLE_ALPHA = 0.35f;
    private static final float WIDTH_PER_HEIGHT = 1.96f;

    private final Path path = new Path();
    private final RectF cornerOval = new RectF();

    static float widthPerHeight() {
        return WIDTH_PER_HEIGHT;
    }

    void draw(Canvas canvas, Paint paint, float left, float top, float bottom, int activity, int color, int alpha) {
        float y0 = Math.round(top);
        float y1 = Math.round(bottom);
        float h = y1 - y0;
        float stemW = h * STEM;
        float leg = h * HEAD_LEG;
        float stemR = stemW / 2f;
        float cornerR = stemW * OUTER_CORNER;
        float filletR = stemW * JOIN_FILLET;
        float x0 = left - (float) Math.sqrt(2.0d) * h * STEM * OUTER_CORNER;
        float arrowW = stemW + leg;
        float minGap = Math.max(1f, h * GAP);
        float gap = minGap + ((((-2f * x0) - (arrowW * 2f + minGap)) % 1f) + 1f) % 1f;
        boolean up = activity == SignalInfo.ACTIVITY_OUT || activity == SignalInfo.ACTIVITY_INOUT;
        boolean down = activity == SignalInfo.ACTIVITY_IN || activity == SignalInfo.ACTIVITY_INOUT;

        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);

        paint.setAlpha(up ? alpha : Math.round(alpha * IDLE_ALPHA));
        float stemLeft = x0 + leg;
        float headBase = y0 + leg;
        path.reset();
        path.addRoundRect(stemLeft, y0, stemLeft + stemW, y1, stemR, stemR, Path.Direction.CW);
        path.moveTo(stemLeft + stemR, headBase);
        roundCorner(path, stemLeft + stemR, headBase, x0, headBase, stemLeft, y0, cornerR);
        path.lineTo(stemLeft, y0);
        path.lineTo(stemLeft + stemR, y0);
        path.close();
        addFillet(path, stemLeft, headBase, -1f, 0f, 0f, 1f, filletR);
        canvas.drawPath(path, paint);

        paint.setAlpha(down ? alpha : Math.round(alpha * IDLE_ALPHA));
        float stem2Left = x0 + arrowW + gap;
        float stem2Right = stem2Left + stemW;
        float head2Base = y1 - leg;
        path.reset();
        path.addRoundRect(stem2Left, y0, stem2Right, y1, stemR, stemR, Path.Direction.CW);
        path.moveTo(stem2Right - stemR, head2Base);
        roundCorner(path, stem2Right - stemR, head2Base, stem2Right + leg, head2Base, stem2Right, y1, cornerR);
        path.lineTo(stem2Right, y1);
        path.lineTo(stem2Right - stemR, y1);
        path.close();
        addFillet(path, stem2Right, head2Base, 1f, 0f, 0f, -1f, filletR);
        canvas.drawPath(path, paint);
    }

    /** 在 (x, y) 处两条边 (dx1, dy1)、(dx2, dy2) 的夹角里补一块半径 r 的内圆角。 */
    private void addFillet(Path p, float x, float y, float dx1, float dy1, float dx2, float dy2, float r) {
        double half = Math.acos(Math.max(-1f, Math.min(1f, dx1 * dx2 + dy1 * dy2))) / 2.0d;
        double tangent = r / Math.tan(half);
        double bisX = dx1 + dx2, bisY = dy1 + dy2;
        double bisLen = Math.hypot(bisX, bisY);
        double centerDist = r / Math.sin(half);
        double cx = x + bisX / bisLen * centerDist;
        double cy = y + bisY / bisLen * centerDist;
        double ax = x + dx1 * tangent, ay = y + dy1 * tangent;
        double bx = x + dx2 * tangent, by = y + dy2 * tangent;
        double start = Math.toDegrees(Math.atan2(ay - cy, ax - cx));
        double sweep = normalize(Math.toDegrees(Math.atan2(by - cy, bx - cx)) - start);
        p.moveTo(x, y);
        p.lineTo((float) ax, (float) ay);
        cornerOval.set((float) (cx - r), (float) (cy - r), (float) (cx + r), (float) (cy + r));
        p.arcTo(cornerOval, (float) start, (float) sweep, false);
        p.close();
    }

    /** 从 (fromX, fromY) 走到拐点 (cx, cy) 再去 (toX, toY)，拐点处画成半径 r 的圆角。 */
    private void roundCorner(Path p, float fromX, float fromY, float cornerX, float cornerY, float toX, float toY, float r) {
        double ux = fromX - cornerX, uy = fromY - cornerY;
        double vx = toX - cornerX, vy = toY - cornerY;
        double uLen = Math.hypot(ux, uy), vLen = Math.hypot(vx, vy);
        ux /= uLen;
        uy /= uLen;
        vx /= vLen;
        vy /= vLen;
        double half = Math.acos(Math.max(-1.0d, Math.min(1.0d, ux * vx + uy * vy))) / 2.0d;
        double tangent = Math.min(r / Math.tan(half), Math.min(uLen, vLen) * 0.9d);
        double radius = Math.tan(half) * tangent;
        double bisX = ux + vx, bisY = uy + vy;
        double bisLen = Math.hypot(bisX, bisY);
        double centerDist = radius / Math.sin(half);
        double ccx = cornerX + bisX / bisLen * centerDist;
        double ccy = cornerY + bisY / bisLen * centerDist;
        double ax = cornerX + ux * tangent, ay = cornerY + uy * tangent;
        double bx = cornerX + vx * tangent, by = cornerY + vy * tangent;
        double start = Math.toDegrees(Math.atan2(ay - ccy, ax - ccx));
        double sweep = normalize(Math.toDegrees(Math.atan2(by - ccy, bx - ccx)) - start);
        p.lineTo((float) ax, (float) ay);
        cornerOval.set((float) (ccx - radius), (float) (ccy - radius), (float) (ccx + radius), (float) (ccy + radius));
        p.arcTo(cornerOval, (float) start, (float) sweep, false);
    }

    private static double normalize(double degrees) {
        while (degrees > 180.0d) degrees -= 360.0d;
        while (degrees < -180.0d) degrees += 360.0d;
        return degrees;
    }
}
