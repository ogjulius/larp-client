package dev.larpclient.core.event.events;

/** Posted once per client tick (20 Hz), after vanilla tick logic, while a world is loaded. */
public final class ClientTickEvent {
    public static final ClientTickEvent INSTANCE = new ClientTickEvent();

    private ClientTickEvent() {
    }
}
