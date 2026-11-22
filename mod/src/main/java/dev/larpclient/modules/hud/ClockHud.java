package dev.larpclient.modules.hud;

import dev.larpclient.core.hud.TextHudModule;
import dev.larpclient.core.setting.ModeSetting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ClockHud extends TextHudModule {
    private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);

    private final ModeSetting format = setting(new ModeSetting("Format", "Clock format", "24h", "24h", "12h"));

    public ClockHud() {
        super("Clock", "Shows the real-world time", 1.0, 0.0);
    }

    @Override
    protected String getText() {
        return LocalTime.now().format(format.is("24h") ? H24 : H12);
    }
}
