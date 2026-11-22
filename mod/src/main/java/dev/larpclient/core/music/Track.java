package dev.larpclient.core.music;

import java.net.URI;

/** A playable audio source: a direct MP3/WAV file URL or an MP3 internet radio stream. */
public final class Track {
    private final String url;
    private final String title;

    public Track(String url, String title) {
        this.url = url.trim();
        this.title = title == null || title.trim().isEmpty() ? titleFromUrl(this.url) : title.trim();
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    /** Derive a readable title from the last path segment of a URL. */
    public static String titleFromUrl(String url) {
        try {
            String path = URI.create(url.trim()).getPath();
            if (path != null) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                int dot = name.lastIndexOf('.');
                if (dot > 0) {
                    name = name.substring(0, dot);
                }
                name = java.net.URLDecoder.decode(name, "UTF-8").replace('_', ' ').trim();
                if (!name.isEmpty()) {
                    return name;
                }
            }
            String host = URI.create(url.trim()).getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }

    /** Only http(s) URLs are accepted. */
    public static boolean isValidUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (Exception e) {
            return false;
        }
    }
}
