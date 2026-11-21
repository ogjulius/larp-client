package dev.larpclient.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An LWJGL key code; 0 ({@code Keyboard.KEY_NONE}) means unbound. */
public class KeySetting extends Setting<Integer> {
    public static final int NONE = 0;

    public KeySetting(String name, String description, int defaultKey) {
        super(name, description, defaultKey);
    }

    @Override
    protected Integer sanitize(Integer candidate) {
        return candidate == null || candidate < 0 || candidate > 255 ? null : candidate;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            set(json.getAsInt());
        }
    }
}
