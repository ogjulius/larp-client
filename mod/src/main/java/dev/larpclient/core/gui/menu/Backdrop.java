package dev.larpclient.core.gui.menu;

import dev.larpclient.core.render.Panorama;
import dev.larpclient.core.render.Shapes;

/** Shared background for LARP screens outside a world: blurred panorama + legibility shading. */
public final class Backdrop {
    private Backdrop() {
    }

    /** Full panorama with a left-to-right scrim for left-aligned content. */
    public static void drawMenu(int width, int height) {
        Panorama.get().draw(width, height);
        Shapes.horizontalGradient(0, 0, width * 0.62f, height, 0xE6070709, 0x40070709);
        Shapes.rect(width * 0.62f, 0, width * 0.38f, height, 0x40070709);
        Shapes.verticalGradient(0, height * 0.6f, width, height * 0.4f, 0x00000000, 0x90000000);
    }

    /** Uniformly dimmed panorama for screens with a centred panel. */
    public static void drawDimmed(int width, int height) {
        Panorama.get().draw(width, height);
        Shapes.rect(0, 0, width, height, 0xA0070709);
    }
}
