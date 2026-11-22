package dev.larpclient.core.gui.menu;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

import java.util.Map;

/** Draws the signed-in player's face (with hat layer), falling back to the default skin. */
public final class PlayerHead {
    private static ResourceLocation skin;
    private static boolean requested;

    private PlayerHead() {
    }

    private static ResourceLocation skin() {
        Minecraft mc = Minecraft.getMinecraft();
        GameProfile profile = mc.getSession().getProfile();
        if (!requested) {
            requested = true;
            try {
                // Vanilla's skin manager: cached + asynchronous, via Mojang's session server.
                Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures =
                        mc.getSkinManager().loadSkinFromCache(profile);
                MinecraftProfileTexture texture = textures.get(MinecraftProfileTexture.Type.SKIN);
                if (texture != null) {
                    skin = mc.getSkinManager().loadSkin(texture, MinecraftProfileTexture.Type.SKIN);
                }
            } catch (RuntimeException ignored) {
                // offline profile or no network: default skin below
            }
        }
        return skin != null ? skin : DefaultPlayerSkin.getDefaultSkin(profile.getId());
    }

    public static void draw(float x, float y, float size) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(skin());
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        float scale = size / 8f;
        GlStateManager.scale(scale, scale, 1f);
        Gui.drawScaledCustomSizeModalRect(0, 0, 8, 8, 8, 8, 8, 8, 64, 64);
        Gui.drawScaledCustomSizeModalRect(0, 0, 40, 8, 8, 8, 8, 8, 64, 64);
        GlStateManager.popMatrix();
    }
}
