package dev.larpclient.modules.utility;

import dev.larpclient.core.module.Category;
import dev.larpclient.core.module.Module;

/** Removes the camera shake when taking damage. Applied by {@code MixinEntityRenderer}. */
public final class NoHurtCam extends Module {
    private static NoHurtCam instance;

    public NoHurtCam() {
        super("No Hurt Cam", "Removes the camera shake when you take damage", Category.UTILITY);
        instance = this;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }
}
