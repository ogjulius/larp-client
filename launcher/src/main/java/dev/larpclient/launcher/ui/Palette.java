package dev.larpclient.launcher.ui;

import java.awt.Color;
import java.awt.Font;

/** Launcher colours and fonts (matches the in-game theme). */
public final class Palette {
    public static final Color BG = new Color(0x111115);
    public static final Color SIDEBAR = new Color(0x0B0B0E);
    public static final Color CARD = new Color(0x18181D);
    public static final Color CARD_HOVER = new Color(0x222229);
    public static final Color BORDER = new Color(0x26262E);
    public static final Color HAIRLINE = new Color(255, 255, 255, 14);
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
        return Fonts.of(style, size);
    }

    public static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}
