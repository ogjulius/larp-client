package dev.larpclient.core.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Small drawing helpers on top of vanilla's immediate mode rendering. */
public final class RenderUtil {
    private RenderUtil() {
    }

    public static void rect(float x1, float y1, float x2, float y2, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        Gui.drawRect(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2), argb);
    }

    public static void outline(float x1, float y1, float x2, float y2, int argb) {
        rect(x1, y1, x2, y1 + 1, argb);
        rect(x1, y2 - 1, x2, y2, argb);
        rect(x1, y1 + 1, x1 + 1, y2 - 1, argb);
        rect(x2 - 1, y1 + 1, x2, y2 - 1, argb);
    }

    public static FontRenderer font() {
        return Minecraft.getMinecraft().fontRendererObj;
    }

    public static void text(String text, float x, float y, int argb, boolean shadow) {
        font().drawString(text, x, y, argb, shadow);
    }

    public static void centeredText(String text, float centerX, float y, int argb, boolean shadow) {
        text(text, centerX - font().getStringWidth(text) / 2f, y, argb, shadow);
    }

    /** Linear blend between two ARGB colours. */
    public static int blend(int from, int to, float t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void resetColor() {
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** Restrict drawing to a GUI-space rectangle. Pair with {@link #endScissor()}. */
    public static void scissor(float x, float y, float width, float height) {
        Minecraft mc = Minecraft.getMinecraft();
        int factor = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(Math.round(x * factor), Math.round(mc.displayHeight - (y + height) * factor),
                Math.max(0, Math.round(width * factor)), Math.max(0, Math.round(height * factor)));
    }

    public static void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static boolean hovered(int mouseX, int mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
