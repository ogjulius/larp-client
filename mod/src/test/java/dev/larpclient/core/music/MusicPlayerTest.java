package dev.larpclient.core.music;

import dev.larpclient.core.music.decode.AudioDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MusicPlayerTest {
    private static final AudioFormat FORMAT = new AudioFormat(44100, 16, 2, true, false);

    /** URLs containing "dead" fail to open, "short" ones yield one block, others {@code blocks} blocks of silence. */
    private static final class FakeBackend implements MusicPlayer.Backend {
        final List<String> opened = new CopyOnWriteArrayList<>();
        final int blocks;

        FakeBackend(int blocks) {
            this.blocks = blocks;
        }

        @Override
        public AudioDecoder open(String url) throws IOException {
            opened.add(url);
            if (url.contains("dead")) {
                throw new IOException("HTTP 404");
            }
            return new AudioDecoder() {
                int left = url.contains("short") ? 1 : blocks;

                @Override
                public AudioFormat getFormat() {
                    return FORMAT;
                }

                @Override
                public short[] read() {
                    return left-- > 0 ? new short[64] : null;
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public MusicPlayer.Output output(AudioFormat format) {
            return new MusicPlayer.Output() {
                @Override
                public void start() {
                }

                @Override
                public void stop() {
                }

                @Override
                public void write(byte[] data, int length) {
                    Thread.yield();
                }

                @Override
                public void drain() {
                }

                @Override
                public void close() {
                }
            };
        }
    }

    /** Runs submitted save tasks only when asked, to observe what happens on the caller's thread. */
    private static final class ManualExecutor implements Executor {
        final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public synchronized void execute(Runnable task) {
            tasks.add(task);
        }

        synchronized void runAll() {
            Runnable r;
            while ((r = tasks.poll()) != null) {
                r.run();
            }
        }
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("Timed out waiting for: " + what);
            }
            Thread.sleep(5);
        }
    }

    private static MusicPlayer player(Path dir, FakeBackend backend, String... urls) {
        MusicPlayer p = new MusicPlayer(dir.resolve("music.json"), backend, Runnable::run);
        for (String url : urls) {
            assertTrue(p.add(url, null));
        }
        return p;
    }

    /**
     * Regression (audit A6): if the user picks a track at the moment the previous one ends, the finished
     * worker must not advance past the user's choice.
     */
    @Test
    void userChoiceWinsOverEndOfTrackAdvance(@TempDir Path dir) throws Exception {
        FakeBackend backend = new FakeBackend(1_000_000);
        MusicPlayer p = player(dir, backend, "https://e.com/0-short.mp3", "https://e.com/1.mp3",
                "https://e.com/2.mp3", "https://e.com/3.mp3");
        boolean[] fired = {false};
        p.beforeAutoAdvance = () -> {
            if (!fired[0]) {
                fired[0] = true;
                p.play(2); // the user clicks track 2 just as track 0 ends
            }
        };
        p.play(0);
        await("track 2 playing", () -> p.getState() == MusicPlayer.State.PLAYING && p.getCurrentIndex() == 2);
        Thread.sleep(150);
        assertEquals(2, p.getCurrentIndex());
        assertFalse(backend.opened.contains("https://e.com/3.mp3"), backend.opened.toString());
        p.stop();
    }

    /** Regression (audit A8): a dead URL reached by auto-advance is skipped instead of ending playback. */
    @Test
    void autoplaySkipsDeadTrack(@TempDir Path dir) throws Exception {
        FakeBackend backend = new FakeBackend(1_000_000);
        MusicPlayer p = player(dir, backend, "https://e.com/dead.mp3", "https://e.com/good.mp3");
        p.setAutoplay(true);
        p.startup();
        await("good track playing", () -> p.getState() == MusicPlayer.State.PLAYING);
        assertEquals(1, p.getCurrentIndex());
        p.stop();
    }

    @Test
    void allDeadTracksEndInErrorWithoutLooping(@TempDir Path dir) throws Exception {
        FakeBackend backend = new FakeBackend(1);
        MusicPlayer p = player(dir, backend, "https://e.com/dead1.mp3", "https://e.com/dead2.mp3", "https://e.com/dead3.mp3");
        p.setAutoplay(true);
        p.startup();
        await("error state", () -> p.getState() == MusicPlayer.State.ERROR);
        Thread.sleep(100);
        assertEquals(3, backend.opened.size(), backend.opened.toString());
        assertNotNull(p.getError());
    }

    @Test
    void userPickedDeadTrackShowsErrorAndStays(@TempDir Path dir) throws Exception {
        FakeBackend backend = new FakeBackend(1);
        MusicPlayer p = player(dir, backend, "https://e.com/good.mp3", "https://e.com/dead.mp3");
        p.play(1);
        await("error state", () -> p.getState() == MusicPlayer.State.ERROR);
        assertEquals(1, p.getCurrentIndex());
        assertEquals(Collections.singletonList("https://e.com/dead.mp3"), backend.opened);
    }

    /** Regression (audit A7): playlist changes must not write to disk on the caller's (render) thread. */
    @Test
    void playlistChangesAreWrittenInBackground(@TempDir Path dir) {
        ManualExecutor saves = new ManualExecutor();
        Path file = dir.resolve("music.json");
        MusicPlayer p = new MusicPlayer(file, new FakeBackend(1), saves);
        assertTrue(p.add("https://e.com/a.mp3", "A"));
        assertFalse(Files.exists(file));
        saves.runAll();
        MusicPlayer loaded = new MusicPlayer(file);
        loaded.load();
        assertEquals("A", loaded.getTracks().get(0).getTitle());
    }

    /** Regression (audit A15): an unreadable playlist is kept aside, not overwritten by the next save. */
    @Test
    void corruptPlaylistIsQuarantined(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("music.json");
        String corrupt = "{\"tracks\":[{\"url\":\"https://e.com/a.mp3\"";
        Files.write(file, corrupt.getBytes(StandardCharsets.UTF_8));
        MusicPlayer p = new MusicPlayer(file);
        p.load();
        assertTrue(p.getTracks().isEmpty());
        p.add("https://e.com/b.mp3", null);
        p.save();
        assertEquals(corrupt, new String(Files.readAllBytes(dir.resolve("music.json.broken")), StandardCharsets.UTF_8));
    }

    @Test
    void saveIsAtomicAndLeavesNoTempFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("music.json");
        MusicPlayer p = new MusicPlayer(file);
        p.add("https://e.com/b.mp3", null);
        p.save();
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(dir.resolve("music.json.tmp")));
    }
}
