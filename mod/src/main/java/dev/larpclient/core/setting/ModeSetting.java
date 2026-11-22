package dev.larpclient.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** One of a fixed list of string options. */
public class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        super(name, description, defaultValue);
        this.modes = Collections.unmodifiableList(Arrays.asList(modes));
        if (!this.modes.contains(defaultValue)) {
            throw new IllegalArgumentException("Default mode not in options for " + name);
        }
    }

    public List<String> getModes() {
        return modes;
    }

    public boolean is(String mode) {
        return get().equals(mode);
    }

    public void cycle(boolean forward) {
        int index = modes.indexOf(get());
        int next = Math.floorMod(index + (forward ? 1 : -1), modes.size());
        set(modes.get(next));
    }

    @Override
    protected String sanitize(String candidate) {
        return modes.contains(candidate) ? candidate : null;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            set(json.getAsString());
        }
    }
}
