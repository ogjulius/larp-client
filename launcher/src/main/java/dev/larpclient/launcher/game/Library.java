package dev.larpclient.launcher.game;

import java.util.List;

/** A resolved library download. */
public final class Library {
    public final String key;
    public final String path;
    public final String url;
    /** Accepted SHA-1 hashes (lower-case hex); the download must match one of them. Empty only when {@link #url} is null. */
    public final List<String> checksums;
    public final boolean natives;
    /** Alternative base URLs tried when {@link #url} is missing (404). */
    public final String[] mirrors;

    public Library(String key, String path, String url, List<String> checksums, boolean natives, String... mirrors) {
        this.key = key;
        this.path = path;
        this.url = url;
        this.checksums = List.copyOf(checksums);
        this.natives = natives;
        this.mirrors = mirrors;
    }

    @Override
    public String toString() {
        return key;
    }
}
