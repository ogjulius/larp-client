package dev.larpclient.launcher.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Procedurally drawn app icon (no binary assets in the repo). */
public final class Icons {
    private Icons() {
    }

    public static BufferedImage appIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Components.smooth(image.getGraphics());
        int arc = size / 3;
        g.setPaint(new GradientPaint(0, 0, Palette.ACCENT_LIGHT, size, size, Palette.ACCENT_DARK));
        g.fillRoundRect(0, 0, size, size, arc, arc);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (size * 0.62)));
        int tw = g.getFontMetrics().stringWidth("L");
        g.drawString("L", (size - tw) / 2f, size * 0.74f);
        g.setColor(new Color(255, 255, 255, 120));
        g.fillOval((int) (size * 0.66), (int) (size * 0.18), size / 7, size / 7);
        g.dispose();
        return image;
    }
}
