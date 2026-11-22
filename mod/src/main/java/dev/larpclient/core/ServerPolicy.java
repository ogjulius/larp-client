package dev.larpclient.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;

/**
 * Knows which servers disallow which client features, so LARP never puts players at risk of a
 * ban for using a feature a server has asked clients not to provide.
 */
public final class ServerPolicy {
    private ServerPolicy() {
    }

    /** Lower-case address of the current multiplayer server, or {@code ""} in singleplayer/menus. */
    public static String currentServer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.isSingleplayer()) {
            return "";
        }
        ServerData data = mc.getCurrentServerData();
        return data == null || data.serverIP == null ? "" : data.serverIP.toLowerCase(Locale.ROOT);
    }

    public static boolean isOn(String... domains) {
        return matches(currentServer(), domains);
    }

    /** True if {@code address} (optionally with port) is any of {@code domains} or a subdomain. */
    public static boolean matches(String address, String... domains) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        String host = address.toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon > 0) {
            host = host.substring(0, colon);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        for (String domain : domains) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }
}
