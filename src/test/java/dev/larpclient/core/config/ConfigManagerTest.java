package dev.larpclient.core.config;

import dev.larpclient.core.event.EventBus;
import dev.larpclient.core.module.Category;
import dev.larpclient.core.module.Module;
import dev.larpclient.core.module.ModuleManager;
import dev.larpclient.core.setting.BooleanSetting;
import dev.larpclient.core.setting.NumberSetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
    static final class Sample extends Module {
        final BooleanSetting flag = setting(new BooleanSetting("Flag", "", false));
        final NumberSetting amount = setting(new NumberSetting("Amount", "", 5, 0, 10, 1));
        private final boolean defaultOn;

        Sample(String name, boolean defaultOn) {
            super(name, "", Category.UTILITY);
            this.defaultOn = defaultOn;
        }

        @Override
        public boolean enabledByDefault() {
            return defaultOn;
        }
    }

    private static ModuleManager manager(Sample... samples) {
        ModuleManager m = new ModuleManager(new EventBus());
        for (Sample s : samples) {
            m.register(s);
        }
        return m;
    }

    @Test
    void missingFileAppliesDefaults(@TempDir Path dir) {
        Sample on = new Sample("On", true);
        Sample off = new Sample("Off", false);
        new ConfigManager(dir.resolve("profile.json"), manager(on, off)).load();
        assertTrue(on.isEnabled());
        assertFalse(off.isEnabled());
    }

    @Test
    void saveThenLoadRestoresState(@TempDir Path dir) {
        Path file = dir.resolve("nested").resolve("profile.json");
        Sample a = new Sample("Alpha Mod", false);
        a.setEnabled(true);
        a.flag.set(true);
        a.amount.set(8.0);
        a.getToggleKey().set(33);
        new ConfigManager(file, manager(a)).save();
        assertTrue(Files.exists(file));

        Sample b = new Sample("Alpha Mod", false);
        new ConfigManager(file, manager(b)).load();
        assertTrue(b.isEnabled());
        assertTrue(b.flag.isOn());
        assertEquals(8.0, b.amount.get());
        assertEquals(33, b.getToggleKey().get());
    }

    @Test
    void newModuleNotInProfileGetsDefault(@TempDir Path dir) {
        Path file = dir.resolve("profile.json");
        new ConfigManager(file, manager(new Sample("Old", false))).save();
        Sample fresh = new Sample("Fresh", true);
        new ConfigManager(file, manager(new Sample("Old", false), fresh)).load();
        assertTrue(fresh.isEnabled());
    }

    @Test
    void corruptFileIsQuarantined(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("profile.json");
        Files.write(file, "{ this is not json".getBytes(StandardCharsets.UTF_8));
        Sample s = new Sample("S", true);
        new ConfigManager(file, manager(s)).load();
        assertTrue(s.isEnabled());
        assertTrue(Files.exists(dir.resolve("profile.json.broken")));
        assertFalse(Files.exists(file));
    }

    @Test
    void moduleIdIsStable() {
        assertEquals("alpha_mod", new Sample("Alpha Mod", false).getId());
    }
}
