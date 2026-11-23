package dev.larpclient.launcher.game;

import dev.larpclient.launcher.auth.Account;
import dev.larpclient.launcher.config.LauncherSettings;
import dev.larpclient.launcher.util.Http;
import dev.larpclient.launcher.util.Platform;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Prepares the game instance directory and starts the Minecraft process. */
public final class GameLauncher {
    public static final String MOD_RESOURCE = "/larp/larpclient.jar.bin";

    private final GameFiles files;

    public GameLauncher(GameFiles files) {
        this.files = files;
    }

    /** Install the bundled LARP mod and first-run defaults into the instance directory. */
    public void prepareInstance() throws IOException {
        Path mods = files.instance.resolve("mods");
        Files.createDirectories(mods);
        Path target = mods.resolve("larpclient.jar");
        try (InputStream in = GameLauncher.class.getResourceAsStream(MOD_RESOURCE)) {
            if (in == null) {
                throw new IOException("Launcher build is missing the bundled LARP mod");
            }
            Path tmp = mods.resolve("larpclient.jar.part");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            if (Files.isRegularFile(target) && Http.sha1(tmp).equals(Http.sha1(target))) {
                Files.delete(tmp);
            } else {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        // Remove stale copies with other names (e.g. manually installed versions).
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, "larpclient*.jar")) {
            for (Path p : stream) {
                if (!p.getFileName().toString().equals("larpclient.jar")) {
                    Files.deleteIfExists(p);
                }
            }
        }
        writeDefaultOptions();
        // Themed Forge loading screen.
        Path splash = files.instance.resolve("config").resolve("splash.properties");
        if (!Files.exists(splash)) {
            Files.createDirectories(splash.getParent());
            Files.writeString(splash, String.join("\n",
                    "enabled=true", "rotate=false", "showMemory=true",
                    "background=0x141419", "font=0xEDEDF2", "barBorder=0x2C2C38", "barColor=0x8B5CF6",
                    "barBackground=0x1C1C24", "memoryGoodColor=0x8B5CF6", "memoryWarnColor=0xE6E84A",
                    "memoryLowColor=0xE5484D", "") );
        }
    }

    /**
     * First run only: performance-oriented video settings (VBOs, no VSync, fast graphics, no clouds,
     * fewer particles) and no tutorial/snooper noise. Never touches an existing options.txt.
     */
    private void writeDefaultOptions() throws IOException {
        Path options = files.instance.resolve("options.txt");
        if (Files.exists(options)) {
            return;
        }
        Files.writeString(options, String.join("\n",
                "useVbo:true", "fboEnable:true", "enableVsync:false", "maxFps:260",
                "fancyGraphics:false", "ao:0", "renderClouds:false", "particles:1",
                "entityShadows:false", "mipmapLevels:0", "renderDistance:8", "useNativeTransport:true",
                "snooperEnabled:false", "showInventoryAchievementHint:false", "pauseOnLostFocus:false",
                "guiScale:0", "") );
    }

    /** The game classpath, with LWJGL swapped for the XWayland mouse fix on Linux (see {@link LwjglMousePatch}). */
    private List<Path> classpath(InstalledGame game) {
        Path lwjgl = Platform.OS == Platform.Os.LINUX ? LwjglMousePatch.findLwjgl(game.classpath) : null;
        if (lwjgl == null) {
            return game.classpath;
        }
        try {
            Path patched = LwjglMousePatch.prepare(lwjgl, files.root.resolve("patches"));
            List<Path> cp = new ArrayList<>(game.classpath);
            cp.set(cp.indexOf(lwjgl), patched);
            return cp;
        } catch (IOException | RuntimeException e) {
            System.err.println("[larp] LWJGL mouse patch unavailable, mouse may lag under XWayland: " + e);
            return game.classpath;
        }
    }

    public List<String> buildCommand(InstalledGame game, Account account, LauncherSettings settings, Path java) {
        return buildCommand(game, account, settings, java, classpath(game), Platform.classpathSeparator());
    }

    /** Pure command construction. Covered by golden files shared with the Rust launcher. */
    List<String> buildCommand(InstalledGame game, Account account, LauncherSettings settings, Path java,
                              List<Path> classpath, String separator) {
        List<String> cmd = new ArrayList<>();
        cmd.add(java.toString());
        cmd.add("-Xmx" + settings.memoryMb + "M");
        // Fixed heap size: no resize pauses while playing.
        cmd.add("-Xms" + settings.memoryMb + "M");
        cmd.addAll(JvmArgs.split(settings.jvmArgs));
        cmd.add("-Djava.library.path=" + game.nativesDir);
        cmd.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
        cmd.add("-Dfml.ignorePatchDiscrepancies=true");
        cmd.add("-Dminecraft.launcher.brand=larpclient");
        if (settings.fullscreen) {
            cmd.add("-Dlarp.startFullscreen=true");
        }
        if (game.loggingArgument != null && game.loggingConfig != null) {
            // Mojang's patched log4j config (mitigates Log4Shell on 1.8.9's log4j 2.0-beta9).
            cmd.add(game.loggingArgument.replace("${path}", game.loggingConfig.toString()));
        }
        cmd.add("-cp");
        cmd.add(classpath.stream().map(Path::toString).collect(Collectors.joining(separator)));
        cmd.add(game.mainClass);

        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", account.username);
        vars.put("version_name", game.versionId);
        vars.put("game_directory", files.instance.toString());
        vars.put("assets_root", files.assets.toString());
        vars.put("assets_index_name", game.assetIndex);
        vars.put("auth_uuid", account.uuid);
        vars.put("auth_access_token", account.accessToken);
        vars.put("user_properties", "{}");
        vars.put("user_type", account.userType());
        for (String arg : game.minecraftArguments.split(" ")) {
            cmd.add(substitute(arg, vars));
        }
        // Fullscreen is requested from the compositor by the mod (works on Wayland/Hyprland);
        // LWJGL's own --fullscreen path breaks under XWayland.
        cmd.add("--width");
        cmd.add(String.valueOf(settings.width));
        cmd.add("--height");
        cmd.add(String.valueOf(settings.height));
        return cmd;
    }

    static String substitute(String arg, Map<String, String> vars) {
        if (arg.startsWith("${") && arg.endsWith("}")) {
            String value = vars.get(arg.substring(2, arg.length() - 1));
            return value == null ? arg : value;
        }
        return arg;
    }

    /** Start the game; each output line is passed to {@code log} on a background thread. */
    public Process start(List<String> command, Consumer<String> log) throws IOException {
        return start(command, log, true);
    }

    public Process start(List<String> command, Consumer<String> log, boolean threadedGl) throws IOException {
        Process process = processBuilder(command, threadedGl).redirectErrorStream(true).start();
        Thread pump = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                Log4jXmlFormatter formatter = new Log4jXmlFormatter(log);
                String line;
                while ((line = reader.readLine()) != null) {
                    formatter.accept(line);
                }
            } catch (IOException ignored) {
                // process ended
            }
        }, "larp-game-log");
        pump.setDaemon(true);
        pump.start();
        return process;
    }

    /**
     * Start the game independent of the launcher: output goes to {@code logFile} instead of a pipe,
     * so the launcher can exit while Minecraft keeps running.
     */
    public Process startDetached(List<String> command, boolean threadedGl, Path logFile) throws IOException {
        Files.createDirectories(logFile.getParent());
        return processBuilder(command, threadedGl)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(new java.io.File(Platform.OS == Platform.Os.WINDOWS ? "NUL" : "/dev/null")))
                .start();
    }

    private ProcessBuilder processBuilder(List<String> command, boolean threadedGl) throws IOException {
        Files.createDirectories(files.instance);
        ProcessBuilder builder = new ProcessBuilder(command).directory(files.instance.toFile());
        if (Platform.OS == Platform.Os.LINUX) {
            java.util.Map<String, String> env = builder.environment();
            if (threadedGl) {
                env.putIfAbsent("mesa_glthread", "true");             // Mesa (AMD / Intel)
                env.putIfAbsent("__GL_THREADED_OPTIMIZATIONS", "1"); // NVIDIA proprietary
            }
            env.putIfAbsent("__GL_SYNC_TO_VBLANK", "0");             // let the in-game VSync option decide
            env.putIfAbsent("vblank_mode", "0");
        }
        return builder;
    }

    /** Redact the access token before showing a command line to the user. */
    public static String redact(List<String> command, Account account) {
        String joined = String.join(" ", command);
        if (account.accessToken != null && account.accessToken.length() > 4) {
            joined = joined.replace(account.accessToken, "<token>");
        }
        return joined;
    }
}
