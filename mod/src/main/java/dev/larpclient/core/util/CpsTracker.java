package dev.larpclient.core.util;

/**
 * Counts clicks in a sliding one second window using a fixed ring buffer (no allocation).
 */
public final class CpsTracker {
    private static final long WINDOW_MS = 1000L;
    private final long[] clicks = new long[64];
    private int head;
    private int size;

    public synchronized void click(long nowMillis) {
        clicks[head] = nowMillis;
        head = (head + 1) % clicks.length;
        if (size < clicks.length) {
            size++;
        }
    }

    public synchronized int getCps(long nowMillis) {
        int count = 0;
        for (int i = 0; i < size; i++) {
            int index = Math.floorMod(head - 1 - i, clicks.length);
            if (nowMillis - clicks[index] < WINDOW_MS) {
                count++;
            } else {
                break; // entries are chronological; everything older is outside the window
            }
        }
        return count;
    }
}
