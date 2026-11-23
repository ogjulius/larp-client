package dev.larpclient.core.module;

import dev.larpclient.core.event.EventBus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModuleTest {
    static final class Tick {
    }

    static final class Counter extends Module {
        int ticks;
        int enables;
        boolean blocked;

        Counter() {
            super("Counter", "", Category.UTILITY);
            on(Tick.class, e -> ticks++);
        }

        @Override
        protected void onEnable() {
            enables++;
        }

        @Override
        public String blockedReason() {
            return blocked ? "nope" : null;
        }
    }

    @Test
    void handlersOnlyActiveWhileEnabled() {
        EventBus bus = new EventBus();
        Counter c = new ModuleManager(bus).register(new Counter());
        bus.post(new Tick());
        assertEquals(0, c.ticks);
        c.setEnabled(true);
        bus.post(new Tick());
        c.setEnabled(false);
        bus.post(new Tick());
        assertEquals(1, c.ticks);
        assertFalse(bus.hasListeners(Tick.class));
    }

    @Test
    void enableIsIdempotent() {
        Counter c = new ModuleManager(new EventBus()).register(new Counter());
        c.setEnabled(true);
        c.setEnabled(true);
        assertEquals(1, c.enables);
    }

    @Test
    void blockedModulesCannotEnableAndAreEnforced() {
        ModuleManager m = new ModuleManager(new EventBus());
        Counter c = m.register(new Counter());
        c.setEnabled(true);
        c.blocked = true;
        m.enforcePolicies();
        assertFalse(c.isEnabled());
        c.setEnabled(true);
        assertFalse(c.isEnabled());
    }

    @Test
    void keyTogglesBoundModules() {
        ModuleManager m = new ModuleManager(new EventBus());
        Counter c = m.register(new Counter());
        c.getToggleKey().set(20);
        m.handleKey(20);
        assertTrue(c.isEnabled());
        m.handleKey(21);
        assertTrue(c.isEnabled());
        m.handleKey(20);
        assertFalse(c.isEnabled());
    }

    @Test
    void duplicateIdsRejected() {
        ModuleManager m = new ModuleManager(new EventBus());
        m.register(new Counter());
        assertThrows(IllegalStateException.class, () -> m.register(new Counter()));
    }

    /** Regression (audit A14): a policy-disabled module comes back when the policy no longer applies. */
    @Test
    void policySuspensionIsTemporary() {
        ModuleManager m = new ModuleManager(new EventBus());
        Counter c = m.register(new Counter());
        c.setEnabled(true);
        c.blocked = true;      // join a server that disallows it
        m.enforcePolicies();
        assertFalse(c.isEnabled());
        assertTrue(c.isWantedEnabled(), "the user's choice is remembered");
        c.blocked = false;     // join a server that allows it
        m.enforcePolicies();
        assertTrue(c.isEnabled());
    }

    @Test
    void explicitChangeWhileSuspendedOverridesTheRememberedChoice() {
        ModuleManager m = new ModuleManager(new EventBus());
        Counter c = m.register(new Counter());
        c.setEnabled(true);
        c.blocked = true;
        m.enforcePolicies();
        c.setEnabled(false);   // the user switches it off on purpose
        c.blocked = false;
        m.enforcePolicies();
        assertFalse(c.isEnabled());
        assertFalse(c.isWantedEnabled());
    }
}
