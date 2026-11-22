package dev.larpclient.core.event;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal, allocation-light, reflection-free event bus.
 * <p>
 * Dispatch is keyed by the exact event class (no hierarchy walk) which keeps {@link #post} to a
 * single map lookup plus an array iteration. Listener lists are copy-on-write: subscribing is rare
 * (module toggles) while posting happens every frame. Modules subscribe only while enabled, so
 * disabled features cost nothing at runtime.
 */
public final class EventBus {
    private final Map<Class<?>, CopyOnWriteArrayList<Entry<?>>> listeners = new ConcurrentHashMap<>();

    public <T> Subscription subscribe(Class<T> type, Listener<? super T> listener) {
        return subscribe(type, listener, Priority.NORMAL);
    }

    public <T> Subscription subscribe(Class<T> type, Listener<? super T> listener, Priority priority) {
        CopyOnWriteArrayList<Entry<?>> list = listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());
        Entry<T> entry = new Entry<>(listener, priority);
        synchronized (list) {
            int index = 0;
            while (index < list.size() && list.get(index).priority.ordinal() <= priority.ordinal()) {
                index++;
            }
            list.add(index, entry);
        }
        return () -> list.remove(entry);
    }

    /** Dispatches {@code event} to all listeners of its exact class and returns it. */
    @SuppressWarnings("unchecked")
    public <T> T post(T event) {
        CopyOnWriteArrayList<Entry<?>> list = listeners.get(event.getClass());
        if (list != null) {
            for (Entry<?> entry : list) {
                ((Entry<T>) entry).listener.on(event);
            }
        }
        return event;
    }

    public boolean hasListeners(Class<?> type) {
        CopyOnWriteArrayList<Entry<?>> list = listeners.get(type);
        return list != null && !list.isEmpty();
    }

    public enum Priority {
        HIGHEST, HIGH, NORMAL, LOW, LOWEST
    }

    private static final class Entry<T> {
        final Listener<? super T> listener;
        final Priority priority;

        Entry(Listener<? super T> listener, Priority priority) {
            this.listener = listener;
            this.priority = priority;
        }
    }
}
