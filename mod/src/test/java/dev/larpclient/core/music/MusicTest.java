package dev.larpclient.core.music;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MusicTest {
    @Test
    void validatesUrls() {
        assertTrue(Track.isValidUrl("https://example.com/a.mp3"));
        assertTrue(Track.isValidUrl("http://radio.example.net:8000/stream"));
        assertFalse(Track.isValidUrl("file:///etc/passwd"));
        assertFalse(Track.isValidUrl("not a url"));
        assertFalse(Track.isValidUrl("https://"));
    }

    @Test
    void derivesTitles() {
        assertEquals("My Song", Track.titleFromUrl("https://cdn.example.com/music/My%20Song.mp3"));
        assertEquals("lofi beats", Track.titleFromUrl("https://example.com/lofi_beats.mp3?x=1"));
        assertEquals("radio.example.net", Track.titleFromUrl("http://radio.example.net:8000/"));
        assertEquals("Custom", new Track("https://e.com/a.mp3", "  Custom ").getTitle());
    }

    @Test
    void playlistPersistsAndRemovalKeepsCurrentIndexConsistent(@TempDir Path dir) {
        Path file = dir.resolve("music.json");
        MusicPlayer player = new MusicPlayer(file);
        assertTrue(player.add("https://e.com/1.mp3", null));
        assertTrue(player.add("https://e.com/2.mp3", "Two"));
        assertFalse(player.add("ftp://e.com/3.mp3", null));
        player.setVolume(1.7f);
        assertEquals(1f, player.getVolume());
        player.setShuffle(true);
        player.setAutoplay(true);
        player.save();

        MusicPlayer loaded = new MusicPlayer(file);
        loaded.load();
        assertEquals(2, loaded.getTracks().size());
        assertEquals("Two", loaded.getTracks().get(1).getTitle());
        assertTrue(loaded.isShuffle());
        assertTrue(loaded.isAutoplay());
        loaded.remove(0);
        assertEquals(1, loaded.getTracks().size());
        assertEquals(MusicPlayer.State.STOPPED, loaded.getState());
    }
}
