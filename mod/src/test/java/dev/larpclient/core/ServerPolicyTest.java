package dev.larpclient.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerPolicyTest {
    @Test
    void matchesDomainsSubdomainsAndPorts() {
        assertTrue(ServerPolicy.matches("hypixel.net", "hypixel.net"));
        assertTrue(ServerPolicy.matches("mc.HYPIXEL.net:25565", "hypixel.net"));
        assertTrue(ServerPolicy.matches("hypixel.net.", "hypixel.net"));
        assertFalse(ServerPolicy.matches("nothypixel.net", "hypixel.net"));
        assertFalse(ServerPolicy.matches("", "hypixel.net"));
        assertFalse(ServerPolicy.matches(null, "hypixel.net"));
    }
}
