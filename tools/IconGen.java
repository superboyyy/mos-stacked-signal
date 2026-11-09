import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;

// 生成模块图标：和设置页"圆润"示意图同样的两行 "5G + 信号格"，输出 108dp 自适应图标前景（vector）和预览 PNG
public class IconGen {
    static final float R = 12.5f;           // 行高（dp，画布 108）
    static String fmt(double v) { return String.format(Locale.US, "%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    static String pathData(Shape s) {
        StringBuilder sb = new StringBuilder();
        double[] c = new double[6];
        for (PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO: sb.append("M").append(fmt(c[0])).append(",").append(fmt(c[1])); break;
                case PathIterator.SEG_LINETO: sb.append("L").append(fmt(c[0])).append(",").append(fmt(c[1])); break;
                case PathIterator.SEG_QUADTO: sb.append("Q").append(fmt(c[0])).append(",").append(fmt(c[1])).append(" ").append(fmt(c[2])).append(",").append(fmt(c[3])); break;
                case PathIterator.SEG_CUBICTO: sb.append("C").append(fmt(c[0])).append(",").append(fmt(c[1])).append(" ").append(fmt(c[2])).append(",").append(fmt(c[3])).append(" ").append(fmt(c[4])).append(",").append(fmt(c[5])); break;
                case PathIterator.SEG_CLOSE: sb.append("Z"); break;
            }
        }
        return sb.toString();
    }
    public static void main(String[] a) throws Exception {
        String fam = a.length > 0 ? a[0] : "Helvetica Neue";
        Font font = new Font(fam, Font.BOLD, 100).deriveFont(R * 1.3f);
        FontRenderContext frc = new FontRenderContext(null, true, true);
        float labelW = (float) font.getStringBounds("5G", frc).getWidth();
        float gapRow = R * 0.2727f;
        float barW = R * 0.4f, space = barW * 0.45f;
        float totalW = labelW + R * 0.25f + 5 * barW + 4 * space;
        float totalH = R * 2 + gapRow;
        float x0 = (108 - totalW) / 2f, y0 = (108 - totalH) / 2f;
        int[] levels = {4, 3};
        Area lit = new Area(), dim = new Area();
        for (int r = 0; r < 2; r++) {
            float bottom = y0 + R * (r + 1) + gapRow * r;
            GlyphVector gv = font.createGlyphVector(frc, "5G");
            lit.add(new Area(gv.getOutline(x0, bottom)));
            float x = x0 + labelW + R * 0.25f;
            float minH = Math.max(barW, R * 0.35f);
            for (int i = 0; i < 5; i++) {
                float bh = minH + (R - minH) * i / 4f;
                Shape bar = new RoundRectangle2D.Float(x, bottom - bh, barW, bh, barW, barW);
                (i < levels[r] ? lit : dim).add(new Area(bar));
                x += barW + space;
            }
        }
        String fg = "#FF1F1F22";
        String vec = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<!-- 由 IconGen 生成：设置页“圆润”信号示意图（两行 5G + 信号格） -->\n"
            + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:width=\"108dp\" android:height=\"108dp\" android:viewportWidth=\"108\" android:viewportHeight=\"108\">\n"
            + "    <path android:fillColor=\"" + fg + "\" android:pathData=\"" + pathData(lit) + "\" />\n"
            + "    <path android:fillColor=\"" + fg + "\" android:fillAlpha=\"0.3\" android:pathData=\"" + pathData(dim) + "\" />\n"
            + "</vector>\n";
        try (Writer w = new OutputStreamWriter(new FileOutputStream("ic_launcher_foreground.xml"), "UTF-8")) { w.write(vec); }
        // 预览：432px，圆形遮罩
        int S = 432; float k = S / 108f;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(new Ellipse2D.Float(0, 0, S, S));
        g.setColor(Color.WHITE); g.fillRect(0, 0, S, S);
        g.scale(k, k);
        g.setColor(new Color(0x1F, 0x1F, 0x22)); g.fill(lit);
        g.setColor(new Color(0x1F, 0x1F, 0x22, 77)); g.fill(dim);
        g.dispose();
        ImageIO.write(img, "png", new File("preview.png"));
        System.out.println("font=" + font.getFontName() + " labelW=" + labelW + " box=" + totalW + "x" + totalH);
    }
}
