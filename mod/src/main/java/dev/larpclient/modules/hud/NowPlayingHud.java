package dev.larpclient.modules.hud;

import dev.larpclient.LarpClient;
import dev.larpclient.core.hud.TextHudModule;
import dev.larpclient.core.music.MusicPlayer;
import dev.larpclient.core.music.Track;

/** Shows the track playing in the LARP music player. */
public final class NowPlayingHud extends TextHudModule {
    public NowPlayingHud() {
        super("Now Playing", "Shows the current music player track", 0.5, 0.0);
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    protected String getText() {
        MusicPlayer player = LarpClient.get().music();
        Track track = player.getCurrentTrack();
        if (track == null || player.getState() != MusicPlayer.State.PLAYING) {
            return null;
        }
        return "♪ " + track.getTitle();
    }

    @Override
    protected String getPlaceholder() {
        return "♪ Song title";
    }
}
