package dev.larpclient.core.event.events;

/** Posted when a keyboard key is pressed while no GUI screen is open. */
public final class KeyPressEvent {
    public final int keyCode;

    public KeyPressEvent(int keyCode) {
        this.keyCode = keyCode;
    }
}
