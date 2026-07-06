package pl.skidam.automodpack_core.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressPinsTest {

    private static final String FP = "72e38cadf4ee70e7e123000cd946d9f6edfde5f6ff722b482550f1ef78f90b9b";

    @Test
    void extractsPinAndStripsSuffix() {
        String clean = AddressPins.extractAndStore("play.example.com;" + FP);
        assertEquals("play.example.com", clean);
        assertEquals(FP, AddressPins.getPin("play.example.com").orElseThrow());
        // lookup is case-insensitive on host
        assertEquals(FP, AddressPins.getPin("PLAY.example.com").orElseThrow());
    }

    @Test
    void extractsPinWithPort() {
        String clean = AddressPins.extractAndStore("mc.pinport.net:25566;" + FP);
        assertEquals("mc.pinport.net:25566", clean);
        assertEquals(FP, AddressPins.getPin("mc.pinport.net").orElseThrow());
    }

    @Test
    void acceptsColonSeparatedFingerprint() {
        StringBuilder pretty = new StringBuilder();
        for (int i = 0; i < FP.length(); i += 2) {
            if (i > 0) pretty.append(':');
            pretty.append(FP, i, i + 2);
        }
        String clean = AddressPins.extractAndStore("mc.colons.net;" + pretty.toString().toUpperCase());
        assertEquals("mc.colons.net", clean);
        assertEquals(FP, AddressPins.getPin("mc.colons.net").orElseThrow());
    }

    @Test
    void leavesAddressesWithoutPinUntouched() {
        assertEquals("play.example.com", AddressPins.extractAndStore("play.example.com"));
        assertEquals("play.example.com:25565", AddressPins.extractAndStore("play.example.com:25565"));
        assertNull(AddressPins.extractAndStore(null));
    }

    @Test
    void rejectsInvalidFingerprints() {
        // wrong length
        assertEquals("mc.bad.net;abcdef", AddressPins.extractAndStore("mc.bad.net;abcdef"));
        // non-hex chars
        String nonHex = FP.substring(0, 63) + "g";
        assertEquals("mc.bad.net;" + nonHex, AddressPins.extractAndStore("mc.bad.net;" + nonHex));
        // empty host
        assertEquals(";" + FP, AddressPins.extractAndStore(";" + FP));
        assertTrue(AddressPins.getPin("mc.bad.net").isEmpty());
    }

    @Test
    void unknownHostHasNoPin() {
        assertTrue(AddressPins.getPin("never-pinned.example.org").isEmpty());
        assertTrue(AddressPins.getPin(null).isEmpty());
    }
}
