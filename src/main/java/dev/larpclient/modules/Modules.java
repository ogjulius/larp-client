package dev.larpclient.modules;

import dev.larpclient.core.module.ModuleManager;
import dev.larpclient.modules.hud.ArmorStatusHud;
import dev.larpclient.modules.hud.ClockHud;
import dev.larpclient.modules.hud.ComboCounterHud;
import dev.larpclient.modules.hud.CoordinatesHud;
import dev.larpclient.modules.hud.CpsHud;
import dev.larpclient.modules.hud.DirectionHud;
import dev.larpclient.modules.hud.FpsHud;
import dev.larpclient.modules.hud.KeystrokesHud;
import dev.larpclient.modules.hud.MemoryHud;
import dev.larpclient.modules.hud.PingHud;
import dev.larpclient.modules.hud.PotionEffectsHud;
import dev.larpclient.modules.hud.ReachDisplayHud;
import dev.larpclient.modules.hud.ServerAddressHud;
import dev.larpclient.modules.performance.BackgroundFpsLimit;
import dev.larpclient.modules.performance.EntityCulling;
import dev.larpclient.modules.performance.ParticleLimiter;
import dev.larpclient.modules.utility.Freelook;
import dev.larpclient.modules.utility.Fullbright;
import dev.larpclient.modules.utility.NoHurtCam;
import dev.larpclient.modules.utility.ToggleSprint;
import dev.larpclient.modules.utility.Zoom;
import net.minecraft.client.settings.KeyBinding;

import java.util.Arrays;
import java.util.List;

/** Single place where every built-in module is registered. Add new modules here. */
public final class Modules {
    private Modules() {
    }

    public static void registerAll(ModuleManager m) {
        // HUD
        m.register(new FpsHud());
        m.register(new CpsHud());
        m.register(new KeystrokesHud());
        m.register(new ArmorStatusHud());
        m.register(new PotionEffectsHud());
        m.register(new CoordinatesHud());
        m.register(new DirectionHud());
        m.register(new PingHud());
        m.register(new ClockHud());
        m.register(new MemoryHud());
        m.register(new ComboCounterHud());
        m.register(new ReachDisplayHud());
        m.register(new ServerAddressHud());
        m.register(new ToggleSprint());
        // Utility
        m.register(new Zoom());
        m.register(new Freelook());
        m.register(new Fullbright());
        m.register(new NoHurtCam());
        // Performance
        m.register(new BackgroundFpsLimit());
        m.register(new ParticleLimiter());
        m.register(new EntityCulling());
    }

    /** Key bindings shown in vanilla Controls (hold-style features). */
    public static List<KeyBinding> keyBindings() {
        return Arrays.asList(Zoom.KEY, Freelook.KEY);
    }
}
