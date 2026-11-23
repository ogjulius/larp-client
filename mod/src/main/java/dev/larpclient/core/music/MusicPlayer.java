package dev.larpclient.core.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.larpclient.core.music.decode.AudioDecoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Streams music from URLs on a background thread. Supports MP3 files, MP3 radio streams and WAV.
 * The playlist, volume and shuffle state persist to {@code music.json}.
 * <p>
 * Thread model: all public methods may be called from the render thread; decoding and audio
 * output run on a single daemon worker thread per playing track. Playlist changes are saved on a
 * background thread ({@link #save()} itself is synchronous, for explicit saves and shutdown).
 * <p>
 * End of track: the finished worker advances only if it is still the current worker, checked under
 * the player lock, so a track the user picks at that moment always wins. A track that fails to open
 * after an automatic advance is skipped; once every track has failed in a row, playback stops with
 * an error instead of looping.
 */
public final class MusicPlayer {
    public enum State { STOPPED, LOADING, PLAYING, PAUSED, ERROR }

    private static final Logger LOGGER = LogManager.getLogger("LARP Client/Music");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Where audio comes from and goes to; replaced in tests. */
    interface Backend {
        AudioDecoder open(String url) throws IOException;

        Output output(AudioFormat format) throws LineUnavailableException;
    }

    /** An audio sink accepting 16-bit PCM in the decoder's format. */
    interface Output {
        void start();

        void stop();

        void write(byte[] data, int length);

        void drain();

        void close();
    }

    private static final Backend JAVA_SOUND = new Backend() {
        @Override
        public AudioDecoder open(String url) throws IOException {
            return AudioDecoder.open(url);
        }

        @Override
        public Output output(AudioFormat format) throws LineUnavailableException {
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format, (int) format.getSampleRate() * format.getFrameSize() / 4);
            return new Output() {
                @Override
                public void start() {
                    line.start();
                }

                @Override
                public void stop() {
                    line.stop();
                }

                @Override
                public void write(byte[] data, int length) {
                    line.write(data, 0, length);
                }

                @Override
                public void drain() {
                    line.drain();
                }

                @Override
                public void close() {
                    line.close();
                }
            };
        }
    };

    private final Path file;
    private final Backend backend;
    private final Executor saveExecutor;
    private final Object fileLock = new Object();
    private final List<Track> tracks = new ArrayList<>();
    private final Object pauseLock = new Object();
    /** Tracks that failed to open in a row during automatic advance; guarded by {@code this}. */
    private int autoFailures;
    private long saveSequence;
    private long writtenSequence;
    /** Test hook: runs on the worker thread just before it tries to advance to the next track. */
    volatile Runnable beforeAutoAdvance = () -> { };

    private volatile State state = State.STOPPED;
    private volatile float volume = 0.5f;
    private volatile boolean shuffle;
    private volatile boolean autoplay;
    private volatile int current = -1;
    private volatile String error;
    private volatile Worker worker;

    public MusicPlayer(Path file) {
        this(file, JAVA_SOUND, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LARP Music save");
            t.setDaemon(true);
            return t;
        }));
    }

    MusicPlayer(Path file, Backend backend, Executor saveExecutor) {
        this.file = file;
        this.backend = backend;
        this.saveExecutor = saveExecutor;
    }

    // ------------------------------------------------------------------ playlist

    public synchronized List<Track> getTracks() {
        return Collections.unmodifiableList(new ArrayList<>(tracks));
    }

    public synchronized boolean add(String url, String title) {
        if (!Track.isValidUrl(url)) {
            return false;
        }
        tracks.add(new Track(url, title));
        saveAsync();
        return true;
    }

    public synchronized void remove(int index) {
        if (index < 0 || index >= tracks.size()) {
            return;
        }
        if (index == current) {
            stop();
            current = -1;
        } else if (index < current) {
            current--;
        }
        tracks.remove(index);
        saveAsync();
    }

    public synchronized Track getCurrentTrack() {
        int i = current;
        return i >= 0 && i < tracks.size() ? tracks.get(i) : null;
    }

    public int getCurrentIndex() {
        return current;
    }

    // ------------------------------------------------------------------ transport

    public synchronized void play(int index) {
        play(index, false);
    }

    private void play(int index, boolean automatic) {
        if (index < 0 || index >= tracks.size()) {
            return;
        }
        if (!automatic) {
            autoFailures = 0;
        }
        stopWorker();
        current = index;
        error = null;
        Worker w = new Worker(tracks.get(index), automatic);
        worker = w;
        state = State.LOADING;
        w.start();
        saveAsync();
    }

    /** Play/pause toggle; starts the playlist if nothing is loaded. */
    public synchronized void togglePause() {
        switch (state) {
            case PLAYING:
                state = State.PAUSED;
                break;
            case PAUSED:
                state = State.PLAYING;
                synchronized (pauseLock) {
                    pauseLock.notifyAll();
                }
                break;
            default:
                if (!tracks.isEmpty()) {
                    play(current >= 0 && current < tracks.size() ? current : 0);
                }
        }
    }

    public synchronized void next() {
        autoFailures = 0;
        advance();
    }

    /** Move to the next (or a random) track as part of automatic playback. Caller holds the lock. */
    private void advance() {
        if (tracks.isEmpty()) {
            return;
        }
        if (shuffle && tracks.size() > 1) {
            int n;
            do {
                n = ThreadLocalRandom.current().nextInt(tracks.size());
            } while (n == current);
            play(n, true);
        } else {
            play((current + 1) % tracks.size(), true);
        }
    }

    public synchronized void previous() {
        if (!tracks.isEmpty()) {
            play(Math.floorMod(current - 1, tracks.size()));
        }
    }

    public synchronized void stop() {
        stopWorker();
        state = State.STOPPED;
    }

    private void stopWorker() {
        Worker w = worker;
        worker = null;
        if (w != null) {
            w.cancelled = true;
            synchronized (pauseLock) {
                pauseLock.notifyAll();
            }
            w.interrupt();
        }
    }

    /** Start playback on launch if autoplay is on. */
    public synchronized void startup() {
        if (autoplay && !tracks.isEmpty()) {
            autoFailures = 0;
            if (shuffle) {
                advance();
            } else {
                play(current >= 0 && current < tracks.size() ? current : 0, true);
            }
        }
    }

    // ------------------------------------------------------------------ properties

    public State getState() {
        return state;
    }

    public String getError() {
        return error;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float v) {
        volume = Math.max(0, Math.min(1, v));
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public void setShuffle(boolean shuffle) {
        this.shuffle = shuffle;
    }

    public boolean isAutoplay() {
        return autoplay;
    }

    public void setAutoplay(boolean autoplay) {
        this.autoplay = autoplay;
    }

    // ------------------------------------------------------------------ persistence

    public synchronized void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            tracks.clear();
            if (root.has("tracks")) {
                for (JsonElement e : root.getAsJsonArray("tracks")) {
                    JsonObject t = e.getAsJsonObject();
                    String url = t.has("url") ? t.get("url").getAsString() : "";
                    if (Track.isValidUrl(url)) {
                        tracks.add(new Track(url, t.has("title") ? t.get("title").getAsString() : null));
                    }
                }
            }
            if (root.has("volume")) {
                setVolume(root.get("volume").getAsFloat());
            }
            shuffle = root.has("shuffle") && root.get("shuffle").getAsBoolean();
            autoplay = root.has("autoplay") && root.get("autoplay").getAsBoolean();
            current = root.has("current") ? root.get("current").getAsInt() : -1;
        } catch (Exception e) {
            // Never overwrite a playlist we couldn't read: keep it for the user to recover.
            tracks.clear();
            current = -1;
            LOGGER.error("Failed to read {}, moving it to {}.broken", file, file.getFileName(), e);
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveFailed) {
                LOGGER.warn("Could not move broken playlist aside", moveFailed);
            }
        }
    }

    /** Write the playlist now (blocking). */
    public void save() {
        long seq;
        String json;
        synchronized (this) {
            seq = ++saveSequence;
            json = serialize();
        }
        write(seq, json);
    }

    /** Save without blocking the caller (use from the render thread). */
    public synchronized void saveInBackground() {
        saveAsync();
    }

    /** Snapshot under the lock, write on the save executor: no disk I/O on the caller's thread. */
    private void saveAsync() {
        long seq = ++saveSequence;
        String json = serialize();
        saveExecutor.execute(() -> write(seq, json));
    }

    private String serialize() {
        JsonObject root = new JsonObject();
        JsonArray list = new JsonArray();
        for (Track t : tracks) {
            JsonObject o = new JsonObject();
            o.addProperty("url", t.getUrl());
            o.addProperty("title", t.getTitle());
            list.add(o);
        }
        root.add("tracks", list);
        root.addProperty("volume", volume);
        root.addProperty("shuffle", shuffle);
        root.addProperty("autoplay", autoplay);
        root.addProperty("current", current);
        return GSON.toJson(root);
    }

    /** Atomic write; a snapshot older than the one on disk is dropped. */
    private void write(long seq, String json) {
        synchronized (fileLock) {
            if (seq <= writtenSequence) {
                return;
            }
            try {
                Files.createDirectories(file.toAbsolutePath().getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                    writer.write(json);
                }
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicUnsupported) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                writtenSequence = seq;
            } catch (IOException e) {
                LOGGER.error("Failed to save {}", file, e);
            }
        }
    }

    // ------------------------------------------------------------------ worker

    private final class Worker extends Thread {
        private final Track track;
        private final boolean automatic;
        volatile boolean cancelled;

        Worker(Track track, boolean automatic) {
            super("LARP Music");
            this.track = track;
            this.automatic = automatic;
            setDaemon(true);
        }

        @Override
        public void run() {
            boolean finishedNaturally = false;
            try (AudioDecoder decoder = backend.open(track.getUrl())) {
                AudioFormat format = decoder.getFormat();
                Output line = backend.output(format);
                line.start();
                try {
                    if (!cancelled) {
                        state = MusicPlayer.State.PLAYING;
                        synchronized (MusicPlayer.this) {
                            if (worker == this) {
                                autoFailures = 0;
                            }
                        }
                    }
                    short[] samples;
                    byte[] bytes = new byte[0];
                    while (!cancelled && (samples = decoder.read()) != null) {
                        waitWhilePaused(line);
                        if (cancelled) {
                            break;
                        }
                        // Perceptual volume curve.
                        float gain = volume * volume;
                        int n = samples.length;
                        if (bytes.length < n * 2) {
                            bytes = new byte[n * 2];
                        }
                        for (int i = 0; i < n; i++) {
                            int s = Math.round(samples[i] * gain);
                            bytes[i * 2] = (byte) s;              // little endian
                            bytes[i * 2 + 1] = (byte) (s >> 8);
                        }
                        line.write(bytes, n * 2);
                    }
                    if (!cancelled) {
                        line.drain();
                        finishedNaturally = true;
                    }
                } finally {
                    line.stop();
                    line.close();
                }
            } catch (Exception e) {
                if (!cancelled) {
                    failed(e);
                }
                return;
            }
            if (finishedNaturally) {
                beforeAutoAdvance.run();
                synchronized (MusicPlayer.this) {
                    if (worker == this) { // the user didn't pick something else meanwhile
                        advance();
                    }
                }
            }
        }

        private void failed(Exception e) {
            LOGGER.warn("Could not play {}: {}", track.getUrl(), e.toString());
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            synchronized (MusicPlayer.this) {
                if (worker != this) {
                    return;
                }
                if (automatic && tracks.size() > 1 && ++autoFailures < tracks.size()) {
                    advance(); // skip the dead track
                    return;
                }
                error = automatic && tracks.size() > 1 ? "No playable tracks (last error: " + message + ")" : message;
                state = MusicPlayer.State.ERROR;
            }
        }

        private void waitWhilePaused(Output line) {
            if (state != MusicPlayer.State.PAUSED) {
                return;
            }
            line.stop();
            synchronized (pauseLock) {
                while (state == MusicPlayer.State.PAUSED && !cancelled) {
                    try {
                        pauseLock.wait(250);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
            line.start();
        }
    }
}
