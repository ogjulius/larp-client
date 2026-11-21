package dev.larpclient.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.larpclient.core.module.Module;
import dev.larpclient.core.module.ModuleManager;
import dev.larpclient.core.setting.Setting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Persists module state to a single human-editable JSON profile.
 * <p>
 * Writes are atomic (temp file + move) so a crash mid-save never corrupts the profile. A profile
 * that fails to parse is moved aside to {@code <name>.broken} and defaults are applied.
 */
public final class ConfigManager {
    public static final int FORMAT_VERSION = 1;
    private static final Logger LOGGER = LogManager.getLogger("LARP Client/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final ModuleManager modules;

    public ConfigManager(Path file, ModuleManager modules) {
        this.file = file;
        this.modules = modules;
    }

    public Path getFile() {
        return file;
    }

    /** Load the profile, or apply defaults if it does not exist / is unreadable. */
    public void load() {
        if (!Files.isRegularFile(file)) {
            modules.applyDefaults();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject()) {
                throw new IllegalStateException("Profile root is not an object");
            }
            apply(root.getAsJsonObject());
        } catch (Exception e) {
            LOGGER.error("Failed to load profile {}, using defaults", file, e);
            quarantine();
            modules.applyDefaults();
        }
    }

    public void save() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(serialize(), writer);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save profile {}", file, e);
        }
    }

    JsonObject serialize() {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        JsonObject moduleObjects = new JsonObject();
        for (Module module : modules.getModules()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());
            entry.add("toggleKey", module.getToggleKey().toJson());
            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                settings.add(setting.getName(), setting.toJson());
            }
            entry.add("settings", settings);
            moduleObjects.add(module.getId(), entry);
        }
        root.add("modules", moduleObjects);
        return root;
    }

    void apply(JsonObject root) {
        JsonObject moduleObjects = root.has("modules") && root.get("modules").isJsonObject()
                ? root.getAsJsonObject("modules") : new JsonObject();
        for (Module module : modules.getModules()) {
            JsonElement element = moduleObjects.get(module.getId());
            if (element == null || !element.isJsonObject()) {
                // Module added since this profile was written.
                module.setEnabled(module.enabledByDefault());
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            module.getToggleKey().fromJson(entry.get("toggleKey"));
            if (entry.has("settings") && entry.get("settings").isJsonObject()) {
                for (Map.Entry<String, JsonElement> s : entry.getAsJsonObject("settings").entrySet()) {
                    Setting<?> setting = module.getSetting(s.getKey());
                    if (setting != null) {
                        setting.fromJson(s.getValue());
                    }
                }
            }
            JsonElement enabled = entry.get("enabled");
            module.setEnabled(enabled != null && enabled.isJsonPrimitive() && enabled.getAsBoolean());
        }
    }

    private void quarantine() {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not move broken profile aside", e);
        }
    }
}
