package dev.larpclient.core.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {
    static final class Ping {
        int hits;
    }

    static final class Other {
    }

    @Test
    void dispatchesToExactTypeOnly() {
        EventBus bus = new EventBus();
        bus.subscribe(Ping.class, e -> e.hits++);
        bus.subscribe(Other.class, e -> fail("wrong type"));
        assertEquals(1, bus.post(new Ping()).hits);
    }

    @Test
    void cancelUnsubscribes() {
        EventBus bus = new EventBus();
        Subscription sub = bus.subscribe(Ping.class, e -> e.hits++);
        sub.cancel();
        assertEquals(0, bus.post(new Ping()).hits);
        assertFalse(bus.hasListeners(Ping.class));
    }

    @Test
    void respectsPriorityOrder() {
        EventBus bus = new EventBus();
        List<String> order = new ArrayList<>();
        bus.subscribe(Ping.class, e -> order.add("low"), EventBus.Priority.LOW);
        bus.subscribe(Ping.class, e -> order.add("highest"), EventBus.Priority.HIGHEST);
        bus.subscribe(Ping.class, e -> order.add("normal"));
        bus.post(new Ping());
        assertEquals(java.util.Arrays.asList("highest", "normal", "low"), order);
    }

    @Test
    void listenerMayUnsubscribeWhileDispatching() {
        EventBus bus = new EventBus();
        Subscription[] holder = new Subscription[1];
        holder[0] = bus.subscribe(Ping.class, e -> {
            e.hits++;
            holder[0].cancel();
        });
        bus.post(new Ping());
        assertEquals(0, bus.post(new Ping()).hits);
    }
}
