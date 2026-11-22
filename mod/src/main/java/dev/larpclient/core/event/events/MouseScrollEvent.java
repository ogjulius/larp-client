package dev.larpclient.core.event.events;

import dev.larpclient.core.event.Cancellable;

/** Posted on in-game mouse wheel movement. Cancel to stop vanilla hotbar scrolling. */
public final class MouseScrollEvent extends Cancellable {
    public final int delta;

    public MouseScrollEvent(int delta) {
        this.delta = delta;
    }
}
