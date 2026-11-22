package dev.larpclient.modules.hud;

import dev.larpclient.LarpClient;
import dev.larpclient.core.hud.TextHudModule;
import dev.larpclient.core.setting.BooleanSetting;

public final class CpsHud extends TextHudModule {
    private final BooleanSetting showRight = setting(new BooleanSetting("Show Right Click", "Also show right-click CPS", false));

    public CpsHud() {
        super("CPS", "Shows your clicks per second", 0.0, 0.085);
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    protected String getText() {
        long now = System.currentTimeMillis();
        int left = LarpClient.get().leftCps().getCps(now);
        boolean modern = dev.larpclient.modules.hud.HudTheme.modern();
        String value = showRight.isOn() ? left + (modern ? " \u00B7 " : " | ") + LarpClient.get().rightCps().getCps(now) : String.valueOf(left);
        return modern ? value : value + " CPS";
    }

    @Override
    protected String getSuffix() {
        return dev.larpclient.modules.hud.HudTheme.modern() ? "CPS" : null;
    }
}
