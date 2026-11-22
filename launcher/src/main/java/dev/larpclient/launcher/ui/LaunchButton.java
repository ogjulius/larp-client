package dev.larpclient.launcher.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/** Primary call to action. While busy it becomes its own progress bar. */
public final class LaunchButton extends JComponent {
    private String label = "Launch";
    private String detail = "";
    private double progress = 0;
    private boolean busy;
    private boolean hovered;
    private boolean pressed;
    private float hover;
    private Runnable action = () -> { };

    public LaunchButton() {
        setPreferredSize(new Dimension(300, 60));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(true);
        new Timer(16, e -> {
            float target = hovered && !busy ? 1f : 0f;
            if (Math.abs(target - hover) > 0.01f || (busy && isShowing())) {
                hover += (target - hover) * 0.2f;
                repaint();
            }
        }).start();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                pressed = false;
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pressed = !busy;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (pressed && hovered && !busy) {
                    action.run();
                }
                pressed = false;
                repaint();
            }
        });
    }

    public void onClick(Runnable action) {
        this.action = action;
    }

    public void setIdle(String label) {
        this.busy = false;
        this.label = label;
        this.detail = "";
        repaint();
    }

    /** @param fraction 0..1, or negative for indeterminate */
    public void setBusy(String label, String detail, double fraction) {
        this.busy = true;
        this.label = label;
        this.detail = detail == null ? "" : detail;
        this.progress = fraction;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Components.smooth(g);
        int w = getWidth();
        int h = getHeight();
        float inset = pressed ? 1.5f : 0f;
        Shape shape = new RoundRectangle2D.Float(inset, inset, w - inset * 2, h - inset * 2, 16, 16);

        if (busy) {
            g2.setColor(new Color(0x1E1A2E));
            g2.fill(shape);
            g2.setClip(shape);
            g2.setPaint(new GradientPaint(0, 0, Palette.ACCENT_DARK, w, 0, Palette.ACCENT));
            if (progress >= 0) {
                g2.fillRect(0, 0, (int) (w * Math.min(1, progress)), h);
            } else {
                int seg = w / 3;
                int x = (int) ((System.currentTimeMillis() / 4) % (w + seg)) - seg;
                g2.fillRect(x, 0, seg, h);
            }
            g2.setClip(null);
        } else {
            Color top = Components.blend(new Color(0x8B5CF6), new Color(0x9D74FF), hover);
            Color bottom = Components.blend(new Color(0x7646E8), new Color(0x8B5CF6), hover);
            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g2.fill(shape);
            g2.setColor(new Color(255, 255, 255, 36));
            g2.draw(new RoundRectangle2D.Float(inset + 0.5f, inset + 0.5f, w - inset * 2 - 1, h - inset * 2 - 1, 16, 16));
        }

        g2.setColor(Color.WHITE);
        if (busy && !detail.isEmpty()) {
            g2.setFont(Fonts.semibold(15f));
            g2.drawString(label, 22, h / 2 - 2);
            g2.setFont(Fonts.regular(12f));
            g2.setColor(new Color(255, 255, 255, 190));
            g2.drawString(detail, 22, h / 2 + 15);
        } else {
            g2.setFont(Fonts.semibold(18f));
            int tw = g2.getFontMetrics().stringWidth(label);
            int iconSpace = busy ? 0 : 26;
            int tx = (w - tw - iconSpace) / 2 + iconSpace;
            if (!busy) {
                Icons.draw(g2, Icons.Glyph.PLAY, tx - 28, h / 2f - 10, Color.WHITE);
            }
            g2.drawString(label, tx, h / 2 + 6);
        }
        g2.dispose();
    }
}
