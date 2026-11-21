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
        box.setScreenPosition(450, 380, 1000, 400);
        assertEquals(450, box.getScreenX(1000), 0.5);
        assertEquals(380, box.getScreenY(400), 0.5);
        // Same relative position on a bigger screen keeps the element at the right/bottom edge ratio.
        assertEquals(0.5, box.getScreenX(1100) / (1100 - 100), 0.01);
    }

    @Test
    void positionIsClampedOnScreen() {
        Box box = new Box();
        box.setScreenPosition(5000, -50, 800, 600);
        assertEquals(700, box.getScreenX(800), 0.5);
        assertEquals(0, box.getScreenY(600), 0.5);
    }

    @Test
    void scaleAffectsScaledSize() {
        Box box = new Box();
        box.setScale(1.5);
        assertEquals(150, box.getScaledWidth(), 1e-6);
    }
}
