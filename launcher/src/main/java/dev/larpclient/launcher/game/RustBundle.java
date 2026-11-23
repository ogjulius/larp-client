package dev.larpclient.launcher.game;

import dev.larpclient.launcher.util.Http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Build tool for the Rust launcher ({@code ./gradlew :launcher:rustBundle}): downloads the pinned
 * LWJGL jar, applies {@link LwjglMousePatch} to its {@code LinuxMouse} class and writes the result,
 * so the Rust launcher can splice in exactly the bytes the Java launcher would produce.
 * <p>
 * Usage: {@code RustBundle <lwjgl url> <lwjgl sha1> <download cache jar> <out dir>}
 */
public final class RustBundle {
    private RustBundle() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: RustBundle <lwjgl url> <lwjgl sha1> <cache jar> <out dir>");
        }
        String url = args[0];
        String sha1 = args[1];
        Path jar = Paths.get(args[2]);
        Path out = Paths.get(args[3]);
        Http.download(url, jar, sha1);
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
}
