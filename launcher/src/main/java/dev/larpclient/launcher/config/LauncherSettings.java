package dev.larpclient.launcher.config;

import dev.larpclient.launcher.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** User preferences persisted to {@code launcher.json}. Public fields keep Gson mapping trivial. */
public final class LauncherSettings {
    public int memoryMb = 3072;
    public String javaPath = "";
    /** Previous default, migrated automatically to {@link #DEFAULT_JVM_ARGS}. */
    private static final String OLD_DEFAULT_JVM_ARGS =
            "-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:MaxGCPauseMillis=50";
    /**
     * G1 tuned for a game client on Java 8: short pauses, a large young generation (Minecraft
     * allocates heavily per frame), no System.gc() stalls from mods, and string de-duplication.
     */
    public static final String DEFAULT_JVM_ARGS = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=30 "
            + "-XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=50 "
            + "-XX:G1HeapRegionSize=8M -XX:G1ReservePercent=15 -XX:InitiatingHeapOccupancyPercent=20 "
            + "-XX:+DisableExplicitGC -XX:+UseStringDeduplication -XX:+UseFastAccessorMethods";

    public String jvmArgs = DEFAULT_JVM_ARGS;
    /** Mesa / NVIDIA threaded GL dispatch: big FPS gains for Minecraft's CPU-bound GL calls. */
    public boolean threadedGl = true;
    public int width = 1280;
    public int height = 720;
    public boolean fullscreen = false;
    /** When false the launcher exits as soon as Minecraft has started. */
    public boolean keepLauncherOpen = false;
    /** Azure application (client) ID used for Microsoft sign-in. */
    public String microsoftClientId = "";

    private transient Path file;

    public static LauncherSettings load(Path file) {
        LauncherSettings settings = null;
        try {
            if (Files.isRegularFile(file)) {
                settings = Json.GSON.fromJson(Files.readString(file), LauncherSettings.class);
            }
        } catch (Exception ignored) {
            // fall back to defaults
        }
        if (settings == null) {
            settings = new LauncherSettings();
        }
        settings.file = file;
        settings.memoryMb = Math.max(1024, Math.min(16384, settings.memoryMb));
        if (settings.jvmArgs == null || settings.jvmArgs.trim().equals(OLD_DEFAULT_JVM_ARGS)) {
            settings.jvmArgs = DEFAULT_JVM_ARGS;
        }
        return settings;
    }

    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, Json.GSON.toJson(this));
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
