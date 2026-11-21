package dev.larpclient.modules.performance;

import dev.larpclient.core.module.Category;
import dev.larpclient.core.module.Module;
import dev.larpclient.core.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;

/** Skips rendering entities past per-type distances. Applied by {@code MixinRenderManager}. */
public final class EntityCulling extends Module {
    private static EntityCulling instance;

    private final NumberSetting players = setting(new NumberSetting("Player Distance", "Max render distance for players (256 = unlimited)", 256, 16, 256, 8));
    private final NumberSetting mobs = setting(new NumberSetting("Mob Distance", "Max render distance for mobs and animals", 64, 16, 256, 8));
    private final NumberSetting misc = setting(new NumberSetting("Misc Distance", "Max render distance for items, orbs, armour stands etc.", 32, 8, 256, 8));

    public EntityCulling() {
        super("Entity Culling", "Stops rendering far-away entities", Category.PERFORMANCE);
        instance = this;
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    /** True if {@code entity} is too far from {@code viewer} to be worth drawing. */
    public static boolean shouldCull(Entity entity, Entity viewer) {
        EntityCulling c = instance;
        if (c == null || !c.isEnabled() || viewer == null || entity == viewer || entity == viewer.ridingEntity) {
            return false;
        }
        double max;
        if (entity instanceof EntityPlayer) {
            max = c.players.get();
            if (max >= 256) {
                return false;
            }
        } else if (entity instanceof EntityLivingBase && !(entity instanceof EntityArmorStand)) {
            max = c.mobs.get();
        } else {
            max = c.misc.get();
        }
        return entity.getDistanceSqToEntity(viewer) > max * max;
    }
}
