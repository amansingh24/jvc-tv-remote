package uk.aman.jvcremote;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JVC TV code sets and the IR protocol encoders that turn them into ConsumerIrManager patterns. */
final class IrCodes {

    enum Protocol { RC5, SAMSUNG32, JVC }

    static final class CodeSet {
        final String name;
        final Protocol protocol;
        final int address;
        final Map<String, Integer> commands;

        CodeSet(String name, Protocol protocol, int address, Map<String, Integer> commands) {
            this.name = name;
            this.protocol = protocol;
            this.address = address;
            this.commands = commands;
        }

        int frequency() {
            return protocol == Protocol.RC5 ? 36000 : 38000;
        }

        /** Number of possible command codes, for stepping through them in the button finder. */
        int commandRange() {
            return protocol == Protocol.RC5 ? 128 : 256;
        }

        /** Microsecond on/off pattern starting with "on". */
        int[] pattern(int command, boolean toggle) {
            switch (protocol) {
                case RC5: return rc5(address, command, toggle);
                case SAMSUNG32: return samsung32(address, command);
                default: return jvc(address, command);
            }
        }
    }

    // Codes from the Flipper-IRDB project (TVs/JVC, TVs/Technika, TVs/Hitachi).
    // UK JVC TVs are made by Vestel, whose remotes use RC5; the address varies by model.
    private static Map<String, Integer> vestelRc5() {
        Map<String, Integer> m = new HashMap<>();
        m.put("Power", 0x0C); m.put("Source", 0x38); m.put("Menu", 0x30); m.put("Home", 0x2E);
        m.put("Back", 0x0A); m.put("Exit", 0x25); m.put("Up", 0x14); m.put("Down", 0x13);
        m.put("Left", 0x15); m.put("Right", 0x16); m.put("OK", 0x35); m.put("Vol+", 0x10);
        m.put("Vol-", 0x11); m.put("Mute", 0x0D); m.put("Ch+", 0x20); m.put("Ch-", 0x21);
        for (int i = 0; i <= 9; i++) m.put(String.valueOf(i), i);
        return m;
    }

    private static Map<String, Integer> jvcSamsung() {
        Map<String, Integer> m = new HashMap<>();
        m.put("Power", 0x0C); m.put("Source", 0x0F); m.put("Menu", 0x11); m.put("Home", 0x78);
        m.put("Back", 0xBB); m.put("Exit", 0xB5); m.put("Up", 0x42); m.put("Down", 0x43);
        m.put("Left", 0x44); m.put("Right", 0x45); m.put("OK", 0x46); m.put("Vol+", 0x14);
        m.put("Vol-", 0x15); m.put("Mute", 0x0D); m.put("Ch+", 0x12); m.put("Ch-", 0x13);
        for (int i = 0; i <= 9; i++) m.put(String.valueOf(i), i);
        return m;
    }

    private static Map<String, Integer> jvcClassic() {
        Map<String, Integer> m = new HashMap<>();
        m.put("Power", 0x17); m.put("Source", 0x13); m.put("Menu", 0x7A); m.put("Back", 0xE6);
        m.put("Up", 0x7C); m.put("Down", 0x7B); m.put("Left", 0x5B); m.put("Right", 0x5A);
        m.put("OK", 0x0A); m.put("Vol+", 0x1E); m.put("Vol-", 0x1F); m.put("Mute", 0x1C);
        m.put("Ch+", 0x19); m.put("Ch-", 0x18);
        for (int i = 0; i <= 9; i++) m.put(String.valueOf(i), 0x20 + i);
        return m;
    }

    static final CodeSet[] SETS = {
        new CodeSet("Vestel-made JVC (most UK models)", Protocol.RC5, 0x01, vestelRc5()),
        new CodeSet("Vestel-made, alternative address", Protocol.RC5, 0x03, vestelRc5()),
        new CodeSet("JVC LT series (Samsung-style codes)", Protocol.SAMSUNG32, 0x0E, jvcSamsung()),
        new CodeSet("Classic JVC (older JVC-made TVs)", Protocol.JVC, 0x03, jvcClassic()),
    };

    // --- Encoders -------------------------------------------------------------------------

    /** Philips RC5: 14 Manchester-coded bits of 1.778ms, 36kHz carrier. */
    static int[] rc5(int address, int command, boolean toggle) {
        final int half = 889;
        int field = command < 64 ? 1 : 0; // second start bit doubles as command bit 6, inverted
        int frame = (1 << 13) | (field << 12) | ((toggle ? 1 : 0) << 11)
                | ((address & 0x1F) << 6) | (command & 0x3F);

        // Each bit is two half-bit levels: 1 = off,on and 0 = on,off.
        boolean[] levels = new boolean[28];
        for (int i = 0; i < 14; i++) {
            boolean one = ((frame >> (13 - i)) & 1) == 1;
            levels[2 * i] = !one;
            levels[2 * i + 1] = one;
        }

        List<Integer> out = new ArrayList<>();
        int start = levels[0] ? 0 : 1; // the pattern must begin with "on"
        boolean current = levels[start];
        int run = 0;
        for (int i = start; i < levels.length; i++) {
            if (levels[i] == current) {
                run += half;
            } else {
                out.add(run);
                current = levels[i];
                run = half;
            }
        }
        if (current) out.add(run); // drop a trailing "off"
        return toArray(out);
    }

    /** Samsung32: 4.5ms header, then address, address, command, ~command, LSB first. */
    static int[] samsung32(int address, int command) {
        List<Integer> out = new ArrayList<>();
        out.add(4500);
        out.add(4500);
        int[] bytes = {address & 0xFF, address & 0xFF, command & 0xFF, ~command & 0xFF};
        for (int b : bytes) appendBits(out, b, 8, 560, 560, 1690);
        out.add(560);
        return toArray(out);
    }

    /** JVC: 8.4ms header, address then command LSB first; repeated once without the header,
     *  as the original remote does on a short press. */
    static int[] jvc(int address, int command) {
        List<Integer> out = new ArrayList<>();
        out.add(8400);
        out.add(4200);
        int data = (address & 0xFF) | ((command & 0xFF) << 8);
        appendBits(out, data, 16, 526, 526, 1574);
        out.add(526);
        out.add(22000);
        appendBits(out, data, 16, 526, 526, 1574);
        out.add(526);
        return toArray(out);
    }

    private static void appendBits(List<Integer> out, int value, int count, int mark, int zero, int one) {
        for (int i = 0; i < count; i++) {
            out.add(mark);
            out.add(((value >> i) & 1) == 1 ? one : zero);
        }
    }

    private static int[] toArray(List<Integer> list) {
        int[] a = new int[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }

    private IrCodes() {}
}
