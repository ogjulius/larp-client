package dev.larpclient.modules.hud;

import dev.larpclient.core.event.events.AttackEntityEvent;
import dev.larpclient.core.hud.TextHudModule;
import dev.larpclient.core.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;

import java.util.Locale;

/** Shows the distance of your last hit (purely informational). */
public final class ReachDisplayHud extends TextHudModule {
    private final NumberSetting timeout = setting(new NumberSetting("Hide After", "Seconds before the value disappears", 3, 1, 10, 1));
    private double lastReach = -1;
    private long lastHit;

    public ReachDisplayHud() {
        super("Reach Display", "Shows how far away your last hit landed", 0.0, 0.7);
        on(AttackEntityEvent.class, this::onAttack);
    }

    private void onAttack(AttackEntityEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && hit.entityHit == event.target && hit.hitVec != null) {
            lastReach = mc.thePlayer.getPositionEyes(1f).distanceTo(hit.hitVec);
            lastHit = System.currentTimeMillis();
        }
    }

    @Override
    protected String getText() {
        if (lastReach < 0 || System.currentTimeMillis() - lastHit > timeout.get() * 1000) {
            return null;
        }
        return String.format(Locale.ROOT, "%.2f blocks", lastReach);
    }

    @Override
    protected String getPlaceholder() {
        return "3.00 blocks";
    }
}
