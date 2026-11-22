package dev.larpclient.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.larpclient.launcher.util.Json;

import java.util.regex.Pattern;

/** Evaluates Mojang library/argument "rules" against the current OS. */
public final class Rules {
    private Rules() {
    }

    public static boolean allowed(JsonArray rules, String osName, String osVersion) {
        if (rules == null || rules.size() == 0) {
            return true;
        }
        boolean allowed = false;
        for (JsonElement element : rules) {
            JsonObject rule = element.getAsJsonObject();
            boolean matches = true;
            JsonObject os = Json.obj(rule, "os");
            if (os != null) {
                String name = Json.str(os, "name");
                String version = Json.str(os, "version");
                if (name != null && !name.equals(osName)) {
                    matches = false;
                }
                if (version != null && osVersion != null && !Pattern.compile(version).matcher(osVersion).find()) {
                    matches = false;
                }
            }
            if (rule.has("features")) {
                matches = false; // demo / custom resolution features are never requested
            }
            if (matches) {
                allowed = "allow".equals(Json.str(rule, "action"));
            }
        }
        return allowed;
    }
}
