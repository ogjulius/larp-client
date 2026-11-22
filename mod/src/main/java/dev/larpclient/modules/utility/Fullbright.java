package dev.larpclient.modules.utility;

import dev.larpclient.core.module.Category;
import dev.larpclient.core.module.Module;
import dev.larpclient.core.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/** Maximum brightness regardless of light level. */
public final class Fullbright extends Module {
    private static final float BRIGHT = 100f;
    private final NumberSetting savedGamma = setting(new NumberSetting("Saved Gamma", "Brightness to restore", 0.5, 0, 1, 0.01)
            .visibleWhen(() -> false));

    public Fullbright() {
        super("Fullbright", "See clearly in the dark", Category.UTILITY);
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return;
        }
        if (mc.gameSettings.gammaSetting <= 1f) {
            savedGamma.set((double) mc.gameSettings.gammaSetting);
        }
        mc.gameSettings.gammaSetting = BRIGHT;
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.gameSettings != null) {
            mc.gameSettings.gammaSetting = savedGamma.floatValue();
        }
    }
}
