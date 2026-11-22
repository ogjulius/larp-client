package dev.larpclient.core.music.decode;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Streaming decoder producing interleaved signed 16-bit little-endian PCM. */
public abstract class AudioDecoder implements Closeable {
    private static final int MAX_REDIRECTS = 5;

    /** PCM output format (16-bit signed little-endian). */
    public abstract AudioFormat getFormat();

    /** Next block of interleaved samples, or {@code null} at end of stream. */
    public abstract short[] read() throws IOException;

    /** Open a URL and pick a decoder based on Content-Type / extension. */
    public static AudioDecoder open(String url) throws IOException {
        HttpURLConnection connection = connect(url);
        String type = connection.getContentType() == null ? "" : connection.getContentType().toLowerCase(Locale.ROOT);
        String path = connection.getURL().getPath().toLowerCase(Locale.ROOT);
        InputStream in = new BufferedInputStream(connection.getInputStream(), 64 * 1024);
        try {
            if (type.contains("wav") || path.endsWith(".wav")) {
                return new WavDecoder(in);
            }
            if (type.contains("ogg") || type.contains("vorbis") || path.endsWith(".ogg")) {
                throw new IOException("OGG is not supported yet; use an MP3 or WAV URL");
            }
            return new Mp3Decoder(in);
        } catch (IOException | RuntimeException e) {
            in.close();
            throw e;
        }
    }

    private static HttpURLConnection connect(String url) throws IOException {
        String target = url;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(target).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(10_000);
            c.setReadTimeout(20_000);
            c.setRequestProperty("User-Agent", "LARPClient-MusicPlayer/1.0");
            c.setRequestProperty("Icy-MetaData", "0");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                // Follow redirects manually so http→https hops work too.
                target = new URL(new URL(target), c.getHeaderField("Location")).toString();
                c.disconnect();
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new IOException("HTTP " + code);
            }
            return c;
        }
        throw new IOException("Too many redirects");
    }
}
