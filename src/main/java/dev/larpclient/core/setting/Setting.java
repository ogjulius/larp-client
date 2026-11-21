package dev.larpclient.core.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A named, persisted, user-editable value owned by a module.
 *
 * @param <T> value type
 */
public abstract class Setting<T> {
    private final String name;
    private final String description;
    private final T defaultValue;
    private T value;
    private BooleanSupplier visibility = () -> true;
    private Consumer<T> changeListener;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public T get() {
        return value;
    }

    public T getDefault() {
        return defaultValue;
    }

    public void set(T newValue) {
        T sanitized = sanitize(newValue);
        if (sanitized == null || sanitized.equals(value)) {
            return;
        }
        value = sanitized;
        if (changeListener != null) {
            changeListener.accept(value);
        }
    }

    public void reset() {
        set(defaultValue);
    }

    /** Only show this setting in the GUI while {@code condition} holds. */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
        this.visibility = condition;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S onChange(Consumer<T> listener) {
        this.changeListener = listener;
        return (S) this;
    }

    public boolean isVisible() {
        return visibility.getAsBoolean();
    }

    /** Clamp / validate a candidate value. Return {@code null} to reject it. */
    protected T sanitize(T candidate) {
        return candidate;
    }

    public abstract JsonElement toJson();

    /** Load from JSON; malformed input is ignored and the current value kept. */
    public abstract void fromJson(JsonElement json);
}
