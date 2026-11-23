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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Persists module state to a single human-editable JSON profile.
 * <p>
 * Writes are atomic (temp file + move) so a crash mid-save never corrupts the profile. A profile
 * that fails to parse is moved aside to {@code <name>.broken} and defaults are applied.
 * <p>
 * Threading: {@link #save()} and {@link #saveAsync()} must be called on the client thread, which owns
 * module state; both take the snapshot there. {@code saveAsync} writes it on a background thread so the
 * render thread never waits for the disk. Snapshots carry a sequence number and an older one is never
 * written over a newer one.
 */
public final class ConfigManager {
    public static final int FORMAT_VERSION = 1;
    private static final Logger LOGGER = LogManager.getLogger("LARP Client/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final ModuleManager modules;
    private final Object writeLock = new Object();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicReference<Snapshot> pending = new AtomicReference<>();
    private long writtenSequence;
    private ExecutorService writer;

    private static final class Snapshot {
        final long sequence;
        final String json;

        Snapshot(long sequence, String json) {
            this.sequence = sequence;
            this.json = json;
        }
    }

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

    /** Snapshot and write now (blocking). */
    public void save() {
        write(snapshot());
    }

    /** Snapshot now, write on the background writer. Bursts of calls coalesce into one write. */
    public void saveAsync() {
        Snapshot snapshot = snapshot();
        if (pending.getAndSet(snapshot) == null) {
            writer().execute(() -> {
                Snapshot latest = pending.getAndSet(null);
                if (latest != null) {
                    write(latest);
                }
            });
        }
    }

    /** Wait (up to 5 s) for queued background writes. */
    public void flush() {
        ExecutorService w;
        synchronized (this) {
            w = writer;
        }
        if (w == null) {
            return;
        }
        try {
            w.submit(() -> { }).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.warn("Timed out waiting for profile writes", e);
        }
    }

    private synchronized ExecutorService writer() {
        if (writer == null) {
            writer = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "LARP config writer");
                t.setDaemon(true);
                return t;
            });
        }
        return writer;
    }

    private Snapshot snapshot() {
        return new Snapshot(sequence.incrementAndGet(), GSON.toJson(serialize()));
    }

    private void write(Snapshot snapshot) {
        synchronized (writeLock) {
            if (snapshot.sequence <= writtenSequence) {
                return; // a newer snapshot is already on disk
            }
            try {
                Files.createDirectories(file.toAbsolutePath().getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                try (Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                    out.write(snapshot.json);
                }
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicUnsupported) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                writtenSequence = snapshot.sequence;
            } catch (IOException e) {
                LOGGER.error("Failed to save profile {}", file, e);
            }
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
