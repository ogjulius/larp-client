package dev.larpclient.core.gui.menu;

import dev.larpclient.core.render.Shapes;
import dev.larpclient.core.render.StarrySky;

/** Shared background for LARP screens outside a world: a starry night sky. */
public final class Backdrop {
    private Backdrop() {
    }

    /** Full sky with a soft scrim behind the left-aligned menu column. */
    public static void drawMenu(int width, int height) {
        StarrySky.get().draw(width, height);
        Shapes.horizontalGradient(0, 0, width * 0.45f, height, 0x90030308, 0x00030308);
    }

    /** Sky dimmed for screens with a centred panel. */
    public static void drawDimmed(int width, int height) {
        StarrySky.get().draw(width, height);
        Shapes.rect(0, 0, width, height, 0x80030308);
    }
}
