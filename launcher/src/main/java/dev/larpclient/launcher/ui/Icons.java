package dev.larpclient.launcher.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** Procedurally drawn app icon and line icons (no binary assets in the repo). */
public final class Icons {
    public enum Glyph { HOME, USER, SLIDERS, TERMINAL, FOLDER, MINIMIZE, CLOSE, PLAY }

    private Icons() {
    }

    public static BufferedImage appIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Components.smooth(image.getGraphics());
        float arc = size * 0.3f;
        g.setPaint(new GradientPaint(0, 0, new Color(0x9F7AFF), size, size, new Color(0x5B3BA8)));
        g.fill(new RoundRectangle2D.Float(0, 0, size, size, arc, arc));
        g.setColor(new Color(255, 255, 255, 40));
        g.fill(new RoundRectangle2D.Float(0, 0, size, size * 0.5f, arc, arc));
        // Monogram: a geometric "L" built from two bars.
        g.setColor(Color.WHITE);
        float u = size / 16f;
        g.fill(new RoundRectangle2D.Float(u * 4.5f, u * 3.5f, u * 2.6f, u * 9f, u, u));
        g.fill(new RoundRectangle2D.Float(u * 4.5f, u * 9.9f, u * 7f, u * 2.6f, u, u));
        g.dispose();
        return image;
    }

    /** Draw a 20×20 line icon at (x, y). */
    public static void draw(Graphics2D g, Glyph glyph, float x, float y, Color color) {
        Graphics2D c = (Graphics2D) g.create();
        c.translate(x, y);
        c.setColor(color);
        c.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (glyph) {
            case HOME: {
                Path2D p = new Path2D.Float();
                p.moveTo(3, 9.5);
                p.lineTo(10, 3.5);
                p.lineTo(17, 9.5);
                p.moveTo(5, 8);
                p.lineTo(5, 16.5);
                p.lineTo(15, 16.5);
                p.lineTo(15, 8);
                c.draw(p);
                c.draw(new RoundRectangle2D.Float(8.5f, 11.5f, 3f, 5f, 1f, 1f));
                break;
            }
            case USER:
                c.draw(new Ellipse2D.Float(6.5f, 3f, 7f, 7f));
                c.draw(new java.awt.geom.Arc2D.Float(3.5f, 11.5f, 13f, 11f, 0, 180, java.awt.geom.Arc2D.OPEN));
                break;
            case SLIDERS:
                for (int i = 0; i < 3; i++) {
                    float ly = 5 + i * 5;
                    c.draw(new java.awt.geom.Line2D.Float(3, ly, 17, ly));
                    float kx = i == 1 ? 12.5f : (i == 0 ? 6.5f : 9f);
                    c.fill(new Ellipse2D.Float(kx - 2.3f, ly - 2.3f, 4.6f, 4.6f));
                }
                break;
            case TERMINAL: {
                c.draw(new RoundRectangle2D.Float(2.5f, 4f, 15f, 12f, 3f, 3f));
                Path2D p = new Path2D.Float();
                p.moveTo(6, 8);
                p.lineTo(8.5, 10);
                p.lineTo(6, 12);
                c.draw(p);
                c.draw(new java.awt.geom.Line2D.Float(10.5f, 12.5f, 14, 12.5f));
                break;
            }
            case FOLDER: {
                Path2D p = new Path2D.Float();
                p.moveTo(2.5, 6);
                p.lineTo(2.5, 15.5);
                p.lineTo(17.5, 15.5);
                p.lineTo(17.5, 7);
                p.lineTo(9.5, 7);
                p.lineTo(8, 5);
                p.lineTo(3.5, 5);
                p.closePath();
                c.draw(p);
                break;
            }
            case MINIMIZE:
                c.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                c.draw(new java.awt.geom.Line2D.Float(6, 10, 14, 10));
                break;
            case CLOSE:
                c.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                c.draw(new java.awt.geom.Line2D.Float(6, 6, 14, 14));
                c.draw(new java.awt.geom.Line2D.Float(14, 6, 6, 14));
                break;
            case PLAY: {
                Path2D p = new Path2D.Float();
                p.moveTo(6, 4);
                p.lineTo(16, 10);
                p.lineTo(6, 16);
                p.closePath();
                c.fill(p);
                break;
            }
            default:
                break;
        }
        c.dispose();
    }
}
