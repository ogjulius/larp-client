package dev.larpclient.core.platform;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import dev.larpclient.LarpClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.LWJGLUtil;

import java.lang.reflect.Method;

/**
 * Fullscreen that works on Wayland compositors (Hyprland, Sway, KDE, GNOME) and X11 window managers.
 * <p>
 * LWJGL 2's own fullscreen recreates the window with a pre-set {@code _NET_WM_STATE_FULLSCREEN}
 * property and may switch video modes through XRandR. Under XWayland that request is commonly
 * ignored: the game renders at the monitor size into a normal-sized window (cropped image, wrong
 * mouse mapping). Instead, we keep the game as an ordinary managed window and send the standard
 * EWMH {@code _NET_WM_STATE} client message, so the compositor itself makes it fullscreen. The
 * window simply gets resized and Minecraft's normal resize handling adapts — no mode switch, instant
 * toggling, Alt-Tab and workspaces keep working.
 */
public final class CompositorFullscreen {
    private static CompositorFullscreen instance;

    private final boolean supported;
    private boolean fullscreen;
    private Boolean pending;
    private int framesRendered;

    private CompositorFullscreen() {
        boolean ok = LWJGLUtil.getPlatform() == LWJGLUtil.PLATFORM_LINUX && !Boolean.getBoolean("larp.legacyFullscreen");
        if (ok) {
            try {
                X11.INSTANCE.hashCode(); // load libX11 eagerly so failures disable us cleanly
            } catch (Throwable t) {
                LarpClient.LOGGER.warn("Compositor fullscreen unavailable, using LWJGL fullscreen: {}", t.toString());
                ok = false;
            }
        }
        supported = ok;
    }

    public static CompositorFullscreen get() {
        if (instance == null) {
            instance = new CompositorFullscreen();
            MinecraftForge.EVENT_BUS.register(instance);
        }
        return instance;
    }

    public boolean isSupported() {
        return supported;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    /** Toggle; returns false if the caller should fall back to vanilla behaviour. */
    public boolean toggle() {
        return set(!fullscreen);
    }

    public boolean set(boolean value) {
        if (!supported) {
            return false;
        }
        fullscreen = value;
        Minecraft.getMinecraft().gameSettings.fullScreen = value;
        if (framesRendered < 20) {
            pending = value; // window may not be mapped yet; apply once it is
        } else {
            send(value);
        }
        return true;
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || framesRendered >= 20) {
            return;
        }
        framesRendered++;
        if (framesRendered == 20) {
            if (Boolean.getBoolean("larp.startFullscreen") && pending == null && !fullscreen) {
                fullscreen = true;
                pending = true;
            }
            if (pending != null) {
                send(pending);
                pending = null;
            }
        }
    }

    private static long lwjglWindow() throws Exception {
        Class<?> linuxDisplay = Class.forName("org.lwjgl.opengl.LinuxDisplay");
        Method getWindow = linuxDisplay.getDeclaredMethod("getWindow");
        getWindow.setAccessible(true);
        return (Long) getWindow.invoke(null);
    }

    private void send(boolean enable) {
        Pointer display = null;
        try {
            long window = lwjglWindow();
            X11 x = X11.INSTANCE;
            display = x.XOpenDisplay(null);
            if (display == null) {
                throw new IllegalStateException("XOpenDisplay failed");
            }
            X11.XClientMessageEvent ev = new X11.XClientMessageEvent();
            ev.type = X11.CLIENT_MESSAGE;
            ev.sendEvent = 1;
            ev.display = display;
            ev.window = new NativeLong(window);
            ev.messageType = x.XInternAtom(display, "_NET_WM_STATE", false);
            ev.format = 32;
            ev.data[0] = new NativeLong(enable ? 1 : 0);                 // _NET_WM_STATE_ADD / REMOVE
            ev.data[1] = x.XInternAtom(display, "_NET_WM_STATE_FULLSCREEN", false);
            ev.data[2] = new NativeLong(0);
            ev.data[3] = new NativeLong(1);                              // source: normal application
            x.XSendEvent(display, x.XDefaultRootWindow(display), false,
                    new NativeLong(X11.SUBSTRUCTURE_REDIRECT_MASK | X11.SUBSTRUCTURE_NOTIFY_MASK), ev);
            x.XFlush(display);
        } catch (Throwable t) {
            LarpClient.LOGGER.error("Could not request fullscreen from the window manager", t);
        } finally {
            if (display != null) {
                X11.INSTANCE.XCloseDisplay(display);
            }
        }
    }
}
