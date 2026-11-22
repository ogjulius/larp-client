package dev.larpclient.core.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Anti-aliased TrueType text for LARP's menus (Inter, bundled).
 * <p>
 * Glyphs are rasterised into an atlas at {@code size × GUI scale factor} pixels, so text maps 1:1
 * onto physical pixels and stays sharp at every GUI scale; the atlas is rebuilt lazily when the
 * scale factor changes. Only Latin-1 is covered; anything else falls back to the vanilla renderer.
 */
public final class LarpFont {
    public enum Weight {
        REGULAR("Inter-Regular.ttf"), SEMIBOLD("Inter-SemiBold.ttf"), BOLD("Inter-Bold.ttf");

        final String file;

        Weight(String file) {
            this.file = file;
        }
    }

    private static final Map<Weight, Font> BASE_FONTS = new EnumMap<>(Weight.class);
    private static final Map<String, LarpFont> CACHE = new HashMap<>();
    private static final int FIRST = 32;
    private static final int LAST = 255;
    /** Common typographic characters beyond Latin-1. */
    private static final String EXTRA = "\u2013\u2014\u2018\u2019\u201C\u201D\u2022\u2026\u2039\u203A\u2190\u2192\u2122\u20AC\u2212\u00D7";
    private static final int GLYPHS = LAST + 1 + EXTRA.length();
    private static final int PAD = 2;

    private final Weight weight;
    private final float size;

    private int builtScale = -1;
    private int textureId = -1;
    private int textureSize;
    private final float[] advance = new float[GLYPHS];
    private final int[] gx = new int[GLYPHS];
    private final int[] gy = new int[GLYPHS];
    private final int[] gw = new int[GLYPHS];
    private final int[] gh = new int[GLYPHS];
    private final int[] offsetX = new int[GLYPHS];
    private final boolean[] present = new boolean[GLYPHS];
    private float ascent;
    private float lineHeight;

    /** Glyph slot for a character, or -1 if the atlas doesn't cover it. */
    private static int slot(char c) {
        if (c >= FIRST && c <= LAST) {
            return c;
        }
        int extra = EXTRA.indexOf(c);
        return extra < 0 ? -1 : LAST + 1 + extra;
    }

    private static char charOf(int slot) {
        return slot <= LAST ? (char) slot : EXTRA.charAt(slot - LAST - 1);
    }

    private LarpFont(Weight weight, float size) {
        this.weight = weight;
        this.size = size;
    }

    /** @param size font size in GUI units (vanilla text is roughly size 8) */
    public static LarpFont get(Weight weight, float size) {
        return CACHE.computeIfAbsent(weight.name() + "@" + size, k -> new LarpFont(weight, size));
    }

    private static Font base(Weight weight) {
        return BASE_FONTS.computeIfAbsent(weight, w -> {
            try (InputStream in = LarpFont.class.getResourceAsStream("/assets/larpclient/fonts/" + w.file)) {
                return Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Exception e) {
                LogManager.getLogger("LARP Client/Font").error("Could not load {}", w.file, e);
                return new Font(Font.SANS_SERIF, w == Weight.REGULAR ? Font.PLAIN : Font.BOLD, 12);
            }
        });
    }

    private static int currentScale() {
        Minecraft mc = Minecraft.getMinecraft();
        return new ScaledResolution(mc).getScaleFactor();
    }

    private void ensure() {
        int scale = currentScale();
        if (scale == builtScale) {
            return;
        }
        builtScale = scale;
        build(scale);
    }

    private void build(int scale) {
        Font font = base(weight).deriveFont(size * scale);
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = scratch.createGraphics();
        hints(sg);
        sg.setFont(font);
        FontMetrics fm = sg.getFontMetrics();
        FontRenderContext frc = sg.getFontRenderContext();
        int cellHeight = fm.getAscent() + fm.getDescent() + PAD * 2;
        ascent = fm.getAscent() / (float) scale;
        lineHeight = (fm.getAscent() + fm.getDescent()) / (float) scale;

        // Measure and pack into rows.
        int atlas = 256;
        while (true) {
            int x = 0;
            int y = 0;
            boolean fits = true;
            for (int c = FIRST; c < GLYPHS; c++) {
                present[c] = font.canDisplay(charOf(c));
                if (!present[c]) {
                    continue;
                }
                GlyphVector gv = font.createGlyphVector(frc, String.valueOf(charOf(c)));
                advance[c] = gv.getGlyphMetrics(0).getAdvanceX();
                Rectangle2D bounds = gv.getVisualBounds();
                int left = (int) Math.floor(Math.min(0, bounds.getMinX()));
                int right = (int) Math.ceil(Math.max(advance[c], bounds.getMaxX()));
                int w = right - left + PAD * 2;
                if (x + w > atlas) {
                    x = 0;
                    y += cellHeight;
                }
                gx[c] = x;
                gy[c] = y;
                gw[c] = w;
                gh[c] = cellHeight;
                offsetX[c] = left - PAD;
                x += w;
                if (y + cellHeight > atlas) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                break;
            }
            atlas *= 2;
        }
        sg.dispose();

        BufferedImage image = new BufferedImage(atlas, atlas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        hints(g);
        g.setFont(font);
        g.setColor(Color.WHITE);
        for (int c = FIRST; c < GLYPHS; c++) {
            if (present[c]) {
                g.drawString(String.valueOf(charOf(c)), gx[c] - offsetX[c], gy[c] + PAD + fm.getAscent());
            }
        }
        g.dispose();

        if (textureId != -1) {
            TextureUtil.deleteTexture(textureId);
        }
        textureId = TextureUtil.glGenTextures();
        TextureUtil.uploadTextureImageAllocate(textureId, image, true, true);
        textureSize = atlas;
    }

    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    // ------------------------------------------------------------------ API

    /** Height of a line in GUI units. */
    public float height() {
        ensure();
        return lineHeight;
    }

    public float width(String text) {
        return width(text, 0);
    }

    public float width(String text, float letterSpacing) {
        ensure();
        float w = 0;
        for (int i = 0; i < text.length(); i++) {
            int c = slot(text.charAt(i));
            w += (c >= 0 && present[c] ? advance[c] / builtScale
                    : Minecraft.getMinecraft().fontRendererObj.getCharWidth(text.charAt(i))) + letterSpacing;
        }
        return text.isEmpty() ? 0 : w - letterSpacing;
    }

    public float draw(String text, float x, float y, int argb) {
        return draw(text, x, y, argb, 0);
    }

    public float drawCentered(String text, float centerX, float y, int argb) {
        return draw(text, centerX - width(text) / 2f, y, argb, 0);
    }

    public float drawRight(String text, float right, float y, int argb) {
        return draw(text, right - width(text), y, argb, 0);
    }

    /** Soft drop shadow then text. */
    public float drawShadowed(String text, float x, float y, int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        draw(text, x + 0.5f, y + 0.5f, ((alpha * 2 / 5) << 24), 0);
        return draw(text, x, y, argb, 0);
    }

    /**
     * Draw {@code text} with its line box's top-left at (x, y).
     *
     * @return x after the last glyph
     */
    public float draw(String text, float x, float y, int argb, float letterSpacing) {
        if (text.isEmpty() || (argb >>> 24) == 0) {
            return x;
        }
        ensure();
        int scale = builtScale;
        // Snap the pen to physical pixels so glyphs land 1:1 on the screen.
        float px = Math.round(x * scale) / (float) scale;
        float py = Math.round(y * scale) / (float) scale;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.bindTexture(textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.color(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f,
                ((argb >>> 24) & 0xFF) / 255f);

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        float inv = 1f / textureSize;
        StringBuilder fallback = null;
        float fallbackX = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int c = slot(ch);
            if (c < 0 || !present[c]) {
                if (fallback == null) {
                    fallback = new StringBuilder();
                    fallbackX = px;
                }
                fallback.append(ch);
                px += Minecraft.getMinecraft().fontRendererObj.getCharWidth(ch) + letterSpacing;
                continue;
            }
            float x0 = px + offsetX[c] / (float) scale;
            float y0 = py - PAD / (float) scale;
            float x1 = x0 + gw[c] / (float) scale;
            float y1 = y0 + gh[c] / (float) scale;
            float u0 = gx[c] * inv;
            float v0 = gy[c] * inv;
            float u1 = (gx[c] + gw[c]) * inv;
            float v1 = (gy[c] + gh[c]) * inv;
            wr.pos(x0, y1, 0).tex(u0, v1).endVertex();
            wr.pos(x1, y1, 0).tex(u1, v1).endVertex();
            wr.pos(x1, y0, 0).tex(u1, v0).endVertex();
            wr.pos(x0, y0, 0).tex(u0, v0).endVertex();
            px += advance[c] / scale + letterSpacing;
        }
        tess.draw();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);
        if (fallback != null) {
            // Rare: symbols Inter lacks. Draw them with the vanilla font, roughly baseline-aligned.
            Minecraft.getMinecraft().fontRendererObj.drawString(fallback.toString(), fallbackX,
                    py + ascent - 7, argb, false);
        }
        return px;
    }
}
