package com.golda.patchertiktok;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TombstoneTest {
    /** A tiny protobuf writer, enough to build a tombstone the way debuggerd lays it out. */
    private static final class Proto {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Proto varint(int field, long value) {
            tag(field, 0);
            raw(value);
            return this;
        }

        Proto bytes(int field, byte[] value) {
            tag(field, 2);
            raw(value.length);
            out.write(value, 0, value.length);
            return this;
        }

        Proto string(int field, String value) { return bytes(field, value.getBytes(StandardCharsets.UTF_8)); }

        Proto message(int field, Proto value) { return bytes(field, value.out.toByteArray()); }

        Proto fixed64(int field) {
            tag(field, 1);
            out.write(new byte[8], 0, 8);
            return this;
        }

        private void tag(int field, int wire) { raw((long) field << 3 | wire); }

        private void raw(long value) {
            while ((value & ~0x7fL) != 0) {
                out.write((int) (value & 0x7f) | 0x80);
                value >>>= 7;
            }
            out.write((int) value);
        }

        byte[] build() { return out.toByteArray(); }
    }

    private static Proto frame(long pc, String function, long offset, String file) {
        Proto frame = new Proto().varint(1, pc).varint(2, pc + 0x7000000000L).varint(3, 0x7fff0000L);
        if (function != null) frame.string(4, function).varint(5, offset);
        return frame.string(6, file).string(8, "abcdef");
    }

    private static Proto thread(int id, String name, Proto... frames) {
        Proto thread = new Proto().varint(1, id).string(2, name)
                .message(3, new Proto().string(1, "x0").varint(2, 0x1234));
        for (Proto frame : frames) thread.message(4, frame);
        // A memory dump, which must be skipped.
        return thread.message(5, new Proto().string(1, "x0").bytes(4, "secret chat text".getBytes(StandardCharsets.UTF_8)));
    }

    private static Proto entry(int id, Proto thread) {
        return new Proto().varint(1, id).message(2, thread);
    }

    @Test public void readsTheCrashingThreadOnly() {
        byte[] data = new Proto()
                .varint(1, 1).string(2, "nubia/NX809J:14").varint(5, 1000).varint(6, 1002)
                .message(10, new Proto().varint(1, 11).string(2, "SIGSEGV").varint(3, 1).string(4, "SEGV_MAPERR")
                        .varint(8, 1).varint(9, 0x18))
                .string(14, "")
                .message(15, new Proto().string(1, "null pointer dereference"))
                .message(16, entry(1000, thread(1000, "main",
                        frame(0x100, "pthread_cond_wait", 4, "/apex/com.android.runtime/lib64/bionic/libc.so"))))
                .message(16, entry(1002, thread(1002, "BPEAThread-3",
                        frame(0x2a4c, null, 0, "/data/app/~~x/com.ss.android.ugc.trill-y/lib/arm64/libpns_ttmachine_adapter.so"),
                        frame(0x9f0, "art_quick_generic_jni_trampoline", 144, "/apex/com.android.art/lib64/libart.so"))))
                .fixed64(22)
                .message(18, new Proto().string(1, "main").string(2, "logcat line with user data"))
                .build();
        String text = Tombstone.read(data, data.length);
        assertTrue(text, text.contains("signal SIGSEGV (SEGV_MAPERR), fault address 0x18"));
        assertTrue(text, text.contains("cause: null pointer dereference"));
        assertTrue(text, text.contains("crashed thread: BPEAThread-3 (1002)"));
        assertTrue(text, text.contains("#00 pc 00002a4c  libpns_ttmachine_adapter.so\n"));
        assertTrue(text, text.contains("#01 pc 000009f0  libart.so (art_quick_generic_jni_trampoline+144)"));
        assertFalse(text, text.contains("pthread_cond_wait"));
        assertFalse(text, text.contains("secret"));
        assertFalse(text, text.contains("logcat"));
        assertFalse(text, text.contains("/data/app"));
    }

    @Test public void readsTheCrashingThreadOfATextTombstone() {
        String record = "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***\n"
                + "Build fingerprint: 'x'\n"
                + "pid: 4321, tid: 4330, name: RenderThread  >>> com.ss.android.ugc.trill <<<\n"
                + "signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0\n"
                + "    x0  0000000000000000  x1  0000007a\n"
                + "backtrace:\n"
                + "      #00 pc 0000000000012345  /data/app/~~a/com.ss.android.ugc.trill-b/lib/arm64/libfoo.so (bar+8)\n"
                + "--- --- --- --- --- --- --- --- --- --- --- --- --- --- --- ---\n"
                + "      #00 pc 0000000000000001  /apex/libc.so (other)\n";
        byte[] data = record.getBytes(StandardCharsets.UTF_8);
        String text = Tombstone.read(data, data.length);
        assertTrue(text, text.contains("signal 11 (SIGSEGV)"));
        assertTrue(text, text.contains("#00 pc 0000000000012345  libfoo.so (bar+8)"));
        assertFalse(text, text.contains("other"));
        assertFalse(text, text.contains("0000007a"));
    }

    @Test public void aRecordCutOffByTheBufferStillGivesTheCrash() {
        Proto head = new Proto().varint(6, 7)
                .message(10, new Proto().varint(1, 11).string(2, "SIGSEGV").string(4, "SEGV_MAPERR"))
                .message(16, entry(7, thread(7, "RenderThread", frame(0x10, "draw", 4, "/system/lib64/libhwui.so"))));
        byte[] data = head.message(17, new Proto().bytes(1, new byte[4096])).build();
        // The buffer ended inside the memory map that follows the threads.
        String text = Tombstone.read(data, data.length - 1000);
        assertTrue(text, text.contains("crashed thread: RenderThread (7)"));
        assertTrue(text, text.contains("libhwui.so (draw+4)"));
    }

    @Test public void aRecordCutBeforeTheThreadKeepsTheSignal() {
        byte[] data = new Proto().varint(6, 7)
                .message(10, new Proto().varint(1, 11).string(2, "SIGSEGV"))
                .message(16, entry(7, thread(7, "main", frame(0x10, "f", 0, "libc.so")))).build();
        String text = Tombstone.read(data, data.length - 5);
        assertTrue(text, text.contains("signal SIGSEGV"));
        assertTrue(text, text.contains("crashing thread not in the record"));
    }

    @Test public void randomDataNeverThrows() {
        java.util.Random random = new java.util.Random(11);
        byte[] good = new Proto().varint(6, 7)
                .message(10, new Proto().varint(1, 11).string(2, "SIGSEGV").varint(8, 1).varint(9, 24))
                .message(15, new Proto().string(1, "cause"))
                .message(16, entry(7, thread(7, "main", frame(0x10, "f", 0, "libc.so"), frame(0x20, null, 0, "libart.so")))).build();
        for (int round = 0; round < 200_000; round++) {
            byte[] data = good.clone();
            for (int flips = 1 + random.nextInt(6); flips > 0; flips--) data[random.nextInt(data.length)] = (byte) random.nextInt(256);
            Tombstone.read(data, 1 + random.nextInt(data.length));
        }
        for (int round = 0; round < 50_000; round++) {
            byte[] data = new byte[random.nextInt(300) + 1];
            random.nextBytes(data);
            if (round % 2 == 0) System.arraycopy("*** ***".getBytes(StandardCharsets.US_ASCII), 0, data, 0, Math.min(7, data.length));
            Tombstone.read(data, data.length);
        }
    }

    @Test public void rejectsData() {
        byte[] junk = {(byte) 0xff, (byte) 0xff, (byte) 0xff};
        assertNull(Tombstone.read(junk, junk.length));
        assertNull(Tombstone.read(new byte[0], 0));
        assertEquals("libc.so", Tombstone.shortFile("/apex/com.android.runtime/lib64/bionic/libc.so"));
    }
}
