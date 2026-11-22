package dev.larpclient.modules.performance;

import dev.larpclient.core.module.Category;
import dev.larpclient.core.module.Module;
import dev.larpclient.core.setting.NumberSetting;

import java.util.concurrent.ThreadLocalRandom;

/** Randomly drops a share of new particles. Applied by {@code MixinEffectRenderer}. */
public final class ParticleLimiter extends Module {
    private static ParticleLimiter instance;
    private final NumberSetting amount = setting(new NumberSetting("Particle Amount", "Percentage of particles kept", 50, 0, 100, 5));

    public ParticleLimiter() {
        super("Particle Limiter", "Reduces particle count for higher FPS in fights", Category.PERFORMANCE);
        instance = this;
    }

    /** Whether a newly spawned particle should be discarded. */
    public static boolean shouldDrop() {
        ParticleLimiter p = instance;
        if (p == null || !p.isEnabled()) {
            return false;
        }
        double keep = p.amount.get() / 100.0;
        return keep < 1 && ThreadLocalRandom.current().nextDouble() >= keep;
    }
}
