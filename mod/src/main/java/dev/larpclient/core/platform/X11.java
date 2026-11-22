package dev.larpclient.core.platform;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/** The handful of Xlib calls needed to ask the window manager for fullscreen (via JNA, shipped with Minecraft). */
interface X11 extends Library {
    X11 INSTANCE = (X11) Native.loadLibrary("X11", X11.class);

    long SUBSTRUCTURE_NOTIFY_MASK = 1L << 19;
    long SUBSTRUCTURE_REDIRECT_MASK = 1L << 20;
    int CLIENT_MESSAGE = 33;

    Pointer XOpenDisplay(String name);

    int XCloseDisplay(Pointer display);

    NativeLong XDefaultRootWindow(Pointer display);

    NativeLong XInternAtom(Pointer display, String name, boolean onlyIfExists);

    int XSendEvent(Pointer display, NativeLong window, boolean propagate, NativeLong mask, XClientMessageEvent event);

    int XFlush(Pointer display);

    /** XClientMessageEvent padded to the size of the XEvent union (24 longs). */
    class XClientMessageEvent extends Structure {
        public int type;
        public NativeLong serial;
        public int sendEvent;
        public Pointer display;
        public NativeLong window;
        public NativeLong messageType;
        public int format;
        public NativeLong[] data = new NativeLong[5];
        public NativeLong[] pad = new NativeLong[12];

        @SuppressWarnings("deprecation")
        public XClientMessageEvent() {
            setFieldOrder(new String[]{"type", "serial", "sendEvent", "display", "window", "messageType", "format", "data", "pad"});
            for (int i = 0; i < data.length; i++) {
                data[i] = new NativeLong(0);
            }
            for (int i = 0; i < pad.length; i++) {
                pad[i] = new NativeLong(0);
            }
        }
    }
}
