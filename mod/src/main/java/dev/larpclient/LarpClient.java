package dev.larpclient;

import dev.larpclient.core.ForgeEventBridge;
import dev.larpclient.core.config.ConfigManager;
import dev.larpclient.core.event.EventBus;
import dev.larpclient.core.event.events.ClientTickEvent;
import dev.larpclient.core.event.events.KeyPressEvent;
import dev.larpclient.core.event.events.MouseClickEvent;
import dev.larpclient.core.event.events.WorldLoadEvent;
import dev.larpclient.core.gui.ModMenuScreen;
import dev.larpclient.core.gui.menu.MenuReplacer;
import dev.larpclient.core.hud.HudRenderer;
import dev.larpclient.core.module.ModuleManager;
import dev.larpclient.core.music.MusicPlayer;
import dev.larpclient.core.util.CpsTracker;
import dev.larpclient.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

import java.nio.file.Path;

@Mod(
        modid = Reference.MOD_ID,
        name = Reference.NAME,
        version = Reference.VERSION,
        clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]"
)
public final class LarpClient {
    public static final Logger LOGGER = LogManager.getLogger(Reference.NAME);
    public static final String KEY_CATEGORY = Reference.NAME;

    private static LarpClient instance;

    private final EventBus events = new EventBus();
    private final ModuleManager modules = new ModuleManager(events);
    private final CpsTracker leftCps = new CpsTracker();
    private final CpsTracker rightCps = new CpsTracker();
    private ConfigManager config;
    private HudRenderer hud;
    private MusicPlayer music;
    private KeyBinding menuKey;

    public static LarpClient get() {
        return instance;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        instance = this;
        long start = System.nanoTime();
        Minecraft mc = Minecraft.getMinecraft();

        Modules.registerAll(modules);
        hud = new HudRenderer(modules, events);

        Path dataDir = mc.mcDataDir.toPath().resolve("config").resolve(Reference.MOD_ID);
        music = new MusicPlayer(dataDir.resolve("music.json"));
        music.load();
        config = new ConfigManager(dataDir.resolve("profile.json"), modules);
        config.load();

        menuKey = new KeyBinding("Open Menu", Keyboard.KEY_RSHIFT, KEY_CATEGORY);
        ClientRegistry.registerKeyBinding(menuKey);
        for (KeyBinding binding : Modules.keyBindings()) {
            ClientRegistry.registerKeyBinding(binding);
        }

        // Always-on core listeners.
        events.subscribe(MouseClickEvent.class, e -> {
            long now = System.currentTimeMillis();
            if (e.button == 0) {
                leftCps.click(now);
            } else if (e.button == 1) {
                rightCps.click(now);
            }
        });
        events.subscribe(KeyPressEvent.class, e -> modules.handleKey(e.keyCode));
        events.subscribe(ClientTickEvent.class, e -> {
            if (menuKey.isPressed()) {
                mc.displayGuiScreen(new ModMenuScreen());
            }
        });
        events.subscribe(WorldLoadEvent.class, e -> modules.enforcePolicies());

        MinecraftForge.EVENT_BUS.register(new ForgeEventBridge(events));
        MinecraftForge.EVENT_BUS.register(new MenuReplacer());
        MinecraftForge.EVENT_BUS.register(new dev.larpclient.core.gui.PauseMenuButton());
        new dev.larpclient.core.Onboarding(dataDir, events, menuKey, dev.larpclient.modules.utility.Zoom.KEY);
        dev.larpclient.core.dev.ScreenshotTour.installIfRequested();
        dev.larpclient.core.dev.FullscreenProbe.installIfRequested();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            config.save();
            music.save();
        }, "LARP config save"));

        LOGGER.info("{} {} initialised {} modules in {} ms", Reference.NAME, Reference.VERSION,
                modules.getModules().size(), (System.nanoTime() - start) / 1_000_000);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        Display.setTitle(Reference.NAME + " " + Reference.VERSION + " | Minecraft " + Reference.MC_VERSION);
        music.startup();
    }

    public EventBus events() {
        return events;
    }

    public ModuleManager modules() {
        return modules;
    }

    public ConfigManager config() {
        return config;
    }

    public MusicPlayer music() {
        return music;
    }

    public HudRenderer hud() {
        return hud;
    }

    public CpsTracker leftCps() {
        return leftCps;
    }

    public CpsTracker rightCps() {
        return rightCps;
    }
}
