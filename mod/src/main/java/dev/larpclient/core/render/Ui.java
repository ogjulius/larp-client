package dev.larpclient.core.render;

/** Typography presets for LARP's menus (HUD elements keep the vanilla font). */
public final class Ui {
    public static final LarpFont BODY = LarpFont.get(LarpFont.Weight.REGULAR, 8f);
    public static final LarpFont BODY_STRONG = LarpFont.get(LarpFont.Weight.SEMIBOLD, 8f);
    public static final LarpFont SMALL = LarpFont.get(LarpFont.Weight.REGULAR, 7f);
    public static final LarpFont LABEL = LarpFont.get(LarpFont.Weight.SEMIBOLD, 6.5f);
    public static final LarpFont TITLE = LarpFont.get(LarpFont.Weight.SEMIBOLD, 11f);
    public static final LarpFont NAV = LarpFont.get(LarpFont.Weight.SEMIBOLD, 10f);
    public static final LarpFont DISPLAY = LarpFont.get(LarpFont.Weight.BOLD, 30f);

    private Ui() {
    }

    /** Draw body text with its top at {@code y}; mirrors the old 8px vanilla metrics. */
    public static float text(String s, float x, float y, int argb) {
        return BODY.draw(s, x, y - 1, argb);
    }

    public static float textStrong(String s, float x, float y, int argb) {
        return BODY_STRONG.draw(s, x, y - 1, argb);
    }

    public static float centered(String s, float cx, float y, int argb) {
        return BODY.drawCentered(s, cx, y - 1, argb);
    }

    public static float width(String s) {
        return BODY.width(s);
    }

    /** Trim with an ellipsis to fit {@code maxWidth}. */
    public static String ellipsize(LarpFont font, String s, float maxWidth) {
        if (font.width(s) <= maxWidth) {
            return s;
        }
        String ellipsis = "…";
        int end = s.length();
        while (end > 0 && font.width(s.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return s.substring(0, end) + ellipsis;
    }
}
