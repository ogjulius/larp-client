package dev.larpclient.launcher.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/** Vertical icon navigation item with a small label underneath. */
public final class NavRail extends JComponent {
    private final String label;
    private final Icons.Glyph glyph;
    private boolean active;
    private boolean hovered;

    public NavRail(String label, Icons.Glyph glyph, Consumer<String> onSelect) {
        this.label = label;
        this.glyph = glyph;
        setPreferredSize(new Dimension(84, 60));
        setMaximumSize(new Dimension(84, 60));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(label);
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

            @Override
            public void mouseClicked(MouseEvent e) {
                onSelect.accept(label);
            }
        });
    }

    public String getLabel() {
        return label;
    }

    public void setActive(boolean active) {
        this.active = active;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Components.smooth(g);
        int w = getWidth();
        if (active || hovered) {
            g2.setColor(active ? new Color(255, 255, 255, 18) : new Color(255, 255, 255, 8));
            g2.fill(new RoundRectangle2D.Float(12, 4, w - 24, 52, 14, 14));
        }
        if (active) {
            g2.setColor(Palette.ACCENT);
            g2.fill(new RoundRectangle2D.Float(0, 20, 3, 20, 3, 3));
        }
        Color color = active ? Palette.ACCENT_LIGHT : hovered ? Palette.TEXT : Palette.TEXT_DIM;
        Icons.draw(g2, glyph, w / 2f - 10, 11, color);
        g2.setFont(Fonts.semibold(10.5f));
        g2.setColor(active ? Palette.TEXT : Palette.TEXT_DIM);
        int tw = g2.getFontMetrics().stringWidth(label);
        g2.drawString(label, (w - tw) / 2f, 47);
        g2.dispose();
    }
}
