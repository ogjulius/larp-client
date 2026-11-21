package dev.larpclient.core.util;

/** Frame-rate independent exponential smoothing toward a target value. */
public final class Animation {
    private double value;
    private double target;
    private long last = -1;

    public Animation(double initial) {
        this.value = initial;
        this.target = initial;
    }

    public void setTarget(double target) {
        this.target = target;
    }

    public double getTarget() {
        return target;
    }

    public void snap(double v) {
        value = v;
        target = v;
    }

    /**
     * Advance toward the target.
     *
     * @param speed approximate fraction of the gap closed per 1/60 s
     */
    public double update(long nowMillis, double speed) {
        if (last < 0) {
            last = nowMillis;
        }
        double frames = Math.min(10, (nowMillis - last) / (1000.0 / 60.0));
        last = nowMillis;
        double factor = 1 - Math.pow(1 - Math.max(0, Math.min(1, speed)), frames);
        value += (target - value) * factor;
        if (Math.abs(target - value) < 1e-4) {
            value = target;
        }
        return value;
    }

    public double get() {
        return value;
    }
}
