package dev.larpclient.launcher.ui;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RadialGradientPaint;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;

/** Panel with the animated LARP constellation background (same look as the in-game menu). */
public class Starfield extends JPanel {
    private static final int COUNT = 70;
    private static final float LINK = 110f;
    private final float[] px = new float[COUNT];
    private final float[] py = new float[COUNT];
    private final float[] vx = new float[COUNT];
    private final float[] vy = new float[COUNT];
    private int mouseX = -1000;
    private int mouseY = -1000;
    private long last = -1;
    private int seededWidth;
    private int seededHeight;

    public Starfield(LayoutManager layout) {
        super(layout);
        setOpaque(true);
        Random r = new Random();
        for (int i = 0; i < COUNT; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            float speed = 6 + r.nextFloat() * 14;
            vx[i] = (float) Math.cos(angle) * speed;
            vy[i] = (float) Math.sin(angle) * speed;
        }
        MouseAdapter tracker = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouseX = mouseY = -1000;
            }
        };
        addMouseMotionListener(tracker);
        addMouseListener(tracker);
        // ~30 fps, and only while visible, to keep the launcher light on CPU.
        new Timer(33, e -> {
            if (isShowing()) {
                repaint();
            } else {
                last = -1;
            }
        }).start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Components.smooth(g);
        int w = getWidth();
        int h = getHeight();
        if (w > 0 && (Math.abs(w - seededWidth) > 50 || Math.abs(h - seededHeight) > 50)) {
            Random r = new Random();
            for (int i = 0; i < COUNT; i++) {
                px[i] = r.nextFloat() * w;
                py[i] = r.nextFloat() * h;
            }
            seededWidth = w;
            seededHeight = h;
        }
        long now = System.currentTimeMillis();
        float dt = last < 0 ? 0 : Math.min(0.1f, (now - last) / 1000f);
        last = now;

        float t = (now % 40_000L) / 40_000f;
        float shift = (float) Math.sin(t * Math.PI * 2);
        g2.setPaint(new GradientPaint(0, 0, Color.getHSBColor(0.72f + 0.03f * shift, 0.65f, 0.17f),
                w, h, Color.getHSBColor(0.66f, 0.6f, 0.05f)));
        g2.fillRect(0, 0, w, h);
        g2.setPaint(new RadialGradientPaint(w * (0.7f + 0.1f * shift), h * 0.3f, Math.max(w, h) * 0.6f,
                new float[]{0f, 1f}, new Color[]{Palette.alpha(Palette.ACCENT, 60), Palette.alpha(Palette.ACCENT, 0)}));
        g2.fillRect(0, 0, w, h);

        for (int i = 0; i < COUNT; i++) {
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            if (px[i] < -20) px[i] += w + 40;
            if (px[i] > w + 20) px[i] -= w + 40;
            if (py[i] < -20) py[i] += h + 40;
            if (py[i] > h + 20) py[i] -= h + 40;
        }
        for (int i = 0; i < COUNT; i++) {
            for (int j = i + 1; j < COUNT; j++) {
                float dx = px[i] - px[j];
                float dy = py[i] - py[j];
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < LINK) {
                    g2.setColor(new Color(170, 140, 255, (int) (60 * (1 - d / LINK))));
                    g2.drawLine((int) px[i], (int) py[i], (int) px[j], (int) py[j]);
                }
            }
            float dx = px[i] - mouseX;
            float dy = py[i] - mouseY;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < LINK * 1.5f) {
                g2.setColor(new Color(200, 180, 255, (int) (110 * (1 - d / (LINK * 1.5f)))));
                g2.drawLine((int) px[i], (int) py[i], mouseX, mouseY);
            }
        }
        g2.setColor(new Color(216, 204, 255, 210));
        for (int i = 0; i < COUNT; i++) {
            g2.fillOval((int) px[i] - 2, (int) py[i] - 2, 4, 4);
        }
        g2.setPaint(new GradientPaint(0, h * 0.55f, new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 150)));
        g2.fillRect(0, (int) (h * 0.55f), w, h);
        g2.dispose();
    }
}
