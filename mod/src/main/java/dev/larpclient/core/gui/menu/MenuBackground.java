package dev.larpclient.core.gui.menu;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.Random;

/**
 * Animated menu backdrop: a slowly shifting colour gradient with a drifting "constellation" of
 * particles that link up when close to each other or to the cursor. Shared by all LARP screens
 * shown outside a world so the menus feel like one app.
 */
public final class MenuBackground {
    private static final int COUNT = 80;
    private static final float LINK = 70f;
    private static final MenuBackground INSTANCE = new MenuBackground();

    private final float[] px = new float[COUNT];
    private final float[] py = new float[COUNT];
    private final float[] vx = new float[COUNT];
    private final float[] vy = new float[COUNT];
    private final float[] size = new float[COUNT];
    private long last = -1;
    private int lastW;
    private int lastH;

    private MenuBackground() {
    }

    public static MenuBackground get() {
        return INSTANCE;
    }

    private void seed(int w, int h) {
        Random r = new Random();
        for (int i = 0; i < COUNT; i++) {
            px[i] = r.nextFloat() * w;
            py[i] = r.nextFloat() * h;
            float angle = r.nextFloat() * (float) Math.PI * 2;
            float speed = 4 + r.nextFloat() * 10;
            vx[i] = (float) Math.cos(angle) * speed;
            vy[i] = (float) Math.sin(angle) * speed;
            size[i] = 0.75f + r.nextFloat() * 1.25f;
        }
        lastW = w;
        lastH = h;
    }

    public void draw(int w, int h, int mouseX, int mouseY) {
        if (w != lastW || h != lastH) {
            seed(w, h);
        }
        long now = System.currentTimeMillis();
        float dt = last < 0 ? 0 : Math.min(0.1f, (now - last) / 1000f);
        last = now;

        float t = (now % 60_000L) / 60_000f;
        int topLeft = hsb(0.72f + 0.05f * wave(t), 0.65f, 0.16f);
        int topRight = hsb(0.62f + 0.05f * wave(t + 0.3f), 0.70f, 0.13f);
        int bottomLeft = hsb(0.80f + 0.04f * wave(t + 0.6f), 0.60f, 0.07f);
        int bottomRight = hsb(0.68f, 0.55f, 0.05f);

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        vertex(wr, w, 0, topRight, 255);
        vertex(wr, 0, 0, topLeft, 255);
        vertex(wr, 0, h, bottomLeft, 255);
        vertex(wr, w, h, bottomRight, 255);
        tess.draw();

        for (int i = 0; i < COUNT; i++) {
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            if (px[i] < -10) px[i] += w + 20;
            if (px[i] > w + 10) px[i] -= w + 20;
            if (py[i] < -10) py[i] += h + 20;
            if (py[i] > h + 10) py[i] -= h + 20;
        }

        GL11.glLineWidth(1f);
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < COUNT; i++) {
            for (int j = i + 1; j < COUNT; j++) {
                float dx = px[i] - px[j];
                float dy = py[i] - py[j];
                float d2 = dx * dx + dy * dy;
                if (d2 < LINK * LINK) {
                    int a = (int) (70 * (1 - Math.sqrt(d2) / LINK));
                    wr.pos(px[i], py[i], 0).color(170, 140, 255, a).endVertex();
                    wr.pos(px[j], py[j], 0).color(170, 140, 255, a).endVertex();
                }
            }
            float dx = px[i] - mouseX;
            float dy = py[i] - mouseY;
            float d2 = dx * dx + dy * dy;
            if (d2 < LINK * LINK * 2) {
                int a = (int) (120 * (1 - Math.sqrt(d2) / (LINK * 1.414)));
                wr.pos(px[i], py[i], 0).color(200, 180, 255, a).endVertex();
                wr.pos(mouseX, mouseY, 0).color(200, 180, 255, 0).endVertex();
            }
        }
        tess.draw();

        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < COUNT; i++) {
            float s = size[i];
            int c = 0xD8CCFF;
            vertex(wr, px[i] + s, py[i] - s, c, 200);
            vertex(wr, px[i] - s, py[i] - s, c, 200);
            vertex(wr, px[i] - s, py[i] + s, c, 200);
            vertex(wr, px[i] + s, py[i] + s, c, 200);
        }
        tess.draw();

        // Vignette
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        vertex(wr, w, h * 0.6f, 0, 0);
        vertex(wr, 0, h * 0.6f, 0, 0);
        vertex(wr, 0, h, 0, 140);
        vertex(wr, w, h, 0, 140);
        tess.draw();

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    private static float wave(float t) {
        return (float) Math.sin(t * Math.PI * 2);
    }

    private static int hsb(float h, float s, float b) {
        return Color.HSBtoRGB(h, s, b) & 0xFFFFFF;
    }

    private static void vertex(WorldRenderer wr, float x, float y, int rgb, int alpha) {
        wr.pos(x, y, 0).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).endVertex();
    }
}
