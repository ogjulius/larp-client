package dev.larpclient.launcher.game;

/** Receives install / launch progress. Implementations must be thread-safe. */
public interface Progress {
    /** @param fraction 0..1, or negative for indeterminate */
    void update(String status, double fraction);

    Progress NONE = (status, fraction) -> { };

    /**
     * Wraps {@code target} so the fraction never goes backwards within one status. Parallel download
     * workers count atomically but can deliver their updates out of order.
     */
    static Progress monotonic(Progress target) {
        return new Progress() {
            private String status;
            private double fraction;

            @Override
            public synchronized void update(String newStatus, double newFraction) {
                if (newFraction >= 0 && newStatus.equals(status) && newFraction < fraction) {
                    return;
                }
                status = newStatus;
                fraction = newFraction;
                target.update(newStatus, newFraction);
            }
        };
    }
}
