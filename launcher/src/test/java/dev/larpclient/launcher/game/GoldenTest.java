package dev.larpclient.launcher.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.larpclient.launcher.auth.Account;
import dev.larpclient.launcher.config.LauncherSettings;
import dev.larpclient.launcher.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden files shared with the Rust launcher: both implementations must produce exactly these outputs
 * for the recorded Minecraft 1.8.9 + Forge metadata in {@code testdata/launcher/}.
 * Regenerate with {@code ./gradlew :launcher:test -PupdateGolden} and review the diff.
 */
class GoldenTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();
    private static final Path DATA = Paths.get(System.getProperty("larp.testdata", "../testdata/launcher"));
    private static final boolean UPDATE = Boolean.getBoolean("larp.updateGolden");

    private static JsonObject fixture(String name) throws IOException {
        return Json.parse(Files.readString(DATA.resolve(name)));
    }

    private static void check(String name, String actual) throws IOException {
        Path file = DATA.resolve("golden").resolve(name);
        if (UPDATE) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, actual + "\n");
        }
        assertEquals(Files.readString(file, StandardCharsets.UTF_8).trim(), actual.trim(), "golden file " + name);
    }

    private static List<Library> libraries(String os, String osVersion) throws IOException {
        return Installer.resolveLibraries(fixture("1.8.9.json"),
                Json.obj(fixture("forge-install_profile.json"), "versionInfo"), os, osVersion, "64");
    }

    private static String toJson(List<Library> libraries) {
        JsonArray out = new JsonArray();
        for (Library l : libraries) {
            JsonObject o = new JsonObject();
            o.addProperty("key", l.key);
            o.addProperty("path", l.path);
            o.addProperty("url", l.url);
            JsonArray checksums = new JsonArray();
            for (String c : l.checksums) {
                checksums.add(c);
            }
            o.add("checksums", checksums);
            o.addProperty("natives", l.natives);
            JsonArray mirrors = new JsonArray();
            for (String m : l.mirrors) {
                mirrors.add(m);
            }
            o.add("mirrors", mirrors);
            out.add(o);
        }
        return GSON.toJson(out);
    }

    @Test
    void librariesLinux() throws IOException {
        check("libraries-linux.json", toJson(libraries("linux", "6.12.0")));
    }

    @Test
    void librariesWindows() throws IOException {
        check("libraries-windows.json", toJson(libraries("windows", "10.0")));
    }

    @Test
    void librariesOsx() throws IOException {
        check("libraries-osx.json", toJson(libraries("osx", "10.15.7")));
    }

    @Test
    void launchCommandLinux(@TempDir Path tmp) throws IOException {
        Path root = Paths.get("/data/larpclient");
        GameFiles files = new GameFiles(root);
        JsonObject vanilla = fixture("1.8.9.json");
        JsonObject profile = fixture("forge-install_profile.json");
        JsonObject forgeInfo = Json.obj(profile, "versionInfo");
        List<Path> classpath = new ArrayList<>();
        for (Library l : libraries("linux", "6.12.0")) {
            if (!l.natives) {
                classpath.add(files.library(l.path));
            }
        }
        classpath.add(files.versionDir("1.8.9").resolve("1.8.9.jar"));
        JsonObject logging = Json.obj(Json.obj(vanilla, "logging"), "client");
        InstalledGame game = new InstalledGame(Json.str(Json.obj(profile, "install"), "target"),
                Json.str(forgeInfo, "mainClass"), Json.str(forgeInfo, "minecraftArguments"), "1.8", classpath,
                files.nativesDir("1.8.9"), files.assets.resolve("log_configs").resolve("client-1.7.xml"),
                Json.str(logging, "argument"));

        LauncherSettings settings = LauncherSettings.load(tmp.resolve("launcher.json")); // defaults
        settings.fullscreen = true;
        settings.jvmArgs = settings.jvmArgs + " -Dlarp.test=\"a b\"";
        Account account = Account.offline("Steve");

        List<String> cmd = new GameLauncher(files).buildCommand(game, account, settings, Paths.get("/usr/bin/java"),
                game.classpath, ":");
        check("command-linux.json", GSON.toJson(cmd));
    }
}
