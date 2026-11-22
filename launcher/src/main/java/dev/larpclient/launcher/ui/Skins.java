package dev.larpclient.launcher.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.larpclient.launcher.auth.Account;
import dev.larpclient.launcher.game.GameFiles;
import dev.larpclient.launcher.util.Http;
import dev.larpclient.launcher.util.Json;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves player skins: Microsoft accounts via Mojang's session server (cached on disk), offline
 * profiles use the default Steve/Alex skin chosen the same way the game does.
 */
public final class Skins {
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private Skins() {
    }

    /** Returns a skin immediately (default if not yet loaded) and loads the real one in the background. */
    public static BufferedImage get(Account account, GameFiles files, Runnable onLoaded) {
        if (account == null) {
            return defaultSkin(null, files);
        }
        BufferedImage cached = CACHE.get(account.uuid);
        if (cached != null) {
            return cached;
        }
        BufferedImage fallback = defaultSkin(account.uuid, files);
        if (account.type == Account.Type.MICROSOFT) {
            Thread loader = new Thread(() -> {
                BufferedImage skin = fetch(account.uuid, files);
                if (skin != null) {
                    CACHE.put(account.uuid, skin);
                    javax.swing.SwingUtilities.invokeLater(onLoaded);
                }
            }, "larp-skin");
            loader.setDaemon(true);
            loader.start();
        }
        if (fallback != null) {
            CACHE.putIfAbsent(account.uuid, fallback);
        }
        return fallback;
    }

    private static BufferedImage defaultSkin(String uuid, GameFiles files) {
        boolean slim = false;
        if (uuid != null && uuid.length() == 32) {
            UUID id = UUID.fromString(uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
            slim = (id.hashCode() & 1) == 1; // same rule as DefaultPlayerSkin in 1.8.9
        }
        return GameArt.readFromClient(files, "assets/minecraft/textures/entity/" + (slim ? "alex" : "steve") + ".png");
    }

    private static BufferedImage fetch(String uuid, GameFiles files) {
        Path cache = files.root.resolve("skins").resolve(uuid + ".png");
        try {
            if (Files.isRegularFile(cache) && System.currentTimeMillis() - Files.getLastModifiedTime(cache).toMillis() < 86_400_000L) {
                return ImageIO.read(cache.toFile());
            }
            JsonObject profile = Json.parse(Http.get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid));
            for (JsonElement p : Json.arr(profile, "properties")) {
                JsonObject prop = p.getAsJsonObject();
                if ("textures".equals(Json.str(prop, "name"))) {
                    String decoded = new String(Base64.getDecoder().decode(Json.str(prop, "value")), StandardCharsets.UTF_8);
                    String url = Json.str(Json.obj(Json.obj(Json.parse(decoded), "textures"), "SKIN"), "url");
                    if (url != null && url.startsWith("http://textures.minecraft.net/")) {
                        url = "https" + url.substring(4);
                    }
                    if (url != null && url.startsWith("https://textures.minecraft.net/")) {
                        Http.download(url, cache, null);
                        Files.setLastModifiedTime(cache, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
                        return ImageIO.read(cache.toFile());
                    }
                }
            }
        } catch (Exception ignored) {
            // keep the default skin
        }
        return null;
    }
}
