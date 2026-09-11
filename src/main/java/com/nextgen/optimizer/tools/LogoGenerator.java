package com.nextgen.optimizer.tools;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class LogoGenerator {

    public static void main(String[] args) throws Exception {
        int size = 256;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        // Enable high-quality anti-aliasing and rendering
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Clear transparent background
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0, 0, size, size);
        g2.setComposite(AlphaComposite.SrcOver);

        // Outer rounded square (app icon background)
        int margin = 8;
        int boxSize = size - 2 * margin;
        RoundRectangle2D bgBox = new RoundRectangle2D.Double(margin, margin, boxSize, boxSize, 56, 56);

        // Deep luxury gradient background
        GradientPaint bgGrad = new GradientPaint(
                0, 0, new Color(15, 20, 35),
                size, size, new Color(6, 8, 16)
        );
        g2.setPaint(bgGrad);
        g2.fill(bgBox);

        // Glowing glassmorphic border gradient
        GradientPaint borderGrad = new GradientPaint(
                0, 0, new Color(0, 240, 255, 200),
                size, size, new Color(112, 0, 255, 200)
        );
        g2.setPaint(borderGrad);
        g2.setStroke(new BasicStroke(4f));
        g2.draw(bgBox);

        // Subtle ambient radial glow behind the emblem
        RadialGradientPaint glow = new RadialGradientPaint(
                size / 2f, size / 2f, size * 0.45f,
                new float[]{0.0f, 1.0f},
                new Color[]{new Color(0, 240, 255, 50), new Color(15, 20, 35, 0)}
        );
        g2.setPaint(glow);
        g2.fill(bgBox);

        // Draw High-Tech Shield / Hexagon Emblem
        GeneralPath shield = new GeneralPath();
        shield.moveTo(128, 38);
        shield.lineTo(206, 72);
        shield.lineTo(192, 164);
        shield.lineTo(128, 216);
        shield.lineTo(64, 164);
        shield.lineTo(50, 72);
        shield.closePath();

        GradientPaint shieldGrad = new GradientPaint(
                60, 40, new Color(24, 32, 54, 220),
                200, 200, new Color(10, 14, 24, 220)
        );
        g2.setPaint(shieldGrad);
        g2.fill(shield);

        // Glowing shield outline
        GradientPaint shieldBorder = new GradientPaint(
                60, 40, new Color(0, 240, 255),
                200, 200, new Color(112, 0, 255)
        );
        g2.setPaint(shieldBorder);
        g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(shield);

        // Draw Futuristic Geometric 'N' inside Shield
        GeneralPath letterN = new GeneralPath();
        // Left vertical pillar
        letterN.moveTo(88, 166);
        letterN.lineTo(88, 92);
        letterN.lineTo(112, 92);
        // Diagonal bridge
        letterN.lineTo(146, 142);
        letterN.lineTo(146, 92);
        // Right vertical pillar
        letterN.lineTo(168, 92);
        letterN.lineTo(168, 166);
        letterN.lineTo(144, 166);
        // Diagonal bridge back down
        letterN.lineTo(110, 116);
        letterN.lineTo(110, 166);
        letterN.closePath();

        GradientPaint nGrad = new GradientPaint(
                85, 90, new Color(0, 240, 255),
                170, 170, new Color(140, 60, 255)
        );
        g2.setPaint(nGrad);
        g2.fill(letterN);

        // Lightning / Speed Slash Accent across bottom right of N
        g2.setPaint(new Color(0, 240, 255, 230));
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Line2D.Double(156, 150, 182, 130));

        g2.dispose();

        // Ensure output directory exists
        Path resourcesDir = Path.of("src/main/resources");
        Files.createDirectories(resourcesDir);

        Path pngPath = resourcesDir.resolve("logo.png");
        ImageIO.write(img, "PNG", pngPath.toFile());
        System.out.println("Generated PNG logo at: " + pngPath.toAbsolutePath());

        // Convert PNG to Windows ICO file (256x256 ICO format)
        ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", pngOut);
        byte[] pngBytes = pngOut.toByteArray();

        Path icoPath = resourcesDir.resolve("logo.ico");
        try (FileOutputStream fos = new FileOutputStream(icoPath.toFile())) {
            // ICONDIR Header (6 bytes)
            fos.write(new byte[]{
                    0, 0,             // Reserved
                    1, 0,             // Type: 1 = ICO
                    1, 0              // Image count: 1
            });
            // ICONDIRENTRY (16 bytes)
            fos.write(0);             // Width (0 = 256)
            fos.write(0);             // Height (0 = 256)
            fos.write(0);             // Color count
            fos.write(0);             // Reserved
            fos.write(1); fos.write(0); // Color planes (1)
            fos.write(32); fos.write(0); // Bits per pixel (32)
            // Size of PNG data (4 bytes, little-endian)
            int len = pngBytes.length;
            fos.write(len & 0xFF);
            fos.write((len >> 8) & 0xFF);
            fos.write((len >> 16) & 0xFF);
            fos.write((len >> 24) & 0xFF);
            // Offset to PNG data (4 bytes, little-endian = 22)
            int offset = 22;
            fos.write(offset & 0xFF);
            fos.write((offset >> 8) & 0xFF);
            fos.write((offset >> 16) & 0xFF);
            fos.write((offset >> 24) & 0xFF);

            // Write PNG payload
            fos.write(pngBytes);
        }
        System.out.println("Generated Windows ICO logo at: " + icoPath.toAbsolutePath());
    }
}
