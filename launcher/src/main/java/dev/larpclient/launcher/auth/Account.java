package dev.larpclient.launcher.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A Minecraft identity: either an offline profile or a Microsoft account. */
public final class Account {
    public enum Type { OFFLINE, MICROSOFT }

    public Type type;
    public String username;
    /** Profile UUID without dashes. */
    public String uuid;
    public String accessToken = "0";
    public long accessTokenExpiresAt;
    public String refreshToken;

    public static Account offline(String username) {
        Account account = new Account();
        account.type = Type.OFFLINE;
        account.username = username;
        account.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        return account;
    }

    public static boolean isValidUsername(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }

    public String userType() {
        return type == Type.MICROSOFT ? "msa" : "legacy";
    }

    public boolean needsRefresh() {
        return type == Type.MICROSOFT && System.currentTimeMillis() > accessTokenExpiresAt - 5 * 60_000L;
    }

    public String describe() {
        return username + (type == Type.MICROSOFT ? "  (Microsoft)" : "  (Offline)");
    }
}
