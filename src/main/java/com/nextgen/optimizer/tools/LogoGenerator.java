package com.nextgen.optimizer.tools;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the NextGen X brand icon (logo.png and a multi-resolution
 * logo.ico) from vector geometry, matching the in-app BrandMark.
 * Run: java -cp target/classes com.nextgen.optimizer.tools.LogoGenerator
 */
public class LogoGenerator {

    private static final int[] ICO_SIZES = {16, 24, 32, 48, 64, 128, 256};

    public static void main(String[] args) throws Exception {
        Path resources = Path.of("src/main/resources");
        Files.createDirectories(resources);

        ImageIO.write(render(256), "PNG", resources.resolve("logo.png").toFile());

        List<byte[]> pngs = new ArrayList<>();
        for (int size : ICO_SIZES) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(render(size), "PNG", out);
            pngs.add(out.toByteArray());
        }
        Files.write(resources.resolve("logo.ico"), ico(pngs));
        System.out.println("Generated logo.png and logo.ico (" + ICO_SIZES.length + " sizes)");
    }

    /** Draws the mark on a 24-unit grid scaled to {@code size} pixels. */
    static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double s = size / 24.0;

        // Dark rounded tile so the mark reads on light and dark taskbars.
        if (size >= 32) {
            g.setColor(new Color(8, 11, 18));
            g.fill(new RoundRectangle2D.Double(0.5 * s, 0.5 * s, 23 * s, 23 * s, 7 * s, 7 * s));
        }

        GeneralPath hex = new GeneralPath();
        double inset = size >= 32 ? 2.2 : 0.8;
        double[][] pts = {{12, inset}, {23 - inset * 0.9, 6.6}, {23 - inset * 0.9, 17.4}, {12, 24 - inset}, {1 + inset * 0.9, 17.4}, {1 + inset * 0.9, 6.6}};
        hex.moveTo(pts[0][0] * s, pts[0][1] * s);
        for (int i = 1; i < pts.length; i++) hex.lineTo(pts[i][0] * s, pts[i][1] * s);
        hex.closePath();
        g.setPaint(new GradientPaint(0, 0, new Color(0x22, 0xd3, 0xee), size, size, new Color(0x8b, 0x5c, 0xf6)));
        g.fill(hex);

        g.setColor(new Color(6, 8, 13));
        g.setStroke(new BasicStroke((float) (2.7 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) Math.round(8.3 * s), (int) Math.round(8 * s), (int) Math.round(15.7 * s), (int) Math.round(16 * s));
        g.drawLine((int) Math.round(15.7 * s), (int) Math.round(8 * s), (int) Math.round(8.3 * s), (int) Math.round(16 * s));
        g.dispose();
        return img;
    }

    /** ICO container with PNG-compressed entries (supported since Windows Vista). */
    static byte[] ico(List<byte[]> pngs) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeShort(out, 0);
        writeShort(out, 1);
        writeShort(out, pngs.size());
        int offset = 6 + 16 * pngs.size();
        for (int i = 0; i < pngs.size(); i++) {
            int size = ICO_SIZES[i];
            out.writeByte(size >= 256 ? 0 : size);
            out.writeByte(size >= 256 ? 0 : size);
            out.writeByte(0);
            out.writeByte(0);
            writeShort(out, 1);
            writeShort(out, 32);
            writeInt(out, pngs.get(i).length);
            writeInt(out, offset);
            offset += pngs.get(i).length;
        }
        for (byte[] png : pngs) out.write(png);
        return bytes.toByteArray();
    }

    private static void writeShort(DataOutputStream out, int v) throws IOException {
        out.writeByte(v & 0xFF);
        out.writeByte((v >> 8) & 0xFF);
    }

    private static void writeInt(DataOutputStream out, int v) throws IOException {
        writeShort(out, v & 0xFFFF);
        writeShort(out, (v >> 16) & 0xFFFF);
    }
}
