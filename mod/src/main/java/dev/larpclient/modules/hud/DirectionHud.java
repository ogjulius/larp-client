package dev.larpclient.modules.hud;

import dev.larpclient.core.hud.TextHudModule;
import dev.larpclient.core.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public final class DirectionHud extends TextHudModule {
    private static final String[] SHORT = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] LONG = {"South", "South West", "West", "North West", "North", "North East", "East", "South East"};

    private final BooleanSetting longNames = setting(new BooleanSetting("Full Names", "Use full direction names", false));
    private final BooleanSetting showYaw = setting(new BooleanSetting("Show Yaw", "Append the exact yaw angle", false));

    public DirectionHud() {
        super("Direction", "Shows which way you are facing", 1.0, 0.34);
    }

    /** Eight-way compass name for a Minecraft yaw (0 = south, 90 = west). */
    public static String facing(float yaw, boolean full) {
        int index = (int) Math.floor((MathHelper.wrapAngleTo180_float(yaw) + 360 + 22.5) / 45.0) & 7;
        return full ? LONG[index] : SHORT[index];
    }

    @Override
    protected String getText() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return null;
        }
        String text = facing(mc.thePlayer.rotationYaw, longNames.isOn());
        if (showYaw.isOn()) {
            text += String.format(" (%.1f)", MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw));
        }
        return text;
    }

    @Override
    protected String getPlaceholder() {
        return "N";
    }
}
