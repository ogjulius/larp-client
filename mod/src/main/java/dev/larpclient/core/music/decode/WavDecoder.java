package dev.larpclient.core.music.decode;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;

/** WAV / AIFF / AU via Java Sound, converted to 16-bit signed little-endian PCM. */
final class WavDecoder extends AudioDecoder {
    private final AudioInputStream pcm;
    private final byte[] buffer = new byte[16 * 1024];

    WavDecoder(InputStream in) throws IOException {
        try {
            AudioInputStream source = AudioSystem.getAudioInputStream(in);
            AudioFormat src = source.getFormat();
            AudioFormat target = new AudioFormat(src.getSampleRate(), 16, src.getChannels(), true, false);
            pcm = AudioSystem.getAudioInputStream(target, source);
        } catch (UnsupportedAudioFileException | IllegalArgumentException e) {
            throw new IOException("Unsupported audio file", e);
        }
    }

    @Override
    public AudioFormat getFormat() {
        return pcm.getFormat();
    }

    @Override
    public short[] read() throws IOException {
        int n = pcm.read(buffer, 0, buffer.length - (buffer.length % 2));
        if (n <= 0) {
            return null;
        }
        short[] out = new short[n / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (short) ((buffer[i * 2] & 0xFF) | (buffer[i * 2 + 1] << 8));
        }
        return out;
    }

    @Override
    public void close() throws IOException {
        pcm.close();
    }
}
