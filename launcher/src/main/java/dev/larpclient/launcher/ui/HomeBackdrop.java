package dev.larpclient.launcher.ui;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Home page background: the game's own panorama, blurred and slowly panning, under legibility
 * gradients and a fine film grain. Falls back to a dark gradient before the game is installed.
 */
public class HomeBackdrop extends JPanel {
    private static final BufferedImage GRAIN = GameArt.grain(128, 18);
    private BufferedImage panorama;
    private final long start = System.currentTimeMillis();

    public HomeBackdrop(LayoutManager layout) {
        super(layout);
        setOpaque(true);
        // ~30 fps while visible; the pan is slow so this is plenty smooth.
        new Timer(33, e -> {
            if (isShowing() && panorama != null) {
                repaint();
            }
        }).start();
    }

    public void setPanorama(BufferedImage panorama) {
        this.panorama = panorama;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Components.smooth(g);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int w = getWidth();
        int h = getHeight();
        g2.setColor(Palette.BG);
        g2.fillRect(0, 0, w, h);

        if (panorama != null) {
            double scale = (h * 1.08) / panorama.getHeight();
            double faceW = panorama.getHeight() * scale;           // one cube face
            double loop = faceW * 4;
            double offset = ((System.currentTimeMillis() - start) / 1000.0 * 6) % loop;
            int dw = (int) (panorama.getWidth() * scale);
            int dh = (int) (panorama.getHeight() * scale);
            int y = (h - dh) / 2;
            for (double x = -offset; x < w; x += loop) {
                g2.drawImage(panorama, (int) x, y, dw, dh, null);
            }
            g2.setColor(new Color(10, 9, 16, 110));
            g2.fillRect(0, 0, w, h);
        } else {
            g2.setPaint(new GradientPaint(0, 0, new Color(0x1A1530), w, h, new Color(0x0B0B0E)));
            g2.fillRect(0, 0, w, h);
        }

        // Legibility: darken the left (text column) and the bottom (launch bar).
        g2.setPaint(new GradientPaint(0, 0, new Color(11, 11, 14, 235), (float) (w * 0.62), 0, new Color(11, 11, 14, 40)));
        g2.fillRect(0, 0, w, h);
        g2.setPaint(new GradientPaint(0, (float) (h * 0.55), new Color(11, 11, 14, 0), 0, h, new Color(11, 11, 14, 230)));
        g2.fillRect(0, (int) (h * 0.55), w, h);
        g2.setPaint(new RadialGradientPaint((float) (w * 0.15), (float) (h * 0.25), (float) (Math.max(w, h) * 0.55),
                new float[]{0f, 1f}, new Color[]{new Color(139, 92, 246, 46), new Color(139, 92, 246, 0)}));
        g2.fillRect(0, 0, w, h);

        g2.setPaint(new TexturePaint(GRAIN, new Rectangle2D.Float(0, 0, GRAIN.getWidth(), GRAIN.getHeight())));
        g2.fillRect(0, 0, w, h);
        g2.dispose();
    }
}
