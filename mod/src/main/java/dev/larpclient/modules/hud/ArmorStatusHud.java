package dev.larpclient.modules.hud;

import dev.larpclient.core.hud.HudDraw;
import dev.larpclient.core.hud.HudModule;
import dev.larpclient.core.setting.BooleanSetting;
import dev.larpclient.core.setting.ModeSetting;
import dev.larpclient.core.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Shows worn armour (and optionally the held item) with durability. */
public final class ArmorStatusHud extends HudModule {
    private static final int ROW = 16;
    private static final ItemStack[] PLACEHOLDER = {
            new ItemStack(Items.diamond_helmet), new ItemStack(Items.diamond_chestplate),
            new ItemStack(Items.diamond_leggings), new ItemStack(Items.diamond_boots),
            new ItemStack(Items.diamond_sword)
    };

    private final ModeSetting durability = setting(new ModeSetting("Durability", "How to show durability",
            "Remaining", "Remaining", "Percent", "None"));
    private final BooleanSetting showHeld = setting(new BooleanSetting("Show Held Item", "Include the item in your hand", true));
    private final BooleanSetting colorCode = setting(new BooleanSetting("Color By Durability", "Green → red as items wear out", true));

    public ArmorStatusHud() {
        super("Armor Status", "Shows your armour and held item durability", 1.0, 0.92);
    }

    private ItemStack[] stacks(boolean editing) {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        ItemStack[] out = new ItemStack[5];
        boolean any = false;
        if (p != null) {
            for (int i = 0; i < 4; i++) {
                out[i] = p.inventory.armorInventory[3 - i]; // helmet first
                any |= out[i] != null;
            }
            if (showHeld.isOn()) {
                out[4] = p.getHeldItem();
                any |= out[4] != null;
            }
        }
        if (!any && editing) {
            out = PLACEHOLDER.clone();
            if (!showHeld.isOn()) {
                out[4] = null;
            }
        }
        return out;
    }

    @Override
    public boolean shouldRender() {
        for (ItemStack stack : stacks(false)) {
            if (stack != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getWidth() {
        return durability.is("None") ? 16 : 16 + 4 + RenderUtil.font().getStringWidth("1561");
    }

    @Override
    public int getHeight() {
        return ROW * (showHeld.isOn() ? 5 : 4);
    }

    @Override
    public void render(float partialTicks, boolean editing) {
        Minecraft mc = Minecraft.getMinecraft();
        ItemStack[] stacks = stacks(editing);
        int y = 0;
        int rows = showHeld.isOn() ? 5 : 4;
        for (int i = 0; i < rows; i++) {
            ItemStack stack = stacks[i];
            if (stack == null) {
                y += ROW;
                continue;
            }
            GlStateManager.pushMatrix();
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, y);
            mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, stack, 0, y);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
            String text = durabilityText(stack);
            if (text != null) {
                HudDraw.text(text, 20, y + (16 - HudDraw.height()) / 2f, color(stack));
            }
            y += ROW;
        }
        GlStateManager.enableBlend();
        GlStateManager.disableLighting();
    }

    private String durabilityText(ItemStack stack) {
        if (durability.is("None")) {
            return null;
        }
        if (!stack.isItemStackDamageable()) {
            return stack.stackSize > 1 ? String.valueOf(stack.stackSize) : null;
        }
        int remaining = stack.getMaxDamage() - stack.getItemDamage();
        return durability.is("Percent")
                ? (remaining * 100 / Math.max(1, stack.getMaxDamage())) + "%"
                : String.valueOf(remaining);
    }

    private int color(ItemStack stack) {
        if (!colorCode.isOn() || !stack.isItemStackDamageable()) {
            return 0xFFFFFFFF;
        }
        float fraction = 1f - (float) stack.getItemDamage() / Math.max(1, stack.getMaxDamage());
        return HudTheme.modern() ? RenderUtil.blend(0xFFFF6B6B, 0xFF7CF29A, fraction) : RenderUtil.blend(0xFFFF5555, 0xFF55FF55, fraction);
    }
}
