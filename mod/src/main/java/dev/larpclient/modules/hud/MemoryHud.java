package dev.larpclient.modules.hud;

import dev.larpclient.core.hud.TextHudModule;

public final class MemoryHud extends TextHudModule {
    public MemoryHud() {
        super("Memory", "Shows Java heap usage", 1.0, 0.085);
    }

    @Override
    protected String getText() {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long max = rt.maxMemory() / (1024 * 1024);
        if (dev.larpclient.modules.hud.HudTheme.modern()) {
            return used + " / " + max;
        }
        return String.format("Mem: %d%% %d/%dMB", used * 100 / Math.max(1, max), used, max);
    }

    @Override
    protected String getSuffix() {
        return dev.larpclient.modules.hud.HudTheme.modern() ? "MB" : null;
    }
}
