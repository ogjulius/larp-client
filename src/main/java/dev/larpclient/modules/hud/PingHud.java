package dev.larpclient.modules.hud;

import dev.larpclient.core.hud.TextHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

public final class PingHud extends TextHudModule {
    public PingHud() {
        super("Ping", "Shows your latency to the server", 0.0, 0.14);
    }

    @Override
    protected String getText() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isSingleplayer() || mc.thePlayer == null || mc.getNetHandler() == null) {
            return null;
        }
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return info == null ? null : info.getResponseTime() + " ms";
    }

    @Override
    protected String getPlaceholder() {
        return "42 ms";
    }
}
