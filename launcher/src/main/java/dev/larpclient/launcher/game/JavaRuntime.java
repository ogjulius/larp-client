package dev.larpclient.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.larpclient.launcher.util.Http;
import dev.larpclient.launcher.util.Json;
import dev.larpclient.launcher.util.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds a Java 8 runtime for Minecraft 1.8.9: the user's configured path, then an installed system
 * JDK/JRE 8, and finally Mojang's own "jre-legacy" runtime which is downloaded on demand.
 */
public final class JavaRuntime {
    private static final String RUNTIME_INDEX =
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
    private static final String COMPONENT = "jre-legacy";

    private JavaRuntime() {
    }

    public static Path resolve(String configured, GameFiles files, Progress progress) throws IOException {
        if (configured != null && !configured.isBlank()) {
            Path path = Paths.get(configured.trim());
            if (Files.isExecutable(path)) {
                return path;
            }
            throw new IOException("Configured Java not found or not executable: " + configured);
        }
        progress.update("Looking for Java 8", -1);
        for (Path candidate : systemCandidates()) {
            if (isJava8(candidate)) {
                return candidate;
            }
        }
        return downloadMojangRuntime(files, progress);
    }

    /** Common Java 8 install locations on the current OS. */
    static List<Path> systemCandidates() {
        List<Path> result = new ArrayList<>();
        String exe = Platform.javaExecutableName();
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            result.add(Paths.get(javaHome, "bin", exe));
        }
        List<Path> roots = new ArrayList<>();
        switch (Platform.OS) {
            case LINUX:
                roots.add(Paths.get("/usr/lib/jvm"));
                roots.add(Paths.get("/usr/lib64/jvm"));
                roots.add(Paths.get(System.getProperty("user.home"), ".sdkman/candidates/java"));
                break;
            case OSX:
                roots.add(Paths.get("/Library/Java/JavaVirtualMachines"));
                break;
            default:
                roots.add(Paths.get("C:\\Program Files\\Java"));
                roots.add(Paths.get("C:\\Program Files\\Eclipse Adoptium"));
        }
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root)) {
                for (Path dir : dirs) {
                    String name = dir.getFileName().toString();
                    if (name.contains("8") || name.contains("1.8")) {
                        result.add(dir.resolve("bin").resolve(exe));
                        result.add(dir.resolve("Contents/Home/bin").resolve(exe));
                        result.add(dir.resolve("jre/bin").resolve(exe));
                    }
                }
            } catch (IOException ignored) {
                // unreadable directory
            }
        }
        return result;
    }

    static boolean isJava8(Path java) {
        if (!Files.isExecutable(java)) {
            return false;
        }
        try {
            Process process = new ProcessBuilder(java.toString(), "-version").redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(10, TimeUnit.SECONDS);
            return output.contains("version \"1.8");
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static Path downloadMojangRuntime(GameFiles files, Progress progress) throws IOException {
        Path home = files.runtimes.resolve(COMPONENT);
        Path java = home.resolve("bin").resolve(Platform.javaExecutableName());
        progress.update("Downloading Java 8 runtime", -1);
        JsonObject index = Json.parse(Http.get(RUNTIME_INDEX));
        JsonArray versions = Json.arr(Json.obj(index, Platform.runtimePlatform()), COMPONENT);
        if (versions.size() == 0) {
            throw new IOException("No Java 8 runtime available for " + Platform.runtimePlatform()
                    + "; install Java 8 and set it in Settings");
        }
        String manifestUrl = Json.str(Json.obj(versions.get(0).getAsJsonObject(), "manifest"), "url");
        JsonObject filesJson = Json.obj(Json.parse(Http.get(manifestUrl)), "files");

        List<Callable<Void>> jobs = new ArrayList<>();
        List<Map.Entry<String, JsonElement>> links = new ArrayList<>();
        AtomicInteger done = new AtomicInteger();
        int total = filesJson.size();
        for (Map.Entry<String, JsonElement> entry : filesJson.entrySet()) {
            JsonObject info = entry.getValue().getAsJsonObject();
            Path target = home.resolve(entry.getKey()).normalize();
            if (!target.startsWith(home)) {
                continue;
            }
            String type = Json.str(info, "type");
            if ("directory".equals(type)) {
                Files.createDirectories(target);
            } else if ("link".equals(type)) {
                links.add(entry);
            } else if ("file".equals(type)) {
                JsonObject raw = Json.obj(Json.obj(info, "downloads"), "raw");
                boolean executable = info.has("executable") && info.get("executable").getAsBoolean();
                jobs.add(() -> {
                    Http.download(Json.str(raw, "url"), target, Json.str(raw, "sha1"));
                    if (executable) {
                        target.toFile().setExecutable(true, false);
                    }
                    progress.update("Downloading Java 8 runtime", done.incrementAndGet() / (double) total);
                    return null;
                });
            }
        }
        Installer.runParallel(jobs, 8);
        for (Map.Entry<String, JsonElement> link : links) {
            Path target = home.resolve(link.getKey());
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(target.getParent());
                Files.createSymbolicLink(target, Paths.get(Json.str(link.getValue().getAsJsonObject(), "target")));
            }
        }
        if (!Files.isExecutable(java)) {
            throw new IOException("Downloaded Java runtime is incomplete");
        }
        return java;
    }
}
