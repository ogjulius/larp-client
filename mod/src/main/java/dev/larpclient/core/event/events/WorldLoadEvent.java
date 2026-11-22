package dev.larpclient.core.event.events;

/** Posted when the client joins / switches to a world. */
public final class WorldLoadEvent {
    public static final WorldLoadEvent INSTANCE = new WorldLoadEvent();

    private WorldLoadEvent() {
    }
}
