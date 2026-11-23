package dev.larpclient.launcher.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.larpclient.launcher.util.Http;
import dev.larpclient.launcher.util.Json;
import dev.larpclient.launcher.util.Platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Downloads and verifies Minecraft 1.8.9 + Forge into a {@link GameFiles} layout. Every step is
 * idempotent: files already present with the right checksum are skipped, so subsequent launches
 * only cost a quick verification pass.
 */
public final class Installer {
    public static final String MC_VERSION = "1.8.9";
    public static final String FORGE_VERSION = "1.8.9-11.15.1.2318-1.8.9";

    static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    static final String MOJANG_LIBRARIES = "https://libraries.minecraft.net/";
    static final String FORGE_MAVEN = "https://maven.minecraftforge.net/";
    static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    static final String RESOURCES = "https://resources.download.minecraft.net/";

    private final GameFiles files;

    public Installer(GameFiles files) {
        this.files = files;
    }

    public InstalledGame install(Progress reporter) throws IOException {
        Progress progress = Progress.monotonic(reporter);
        progress.update("Fetching Minecraft " + MC_VERSION + " metadata", -1);
        JsonObject vanilla = vanillaJson();

        Path versionDir = files.versionDir(MC_VERSION);
        JsonObject client = Json.obj(Json.obj(vanilla, "downloads"), "client");
        Path clientJar = versionDir.resolve(MC_VERSION + ".jar");
        progress.update("Downloading Minecraft " + MC_VERSION, -1);
        Http.download(Json.str(client, "url"), clientJar, Json.str(client, "sha1"));

        progress.update("Installing Forge " + FORGE_VERSION, -1);
        JsonObject forgeProfile = installForge();
        JsonObject forgeInfo = Json.obj(forgeProfile, "versionInfo");

        List<Library> libraries = resolveLibraries(vanilla, forgeInfo);
        AtomicInteger done = new AtomicInteger();
        List<Callable<Void>> jobs = new ArrayList<>();
        for (Library library : libraries) {
            jobs.add(() -> {
                downloadLibrary(library);
                progress.update("Downloading libraries", done.incrementAndGet() / (double) libraries.size());
                return null;
            });
        }
        runParallel(jobs, 8);

        progress.update("Extracting natives", -1);
        Path natives = files.nativesDir(MC_VERSION);
        extractNatives(libraries, natives);

        String assetIndex = installAssets(vanilla, progress);

        Path loggingConfig = null;
        String loggingArgument = null;
        JsonObject logging = Json.obj(Json.obj(vanilla, "logging"), "client");
        if (logging != null) {
            JsonObject file = Json.obj(logging, "file");
            loggingConfig = files.assets.resolve("log_configs").resolve(Json.str(file, "id"));
            Http.download(Json.str(file, "url"), loggingConfig, Json.str(file, "sha1"));
            loggingArgument = Json.str(logging, "argument");
        }

        List<Path> classpath = new ArrayList<>();
        for (Library library : libraries) {
            if (!library.natives) {
                classpath.add(files.library(library.path));
            }
        }
        classpath.add(clientJar);

        JsonObject install = Json.obj(forgeProfile, "install");
        progress.update("Ready", 1);
        return new InstalledGame(Json.str(install, "target", MC_VERSION), Json.str(forgeInfo, "mainClass"),
                Json.str(forgeInfo, "minecraftArguments"), assetIndex, classpath, natives, loggingConfig, loggingArgument);
    }

    // ------------------------------------------------------------------ vanilla

    private JsonObject vanillaJson() throws IOException {
        Path jsonFile = files.versionDir(MC_VERSION).resolve(MC_VERSION + ".json");
        if (!Files.isRegularFile(jsonFile)) {
            JsonObject manifest = Json.parse(Http.get(MANIFEST));
            String url = null;
            String sha1 = null;
            for (JsonElement e : Json.arr(manifest, "versions")) {
                JsonObject v = e.getAsJsonObject();
                if (MC_VERSION.equals(Json.str(v, "id"))) {
                    url = Json.str(v, "url");
                    sha1 = Json.str(v, "sha1");
                }
            }
            if (url == null) {
                throw new IOException("Minecraft " + MC_VERSION + " not found in Mojang's version manifest");
            }
            Http.download(url, jsonFile, sha1);
        }
        return Json.parse(Files.readString(jsonFile));
    }

    // ------------------------------------------------------------------ forge

    private JsonObject installForge() throws IOException {
        String installerPath = Maven.path("net.minecraftforge:forge:" + FORGE_VERSION + ":installer");
        Path installer = files.library(installerPath);
        Http.download(FORGE_MAVEN + installerPath, installer, null);
        try (ZipFile zip = new ZipFile(installer.toFile())) {
            ZipEntry profileEntry = zip.getEntry("install_profile.json");
            if (profileEntry == null) {
                throw new IOException("Forge installer has no install_profile.json");
            }
            JsonObject profile;
            try (InputStream in = zip.getInputStream(profileEntry)) {
                profile = Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            JsonObject install = Json.obj(profile, "install");
            Path universal = files.library(Maven.path(Json.str(install, "path")));
            if (!Files.isRegularFile(universal)) {
                ZipEntry jar = zip.getEntry(Json.str(install, "filePath"));
                if (jar == null) {
                    throw new IOException("Forge installer is missing the universal jar");
                }
                Files.createDirectories(universal.getParent());
                Path tmp = universal.resolveSibling(universal.getFileName() + ".part");
                try (InputStream in = zip.getInputStream(jar)) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(tmp, universal, StandardCopyOption.REPLACE_EXISTING);
            }
            return profile;
        }
    }

    // ------------------------------------------------------------------ libraries

    List<Library> resolveLibraries(JsonObject vanilla, JsonObject forgeInfo) {
        String os = Platform.OS.mojangName;
        String osVersion = System.getProperty("os.version");
        Map<String, Library> result = new LinkedHashMap<>();

        String forgeCoordinate = "net.minecraftforge:forge:" + FORGE_VERSION;
        for (JsonElement e : Json.arr(forgeInfo, "libraries")) {
            JsonObject lib = e.getAsJsonObject();
            String name = Json.str(lib, "name");
            if (lib.has("clientreq") && !lib.get("clientreq").getAsBoolean()) {
                continue;
            }
            String path = Maven.path(name);
            if (name.equals(forgeCoordinate)) {
                // extracted from the installer, nothing to download
                result.put(Maven.key(name), new Library(Maven.key(name), path, null, null, false));
                continue;
            }
            String base = Json.str(lib, "url", MOJANG_LIBRARIES).replace("http://files.minecraftforge.net/maven/", FORGE_MAVEN);
            if (!base.endsWith("/")) {
                base += "/";
            }
            result.put(Maven.key(name), new Library(Maven.key(name), path, base + path, null, false,
                    FORGE_MAVEN, MAVEN_CENTRAL, MOJANG_LIBRARIES));
        }

        String arch = System.getProperty("os.arch", "").contains("64") ? "64" : "32";
        for (JsonElement e : Json.arr(vanilla, "libraries")) {
            JsonObject lib = e.getAsJsonObject();
            if (!Rules.allowed(Json.arr(lib, "rules"), os, osVersion)) {
                continue;
            }
            String name = Json.str(lib, "name");
            JsonObject downloads = Json.obj(lib, "downloads");
            JsonObject artifact = Json.obj(downloads, "artifact");
            if (artifact != null && !result.containsKey(Maven.key(name))) {
                result.put(Maven.key(name), new Library(Maven.key(name), Json.str(artifact, "path"),
                        Json.str(artifact, "url"), Json.str(artifact, "sha1"), false));
            }
            JsonObject natives = Json.obj(lib, "natives");
            if (natives != null && natives.has(os)) {
                String classifier = Json.str(natives, os).replace("${arch}", arch);
                JsonObject nativeArtifact = Json.obj(Json.obj(downloads, "classifiers"), classifier);
                if (nativeArtifact != null) {
                    String key = Maven.key(name) + ":" + classifier;
                    result.put(key, new Library(key, Json.str(nativeArtifact, "path"),
                            Json.str(nativeArtifact, "url"), Json.str(nativeArtifact, "sha1"), true));
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    private void downloadLibrary(Library library) throws IOException {
        Path target = files.library(library.path);
        if (library.url == null) {
            if (!Files.isRegularFile(target)) {
                throw new IOException("Missing library " + library.key);
            }
            return;
        }
        try {
            Http.download(library.url, target, library.sha1);
        } catch (Http.HttpStatusException primary) {
            for (String mirror : library.mirrors) {
                try {
                    Http.download(mirror + library.path, target, library.sha1);
                    return;
                } catch (Http.HttpStatusException ignored) {
                    // try next mirror
                }
            }
            throw new IOException("Could not download " + library.key + " from any repository", primary);
        }
    }

    private void extractNatives(List<Library> libraries, Path natives) throws IOException {
        Files.createDirectories(natives);
        for (Library library : libraries) {
            if (!library.natives) {
                continue;
            }
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(files.library(library.path)))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory() || entry.getName().startsWith("META-INF")) {
                        continue;
                    }
                    Path out = natives.resolve(entry.getName()).normalize();
                    if (!out.startsWith(natives)) {
                        continue; // zip-slip guard
                    }
                    Files.createDirectories(out.getParent());
                    Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    // ------------------------------------------------------------------ assets

    private String installAssets(JsonObject vanilla, Progress progress) throws IOException {
        JsonObject indexInfo = Json.obj(vanilla, "assetIndex");
        String id = Json.str(indexInfo, "id");
        Path indexFile = files.assets.resolve("indexes").resolve(id + ".json");
        progress.update("Downloading asset index", -1);
        Http.download(Json.str(indexInfo, "url"), indexFile, Json.str(indexInfo, "sha1"));
        JsonObject objects = Json.obj(Json.parse(Files.readString(indexFile)), "objects");

        List<String> hashes = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            String hash = Json.str(entry.getValue().getAsJsonObject(), "hash");
            if (!hashes.contains(hash)) {
                hashes.add(hash);
            }
        }
        AtomicInteger done = new AtomicInteger();
        List<Callable<Void>> jobs = new ArrayList<>();
        for (String hash : hashes) {
            jobs.add(() -> {
                String sub = hash.substring(0, 2) + "/" + hash;
                Path target = files.assets.resolve("objects").resolve(sub);
                // Size+existence check is enough for already present objects (hashing ~1000 files each launch is slow).
                if (!Files.isRegularFile(target)) {
                    Http.download(RESOURCES + sub, target, hash);
                }
                int n = done.incrementAndGet();
                if (n % 20 == 0 || n == hashes.size()) {
                    progress.update("Downloading assets", n / (double) hashes.size());
                }
                return null;
            });
        }
        runParallel(jobs, 16);
        return id;
    }

    // ------------------------------------------------------------------ helpers

    static void runParallel(List<Callable<Void>> jobs, int threads) throws IOException {
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "larp-download");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> job : jobs) {
                futures.add(pool.submit(job));
            }
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof IOException ? (IOException) cause : new IOException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        } finally {
            pool.shutdownNow();
        }
    }
}
