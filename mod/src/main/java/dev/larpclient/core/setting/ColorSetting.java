package dev.larpclient.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.awt.Color;

/** An ARGB colour, optionally animated as a rainbow ("chroma"). */
public class ColorSetting extends Setting<Integer> {
    private boolean chroma;

    public ColorSetting(String name, String description, int defaultArgb) {
        super(name, description, defaultArgb);
    }

    public boolean isChroma() {
        return chroma;
    }

    public void setChroma(boolean chroma) {
        this.chroma = chroma;
    }

    /** The colour to draw with right now (resolves chroma against the wall clock). */
    public int resolve() {
        if (!chroma) {
            return get();
        }
        float hue = (System.currentTimeMillis() % 4000L) / 4000f;
        return (get() & 0xFF000000) | (Color.HSBtoRGB(hue, 0.8f, 1f) & 0x00FFFFFF);
    }

    public float[] toHsb() {
        int c = get();
        return Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
    }

    public void setHsb(float hue, float saturation, float brightness) {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF;
        set((get() & 0xFF000000) | rgb);
    }

    public int getAlpha() {
        return (get() >>> 24) & 0xFF;
    }

    public void setAlpha(int alpha) {
        set((Math.max(0, Math.min(255, alpha)) << 24) | (get() & 0x00FFFFFF));
    }

    @Override
    public JsonElement toJson() {
        String hex = String.format("#%08X", get());
        return new JsonPrimitive(chroma ? hex + ":chroma" : hex);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
            return;
        }
        String raw = json.getAsString();
        boolean isChroma = raw.endsWith(":chroma");
        if (isChroma) {
            raw = raw.substring(0, raw.length() - ":chroma".length());
        }
        if (!raw.startsWith("#") || raw.length() != 9) {
            return;
        }
        try {
            set((int) Long.parseLong(raw.substring(1), 16));
            chroma = isChroma;
        } catch (NumberFormatException ignored) {
            // keep current value
        }
    }
}
