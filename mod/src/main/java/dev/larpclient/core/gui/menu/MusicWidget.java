package dev.larpclient.core.gui.menu;

import dev.larpclient.LarpClient;
import dev.larpclient.core.gui.Theme;
import dev.larpclient.core.music.MusicPlayer;
import dev.larpclient.core.music.Track;
import dev.larpclient.core.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/** Compact "now playing" card with transport controls and a volume bar. */
public final class MusicWidget {
    public static final int WIDTH = 170;
    public static final int HEIGHT = 50;

    private int x;
    private int y;
    private boolean draggingVolume;

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    private static MusicPlayer player() {
        return LarpClient.get().music();
    }

    public void draw(int mouseX, int mouseY) {
        MusicPlayer p = player();
        RenderUtil.rect(x, y, x + WIDTH, y + HEIGHT, 0x90000000);
        RenderUtil.outline(x, y, x + WIDTH, y + HEIGHT, 0x30FFFFFF);
        RenderUtil.rect(x, y, x + 2, y + HEIGHT, Theme.ACCENT);

        Track track = p.getCurrentTrack();
        String title = track == null ? (p.getTracks().isEmpty() ? "No music yet" : "Nothing playing") : track.getTitle();
        String status;
        switch (p.getState()) {
            case LOADING: status = "Loading…"; break;
            case PLAYING: status = "Now playing"; break;
            case PAUSED: status = "Paused"; break;
            case ERROR: status = "Error: " + p.getError(); break;
            default: status = p.getTracks().isEmpty() ? "Click ♫ to add a URL" : "Stopped";
        }
        RenderUtil.text(status, x + 8, y + 5, p.getState() == MusicPlayer.State.ERROR ? Theme.DANGER : Theme.TEXT_DIM, false);
        drawMarquee(title, x + 8, y + 16, WIDTH - 16);

        int by = y + 31;
        icon("\u00AB", x + 8, by, mouseX, mouseY);
        icon(p.getState() == MusicPlayer.State.PLAYING ? "||" : "▶", x + 24, by, mouseX, mouseY);
        icon("\u00BB", x + 40, by, mouseX, mouseY);
        icon("♫", x + WIDTH - 18, by, mouseX, mouseY);

        int vx = volX();
        RenderUtil.rect(vx, by + 5, vx + volW(), by + 7, Theme.OFF);
        RenderUtil.rect(vx, by + 5, vx + volW() * p.getVolume(), by + 7, Theme.ACCENT);
        RenderUtil.rect(vx + volW() * p.getVolume() - 1, by + 3, vx + volW() * p.getVolume() + 1, by + 9, Theme.TEXT);
    }

    private int volX() {
        return x + 62;
    }

    private int volW() {
        return WIDTH - 62 - 28;
    }

    private void drawMarquee(String text, int tx, int ty, int width) {
        int textW = RenderUtil.font().getStringWidth(text);
        if (textW <= width) {
            RenderUtil.text(text, tx, ty, Theme.TEXT, false);
            return;
        }
        int gap = 30;
        float offset = (System.currentTimeMillis() / 40f) % (textW + gap);
        RenderUtil.scissor(tx, ty - 1, width, 10);
        RenderUtil.text(text, tx - offset, ty, Theme.TEXT, false);
        RenderUtil.text(text, tx - offset + textW + gap, ty, Theme.TEXT, false);
        RenderUtil.endScissor();
    }

    private void icon(String glyph, int ix, int iy, int mouseX, int mouseY) {
        boolean hover = RenderUtil.hovered(mouseX, mouseY, ix - 2, iy - 1, 13, 12);
        RenderUtil.text(glyph, ix, iy + 1, hover ? Theme.ACCENT : Theme.TEXT, false);
    }

    /** @return true if the click was handled */
    public boolean click(int mouseX, int mouseY, int button, GuiScreen parent) {
        if (button != 0 || !RenderUtil.hovered(mouseX, mouseY, x, y, WIDTH, HEIGHT)) {
            return false;
        }
        MusicPlayer p = player();
        int by = y + 31;
        if (RenderUtil.hovered(mouseX, mouseY, x + 6, by - 1, 13, 12)) {
            p.previous();
        } else if (RenderUtil.hovered(mouseX, mouseY, x + 22, by - 1, 13, 12)) {
            if (p.getTracks().isEmpty()) {
                Minecraft.getMinecraft().displayGuiScreen(new MusicScreen(parent));
            } else {
                p.togglePause();
            }
        } else if (RenderUtil.hovered(mouseX, mouseY, x + 38, by - 1, 13, 12)) {
            p.next();
        } else if (RenderUtil.hovered(mouseX, mouseY, x + WIDTH - 20, by - 1, 13, 12)) {
            Minecraft.getMinecraft().displayGuiScreen(new MusicScreen(parent));
        } else if (RenderUtil.hovered(mouseX, mouseY, volX() - 3, by, volW() + 6, 12)) {
            draggingVolume = true;
            drag(mouseX);
        }
        return true;
    }

    public void drag(int mouseX) {
        if (draggingVolume) {
            player().setVolume((mouseX - volX()) / (float) volW());
        }
    }

    public void release() {
        if (draggingVolume) {
            draggingVolume = false;
            player().save();
        }
    }
}
