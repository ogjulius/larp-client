package dev.larpclient.core.event;

/** A typed event callback. */
@FunctionalInterface
public interface Listener<T> {
    void on(T event);
}
