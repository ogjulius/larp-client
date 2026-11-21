package dev.larpclient.core.event;

/** Base class for events whose default behaviour listeners may suppress. */
public abstract class Cancellable {
    private boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }
}
