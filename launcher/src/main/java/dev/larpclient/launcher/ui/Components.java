package dev.larpclient.launcher.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Custom painted widgets. */
public final class Components {
    private Components() {
    }

    public static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g2;
    }

    public static JLabel label(String text, float size, int style, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(Palette.font(style, size));
        label.setForeground(color);
        return label;
    }

    /** Rounded card container. */
    public static class Card extends JPanel {
        private final int arc;
        private Color fill = Palette.CARD;

        public Card(LayoutManager layout, int arc) {
            super(layout);
            this.arc = arc;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        }

        public Card fill(Color color) {
            this.fill = color;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(Palette.alpha(Color.WHITE, 18));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Big gradient call-to-action button with hover glow. */
    public static class PlayButton extends JButton {
        private float hover;
        private boolean hovered;
        private final javax.swing.Timer animator;

        public PlayButton(String text) {
            super(text);
            setFont(Palette.font(Font.BOLD, 22f));
            setForeground(Color.WHITE);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(280, 66));
            animator = new javax.swing.Timer(16, e -> {
                float target = hovered && isEnabled() ? 1f : 0f;
                hover += (target - hover) * 0.2f;
                if (Math.abs(target - hover) < 0.01f) {
                    hover = target;
                    ((javax.swing.Timer) e.getSource()).stop();
                }
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    animator.start();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    animator.start();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth();
            int h = getHeight();
            int pad = 6;
            if (isEnabled() && hover > 0) {
                for (int i = pad; i > 0; i--) {
                    g2.setColor(Palette.alpha(Palette.ACCENT, (int) (14 * hover)));
                    g2.fillRoundRect(pad - i, pad - i, w - 2 * (pad - i), h - 2 * (pad - i), 22 + i, 22 + i);
                }
            }
            Color a = isEnabled() ? blend(Palette.ACCENT_DARK, Palette.ACCENT, 0.5f + hover * 0.5f) : Palette.CARD_HOVER;
            Color b = isEnabled() ? blend(Palette.ACCENT, Palette.ACCENT_LIGHT, hover * 0.6f) : Palette.CARD;
            g2.setPaint(new GradientPaint(0, 0, a, w, h, b));
            g2.fillRoundRect(pad, pad, w - 2 * pad, h - 2 * pad, 20, 20);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(Palette.alpha(Color.WHITE, isEnabled() ? 50 : 15));
            g2.drawRoundRect(pad, pad, w - 2 * pad - 1, h - 2 * pad - 1, 20, 20);
            g2.setFont(getFont());
            g2.setColor(isEnabled() ? Color.WHITE : Palette.TEXT_DIM);
            int tw = g2.getFontMetrics().stringWidth(getText());
            g2.drawString(getText(), (w - tw) / 2, h / 2 + g2.getFontMetrics().getAscent() / 2 - 3);
            g2.dispose();
        }
    }

    /** Thin rounded progress bar; negative value = indeterminate shimmer. */
    public static class ThinProgress extends JComponent {
        private double value;

        public ThinProgress() {
            setPreferredSize(new Dimension(200, 6));
            new javax.swing.Timer(30, e -> {
                if (value < 0 && isShowing()) {
                    repaint();
                }
            }).start();
        }

        public void setValue(double value) {
            this.value = value;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(Palette.CARD_HOVER);
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setPaint(new GradientPaint(0, 0, Palette.ACCENT, w, 0, Palette.ACCENT_LIGHT));
            if (value < 0) {
                int seg = w / 4;
                int x = (int) ((System.currentTimeMillis() / 3) % (w + seg)) - seg;
                g2.setClip(0, 0, w, h);
                g2.fillRoundRect(x, 0, seg, h, h, h);
            } else if (value > 0) {
                g2.fillRoundRect(0, 0, (int) Math.max(h, w * Math.min(1, value)), h, h, h);
            }
            g2.dispose();
        }
    }

    /** Sidebar navigation entry. */
    public static class NavButton extends JButton {
        private boolean active;
        private boolean hovered;

        public NavButton(String text) {
            super(text);
            setFont(Palette.font(Font.BOLD, 14f));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setHorizontalAlignment(LEFT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            setPreferredSize(new Dimension(180, 42));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
        }

        public void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            if (active || hovered) {
                g2.setColor(active ? Palette.CARD_HOVER : Palette.CARD);
                g2.fillRoundRect(8, 2, getWidth() - 16, getHeight() - 4, 12, 12);
            }
            if (active) {
                g2.setColor(Palette.ACCENT);
                g2.fillRoundRect(8, 10, 4, getHeight() - 20, 4, 4);
            }
            g2.setFont(getFont());
            g2.setColor(active ? Palette.TEXT : Palette.TEXT_DIM);
            g2.drawString(getText(), 26, getHeight() / 2 + g2.getFontMetrics().getAscent() / 2 - 2);
            g2.dispose();
        }
    }

    static Color blend(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
