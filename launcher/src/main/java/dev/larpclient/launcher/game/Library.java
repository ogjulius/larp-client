package dev.larpclient.launcher.game;

/** A resolved library download. */
public final class Library {
    public final String key;
    public final String path;
    public final String url;
    public final String sha1;
    public final boolean natives;
    /** Alternative base URLs tried when {@link #url} is missing (404). */
    public final String[] mirrors;

    public Library(String key, String path, String url, String sha1, boolean natives, String... mirrors) {
        this.key = key;
        this.path = path;
        this.url = url;
        this.sha1 = sha1;
        this.natives = natives;
        this.mirrors = mirrors;
    }

    @Override
    public String toString() {
        return key;
    }
}
