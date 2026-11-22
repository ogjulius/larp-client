package dev.larpclient.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CpsTrackerTest {
    @Test
    void countsClicksInLastSecond() {
        CpsTracker t = new CpsTracker();
        t.click(0);
        t.click(500);
        t.click(900);
        assertEquals(3, t.getCps(999));
        assertEquals(2, t.getCps(1000));
        assertEquals(0, t.getCps(5000));
    }

    @Test
    void ringBufferOverflowKeepsNewest() {
        CpsTracker t = new CpsTracker();
        for (int i = 0; i < 200; i++) {
            t.click(10_000 + i);
        }
        assertEquals(64, t.getCps(10_200));
    }
}
