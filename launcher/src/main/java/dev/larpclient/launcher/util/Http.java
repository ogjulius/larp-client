package dev.larpclient.launcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

/** HTTP utilities: JSON requests and verified, atomic, retried file downloads. */
public final class Http {
    public static final String USER_AGENT = "LARPClient-Launcher/1.0";
    private static final int RETRIES = 3;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private Http() {
    }

    public static String get(String url) throws IOException {
        return send(HttpRequest.newBuilder(URI.create(url)).GET(), url);
    }

    public static String get(String url, String bearer) throws IOException {
        return send(HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + bearer).GET(), url);
    }

    public static String postJson(String url, String json) throws IOException {
        return send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)), url);
    }

    public static String postForm(String url, Map<String, String> form) throws IOException {
        String body = form.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        return send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)), url);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String send(HttpRequest.Builder builder, String url) throws IOException {
        HttpRequest request = builder.header("User-Agent", USER_AGENT).timeout(Duration.ofSeconds(30)).build();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new HttpStatusException(response.statusCode(), url, response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    /**
     * Download {@code url} to {@code target} unless it already exists with the expected SHA-1.
     *
     * @param sha1 expected hex SHA-1, or {@code null} to skip verification (existing files are kept)
     * @return true if a download happened
     */
    public static boolean download(String url, Path target, String sha1) throws IOException {
        if (Files.isRegularFile(target) && (sha1 == null || sha1.equalsIgnoreCase(sha1(target)))) {
            return false;
        }
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        IOException last = null;
        for (int attempt = 1; attempt <= RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", USER_AGENT).timeout(Duration.ofMinutes(5)).GET().build();
                HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    response.body().close();
                    throw new HttpStatusException(response.statusCode(), url, "");
                }
                try (InputStream in = response.body()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                if (sha1 != null && !sha1.equalsIgnoreCase(sha1(tmp))) {
                    throw new IOException("Checksum mismatch for " + url);
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return true;
            } catch (HttpStatusException e) {
                if (e.status == 404) {
                    throw e; // pointless to retry
                }
                last = e;
            } catch (IOException e) {
                last = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }
        }
        Files.deleteIfExists(tmp);
        throw last;
    }

    public static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Non-2xx response. */
    public static final class HttpStatusException extends IOException {
        public final int status;
        public final String body;

        HttpStatusException(int status, String url, String body) {
            super("HTTP " + status + " for " + url);
            this.status = status;
            this.body = body;
        }
    }
}
