package dev.larpclient.core.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.Random;

/**
 * Procedural night sky for LARP's menus: deep gradient, a slowly turning star field with twinkle
 * and colour temperature, a faint Milky Way, the odd shooting star and a mountain silhouette.
 * Everything is generated once from a fixed seed and drawn in a handful of batched draw calls.
 */
public final class StarrySky {
    private static final StarrySky INSTANCE = new StarrySky();
    private static final int STARS = 1500;
    private static final int BAND_STARS = 450; // first N stars form the Milky Way band

    // Star positions are relative to the celestial pole, in units of the pole→farthest-corner distance.
    private final float[] sx = new float[STARS];
    private final float[] sy = new float[STARS];
    private final float[] size = new float[STARS];
    private final float[] brightness = new float[STARS];
    private final float[] twinkleSpeed = new float[STARS];
    private final float[] phase = new float[STARS];
    private final int[] tint = new int[STARS];
    private final float[] farRidge = new float[129];
    private final float[] nearRidge = new float[129];
    private static final float BAND_X0 = -0.85f;
    private static final float BAND_Y0 = 0.62f;
    private static final float BAND_X1 = 0.85f;
    private static final float BAND_Y1 = 0.18f;
    private final long start = System.currentTimeMillis();
    private final Random random = new Random();

    private long nextShootingStar = System.currentTimeMillis() + 4000;
    private float shootX;
    private float shootY;
    private float shootAngle;
    private long shootStarted = -1;

    private StarrySky() {
        Random r = new Random(1897);
        for (int i = 0; i < STARS; i++) {
            boolean band = i < BAND_STARS;
            if (band) {
                // Milky Way: a soft line across the visible part of the sky.
                float t = r.nextFloat();
                float spread = (float) (r.nextGaussian() * 0.045);
                sx[i] = BAND_X0 + (BAND_X1 - BAND_X0) * t - spread * 0.3f;
                sy[i] = BAND_Y0 + (BAND_Y1 - BAND_Y0) * t + spread;
            } else {
                // Uniform over the annulus the viewport sweeps through as the sky turns.
                double radius = Math.sqrt(0.02 + r.nextDouble() * 0.98);
                double angle = r.nextDouble() * Math.PI * 2;
                sx[i] = (float) (Math.cos(angle) * radius);
                sy[i] = (float) (Math.sin(angle) * radius);
            }
            float m = r.nextFloat();
            size[i] = m < 0.86f ? 0.6f + r.nextFloat() * 0.5f : m < 0.97f ? 1.2f + r.nextFloat() * 0.5f : 1.8f + r.nextFloat() * 0.6f;
            brightness[i] = band ? 0.3f + r.nextFloat() * 0.4f : 0.4f + r.nextFloat() * 0.6f;
            twinkleSpeed[i] = 0.6f + r.nextFloat() * 2.2f;
            phase[i] = r.nextFloat() * 6.283f;
            float k = r.nextFloat();
            tint[i] = k < 0.18f ? 0xBFD4FF : k < 0.30f ? 0xFFE6C2 : k < 0.34f ? 0xFFC9A8 : 0xFFFFFF;
        }
        fillRidge(farRidge, r);
        fillRidge(nearRidge, r);
    }

    /** A ridge line from a few octaves of smooth sine noise, values roughly in -1..1. */
    private static void fillRidge(float[] ridge, Random r) {
        float[] amplitude = {0.55f, 0.28f, 0.12f, 0.05f};
        float[] frequency = new float[amplitude.length];
        float[] phase = new float[amplitude.length];
        for (int o = 0; o < amplitude.length; o++) {
            frequency[o] = 1.3f + o * 2.6f + r.nextFloat();
            phase[o] = r.nextFloat() * 10;
        }
        for (int i = 0; i < ridge.length; i++) {
            float x = i / (float) (ridge.length - 1);
            float y = 0;
            for (int o = 0; o < amplitude.length; o++) {
                y += amplitude[o] * Math.sin(x * frequency[o] * Math.PI + phase[o]);
            }
            ridge[i] = y;
        }
    }

    public static StarrySky get() {
        return INSTANCE;
    }

    public void draw(int width, int height) {
        float t = (System.currentTimeMillis() - start) / 1000f;
        int scale = new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
        float px = 1f / scale; // one physical pixel in GUI units

        // Sky gradient: near-black zenith to a violet glow at the horizon.
        Shapes.gradient(0, 0, width, height * 0.55f, 0xFF04050C, 0xFF05060F, 0xFF0B0E26, 0xFF0A0C22);
        Shapes.gradient(0, height * 0.55f, width, height * 0.45f, 0xFF0A0C22, 0xFF0B0E26, 0xFF221640, 0xFF1C1338);

        // The sky turns slowly around a pole just above the top edge.
        float poleX = width * 0.5f;
        float poleY = -height * 0.25f;
        float radius = (float) Math.hypot(width * 0.5f, height * 1.25f);
        double angle = t * 0.004;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        // Milky Way glow follows the band of stars; plus warm light at the horizon.
        for (int k = 0; k <= 6; k++) {
            float bx = BAND_X0 + (BAND_X1 - BAND_X0) * k / 6f;
            float by = BAND_Y0 + (BAND_Y1 - BAND_Y0) * k / 6f;
            float gx = poleX + (bx * cos - by * sin) * radius;
            float gy = poleY + (bx * sin + by * cos) * radius;
            Shapes.glow(gx, gy, radius * 0.16f, radius * 0.11f, 0x146F5BD8);
        }
        Shapes.glow(width * 0.5f, height * 1.02f, width * 0.8f, height * 0.35f, 0x307A4BD8);

        prepare();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < STARS; i++) {
            float x = poleX + (sx[i] * cos - sy[i] * sin) * radius;
            float y = poleY + (sx[i] * sin + sy[i] * cos) * radius;
            if (x < -4 || x > width + 4 || y < -4 || y > height + 4) {
                continue;
            }
            float twinkle = 0.75f + 0.25f * (float) Math.sin(t * twinkleSpeed[i] + phase[i]);
            // fade stars towards the horizon haze
            float haze = Math.max(0f, Math.min(1f, (height * 0.92f - y) / (height * 0.35f)));
            int alpha = (int) (255 * brightness[i] * twinkle * haze);
            if (alpha <= 3) {
                continue;
            }
            float s = size[i] * px * Math.max(1, scale * 0.75f);
            quad(wr, x - s / 2, y - s / 2, s, s, tint[i], alpha);
            if (size[i] > 1.7f) {
                // subtle cross glint on the brightest stars
                float g = s * 2.6f;
                quad(wr, x - g / 2, y - px / 2, g, px, tint[i], alpha / 3);
                quad(wr, x - px / 2, y - g / 2, px, g, tint[i], alpha / 3);
            }
        }
        tess.draw();
        finish();

        drawShootingStar(width, height, px);

        // Mountain silhouettes: a far, lighter ridge and a near, dark one.
        drawRidge(farRidge, width, height, height * 0.85f, height * 0.06f, 0xFF120E26);
        drawRidge(nearRidge, width, height, height * 0.92f, height * 0.05f, 0xFF07060F);
    }

    private void drawShootingStar(int width, int height, float px) {
        long now = System.currentTimeMillis();
        if (shootStarted < 0 && now >= nextShootingStar) {
            shootStarted = now;
            shootX = width * (0.3f + random.nextFloat() * 0.6f);
            shootY = height * (0.05f + random.nextFloat() * 0.25f);
            shootAngle = (float) Math.toRadians(150 + random.nextFloat() * 25);
        }
        if (shootStarted < 0) {
            return;
        }
        float life = (now - shootStarted) / 900f;
        if (life >= 1) {
            shootStarted = -1;
            nextShootingStar = now + 7000 + random.nextInt(9000);
            return;
        }
        float travel = Math.max(width, height) * 0.35f * life;
        float hx = shootX + (float) Math.cos(shootAngle) * travel;
        float hy = shootY + (float) Math.sin(shootAngle) * travel;
        float tail = Math.max(width, height) * 0.09f;
        float tx = hx - (float) Math.cos(shootAngle) * tail;
        float ty = hy - (float) Math.sin(shootAngle) * tail;
        int alpha = (int) (220 * Math.sin(life * Math.PI));
        prepare();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        float nx = -(hy - ty) / tail * px;
        float ny = (hx - tx) / tail * px;
        vertex(wr, hx + nx, hy + ny, 0xFFFFFF, alpha);
        vertex(wr, hx - nx, hy - ny, 0xFFFFFF, alpha);
        vertex(wr, tx, ty, 0xC8B8FF, 0);
        tess.draw();
        finish();
    }

    private static void drawRidge(float[] ridge, int width, int height, float baseY, float amplitude, int argb) {
        prepare();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        int rgb = argb & 0xFFFFFF;
        int alpha = (argb >>> 24) & 0xFF;
        int n = ridge.length - 1;
        for (int i = 0; i < n; i++) {
            float x0 = width * i / (float) n;
            float x1 = width * (i + 1) / (float) n;
            float y0 = baseY - amplitude * ridge[i];
            float y1 = baseY - amplitude * ridge[i + 1];
            vertex(wr, x0, y0, rgb, alpha);
            vertex(wr, x0, height, rgb, alpha);
            vertex(wr, x1, height, rgb, alpha);
            vertex(wr, x0, y0, rgb, alpha);
            vertex(wr, x1, height, rgb, alpha);
            vertex(wr, x1, y1, rgb, alpha);
        }
        tess.draw();
        finish();
    }

    private static void prepare() {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    private static void finish() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void quad(WorldRenderer wr, float x, float y, float w, float h, int rgb, int alpha) {
        vertex(wr, x, y, rgb, alpha);
        vertex(wr, x, y + h, rgb, alpha);
        vertex(wr, x + w, y + h, rgb, alpha);
        vertex(wr, x, y, rgb, alpha);
        vertex(wr, x + w, y + h, rgb, alpha);
        vertex(wr, x + w, y, rgb, alpha);
    }

    private static void vertex(WorldRenderer wr, float x, float y, int rgb, int alpha) {
        wr.pos(x, y, 0).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Math.max(0, Math.min(255, alpha))).endVertex();
    }
}
