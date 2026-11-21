package dev.larpclient.core;

import dev.larpclient.core.event.EventBus;
import dev.larpclient.core.event.events.AttackEntityEvent;
import dev.larpclient.core.event.events.ClientTickEvent;
import dev.larpclient.core.event.events.FovEvent;
import dev.larpclient.core.event.events.KeyPressEvent;
import dev.larpclient.core.event.events.MouseClickEvent;
import dev.larpclient.core.event.events.MouseScrollEvent;
import dev.larpclient.core.event.events.Render2DEvent;
import dev.larpclient.core.event.events.WorldLoadEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Translates the Forge events we care about into LARP's own lightweight events. This is the only
 * place that registers on the Forge bus; everything else listens on {@link EventBus}.
 */
public final class ForgeEventBridge {
    private final EventBus bus;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Render2DEvent render2D = new Render2DEvent();
    private final FovEvent fovEvent = new FovEvent();

    public ForgeEventBridge(EventBus bus) {
        this.bus = bus;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && mc.theWorld != null && mc.thePlayer != null) {
            bus.post(ClientTickEvent.INSTANCE);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        render2D.partialTicks = event.partialTicks;
        render2D.scaledWidth = event.resolution.getScaledWidth();
        render2D.scaledHeight = event.resolution.getScaledHeight();
        bus.post(render2D);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (mc.currentScreen == null && Keyboard.getEventKeyState()) {
            int key = Keyboard.getEventKey();
            if (key > Keyboard.KEY_NONE && key < 256) {
                bus.post(new KeyPressEvent(key));
            }
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.button >= 0 && event.buttonstate) {
            bus.post(new MouseClickEvent(event.button));
        }
        if (event.dwheel != 0) {
            if (bus.post(new MouseScrollEvent(event.dwheel)).isCancelled()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (event.entityPlayer == mc.thePlayer) {
            bus.post(new AttackEntityEvent(event.target));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFov(EntityViewRenderEvent.FOVModifier event) {
        fovEvent.fov = event.getFOV();
        bus.post(fovEvent);
        event.setFOV(fovEvent.fov);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.world.isRemote) {
            bus.post(WorldLoadEvent.INSTANCE);
        }
    }
}
