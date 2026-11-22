package dev.larpclient.core.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudModuleTest {
    static final class Box extends HudModule {
        Box() {
            super("Box", "", 0, 0);
        }

        @Override
        public int getWidth() {
            return 100;
        }

        @Override
        public int getHeight() {
            return 20;
        }

        @Override
        public void render(float partialTicks, boolean editing) {
        }
    }

    @Test
    void positionRoundTripsAndScalesWithScreen() {
        Box box = new Box();
        box.setScreenPosition(450, 300, 1000, 400);
        assertEquals(450, box.getScreenX(1000), 0.5);
        assertEquals(300, box.getScreenY(400), 0.5);
        // Fraction of free space is preserved on a bigger screen.
        float m = HudModule.EDGE_MARGIN;
        assertEquals((450 - m) / (1000 - 100 - 2 * m), (box.getScreenX(1100) - m) / (1100 - 100 - 2 * m), 0.001);
    }

    @Test
    void positionIsClampedOnScreen() {
        Box box = new Box();
        box.setScreenPosition(5000, -50, 800, 600);
        assertEquals(800 - 100 - HudModule.EDGE_MARGIN, box.getScreenX(800), 0.5);
        assertEquals(HudModule.EDGE_MARGIN, box.getScreenY(600), 0.5);
    }

    @Test
    void scaleAffectsScaledSize() {
        Box box = new Box();
        box.setScale(1.5);
        assertEquals(150, box.getScaledWidth(), 1e-6);
    }
}
