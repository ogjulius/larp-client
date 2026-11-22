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
    public String jvmArgs = "-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:MaxGCPauseMillis=50";
    public int width = 1280;
    public int height = 720;
    public boolean fullscreen = false;
    public boolean keepLauncherOpen = true;
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
        return settings;
    }

    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, Json.GSON.toJson(this));
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
