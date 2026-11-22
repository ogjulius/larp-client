package dev.larpclient.core.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

/**
 * Slowly rotating, softly blurred panorama behind LARP's menus. The cube map is rendered into a
 * quarter-resolution framebuffer and upscaled with a 4-tap filter, which gives a cheap, smooth blur.
 */
public final class Panorama {
    private static final Panorama INSTANCE = new Panorama();
    private static final int DOWNSCALE = 4;
    private static final ResourceLocation[] FACES = new ResourceLocation[6];

    static {
        for (int i = 0; i < 6; i++) {
            FACES[i] = new ResourceLocation("textures/gui/title/background/panorama_" + i + ".png");
        }
    }

    private Framebuffer buffer;
    private final long start = System.currentTimeMillis();

    private Panorama() {
    }

    public static Panorama get() {
        return INSTANCE;
    }

    /** Draws the panorama covering the GUI-space rectangle (0, 0, width, height). */
    public void draw(int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        float t = (System.currentTimeMillis() - start) / 1000f;
        if (!OpenGlHelper.isFramebufferEnabled()) {
            renderCube(t, mc.displayWidth, mc.displayHeight);
            return;
        }
        int bw = Math.max(1, mc.displayWidth / DOWNSCALE);
        int bh = Math.max(1, mc.displayHeight / DOWNSCALE);
        if (buffer == null || buffer.framebufferWidth != bw || buffer.framebufferHeight != bh) {
            if (buffer != null) {
                buffer.deleteFramebuffer();
            }
            buffer = new Framebuffer(bw, bh, false);
            buffer.setFramebufferFilter(GL11.GL_LINEAR);
        }
        buffer.bindFramebuffer(true);
        GlStateManager.clearColor(0f, 0f, 0f, 1f);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);
        renderCube(t, bw, bh);
        mc.getFramebuffer().bindFramebuffer(true);

        // Upscale with a 4-tap box filter (running average via decreasing alpha).
        float texelX = (float) width / bw;
        float texelY = (float) height / bh;
        float[][] taps = {{-0.5f, -0.5f}, {0.5f, 0.5f}, {0.5f, -0.5f}, {-0.5f, 0.5f}};
        GlStateManager.enableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        buffer.bindFramebufferTexture();
        float u = (float) buffer.framebufferWidth / buffer.framebufferTextureWidth;
        float v = (float) buffer.framebufferHeight / buffer.framebufferTextureHeight;
        for (int i = 0; i < taps.length; i++) {
            GlStateManager.color(1f, 1f, 1f, 1f / (i + 1));
            float ox = taps[i][0] * texelX;
            float oy = taps[i][1] * texelY;
            Tessellator tess = Tessellator.getInstance();
            WorldRenderer wr = tess.getWorldRenderer();
            wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            wr.pos(ox, height + oy, 0).tex(0, 0).endVertex();
            wr.pos(width + ox, height + oy, 0).tex(u, 0).endVertex();
            wr.pos(width + ox, oy, 0).tex(u, v).endVertex();
            wr.pos(ox, oy, 0).tex(0, v).endVertex();
            tess.draw();
        }
        buffer.unbindFramebufferTexture();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableAlpha();
    }

    private void renderCube(float t, int viewportW, int viewportH) {
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.viewport(0, 0, viewportW, viewportH);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Project.gluPerspective(80f, viewportW / (float) viewportH, 0.05f, 10f);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.rotate(8f + (float) Math.sin(t * 0.05f) * 4f, 1f, 0f, 0f);
        GlStateManager.rotate(t * 2.2f, 0f, 1f, 0f);

        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        for (int face = 0; face < 6; face++) {
            GlStateManager.pushMatrix();
            switch (face) {
                case 1: GlStateManager.rotate(-90f, 0f, 1f, 0f); break;
                case 2: GlStateManager.rotate(180f, 0f, 1f, 0f); break;
                case 3: GlStateManager.rotate(90f, 0f, 1f, 0f); break;
                case 4: GlStateManager.rotate(90f, 1f, 0f, 0f); break;
                case 5: GlStateManager.rotate(-90f, 1f, 0f, 0f); break;
                default: break;
            }
            mc.getTextureManager().bindTexture(FACES[face]);
            Tessellator tess = Tessellator.getInstance();
            WorldRenderer wr = tess.getWorldRenderer();
            wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            wr.pos(-1, 1, -1).tex(0, 0).endVertex();
            wr.pos(1, 1, -1).tex(1, 0).endVertex();
            wr.pos(1, -1, -1).tex(1, 1).endVertex();
            wr.pos(-1, -1, -1).tex(0, 1).endVertex();
            tess.draw();
            GlStateManager.popMatrix();
        }
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
    }
}
