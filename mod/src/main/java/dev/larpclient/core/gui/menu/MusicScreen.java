package dev.larpclient.core.gui.menu;

import dev.larpclient.LarpClient;
import dev.larpclient.core.gui.Theme;
import dev.larpclient.core.gui.Widgets;
import dev.larpclient.core.music.MusicPlayer;
import dev.larpclient.core.music.Track;
import dev.larpclient.core.render.Shapes;
import dev.larpclient.core.render.Ui;
import dev.larpclient.core.util.RenderUtil;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.List;

/** Playlist manager: add MP3/WAV/radio URLs, play, remove, shuffle, autoplay, volume. */
public final class MusicScreen extends GuiScreen {
    private static final int ROW = 22;

    private final GuiScreen parent;
    private final MusicWidget widget = new MusicWidget();
    private Widgets.TextInput urlField;
    private Widgets.TextInput titleField;
    private MenuButton addButton;
    private String message;
    private int messageColor;
    private float scroll;
    private boolean draggingVolume;
    private float px;
    private float py;
    private float pw;
    private float ph;

    public MusicScreen(GuiScreen parent) {
        this.parent = parent;
    }

    private static MusicPlayer player() {
        return LarpClient.get().music();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        pw = Math.min(400, width - 24);
        ph = Math.min(280, height - 24);
        px = (width - pw) / 2f;
        py = (height - ph) / 2f;
        String url = urlField == null ? "" : urlField.text;
        String title = titleField == null ? "" : titleField.text;
        urlField = new Widgets.TextInput(px + 14, py + 40, pw - 28 - 66, 16, "https://example.com/track.mp3", 512);
        titleField = new Widgets.TextInput(px + 14, py + 60, pw - 28 - 66, 16, "Title (optional)", 64);
        urlField.setText(url);
        titleField.setText(title);
        urlField.focused = true;
        addButton = new MenuButton("Add", this::addTrack).bounds(px + pw - 14 - 58, py + 40, 58, 36).primary();
        widget.setPosition(px + 14, py + ph - MusicWidget.HEIGHT - 14);
    }

    private void addTrack() {
        String url = urlField.text.trim();
        if (player().add(url, titleField.text)) {
            message = "Added “" + Track.titleFromUrl(url) + "”";
            messageColor = 0xFF5FD39A;
            urlField.setText("");
            titleField.setText("");
        } else {
            message = "Use a direct http(s) link to an .mp3/.wav file or MP3 stream";
            messageColor = 0xFFFF7A7F;
        }
    }

    private float listTop() {
        return py + 100;
    }

    private float listHeight() {
        return ph - 100 - MusicWidget.HEIGHT - 26;
    }

    private float volumeX() {
        return px + 14 + MusicWidget.WIDTH + 18;
    }

    private float volumeWidth() {
        return px + pw - 14 - volumeX() - 22;
    }

    private float volumeY() {
        return py + ph - 14 - MusicWidget.HEIGHT / 2f;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (mc.theWorld == null) {
            Backdrop.drawDimmed(width, height);
        } else {
            Shapes.rect(0, 0, width, height, 0xA0000000);
        }
        Widgets.panel(px, py, pw, ph);
        Ui.TITLE.draw("Music", px + 14, py + 12, 0xFFFFFFFF);
        Ui.SMALL.draw("MP3 / WAV files and MP3 radio streams", px + 14 + Ui.TITLE.width("Music") + 8, py + 15, 0x80FFFFFF);
        Widgets.closeButton(px + pw - 22, py + 10, mouseX, mouseY);

        urlField.draw();
        titleField.draw();
        addButton.draw(mouseX, mouseY);
        if (message != null) {
            Ui.SMALL.draw(Ui.ellipsize(Ui.SMALL, message, pw - 200), px + 14, py + 82, messageColor);
        }

        MusicPlayer p = player();
        float tx = px + pw - 14;
        tx = Widgets.labeledSwitch("Autoplay", tx, py + 81, p.isAutoplay());
        Widgets.labeledSwitch("Shuffle", tx - 10, py + 81, p.isShuffle());

        // playlist
        List<Track> tracks = p.getTracks();
        float top = listTop();
        float lh = listHeight();
        Shapes.rect(px + 14, top - 4, pw - 28, 0.5f, 0x14FFFFFF);
        scroll = Math.max(0, Math.min(Math.max(0, tracks.size() * ROW - lh), scroll));
        RenderUtil.scissor(px + 10, top, pw - 20, lh);
        float y = top - scroll;
        if (tracks.isEmpty()) {
            Ui.BODY.drawCentered("Your playlist is empty", px + pw / 2f, top + lh / 2f - 10, 0x99FFFFFF);
            Ui.SMALL.drawCentered("Paste a direct link to a song or an internet radio stream above",
                    px + pw / 2f, top + lh / 2f + 2, 0x66FFFFFF);
        }
        boolean inList = RenderUtil.hovered(mouseX, mouseY, px, top, pw, lh);
        for (int i = 0; i < tracks.size(); i++) {
            boolean hover = inList && RenderUtil.hovered(mouseX, mouseY, px + 14, y, pw - 28, ROW - 2);
            boolean current = i == p.getCurrentIndex() && p.getState() != MusicPlayer.State.STOPPED;
            if (hover || current) {
                Shapes.roundedRect(px + 14, y, pw - 28, ROW - 2, 4, current ? 0x288B5CF6 : 0x10FFFFFF);
            }
            Ui.SMALL.drawRight(String.valueOf(i + 1), px + 30, y + 7, current ? Theme.ACCENT : 0x66FFFFFF);
            Ui.BODY.draw(Ui.ellipsize(Ui.BODY, tracks.get(i).getTitle(), pw - 110), px + 38, y + 5.5f,
                    current ? 0xFFFFFFFF : 0xD0FFFFFF);
            if (hover) {
                boolean removeHover = mouseX >= px + pw - 34;
                Widgets.cross(px + pw - 28, y + 6, 7, removeHover ? 0xFFFF7A7F : 0x80FFFFFF);
            }
            y += ROW;
        }
        RenderUtil.endScissor();

        widget.draw(mouseX, mouseY);
        // volume
        float vx = volumeX();
        float vw = volumeWidth();
        float vy = volumeY();
        Ui.LABEL.draw("VOLUME", vx, vy - 12, 0x80FFFFFF, 1.2f);
        Widgets.slider(vx, vy, vw, p.getVolume());
        Ui.SMALL.draw(Math.round(p.getVolume() * 100) + "%", vx + vw + 6, vy - 3, 0xB0FFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        urlField.mouseClicked(mouseX, mouseY);
        titleField.mouseClicked(mouseX, mouseY);
        if (button == 0 && RenderUtil.hovered(mouseX, mouseY, px + pw - 24, py + 8, 16, 16)) {
            MenuButton.playClick();
            mc.displayGuiScreen(parent);
            return;
        }
        if (addButton.click(mouseX, mouseY, button) || widget.click(mouseX, mouseY, button, this)) {
            return;
        }
        MusicPlayer p = player();
        if (button == 0 && RenderUtil.hovered(mouseX, mouseY, volumeX() - 3, volumeY() - 5, volumeWidth() + 6, 10)) {
            draggingVolume = true;
            mouseClickMove(mouseX, mouseY, button, 0);
            return;
        }
        float tx = px + pw - 14;
        float autoplayX = tx - Widgets.labeledSwitchWidth("Autoplay");
        float shuffleRight = autoplayX - 10;
        if (button == 0 && RenderUtil.hovered(mouseX, mouseY, autoplayX, py + 78, tx - autoplayX, 14)) {
            p.setAutoplay(!p.isAutoplay());
            p.saveInBackground();
            return;
        }
        if (button == 0 && RenderUtil.hovered(mouseX, mouseY, shuffleRight - Widgets.labeledSwitchWidth("Shuffle"), py + 78,
                Widgets.labeledSwitchWidth("Shuffle"), 14)) {
            p.setShuffle(!p.isShuffle());
            p.saveInBackground();
            return;
        }
        if (button != 0 || !RenderUtil.hovered(mouseX, mouseY, px + 14, listTop(), pw - 28, listHeight())) {
            return;
        }
        int index = (int) ((mouseY - listTop() + scroll) / ROW);
        if (index >= 0 && index < p.getTracks().size()) {
            if (mouseX >= px + pw - 34) {
                p.remove(index);
            } else {
                p.play(index);
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (draggingVolume) {
            player().setVolume((mouseX - volumeX()) / volumeWidth());
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (draggingVolume) {
            draggingVolume = false;
            player().saveInBackground();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll -= Integer.signum(wheel) * ROW;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN && (urlField.focused || titleField.focused)) {
            addTrack();
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            boolean url = urlField.focused;
            urlField.focused = !url;
            titleField.focused = url;
            return;
        }
        urlField.keyTyped(typedChar, keyCode);
        titleField.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
