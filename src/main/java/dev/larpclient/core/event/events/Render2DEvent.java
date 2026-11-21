package dev.larpclient.core.event.events;

/** Posted every frame after the vanilla in-game overlay has been drawn. Reused instance. */
public final class Render2DEvent {
    public float partialTicks;
    public int scaledWidth;
    public int scaledHeight;
}
