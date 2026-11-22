package dev.larpclient.launcher.game;

/** Receives install / launch progress. Implementations must be thread-safe. */
public interface Progress {
    /** @param fraction 0..1, or negative for indeterminate */
    void update(String status, double fraction);

    Progress NONE = (status, fraction) -> { };
}
