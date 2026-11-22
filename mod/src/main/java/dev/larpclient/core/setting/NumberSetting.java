package dev.larpclient.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A bounded numeric value, snapped to {@code step}. */
public class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, defaultValue);
        if (min > max || step <= 0) {
            throw new IllegalArgumentException("Invalid range for " + name);
        }
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    public int intValue() {
        return (int) Math.round(get());
    }

    public float floatValue() {
        return get().floatValue();
    }

    /** Fraction in [0, 1] of the current value inside the range (for sliders). */
    public double getPercent() {
        return max == min ? 0 : (get() - min) / (max - min);
    }

    public void setPercent(double percent) {
        set(min + (max - min) * percent);
    }

    @Override
    protected Double sanitize(Double candidate) {
        if (candidate == null || candidate.isNaN() || candidate.isInfinite()) {
            return null;
        }
        double snapped = min + Math.round((candidate - min) / step) * step;
        snapped = Math.max(min, Math.min(max, snapped));
        // Remove floating point noise from step arithmetic (e.g. 0.30000000000000004).
        return Math.round(snapped * 1e6) / 1e6;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            set(json.getAsDouble());
        }
    }

    /** Human readable value, trimming ".0" for integer steps. */
    public String format() {
        double v = get();
        if (step >= 1 && v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
