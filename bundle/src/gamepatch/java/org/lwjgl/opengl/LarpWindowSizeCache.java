package org.lwjgl.opengl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Runs inside the game (Java 8), next to a patched {@code LinuxMouse}: see the launcher's
 * {@code LwjglMousePatch}. LWJGL 2 asks the X server for the window size (a blocking round trip) on
 * every pointer-motion event. Under XWayland a round trip can take milliseconds, so a 1000 Hz mouse
 * queues up seconds of work and the game appears frozen. The window size is cached here briefly instead.
 * <p>
 * Must stay a single class with no nested classes: only this one class file is bundled.
 */
public final class LarpWindowSizeCache {
    private static final long TTL_NANOS = 50_000_000L; // 50 ms: at most ~20 queries per second per window
    private static final int SLOTS = 4;                // LWJGL only ever asks about two windows
    private static final int HEIGHT = 0;
    private static final int WIDTH = 1;

    private static final MethodHandle[] QUERIES = {handle("nGetWindowHeight"), handle("nGetWindowWidth")};
    private static final long[][] windows = new long[2][SLOTS];
    private static final int[][] values = new int[2][SLOTS];
    private static final long[][] times = new long[2][SLOTS];
    private static final boolean[][] used = new boolean[2][SLOTS];
    private static final int[] next = new int[2];

    private LarpWindowSizeCache() {
    }

    public static int height(long display, long window) {
        return get(HEIGHT, display, window);
    }

    public static int width(long display, long window) {
        return get(WIDTH, display, window);
    }

    private static MethodHandle handle(String name) {
        try {
            Method m = Class.forName("org.lwjgl.opengl.LinuxMouse").getDeclaredMethod(name, long.class, long.class);
            m.setAccessible(true);
            return MethodHandles.lookup().unreflect(m);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static synchronized int get(int kind, long display, long window) {
        long now = System.nanoTime();
        int slot = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (used[kind][i] && windows[kind][i] == window) {
                slot = i;
                break;
            }
        }
        if (slot >= 0 && now - times[kind][slot] < TTL_NANOS) {
            return values[kind][slot];
        }
        if (slot < 0) {
            slot = next[kind];
            next[kind] = (slot + 1) % SLOTS;
        }
        int value;
        try {
            value = (int) QUERIES[kind].invokeExact(display, window);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        windows[kind][slot] = window;
        values[kind][slot] = value;
        times[kind][slot] = now;
        used[kind][slot] = true;
        return value;
    }
}
