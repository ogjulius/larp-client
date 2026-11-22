package dev.larpclient.modules.hud;

import dev.larpclient.core.hud.HudDraw;
import dev.larpclient.core.hud.HudModule;
import dev.larpclient.core.setting.BooleanSetting;
import dev.larpclient.core.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Lists active potion effects with icon, level and time remaining. */
public final class PotionEffectsHud extends HudModule {
    private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");
    private static final int ROW = 22;
    private static final int WIDTH = 110;

    private final BooleanSetting showIcons = setting(new BooleanSetting("Show Icons", "Draw potion icons", true));
    private final BooleanSetting blinkExpiring = setting(new BooleanSetting("Blink When Expiring", "Flash effects under 10 seconds", true));

    public PotionEffectsHud() {
        super("Potion Effects", "Lists your active potion effects", 1.0, 0.52);
    }

    private List<PotionEffect> effects(boolean editing) {
        Minecraft mc = Minecraft.getMinecraft();
        Collection<PotionEffect> active = mc.thePlayer == null
                ? Collections.<PotionEffect>emptyList() : mc.thePlayer.getActivePotionEffects();
        List<PotionEffect> list = new ArrayList<>(active);
        if (list.isEmpty() && editing) {
            list.add(new PotionEffect(Potion.moveSpeed.id, 20 * 90, 1));
            list.add(new PotionEffect(Potion.fireResistance.id, 20 * 180, 0));
        }
        return list;
    }

    @Override
    public boolean shouldRender() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null && !mc.thePlayer.getActivePotionEffects().isEmpty();
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return Math.max(1, effects(true).size()) * ROW;
    }

    @Override
    public void render(float partialTicks, boolean editing) {
        Minecraft mc = Minecraft.getMinecraft();
        int y = 0;
        for (PotionEffect effect : effects(editing)) {
            Potion potion = Potion.potionTypes[effect.getPotionID()];
            if (potion == null) {
                continue;
            }
            boolean expiring = effect.getDuration() < 200 && blinkExpiring.isOn();
            if (expiring && (System.currentTimeMillis() / 400) % 2 == 0) {
                y += ROW;
                continue;
            }
            int textX = 0;
            if (showIcons.isOn() && potion.hasStatusIcon()) {
                GlStateManager.color(1f, 1f, 1f, 1f);
                mc.getTextureManager().bindTexture(INVENTORY);
                int icon = potion.getStatusIconIndex();
                mc.ingameGUI.drawTexturedModalRect(0, y + 2, (icon % 8) * 18, 198 + (icon / 8) * 18, 18, 18);
                textX = 22;
            }
            String name = I18n.format(potion.getName());
            if (effect.getAmplifier() > 0) {
                name += " " + I18n.format("enchantment.level." + (effect.getAmplifier() + 1));
            }
            if (HudTheme.modern()) {
                HudDraw.panel(-2, y, WIDTH + 2, ROW - 2, HudTheme.background());
                mc.getTextureManager().bindTexture(INVENTORY);
                if (showIcons.isOn() && potion.hasStatusIcon()) {
                    GlStateManager.color(1f, 1f, 1f, 1f);
                    int icon = potion.getStatusIconIndex();
                    mc.ingameGUI.drawTexturedModalRect(0, y + 1, (icon % 8) * 18, 198 + (icon / 8) * 18, 18, 18);
                }
                HudDraw.text(name, textX, y + 2, 0xFFFFFFFF);
                HudDraw.text(Potion.getDurationString(effect), textX, y + 11, potion.isBadEffect() ? 0xFFFF8A8A : 0xB3FFFFFF);
            } else {
                RenderUtil.text(name, textX, y + 2, 0xFFFFFFFF, true);
                RenderUtil.text(Potion.getDurationString(effect), textX, y + 12, 0xFFAAAAAA, true);
            }
            y += ROW;
        }
    }
}
