package dev.larpclient.launcher;

import com.google.gson.JsonArray;
import dev.larpclient.launcher.auth.Account;
import dev.larpclient.launcher.game.Maven;
import dev.larpclient.launcher.game.Rules;
import dev.larpclient.launcher.util.Json;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LauncherLogicTest {
    @Test
    void mavenPaths() {
        assertEquals("net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar", Maven.path("net.minecraft:launchwrapper:1.12"));
        assertEquals("org/lwjgl/lwjgl/lwjgl-platform/2.9.4/lwjgl-platform-2.9.4-natives-linux.jar",
                Maven.path("org.lwjgl.lwjgl:lwjgl-platform:2.9.4:natives-linux"));
        assertEquals("a/b/c/1/c-1.zip", Maven.path("a.b:c:1@zip"));
        assertEquals("org.ow2.asm:asm-all", Maven.key("org.ow2.asm:asm-all:5.0.3"));
    }

    @Test
    void rulesFollowMojangSemantics() {
        JsonArray osxOnly = Json.GSON.fromJson("[{\"action\":\"allow\",\"os\":{\"name\":\"osx\"}}]", JsonArray.class);
        assertFalse(Rules.allowed(osxOnly, "linux", "6.0"));
        assertTrue(Rules.allowed(osxOnly, "osx", "10.15"));
        JsonArray allButOsx = Json.GSON.fromJson(
                "[{\"action\":\"allow\"},{\"action\":\"disallow\",\"os\":{\"name\":\"osx\"}}]", JsonArray.class);
        assertTrue(Rules.allowed(allButOsx, "linux", "6.0"));
        assertFalse(Rules.allowed(allButOsx, "osx", "10.15"));
        assertTrue(Rules.allowed(new JsonArray(), "linux", "6.0"));
    }

    @Test
    void offlineAccountsHaveStableVanillaUuid() {
        Account a = Account.offline("Notch");
        // Same algorithm as vanilla offline mode: UUID v3 of "OfflinePlayer:<name>".
        assertEquals("b50ad385829d3141a2167e7d7539ba7f", a.uuid);
        assertEquals("legacy", a.userType());
        assertTrue(Account.isValidUsername("Player_1"));
        assertFalse(Account.isValidUsername("ab"));
        assertFalse(Account.isValidUsername("has space"));
    }

    @Test
    void log4jXmlIsFormatted() throws Exception {
        List<String> out = new ArrayList<>();
        java.lang.reflect.Constructor<?> ctor = Class.forName("dev.larpclient.launcher.game.Log4jXmlFormatter")
                .getDeclaredConstructor(java.util.function.Consumer.class);
        ctor.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<String> f = (java.util.function.Consumer<String>) ctor.newInstance(
                (java.util.function.Consumer<String>) out::add);
        f.accept("plain line");
        f.accept("<log4j:Event logger=\"FML\" timestamp=\"0\" level=\"INFO\" thread=\"main\">");
        f.accept("  <log4j:Message><![CDATA[Hello world]]></log4j:Message>");
        f.accept("</log4j:Event>");
        assertEquals("plain line", out.get(0));
        assertTrue(out.get(1).endsWith("[main/INFO] (FML) Hello world"), out.get(1));
    }
}
