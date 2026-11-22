package dev.larpclient.launcher.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

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
            setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
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
            g2.setColor(Palette.HAIRLINE);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Page title + subtitle used at the top of every page. */
    public static JPanel pageHeader(String title, String subtitle) {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(Fonts.bold(26f));
        t.setForeground(Palette.TEXT);
        JLabel s = new JLabel(subtitle);
        s.setFont(Fonts.regular(13f));
        s.setForeground(Palette.TEXT_DIM);
        header.add(t);
        header.add(javax.swing.Box.createVerticalStrut(4));
        header.add(s);
        return header;
    }

    /** Small uppercase section label. */
    public static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text.toUpperCase(java.util.Locale.ROOT));
        label.setFont(Fonts.semibold(11f));
        label.setForeground(Palette.TEXT_DIM);
        label.setBorder(BorderFactory.createEmptyBorder(0, 4, 8, 0));
        return label;
    }

    public static Color blend(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
