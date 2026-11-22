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
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Streams music from URLs on a background thread. Supports MP3 files, MP3 radio streams and WAV.
 * The playlist, volume and shuffle state persist to {@code music.json}.
 * <p>
 * Thread model: all public methods may be called from the render thread; decoding and audio
 * output run on a single daemon worker thread per playing track.
 */
public final class MusicPlayer {
    public enum State { STOPPED, LOADING, PLAYING, PAUSED, ERROR }

    private static final Logger LOGGER = LogManager.getLogger("LARP Client/Music");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final List<Track> tracks = new ArrayList<>();
    private final Object pauseLock = new Object();

    private volatile State state = State.STOPPED;
    private volatile float volume = 0.5f;
    private volatile boolean shuffle;
    private volatile boolean autoplay;
    private volatile int current = -1;
    private volatile String error;
    private volatile Worker worker;

    public MusicPlayer(Path file) {
        this.file = file;
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
        save();
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
        save();
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
        if (index < 0 || index >= tracks.size()) {
            return;
        }
        stopWorker();
        current = index;
        error = null;
        Worker w = new Worker(tracks.get(index));
        worker = w;
        state = State.LOADING;
        w.start();
        save();
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
        if (tracks.isEmpty()) {
            return;
        }
        if (shuffle && tracks.size() > 1) {
            int n;
            do {
                n = ThreadLocalRandom.current().nextInt(tracks.size());
            } while (n == current);
            play(n);
        } else {
            play((current + 1) % tracks.size());
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
    public void startup() {
        if (autoplay && !tracks.isEmpty()) {
            if (shuffle) {
                next();
            } else {
                play(current >= 0 && current < tracks.size() ? current : 0);
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
            LOGGER.error("Failed to read {}", file, e);
        }
    }

    public synchronized void save() {
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
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save {}", file, e);
        }
    }

    // ------------------------------------------------------------------ worker

    private final class Worker extends Thread {
        private final Track track;
        volatile boolean cancelled;

        Worker(Track track) {
            super("LARP Music");
            this.track = track;
            setDaemon(true);
        }

        @Override
        public void run() {
            boolean finishedNaturally = false;
            try (AudioDecoder decoder = AudioDecoder.open(track.getUrl())) {
                AudioFormat format = decoder.getFormat();
                SourceDataLine line = AudioSystem.getSourceDataLine(format);
                line.open(format, (int) format.getSampleRate() * format.getFrameSize() / 4);
                line.start();
                try {
                    if (!cancelled) {
                        state = MusicPlayer.State.PLAYING;
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
                        line.write(bytes, 0, n * 2);
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
                    LOGGER.warn("Could not play {}: {}", track.getUrl(), e.toString());
                    error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    state = MusicPlayer.State.ERROR;
                }
                return;
            }
            if (finishedNaturally && worker == this) {
                next();
            }
        }

        private void waitWhilePaused(SourceDataLine line) {
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
