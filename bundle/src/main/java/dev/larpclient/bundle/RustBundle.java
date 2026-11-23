package dev.larpclient.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Build tool for the launcher ({@code ./gradlew :bundle:rustBundle}): downloads the pinned LWJGL jar,
 * applies {@link LwjglMousePatch} to its {@code LinuxMouse} class and writes the result for the launcher
 * to embed.
 * <p>
 * Usage: {@code RustBundle <lwjgl url> <lwjgl sha1> <download cache jar> <out dir>}
 */
public final class RustBundle {
    private RustBundle() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: RustBundle <lwjgl url> <lwjgl sha1> <cache jar> <out dir>");
        }
        String url = args[0];
        String sha1 = args[1];
        Path jar = Paths.get(args[2]);
        Path out = Paths.get(args[3]);
        download(url, jar, sha1);
        byte[] original;
        try (JarFile in = new JarFile(jar.toFile())) {
            JarEntry entry = in.getJarEntry(LwjglMousePatch.TARGET + ".class");
            if (entry == null) {
                throw new IOException(LwjglMousePatch.TARGET + " not found in " + jar);
            }
            try (InputStream is = in.getInputStream(entry)) {
                original = is.readAllBytes();
            }
        }
        Files.createDirectories(out);
        Files.write(out.resolve("LinuxMouse.class"), LwjglMousePatch.patch(original));
        Files.writeString(out.resolve("lwjgl.sha1"), sha1 + "\n", StandardCharsets.UTF_8);
    }

    /** Download {@code url} to {@code target} unless it is already there with the expected SHA-1. */
    static void download(String url, Path target, String sha1) throws IOException, InterruptedException {
        if (Files.isRegularFile(target) && sha1(target).equals(sha1)) {
            return;
        }
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        HttpResponse<Path> response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
                .send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofFile(part));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(part);
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        String actual = sha1(part);
        if (!actual.equals(sha1)) {
            Files.deleteIfExists(part);
            throw new IOException("SHA-1 mismatch for " + url + ": expected " + sha1 + ", got " + actual);
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static String sha1(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
