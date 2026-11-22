package dev.larpclient.modules.hud;

import dev.larpclient.core.ServerPolicy;
import dev.larpclient.core.hud.TextHudModule;

public final class ServerAddressHud extends TextHudModule {
    public ServerAddressHud() {
        super("Server Address", "Shows the server you are playing on", 1.0, 0.17);
    }

    @Override
    protected String getText() {
        String server = ServerPolicy.currentServer();
        return server.isEmpty() ? null : server;
    }

    @Override
    protected String getPlaceholder() {
        return "play.example.net";
    }
}
