package dev.larpclient.launcher.ui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

/** Bundled Inter typeface. */
public final class Fonts {
    private static final Font REGULAR = load("Inter-Regular.ttf", Font.PLAIN);
    private static final Font SEMIBOLD = load("Inter-SemiBold.ttf", Font.BOLD);
    private static final Font BOLD = load("Inter-Bold.ttf", Font.BOLD);

    private Fonts() {
    }

    private static Font load(String file, int fallbackStyle) {
        try (InputStream in = Fonts.class.getResourceAsStream("/larp/fonts/" + file)) {
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (Exception e) {
            return new Font(Font.SANS_SERIF, fallbackStyle, 13);
        }
    }

    public static Font regular(float size) {
        return REGULAR.deriveFont(Font.PLAIN, size);
    }

    public static Font semibold(float size) {
        return SEMIBOLD.deriveFont(Font.PLAIN, size);
    }

    public static Font bold(float size) {
        return BOLD.deriveFont(Font.PLAIN, size);
    }

    /** Maps the legacy (style, size) API onto Inter weights. */
    public static Font of(int style, float size) {
        return style == Font.BOLD ? semibold(size) : regular(size);
    }
}
