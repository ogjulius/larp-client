package dev.larpclient.launcher.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LauncherSettingsTest {
    @Test
    void roundTrip(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("launcher.json");
        LauncherSettings s = LauncherSettings.load(file);
        s.memoryMb = 4096;
        s.jvmArgs = "-Dx=1";
        s.save();
        LauncherSettings loaded = LauncherSettings.load(file);
        assertEquals(4096, loaded.memoryMb);
        assertEquals("-Dx=1", loaded.jvmArgs);
        assertNull(loaded.loadProblem());
    }

    /** Regression (audit A3): corrupt settings are reported and kept aside instead of silently reset. */
    @Test
    void corruptFileIsQuarantinedAndReported(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("launcher.json");
        Files.writeString(file, "{\"memoryMb\": 4096,");
        LauncherSettings s = LauncherSettings.load(file);
        assertNotNull(s.loadProblem());
        assertEquals(3072, s.memoryMb);
        assertEquals("{\"memoryMb\": 4096,", Files.readString(dir.resolve("launcher.json.broken")));
    }

    @Test
    void memoryIsClamped(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("launcher.json");
        Files.writeString(file, "{\"memoryMb\": 99999}");
        assertEquals(16384, LauncherSettings.load(file).memoryMb);
    }
}
