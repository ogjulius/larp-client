package dev.larpclient.modules.hud;

import dev.larpclient.core.hud.TextHudModule;
import net.minecraft.client.Minecraft;

public final class FpsHud extends TextHudModule {
    public FpsHud() {
        super("FPS", "Shows your frames per second", 0.0, 0.0);
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    protected String getText() {
        return Minecraft.getDebugFPS() + " FPS";
    }
}
