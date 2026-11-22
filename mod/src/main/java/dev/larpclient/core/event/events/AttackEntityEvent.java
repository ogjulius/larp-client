package dev.larpclient.core.event.events;

import net.minecraft.entity.Entity;

/** Posted when the local player attacks an entity. */
public final class AttackEntityEvent {
    public final Entity target;

    public AttackEntityEvent(Entity target) {
        this.target = target;
    }
}
