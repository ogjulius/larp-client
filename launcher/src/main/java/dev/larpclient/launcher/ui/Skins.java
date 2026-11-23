package dev.larpclient.launcher.ui;

import dev.larpclient.launcher.auth.Account;
import dev.larpclient.launcher.game.GameFiles;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Player skins for offline profiles: the default Steve/Alex skin, chosen the same way the game does. */
public final class Skins {
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private Skins() {
    }

    public static BufferedImage get(Account account, GameFiles files) {
        if (account == null) {
            return defaultSkin(null, files);
        }
        BufferedImage cached = CACHE.get(account.uuid);
        if (cached != null) {
            return cached;
        }
        BufferedImage skin = defaultSkin(account.uuid, files);
        if (skin != null) {
            CACHE.putIfAbsent(account.uuid, skin);
        }
        return skin;
    }

    private static BufferedImage defaultSkin(String uuid, GameFiles files) {
        boolean slim = false;
        if (uuid != null && uuid.length() == 32) {
            UUID id = UUID.fromString(uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
            slim = (id.hashCode() & 1) == 1; // same rule as DefaultPlayerSkin in 1.8.9
        }
        return GameArt.readFromClient(files, "assets/minecraft/textures/entity/" + (slim ? "alex" : "steve") + ".png");
    }
}
