package dev.larpclient.launcher.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressTest {
    /** Regression (audit A18): parallel workers delivering updates out of order must not make progress jump back. */
    @Test
    void monotonicDropsStaleFractionsWithinAStep() {
        List<String> seen = new ArrayList<>();
        Progress p = Progress.monotonic((status, fraction) -> seen.add(status + " " + fraction));
        p.update("Downloading libraries", 0.2);
        p.update("Downloading libraries", 0.4);
        p.update("Downloading libraries", 0.3); // a slower worker's older count
        p.update("Downloading libraries", 1.0);
        p.update("Downloading assets", 0.1);    // a new step starts from zero
        p.update("Downloading assets", -1);     // indeterminate is always passed on
        assertEquals(List.of("Downloading libraries 0.2", "Downloading libraries 0.4", "Downloading libraries 1.0",
                "Downloading assets 0.1", "Downloading assets -1.0"), seen);
    }
}
