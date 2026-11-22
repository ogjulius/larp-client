package dev.larpclient.core.gui.menu;

import dev.larpclient.LarpClient;
import dev.larpclient.core.music.MusicPlayer;
import dev.larpclient.core.music.Track;
import dev.larpclient.core.render.Shapes;
import dev.larpclient.core.render.Ui;
import dev.larpclient.core.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/** Compact now-playing card: artwork with live equaliser, title, transport controls. */
public final class MusicWidget {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 40;

    private float x;
    private float y;

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    private static MusicPlayer player() {
        return LarpClient.get().music();
    }

    private float controlX(int index) {
        return x + WIDTH - 50 + index * 15;
    }

    private float controlY() {
        return y + HEIGHT / 2f - 5;
    }

    public void draw(int mouseX, int mouseY) {
        MusicPlayer p = player();
        boolean hover = RenderUtil.hovered(mouseX, mouseY, x, y, WIDTH, HEIGHT);
        Shapes.roundedRect(x, y, WIDTH, HEIGHT, 6, hover ? 0xB0121218 : 0x90101014);
        Shapes.roundedOutline(x, y, WIDTH, HEIGHT, 6, 0.5f, 0x1CFFFFFF);

        // Artwork tile with an equaliser that dances while playing.
        float ax = x + 6;
        float ay = y + 6;
        float as = HEIGHT - 12;
        Shapes.gradient(ax, ay, as, as, 0xFF7C4DFF, 0xFF5B3BA8, 0xFF2A1B5C, 0xFF4A2D99);
        Shapes.roundedOutline(ax, ay, as, as, 3, 1.2f, 0x90101014);
        boolean playing = p.getState() == MusicPlayer.State.PLAYING;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            float level = playing ? 0.35f + 0.65f * (float) Math.abs(Math.sin(now / (180.0 + i * 47) + i * 1.3)) : 0.25f;
            float bh = (as - 10) * level;
            float barsWidth = 4 * 2.5f + 3 * 2f;
            Shapes.roundedRect(ax + (as - barsWidth) / 2f + i * 4.5f, ay + as / 2f + (as - 10) / 2f - bh, 2.5f, bh, 1, 0xE6FFFFFF);
        }

        Track track = p.getCurrentTrack();
        String title = track == null ? (p.getTracks().isEmpty() ? "Add music" : "Not playing") : track.getTitle();
        String status;
        int statusColor = 0x99FFFFFF;
        switch (p.getState()) {
            case LOADING: status = "Loading…"; break;
            case PLAYING: status = "Now playing"; break;
            case PAUSED: status = "Paused"; break;
            case ERROR: status = p.getError(); statusColor = 0xFFFF7A7F; break;
            default: status = p.getTracks().isEmpty() ? "Paste a URL to start" : p.getTracks().size() + " in playlist";
        }
        float textX = ax + as + 7;
        float textW = controlX(0) - textX - 6;
        Ui.BODY_STRONG.draw(Ui.ellipsize(Ui.BODY_STRONG, title, textW), textX, y + 9, 0xFFFFFFFF);
        Ui.SMALL.draw(Ui.ellipsize(Ui.SMALL, status == null ? "" : status, textW), textX, y + 21, statusColor);

        icon(0, mouseX, mouseY, false);
        icon(1, mouseX, mouseY, playing);
        icon(2, mouseX, mouseY, false);
    }

    private void icon(int index, int mouseX, int mouseY, boolean playing) {
        float ix = controlX(index);
        float iy = controlY();
        boolean hover = RenderUtil.hovered(mouseX, mouseY, ix - 2, iy - 2, 14, 14);
        if (index == 1) {
            Shapes.circle(ix + 5, iy + 5, 7, hover ? 0xFFFFFFFF : 0xF0FFFFFF);
            if (playing) {
                Shapes.iconPause(ix + 2, iy + 2, 6, 0xFF101014);
            } else {
                Shapes.iconPlay(ix + 2.6f, iy + 2, 6, 0xFF101014);
            }
            return;
        }
        int color = hover ? 0xFFFFFFFF : 0xA0FFFFFF;
        if (index == 0) {
            Shapes.iconPrevious(ix + 1, iy + 1, 8, color);
        } else {
            Shapes.iconNext(ix + 1, iy + 1, 8, color);
        }
    }

    public boolean click(int mouseX, int mouseY, int button, GuiScreen parent) {
        if (button != 0 || !RenderUtil.hovered(mouseX, mouseY, x, y, WIDTH, HEIGHT)) {
            return false;
        }
        MusicPlayer p = player();
        for (int i = 0; i < 3; i++) {
            if (RenderUtil.hovered(mouseX, mouseY, controlX(i) - 3, controlY() - 3, 16, 16)) {
                MenuButton.playClick();
                if (i == 0) {
                    p.previous();
                } else if (i == 2) {
                    p.next();
                } else if (p.getTracks().isEmpty()) {
                    Minecraft.getMinecraft().displayGuiScreen(new MusicScreen(parent));
                } else {
                    p.togglePause();
                }
                return true;
            }
        }
        MenuButton.playClick();
        Minecraft.getMinecraft().displayGuiScreen(new MusicScreen(parent));
        return true;
    }

}
