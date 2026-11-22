package dev.larpclient.launcher.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;

/** Launcher colours and fonts (matches the in-game theme). */
public final class Palette {
    public static final Color BG = new Color(0x141419);
    public static final Color SIDEBAR = new Color(0x0E0E12);
    public static final Color CARD = new Color(0x1C1C24);
    public static final Color CARD_HOVER = new Color(0x25252F);
    public static final Color BORDER = new Color(0x2C2C38);
    public static final Color ACCENT = new Color(0x8B5CF6);
    public static final Color ACCENT_LIGHT = new Color(0xC4B5FD);
    public static final Color ACCENT_DARK = new Color(0x5B3BA8);
    public static final Color TEXT = new Color(0xEDEDF2);
    public static final Color TEXT_DIM = new Color(0x8C8C9C);
    public static final Color DANGER = new Color(0xE5484D);
    public static final Color SUCCESS = new Color(0x46C47E);

    private Palette() {
    }

    public static String hex(Color c) {
        return String.format("#%06X", c.getRGB() & 0xFFFFFF);
    }

    public static Font font(int style, float size) {
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        }
        return base.deriveFont(style, size);
    }

    public static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}
