package dev.larpclient.modules.hud;

import dev.larpclient.core.module.Category;
import dev.larpclient.core.module.Module;
import dev.larpclient.core.setting.BooleanSetting;
import dev.larpclient.core.setting.ColorSetting;
import dev.larpclient.core.setting.ModeSetting;
import dev.larpclient.core.setting.NumberSetting;

/** Global look of all HUD elements. Disabling it falls back to the classic vanilla-font style. */
public final class HudTheme extends Module {
    private static HudTheme instance;

    private final ModeSetting font = setting(new ModeSetting("Font", "Typeface for HUD text", "Inter", "Inter", "Minecraft"));
    private final NumberSetting opacity = setting(new NumberSetting("Background Opacity", "How dark element backgrounds are", 55, 0, 100, 5));
    private final NumberSetting radius = setting(new NumberSetting("Corner Radius", "Roundness of element backgrounds", 3.5, 0, 7, 0.5));
    private final BooleanSetting border = setting(new BooleanSetting("Hairline Border", "Thin light outline around elements", true));
    private final ColorSetting accent = setting(new ColorSetting("Accent", "Colour for pressed keys and highlights", 0xFF8B5CF6));
    private final BooleanSetting textShadow = setting(new BooleanSetting("Soft Text Shadow", "Subtle shadow behind text", true));

    public HudTheme() {
        super("HUD Theme", "Modern look for every HUD element (disable for classic)", Category.HUD);
        instance = this;
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    public static boolean modern() {
        return instance == null || instance.isEnabled();
    }

    public static boolean interFont() {
        return modern() && (instance == null || instance.font.is("Inter"));
    }

    public static int background() {
        int alpha = instance == null ? 140 : (int) (instance.opacity.get() * 2.55);
        return (alpha << 24) | 0x0C0C10;
    }

    public static float radius() {
        return instance == null ? 3.5f : instance.radius.floatValue();
    }

    public static boolean border() {
        return instance == null || instance.border.isOn();
    }

    public static int accent() {
        return instance == null ? 0xFF8B5CF6 : instance.accent.resolve();
    }

    public static boolean shadow() {
        return instance == null || instance.textShadow.isOn();
    }
}
