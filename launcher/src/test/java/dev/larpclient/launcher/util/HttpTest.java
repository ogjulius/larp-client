package dev.larpclient.launcher.util;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpTest {
    private static final byte[] BODY = "hello minecraft".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    /** Status codes to answer with, in order; when empty, 200 with {@link #BODY}. */
    private final ConcurrentLinkedQueue<Integer> script = new ConcurrentLinkedQueue<>();
    private final List<Long> sleeps = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            Integer status = script.poll();
            if (status == null || status == 200) {
                exchange.sendResponseHeaders(200, BODY.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(BODY);
                }
            } else {
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/file.jar";
    }

    private static String sha1(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
    }

    private boolean download(Path target, String sha1) throws IOException {
        return Http.download(url(), target, sha1, sleeps::add);
    }

    @Test
    void downloadsVerifiesAndSkipsWhenPresent(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("a").resolve("file.jar");
        assertTrue(download(target, sha1(BODY)));
        assertEquals("hello minecraft", Files.readString(target));
        assertFalse(download(target, sha1(BODY)));
        assertEquals(1, requests.get());
    }

    @Test
    void replacesExistingFileWithWrongHash(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("file.jar");
        Files.writeString(target, "tampered");
        assertTrue(download(target, sha1(BODY)));
        assertEquals("hello minecraft", Files.readString(target));
    }

    @Test
    void checksumMismatchNeverReplacesTargetOrLeavesPartFile(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("file.jar");
        Files.writeString(target, "old");
        assertThrows(IOException.class, () -> download(target, sha1("other".getBytes(StandardCharsets.UTF_8))));
        assertEquals("old", Files.readString(target));
        assertFalse(Files.exists(dir.resolve("file.jar.part")));
    }

    @Test
    void notFoundIsNotRetried(@TempDir Path dir) {
        script.add(404);
        Http.HttpStatusException e = assertThrows(Http.HttpStatusException.class, () -> download(dir.resolve("f"), null));
        assertEquals(404, e.status);
        assertEquals(1, requests.get());
    }

    @Test
    void clientErrorsAreNotRetried(@TempDir Path dir) {
        script.add(403);
        assertThrows(Http.HttpStatusException.class, () -> download(dir.resolve("f"), null));
        assertEquals(1, requests.get());
        assertTrue(sleeps.isEmpty());
    }

    /** Regression (audit A10): retries back off instead of hammering a struggling server instantly. */
    @Test
    void serverErrorsAreRetriedWithGrowingBackoff(@TempDir Path dir) throws Exception {
        script.add(503);
        script.add(500);
        assertTrue(download(dir.resolve("f"), sha1(BODY)));
        assertEquals(3, requests.get());
        assertEquals(2, sleeps.size(), sleeps.toString());
        assertTrue(sleeps.get(0) >= 500 && sleeps.get(0) < 1000, sleeps.toString());
        assertTrue(sleeps.get(1) >= 1000 && sleeps.get(1) < 2000, sleeps.toString());
    }

    @Test
    void givesUpAfterThreeAttempts(@TempDir Path dir) {
        script.add(503);
        script.add(503);
        script.add(503);
        Http.HttpStatusException e = assertThrows(Http.HttpStatusException.class, () -> download(dir.resolve("f"), null));
        assertEquals(503, e.status);
        assertEquals(3, requests.get());
        assertFalse(Files.exists(dir.resolve("f.part")));
    }
}
