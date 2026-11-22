package dev.larpclient.modules.performance;

import dev.larpclient.core.module.Category;
import dev.larpclient.core.module.Module;
import dev.larpclient.core.setting.BooleanSetting;
import dev.larpclient.core.setting.NumberSetting;

/** Low-level rendering optimisations applied through mixins. */
public final class FastRender extends Module {
    private static FastRender instance;

    private final BooleanSetting skipGlErrors = setting(new BooleanSetting("Skip GL Error Checks",
            "Removes per-frame glGetError calls that stall the GPU pipeline", true));
    private final NumberSetting menuFps = setting(new NumberSetting("Menu FPS",
            "Frame cap in menus outside a world (vanilla: 30)", 120, 30, 240, 10));

    public FastRender() {
        super("Fast Render", "Removes rendering stalls and smooths out menus", Category.PERFORMANCE);
        instance = this;
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    /** Whether vanilla's glGetError checks should still run. */
    public static boolean glErrorChecks() {
        return instance == null || !instance.isEnabled() || !instance.skipGlErrors.isOn();
    }

    public static int menuFramerate(int userLimit) {
        if (instance == null || !instance.isEnabled()) {
            return 30;
        }
        return Math.min(userLimit, instance.menuFps.intValue());
    }
}
