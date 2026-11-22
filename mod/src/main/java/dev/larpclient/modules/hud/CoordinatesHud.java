package dev.larpclient.modules.hud;

import dev.larpclient.core.hud.TextHudModule;
import dev.larpclient.core.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public final class CoordinatesHud extends TextHudModule {
    private final BooleanSetting showDirection = setting(new BooleanSetting("Show Facing", "Append the facing direction", true));

    public CoordinatesHud() {
        super("Coordinates", "Shows your block position", 0.0, 0.15);
    }

    @Override
    protected String getText() {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        if (p == null) {
            return null;
        }
        String text = String.format("XYZ: %d, %d, %d",
                (int) Math.floor(p.posX), (int) Math.floor(p.getEntityBoundingBox().minY), (int) Math.floor(p.posZ));
        return showDirection.isOn() ? text + " " + DirectionHud.facing(p.rotationYaw, false) : text;
    }

    @Override
    protected String getPlaceholder() {
        return "XYZ: 0, 64, 0 S";
    }
}
