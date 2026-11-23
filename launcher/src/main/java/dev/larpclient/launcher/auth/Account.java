package dev.larpclient.launcher.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** An offline (non-premium) Minecraft profile. Microsoft accounts are not supported. */
public final class Account {
    /** Only offline profiles exist; an unknown type in the file (e.g. MICROSOFT) reads as null. */
    public enum Type { OFFLINE }

    public Type type;
    public String username;
    /** Profile UUID without dashes. */
    public String uuid;
    /** Always "0" for offline play; written so older launchers can read the file. */
    public String accessToken = "0";

    /** An offline profile with the UUID vanilla Minecraft derives from the name. */
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

    /** Minecraft's --userType for an offline profile. */
    public String userType() {
        return "legacy";
    }

    public String describe() {
        return username + "  (Offline)";
    }
}
