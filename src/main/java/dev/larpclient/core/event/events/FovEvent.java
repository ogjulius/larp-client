package dev.larpclient.core.event.events;

/** Posted when the camera FOV is computed for a frame; listeners may modify {@link #fov}. */
public final class FovEvent {
    public float fov;
}
