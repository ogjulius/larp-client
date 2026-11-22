package dev.larpclient.core.event;

/** Handle returned by {@link EventBus#subscribe}; call {@link #cancel()} to unsubscribe. */
public interface Subscription {
    void cancel();
}
