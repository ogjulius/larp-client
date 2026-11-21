package dev.larpclient.core.setting;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @Test
    void numberClampsAndSnaps() {
        NumberSetting n = new NumberSetting("n", "", 1, 0, 2, 0.1);
        n.set(5.0);
        assertEquals(2.0, n.get());
        n.set(-1.0);
        assertEquals(0.0, n.get());
        n.set(0.34);
        assertEquals(0.3, n.get(), 1e-9);
        n.set(Double.NaN);
        assertEquals(0.3, n.get(), 1e-9);
    }

    @Test
    void numberPercentRoundTrip() {
        NumberSetting n = new NumberSetting("n", "", 50, 0, 100, 5);
        n.setPercent(0.5);
        assertEquals(50.0, n.get());
        assertEquals(0.5, n.getPercent(), 1e-9);
        assertEquals("50", n.format());
    }

    @Test
    void modeRejectsUnknownAndCycles() {
        ModeSetting m = new ModeSetting("m", "", "A", "A", "B", "C");
        m.set("Z");
        assertEquals("A", m.get());
        m.cycle(false);
        assertEquals("C", m.get());
        m.cycle(true);
        assertEquals("A", m.get());
    }

    @Test
    void modeDefaultMustBeOption() {
        assertThrows(IllegalArgumentException.class, () -> new ModeSetting("m", "", "X", "A"));
    }

    @Test
    void colorJsonRoundTripWithChroma() {
        ColorSetting c = new ColorSetting("c", "", 0x80FF0000);
        c.setChroma(true);
        ColorSetting copy = new ColorSetting("c", "", 0);
        copy.fromJson(c.toJson());
        assertEquals(0x80FF0000, copy.get());
        assertTrue(copy.isChroma());
    }

    @Test
    void colorIgnoresMalformedJson() {
        ColorSetting c = new ColorSetting("c", "", 0xFFFFFFFF);
        c.fromJson(new JsonPrimitive("not-a-colour"));
        c.fromJson(new JsonPrimitive(5));
        assertEquals(0xFFFFFFFF, c.get());
    }

    @Test
    void booleanIgnoresWrongType() {
        BooleanSetting b = new BooleanSetting("b", "", true);
        b.fromJson(new JsonPrimitive("false"));
        assertTrue(b.isOn());
        b.fromJson(new JsonPrimitive(false));
        assertFalse(b.isOn());
    }

    @Test
    void changeListenerFiresOnlyOnRealChange() {
        int[] calls = {0};
        BooleanSetting b = new BooleanSetting("b", "", false).onChange(v -> calls[0]++);
        b.set(false);
        b.set(true);
        b.set(true);
        assertEquals(1, calls[0]);
    }

    @Test
    void keyRejectsOutOfRange() {
        KeySetting k = new KeySetting("k", "", 0);
        k.set(300);
        assertEquals(0, k.get());
        k.set(42);
        assertEquals(42, k.get());
    }
}
