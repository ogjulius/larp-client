package dev.larpclient.modules.hud;

import dev.larpclient.LarpClient;
import dev.larpclient.core.hud.TextHudModule;
import dev.larpclient.core.setting.BooleanSetting;

public final class CpsHud extends TextHudModule {
    private final BooleanSetting showRight = setting(new BooleanSetting("Show Right Click", "Also show right-click CPS", false));

    public CpsHud() {
        super("CPS", "Shows your clicks per second", 0.0, 0.1);
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    protected String getText() {
        long now = System.currentTimeMillis();
        int left = LarpClient.get().leftCps().getCps(now);
        if (showRight.isOn()) {
            return left + " | " + LarpClient.get().rightCps().getCps(now) + " CPS";
        }
        return left + " CPS";
    }
}
