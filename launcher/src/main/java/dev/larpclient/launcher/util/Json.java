package dev.larpclient.launcher.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Gson helpers with null-safe accessors. */
public final class Json {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private Json() {
    }

    public static JsonObject parse(String text) {
        return GSON.fromJson(text, JsonObject.class);
    }

    public static String str(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    public static String str(JsonObject o, String key, String fallback) {
        String s = str(o, key);
        return s == null ? fallback : s;
    }

    public static long num(JsonObject o, String key, long fallback) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? fallback : e.getAsLong();
    }

    public static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    public static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }
}
