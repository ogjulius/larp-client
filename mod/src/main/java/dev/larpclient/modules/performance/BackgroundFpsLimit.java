package dev.larpclient.modules.performance;

import dev.larpclient.core.module.Category;
import dev.larpclient.core.module.Module;
import dev.larpclient.core.setting.NumberSetting;

/** Caps the frame rate while the window is unfocused. Applied by {@code MixinMinecraft}. */
public final class BackgroundFpsLimit extends Module {
    private static BackgroundFpsLimit instance;
    private final NumberSetting fps = setting(new NumberSetting("Background FPS", "Frame cap when unfocused", 15, 1, 60, 1));

    public BackgroundFpsLimit() {
        super("Background FPS Limit", "Saves CPU, GPU and battery when the game is not focused", Category.PERFORMANCE);
        instance = this;
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    /** The background frame cap, or -1 when inactive. */
    public static int limit() {
        return instance != null && instance.isEnabled() ? instance.fps.intValue() : -1;
    }
}
