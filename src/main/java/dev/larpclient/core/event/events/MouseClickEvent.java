package dev.larpclient.core.event.events;

/** Posted when a mouse button is pressed in-game (0 = left, 1 = right, 2 = middle). */
public final class MouseClickEvent {
    public final int button;

    public MouseClickEvent(int button) {
        this.button = button;
    }
}
