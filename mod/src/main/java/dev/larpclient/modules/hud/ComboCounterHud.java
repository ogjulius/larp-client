package dev.larpclient.modules.hud;

import dev.larpclient.core.event.events.AttackEntityEvent;
import dev.larpclient.core.event.events.ClientTickEvent;
import dev.larpclient.core.hud.TextHudModule;
import dev.larpclient.core.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/** Counts consecutive hits you land without taking damage. */
public final class ComboCounterHud extends TextHudModule {
    private static final long RESET_MS = 2000;

    private final BooleanSetting hideZero = setting(new BooleanSetting("Hide At Zero", "Hide when you have no combo", true));

    private EntityLivingBase target;
    private int combo;
    private long lastHit;
    private boolean awaitingHurt;

    public ComboCounterHud() {
        super("Combo Counter", "Counts consecutive hits without being hit back", 0.0, 0.82);
        on(AttackEntityEvent.class, this::onAttack);
        on(ClientTickEvent.class, e -> onTick());
    }

    private void onAttack(AttackEntityEvent event) {
        Entity hit = event.target;
        if (hit instanceof EntityLivingBase) {
            if (hit != target) {
                combo = 0;
            }
            target = (EntityLivingBase) hit;
            awaitingHurt = true;
        }
    }

    private void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.currentTimeMillis();
        if (mc.thePlayer.hurtTime == mc.thePlayer.maxHurtTime && mc.thePlayer.maxHurtTime > 0) {
            combo = 0; // we took a hit
        }
        if (target != null) {
            // The server confirmed our hit when the target starts its hurt animation.
            if (awaitingHurt && target.hurtTime == target.maxHurtTime && target.maxHurtTime > 0) {
                combo++;
                lastHit = now;
                awaitingHurt = false;
            }
            if (target.isDead) {
                target = null;
            }
        }
        if (combo > 0 && now - lastHit > RESET_MS) {
            combo = 0;
        }
    }

    @Override
    protected void onDisable() {
        target = null;
        combo = 0;
    }

    @Override
    protected String getText() {
        if (combo == 0 && hideZero.isOn()) {
            return null;
        }
        return combo == 1 ? "1 Combo" : combo + " Combo";
    }

    @Override
    protected String getPlaceholder() {
        return "3 Combo";
    }
}
