package dev.larpclient.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnimationTest {
    @Test
    void convergesToTarget() {
        Animation a = new Animation(0);
        a.setTarget(1);
        long t = 0;
        a.update(t, 0.3);
        for (int i = 0; i < 200; i++) {
            t += 16;
            a.update(t, 0.3);
        }
        assertEquals(1.0, a.get(), 1e-9);
    }

    @Test
    void frameRateIndependent() {
        Animation fast = new Animation(0);
        Animation slow = new Animation(0);
        fast.setTarget(1);
        slow.setTarget(1);
        fast.update(0, 0.2);
        slow.update(0, 0.2);
        for (int t = 8; t <= 160; t += 8) {
            fast.update(t, 0.2);
        }
        for (int t = 32; t <= 160; t += 32) {
            slow.update(t, 0.2);
        }
        assertEquals(fast.get(), slow.get(), 1e-6);
    }
}
