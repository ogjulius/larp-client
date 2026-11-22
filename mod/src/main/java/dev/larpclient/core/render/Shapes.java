package dev.larpclient.core.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** Anti-aliasing-free but smooth vector primitives: rounded rects, gradients and UI icons. */
public final class Shapes {
    private static final int CORNER_SEGMENTS = 8;

    private Shapes() {
    }

    private static void begin() {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    private static void end() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void color(WorldRenderer wr, double x, double y, int argb) {
        wr.pos(x, y, 0).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }

    private static WorldRenderer triangles() {
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        return wr;
    }

    public static void rect(float x, float y, float w, float h, int argb) {
        gradient(x, y, w, h, argb, argb, argb, argb);
    }

    public static void verticalGradient(float x, float y, float w, float h, int top, int bottom) {
        gradient(x, y, w, h, top, top, bottom, bottom);
    }

    public static void horizontalGradient(float x, float y, float w, float h, int left, int right) {
        gradient(x, y, w, h, left, right, right, left);
    }

    /** Colours: top-left, top-right, bottom-right, bottom-left. */
    public static void gradient(float x, float y, float w, float h, int tl, int tr, int br, int bl) {
        if (w <= 0 || h <= 0) {
            return;
        }
        begin();
        WorldRenderer wr = triangles();
        color(wr, x, y, tl);
        color(wr, x, y + h, bl);
        color(wr, x + w, y + h, br);
        color(wr, x, y, tl);
        color(wr, x + w, y + h, br);
        color(wr, x + w, y, tr);
        Tessellator.getInstance().draw();
        end();
    }

    /** Perimeter points of a rounded rectangle, clockwise from the top-right corner. */
    private static float[] perimeter(float x, float y, float w, float h, float r) {
        float[] centers = {x + w - r, y + r, x + w - r, y + h - r, x + r, y + h - r, x + r, y + r};
        float[] start = {-90, 0, 90, 180};
        float[] pts = new float[4 * (CORNER_SEGMENTS + 1) * 2];
        int n = 0;
        for (int c = 0; c < 4; c++) {
            for (int i = 0; i <= CORNER_SEGMENTS; i++) {
                double a = Math.toRadians(start[c] + 90.0 * i / CORNER_SEGMENTS);
                pts[n++] = (float) (centers[c * 2] + Math.cos(a) * r);
                pts[n++] = (float) (centers[c * 2 + 1] + Math.sin(a) * r);
            }
        }
        return pts;
    }

    public static void roundedRect(float x, float y, float w, float h, float r, int argb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
        float[] p = perimeter(x, y, w, h, r);
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        begin();
        WorldRenderer wr = triangles();
        int count = p.length / 2;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            color(wr, cx, cy, argb);
            color(wr, p[i * 2], p[i * 2 + 1], argb);
            color(wr, p[j * 2], p[j * 2 + 1], argb);
        }
        Tessellator.getInstance().draw();
        end();
    }

    /** Rounded outline of the given thickness, drawn inside the bounds. */
    public static void roundedOutline(float x, float y, float w, float h, float r, float thickness, int argb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(thickness, Math.min(r, Math.min(w, h) / 2f));
        float[] outer = perimeter(x, y, w, h, r);
        float[] inner = perimeter(x + thickness, y + thickness, w - thickness * 2, h - thickness * 2, r - thickness);
        begin();
        WorldRenderer wr = triangles();
        int count = outer.length / 2;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            color(wr, outer[i * 2], outer[i * 2 + 1], argb);
            color(wr, inner[i * 2], inner[i * 2 + 1], argb);
            color(wr, inner[j * 2], inner[j * 2 + 1], argb);
            color(wr, outer[i * 2], outer[i * 2 + 1], argb);
            color(wr, inner[j * 2], inner[j * 2 + 1], argb);
            color(wr, outer[j * 2], outer[j * 2 + 1], argb);
        }
        Tessellator.getInstance().draw();
        end();
    }

    public static void circle(float cx, float cy, float r, int argb) {
        begin();
        WorldRenderer wr = triangles();
        int segments = 28;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            color(wr, cx, cy, argb);
            color(wr, cx + Math.cos(a0) * r, cy + Math.sin(a0) * r, argb);
            color(wr, cx + Math.cos(a1) * r, cy + Math.sin(a1) * r, argb);
        }
        Tessellator.getInstance().draw();
        end();
    }

    public static void triangle(float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        begin();
        WorldRenderer wr = triangles();
        color(wr, x1, y1, argb);
        color(wr, x2, y2, argb);
        color(wr, x3, y3, argb);
        Tessellator.getInstance().draw();
        end();
    }

    /** Thick line segment as a quad. */
    public static void line(float x1, float y1, float x2, float y2, float thickness, int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len == 0) {
            return;
        }
        float nx = -dy / len * thickness / 2;
        float ny = dx / len * thickness / 2;
        begin();
        WorldRenderer wr = triangles();
        color(wr, x1 + nx, y1 + ny, argb);
        color(wr, x1 - nx, y1 - ny, argb);
        color(wr, x2 - nx, y2 - ny, argb);
        color(wr, x1 + nx, y1 + ny, argb);
        color(wr, x2 - nx, y2 - ny, argb);
        color(wr, x2 + nx, y2 + ny, argb);
        Tessellator.getInstance().draw();
        end();
    }

    // ------------------------------------------------------------------ icons (size = box edge)

    public static void iconPlay(float x, float y, float s, int argb) {
        triangle(x + s * 0.2f, y + s * 0.1f, x + s * 0.2f, y + s * 0.9f, x + s * 0.9f, y + s * 0.5f, argb);
    }

    public static void iconPause(float x, float y, float s, int argb) {
        roundedRect(x + s * 0.2f, y + s * 0.12f, s * 0.22f, s * 0.76f, s * 0.06f, argb);
        roundedRect(x + s * 0.58f, y + s * 0.12f, s * 0.22f, s * 0.76f, s * 0.06f, argb);
    }

    public static void iconNext(float x, float y, float s, int argb) {
        triangle(x + s * 0.12f, y + s * 0.15f, x + s * 0.12f, y + s * 0.85f, x + s * 0.68f, y + s * 0.5f, argb);
        rect(x + s * 0.7f, y + s * 0.15f, s * 0.14f, s * 0.7f, argb);
    }

    public static void iconPrevious(float x, float y, float s, int argb) {
        triangle(x + s * 0.88f, y + s * 0.15f, x + s * 0.32f, y + s * 0.5f, x + s * 0.88f, y + s * 0.85f, argb);
        rect(x + s * 0.16f, y + s * 0.15f, s * 0.14f, s * 0.7f, argb);
    }

    /** Three stacked lines ("list"). */
    public static void iconList(float x, float y, float s, int argb) {
        for (int i = 0; i < 3; i++) {
            roundedRect(x + s * 0.12f, y + s * (0.2f + i * 0.27f), s * 0.76f, s * 0.12f, s * 0.06f, argb);
        }
    }
}
