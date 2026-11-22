package dev.larpclient.core.hud;

import dev.larpclient.core.render.LarpFont;
import dev.larpclient.core.render.Shapes;
import dev.larpclient.core.util.RenderUtil;
import dev.larpclient.modules.hud.HudTheme;

/** Drawing helpers shared by HUD elements so they follow {@link HudTheme}. */
public final class HudDraw {
    public static final LarpFont FONT = LarpFont.get(LarpFont.Weight.SEMIBOLD, 8f);
    public static final LarpFont SMALL = LarpFont.get(LarpFont.Weight.SEMIBOLD, 5.5f);

    private HudDraw() {
    }

    public static float width(String text) {
        return HudTheme.interFont() ? FONT.width(text) : RenderUtil.font().getStringWidth(text);
    }

    public static float height() {
        return HudTheme.interFont() ? FONT.height() : RenderUtil.font().FONT_HEIGHT;
    }

    /** Draws text with its line box top-left at (x, y); returns the end x. */
    public static float text(String text, float x, float y, int argb) {
        if (HudTheme.interFont()) {
            if (HudTheme.shadow()) {
                FONT.draw(text, x + 0.5f, y + 0.5f, ((argb >>> 24) * 2 / 5) << 24);
            }
            return FONT.draw(text, x, y, argb);
        }
        RenderUtil.font().drawString(text, x, y, argb, HudTheme.shadow());
        return x + RenderUtil.font().getStringWidth(text);
    }

    public static void panel(float x, float y, float w, float h, int argb) {
        float r = HudTheme.radius();
        if (r <= 0) {
            Shapes.rect(x, y, w, h, argb);
        } else {
            Shapes.roundedRect(x, y, w, h, r, argb);
        }
        if (HudTheme.border() && (argb >>> 24) > 0) {
            Shapes.roundedOutline(x, y, w, h, Math.max(r, 0.5f), 0.5f, 0x16FFFFFF);
        }
    }

    /** Dim a colour's alpha (for secondary text such as units). */
    public static int dim(int argb, float factor) {
        int a = (int) (((argb >>> 24) & 0xFF) * factor);
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
