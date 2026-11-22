package dev.larpclient.core.music.decode;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;

/** MP3 (files and Shoutcast/Icecast streams) via JLayer. */
final class Mp3Decoder extends AudioDecoder {
    private final InputStream in;
    private final Bitstream bitstream;
    private final Decoder decoder = new Decoder();
    private AudioFormat format;
    private short[] pending;

    Mp3Decoder(InputStream in) throws IOException {
        this.in = in;
        this.bitstream = new Bitstream(in);
        // Decode the first frame up front to learn the format.
        pending = decodeFrame();
        if (pending == null) {
            throw new IOException("Not a playable MP3 stream");
        }
    }

    private short[] decodeFrame() throws IOException {
        try {
            for (int skipped = 0; skipped < 64; skipped++) {
                Header header = bitstream.readFrame();
                if (header == null) {
                    return null;
                }
                try {
                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    if (format == null) {
                        format = new AudioFormat(output.getSampleFrequency(), 16, output.getChannelCount(), true, false);
                    }
                    short[] copy = new short[output.getBufferLength()];
                    System.arraycopy(output.getBuffer(), 0, copy, 0, copy.length);
                    return copy;
                } catch (DecoderException | ArrayIndexOutOfBoundsException corrupt) {
                    // skip a damaged frame
                } finally {
                    bitstream.closeFrame();
                }
            }
            throw new IOException("Too many corrupt MP3 frames");
        } catch (BitstreamException e) {
            throw new IOException("MP3 stream error: " + e.getMessage(), e);
        }
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public short[] read() throws IOException {
        if (pending != null) {
            short[] first = pending;
            pending = null;
            return first;
        }
        return decodeFrame();
    }

    @Override
    public void close() throws IOException {
        try {
            bitstream.close();
        } catch (BitstreamException ignored) {
            // closing anyway
        }
        in.close();
    }
}
