package dev.larpclient.launcher.game;

import java.nio.file.Path;

/** Directory layout of a LARP installation. */
public final class GameFiles {
    public final Path root;
    public final Path libraries;
    public final Path assets;
    public final Path versions;
    public final Path runtimes;
    public final Path instance;

    public GameFiles(Path root) {
        this.root = root;
        this.libraries = root.resolve("libraries");
        this.assets = root.resolve("assets");
        this.versions = root.resolve("versions");
        this.runtimes = root.resolve("runtime");
        this.instance = root.resolve("instance");
    }

    public Path versionDir(String id) {
        return versions.resolve(id);
    }

    public Path nativesDir(String id) {
        return versionDir(id).resolve("natives");
    }

    public Path library(String relativePath) {
        return libraries.resolve(relativePath);
    }
}
