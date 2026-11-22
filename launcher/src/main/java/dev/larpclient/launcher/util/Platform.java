package dev.larpclient.launcher.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/** OS detection and per-OS data/config directories (XDG on Linux). */
public final class Platform {
    public enum Os {
        LINUX("linux"), WINDOWS("windows"), OSX("osx");

        public final String mojangName;

        Os(String mojangName) {
            this.mojangName = mojangName;
        }
    }

    public static final Os OS = detect();

    private Platform() {
    }

    private static Os detect() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return Os.WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return Os.OSX;
        }
        return Os.LINUX;
    }

    private static Path home() {
        return Paths.get(System.getProperty("user.home"));
    }

    private static Path xdg(String variable, String fallback) {
        String value = System.getenv(variable);
        return value != null && !value.isEmpty() && Paths.get(value).isAbsolute() ? Paths.get(value) : home().resolve(fallback);
    }

    /** Where game files, libraries, assets and runtimes live. */
    public static Path dataDir() {
        switch (OS) {
            case WINDOWS:
                return Paths.get(System.getenv().getOrDefault("APPDATA", home().toString())).resolve("larpclient");
            case OSX:
                return home().resolve("Library/Application Support/larpclient");
            default:
                return xdg("XDG_DATA_HOME", ".local/share").resolve("larpclient");
        }
    }

    /** Where launcher settings and accounts live. */
    public static Path configDir() {
        return OS == Os.LINUX ? xdg("XDG_CONFIG_HOME", ".config").resolve("larpclient") : dataDir().resolve("config");
    }

    public static String javaExecutableName() {
        return OS == Os.WINDOWS ? "javaw.exe" : "java";
    }

    public static String classpathSeparator() {
        return OS == Os.WINDOWS ? ";" : ":";
    }

    /** Mojang's name for this machine in the Java runtime manifest. */
    public static String runtimePlatform() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean x64 = arch.contains("64");
        switch (OS) {
            case WINDOWS:
                return x64 ? "windows-x64" : "windows-x86";
            case OSX:
                return "mac-os";
            default:
                return x64 ? "linux" : "linux-i386";
        }
    }

    /** Open a URL or folder with the desktop's default handler (xdg-open on Linux). */
    public static void open(String target) {
        try {
            if (OS == Os.LINUX) {
                new ProcessBuilder("xdg-open", target).start();
            } else if (java.awt.Desktop.isDesktopSupported()) {
                if (target.startsWith("http")) {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(target));
                } else {
                    java.awt.Desktop.getDesktop().open(new java.io.File(target));
                }
            }
        } catch (Exception ignored) {
            // nothing sensible to do; the UI shows the target text anyway
        }
    }
}
