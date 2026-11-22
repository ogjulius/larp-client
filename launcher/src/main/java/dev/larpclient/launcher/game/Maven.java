package dev.larpclient.launcher.game;

/** Maven coordinate helpers. */
public final class Maven {
    private Maven() {
    }

    /** {@code group:artifact:version[:classifier]} → relative repository path. */
    public static String path(String coordinate) {
        String[] parts = coordinate.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Bad maven coordinate: " + coordinate);
        }
        String extension = "jar";
        String version = parts[2];
        int at = version.indexOf('@');
        if (at >= 0) {
            extension = version.substring(at + 1);
            version = version.substring(0, at);
        }
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return parts[0].replace('.', '/') + "/" + parts[1] + "/" + version + "/"
                + parts[1] + "-" + version + classifier + "." + extension;
    }

    /** {@code group:artifact} — used to de-duplicate libraries across version JSONs. */
    public static String key(String coordinate) {
        String[] parts = coordinate.split(":");
        return parts[0] + ":" + parts[1] + (parts.length > 3 ? ":" + parts[3] : "");
    }
}
