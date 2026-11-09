import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

// 生成 README 用的样式示意图（和设置页示意图同样的画法）：信号 / WiFi 各两种样式
public class PreviewGen {
    static final Color INK = new Color(0x1F, 0x1F, 0x22);
    static final Color DIM = new Color(0x1F, 0x1F, 0x22, 77);
    static final int S = 4;

    public static void main(String[] a) throws Exception {
        String dir = a.length > 0 ? a[0] : ".";
        save(signal(false), dir + "/signal_round.png");
        save(signal(true), dir + "/signal_square.png");
        save(wifi(false), dir + "/wifi_round.png");
        save(wifi(true), dir + "/wifi_square.png");
    }

    static void save(BufferedImage img, String path) throws Exception {
        ImageIO.write(img, "png", new File(path));
    }

    static BufferedImage canvas(int w, int h) {
        BufferedImage img = new BufferedImage(w * S, h * S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(0, 0, w * S, h * S, 16 * S, 16 * S));
        g.scale(S, S);
        g.dispose();
        return img;
    }

    static BufferedImage signal(boolean square) {
        int W = 96, H = 52;
        BufferedImage img = canvas(W, H);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(S, S);
        float h = 32, gapRow = h * 0.12f, rowH = (h - gapRow) / 2f;
        float barW = rowH * 0.4f, space = barW * 0.45f;
        Font font = new Font("Helvetica Neue", Font.BOLD, 100).deriveFont(rowH * 1.3f);
        FontRenderContext frc = g.getFontRenderContext();
        float labelW = (float) font.getStringBounds("5G", frc).getWidth();
        float total = labelW + rowH * 0.25f + 5 * barW + 4 * space;
        float x0 = (W - total) / 2f, y0 = (H - h) / 2f;
        int[] levels = {4, 3};
        for (int r = 0; r < 2; r++) {
            float bottom = y0 + rowH * (r + 1) + gapRow * r;
            g.setColor(INK);
            g.fill(font.createGlyphVector(frc, "5G").getOutline(x0, bottom));
            float x = x0 + labelW + rowH * 0.25f;
            float minH = square ? rowH * 0.3f : Math.max(barW, rowH * 0.35f);
            float rad = square ? 0 : barW;
            for (int i = 0; i < 5; i++) {
                float bh = minH + (rowH - minH) * i / 4f;
                g.setColor(i < levels[r] ? INK : DIM);
                g.fill(new RoundRectangle2D.Float(x, bottom - bh, barW, bh, rad, rad));
                x += barW + space;
            }
        }
        g.dispose();
        return img;
    }

    static BufferedImage wifi(boolean square) {
        int W = 96, H = 52;
        BufferedImage img = canvas(W, H);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(S, S);
        float f = 32, cx = W / 2f, bottom = (H + f) / 2f;
        int level = 3;
        if (!square) {
            float d = f * 0.13f, t = f * 0.15f, gp = (f - 2 * d - 3 * t) / 3f, cy = bottom - d;
            g.setColor(INK);
            g.fill(new Ellipse2D.Float(cx - d, cy - d, 2 * d, 2 * d));
            g.setStroke(new BasicStroke(t, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i <= 3; i++) {
                float r = d + gp * i + (i - 0.5f) * t;
                g.setColor(level >= i + 1 ? INK : DIM);
                g.draw(new Arc2D.Float(cx - r, cy - r, 2 * r, 2 * r, 45, 90, Arc2D.OPEN));
            }
        } else {
            float half = 41f, w = f * 0.25f, t = f * 0.14f, gp = f * 0.11f;
            g.setColor(INK);
            g.fill(new Arc2D.Float(cx - w, bottom - w, 2 * w, 2 * w, 90 - half, 2 * half, Arc2D.PIE));
            for (int i = 1; i <= 3; i++) {
                float ri = w + gp * i + t * (i - 1), ro = ri + t;
                Area band = new Area(new Arc2D.Float(cx - ro, bottom - ro, 2 * ro, 2 * ro, 90 - half, 2 * half, Arc2D.PIE));
                band.subtract(new Area(new Arc2D.Float(cx - ri, bottom - ri, 2 * ri, 2 * ri, 90 - half, 2 * half, Arc2D.PIE)));
                g.setColor(level >= i + 1 ? INK : DIM);
                g.fill(band);
            }
        }
        g.dispose();
        return img;
    }
}
