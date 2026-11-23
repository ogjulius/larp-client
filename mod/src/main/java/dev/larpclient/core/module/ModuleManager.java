package dev.larpclient.core.module;

import dev.larpclient.core.event.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of all modules. */
public final class ModuleManager {
    private final EventBus bus;
    private final Map<String, Module> byId = new LinkedHashMap<>();
    private final Map<Class<? extends Module>, Module> byClass = new IdentityHashMap<>();
    private Runnable changeListener = () -> { };

    public ModuleManager(EventBus bus) {
        this.bus = bus;
    }

    public <M extends Module> M register(M module) {
        if (byId.containsKey(module.getId())) {
            throw new IllegalStateException("Duplicate module id: " + module.getId());
        }
        module.attach(bus);
        byId.put(module.getId(), module);
        byClass.put(module.getClass(), module);
        return module;
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    public List<Module> getModules(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : byId.values()) {
            if (module.getCategory() == category) {
                result.add(module);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public <M extends Module> M get(Class<M> type) {
        return (M) byClass.get(type);
    }

    public Module get(String id) {
        return byId.get(id);
    }

    /** Called after a module is toggled outside a menu (hotkey), so the change can be persisted. */
    public void setChangeListener(Runnable listener) {
        changeListener = listener == null ? () -> { } : listener;
    }

    /** Toggle every module bound to {@code keyCode}. */
    public void handleKey(int keyCode) {
        if (keyCode <= 0) {
            return;
        }
        boolean changed = false;
        for (Module module : byId.values()) {
            if (module.getToggleKey().get() == keyCode) {
                boolean before = module.isEnabled();
                module.toggle();
                changed |= module.isEnabled() != before;
            }
        }
        if (changed) {
            changeListener.run();
        }
    }

    /** Enable the modules flagged {@link Module#enabledByDefault()} (fresh install). */
    public void applyDefaults() {
        for (Module module : byId.values()) {
            module.setEnabled(module.enabledByDefault());
        }
    }

    /** Disable any enabled module that is currently blocked (e.g. after joining a server). */
    public void enforcePolicies() {
        for (Module module : byId.values()) {
            if (module.isEnabled() && module.blockedReason() != null) {
                module.setEnabled(false);
            }
        }
    }
}
