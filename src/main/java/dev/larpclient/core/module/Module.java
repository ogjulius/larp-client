package dev.larpclient.core.module;

import dev.larpclient.core.event.EventBus;
import dev.larpclient.core.event.Listener;
import dev.larpclient.core.event.Subscription;
import dev.larpclient.core.setting.KeySetting;
import dev.larpclient.core.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A self-contained client feature.
 * <p>
 * Modules declare event handlers with {@link #on} in their constructor. Handlers are only attached
 * to the {@link EventBus} while the module is enabled, so disabled modules have zero per-frame cost.
 * Modules must not touch Minecraft state in their constructor (keeps them unit-testable and safe to
 * construct before the game finishes loading).
 */
public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<Binding<?>> bindings = new ArrayList<>();
    private final List<Subscription> active = new ArrayList<>();
    private final KeySetting toggleKey;

    private EventBus bus;
    private boolean enabled;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.toggleKey = new KeySetting("Toggle Key", "Key that toggles this module (none = unbound)", KeySetting.NONE);
    }

    // ------------------------------------------------------------------ lifecycle

    /** Called by the {@link ModuleManager} on registration. */
    final void attach(EventBus bus) {
        this.bus = bus;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean enable) {
        if (enable == enabled) {
            return;
        }
        if (enable && blockedReason() != null) {
            return;
        }
        enabled = enable;
        if (enable) {
            if (bus != null) {
                for (Binding<?> binding : bindings) {
                    active.add(binding.subscribe(bus));
                }
            }
            onEnable();
        } else {
            for (Subscription subscription : active) {
                subscription.cancel();
            }
            active.clear();
            onDisable();
        }
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /**
     * Non-null if the module may not currently be enabled (e.g. a server disallows it). The
     * returned text is shown to the user.
     */
    public String blockedReason() {
        return null;
    }

    /** Whether this module starts enabled on a fresh install. */
    public boolean enabledByDefault() {
        return false;
    }

    // ------------------------------------------------------------------ declaration helpers

    protected final <T> void on(Class<T> type, Listener<? super T> listener) {
        bindings.add(new Binding<>(type, listener, EventBus.Priority.NORMAL));
    }

    protected final <T> void on(Class<T> type, Listener<? super T> listener, EventBus.Priority priority) {
        bindings.add(new Binding<>(type, listener, priority));
    }

    protected final <S extends Setting<?>> S setting(S setting) {
        settings.add(setting);
        return setting;
    }

    // ------------------------------------------------------------------ accessors

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public KeySetting getToggleKey() {
        return toggleKey;
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    public Setting<?> getSetting(String settingName) {
        for (Setting<?> setting : settings) {
            if (setting.getName().equalsIgnoreCase(settingName)) {
                return setting;
            }
        }
        return null;
    }

    /** Stable identifier used for persistence. */
    public String getId() {
        return name.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
    }

    private static final class Binding<T> {
        final Class<T> type;
        final Listener<? super T> listener;
        final EventBus.Priority priority;

        Binding(Class<T> type, Listener<? super T> listener, EventBus.Priority priority) {
            this.type = type;
            this.listener = listener;
            this.priority = priority;
        }

        Subscription subscribe(EventBus bus) {
            return bus.subscribe(type, listener, priority);
        }
    }
}
