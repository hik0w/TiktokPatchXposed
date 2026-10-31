package com.golda.patchertiktok;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the part of an Android native crash record ("tombstone") that tells where the crash
 * happened: the signal and fault address, the abort message and causes, and the backtrace of the
 * thread that crashed, as library and function names. Android 12+ writes the record as protobuf
 * (AOSP system/core/debuggerd/proto/tombstone.proto); Android 11 writes text. Register values,
 * memory dumps, logcat and open files are skipped: they say little here and may hold user data.
 */
final class Tombstone {
    /** Frames of the crashing thread kept in the report. */
    static final int MAX_FRAMES = 40;

    private Tombstone() { }

    /** The readable summary, or null if the data is neither form. */
    static String read(byte[] data, int length) {
        if (length <= 0) return null;
        String text = text(data, length);
        if (text != null) return text;
        try {
            return proto(data, length);
        } catch (RuntimeException broken) {
            return null;
        }
    }

    // ---- Android 12+: protobuf ---------------------------------------------------------------

    private static String proto(byte[] data, int length) {
        Reader top = new Reader(data, 0, length);
        long tid = -1;
        String abort = null;
        int[] signal = null;
        List<String> causes = new ArrayList<>();
        List<int[]> threads = new ArrayList<>();
        while (top.more()) {
            int key = (int) top.varint();
            int field = key >>> 3, wire = key & 7;
            if (field == 6 && wire == 0) tid = top.varint();
            else if (field == 10 && wire == 2) signal = top.range();
            else if (field == 14 && wire == 2) abort = top.string();
            else if (field == 15 && wire == 2) {
                Reader cause = top.sub();
                while (cause.more()) {
                    int k = (int) cause.varint();
                    if (k >>> 3 == 1 && (k & 7) == 2) causes.add(cause.string());
                    else cause.skip(k & 7);
                }
            } else if (field == 16 && wire == 2) threads.add(top.range());
            else top.skip(wire);
        }
        StringBuilder out = new StringBuilder();
        if (signal != null) signal(new Reader(data, signal[0], signal[1]), out);
        if (abort != null && !abort.isEmpty()) out.append("abort: ").append(abort).append('\n');
        for (String cause : causes) out.append("cause: ").append(cause).append('\n');
        boolean found = false;
        for (int[] entry : threads) {
            Reader pair = new Reader(data, entry[0], entry[1]);
            long id = -1;
            int[] thread = null;
            while (pair.more()) {
                int k = (int) pair.varint();
                if (k >>> 3 == 1 && (k & 7) == 0) id = pair.varint();
                else if (k >>> 3 == 2 && (k & 7) == 2) thread = pair.range();
                else pair.skip(k & 7);
            }
            if (thread == null || (tid >= 0 && id != tid)) continue;
            thread(new Reader(data, thread[0], thread[1]), id, out);
            found = true;
            break;
        }
        if (!found && out.length() == 0) return null;
        if (!found) out.append("(crashing thread not in the record)\n");
        return out.toString();
    }

    private static void signal(Reader in, StringBuilder out) {
        String name = null, codeName = null;
        long number = 0, code = 0, fault = 0;
        boolean hasFault = false;
        while (in.more()) {
            int k = (int) in.varint();
            int field = k >>> 3, wire = k & 7;
            if (field == 1 && wire == 0) number = in.varint();
            else if (field == 2 && wire == 2) name = in.string();
            else if (field == 3 && wire == 0) code = in.varint();
            else if (field == 4 && wire == 2) codeName = in.string();
            else if (field == 8 && wire == 0) hasFault = in.varint() != 0;
            else if (field == 9 && wire == 0) fault = in.varint();
            else in.skip(wire);
        }
        out.append("signal ").append(name != null ? name : String.valueOf(number))
                .append(" (").append(codeName != null ? codeName : String.valueOf((int) code)).append(')');
        if (hasFault) out.append(", fault address 0x").append(Long.toHexString(fault));
        out.append('\n');
    }

    private static void thread(Reader in, long id, StringBuilder out) {
        String name = null;
        List<String> frames = new ArrayList<>();
        int total = 0;
        while (in.more()) {
            int k = (int) in.varint();
            int field = k >>> 3, wire = k & 7;
            if (field == 2 && wire == 2) name = in.string();
            else if (field == 4 && wire == 2) {
                Reader frame = in.sub();
                if (total++ < MAX_FRAMES) frames.add(frame(frame, frames.size()));
            } else in.skip(wire);
        }
        out.append("crashed thread: ").append(name == null ? "?" : name).append(" (").append(id).append(")\n");
        for (String frame : frames) out.append(frame).append('\n');
        if (total > frames.size()) out.append("  … ").append(total - frames.size()).append(" more frames\n");
    }

    private static String frame(Reader in, int index) {
        long pc = 0, offset = 0;
        String function = null, file = null;
        while (in.more()) {
            int k = (int) in.varint();
            int field = k >>> 3, wire = k & 7;
            if (field == 1 && wire == 0) pc = in.varint();
            else if (field == 4 && wire == 2) function = in.string();
            else if (field == 5 && wire == 0) offset = in.varint();
            else if (field == 6 && wire == 2) file = in.string();
            else in.skip(wire);
        }
        StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "  #%02d pc %08x  %s", index, pc, shortFile(file)));
        if (function != null && !function.isEmpty()) line.append(" (").append(function).append('+').append(offset).append(')');
        return line.toString();
    }

    /** The library or APK name without TikTok's install path, which differs per phone. */
    static String shortFile(String file) {
        if (file == null || file.isEmpty()) return "?";
        int slash = file.lastIndexOf('/');
        return slash < 0 ? file : file.substring(slash + 1);
    }

    /** Minimal protobuf reader over one message's bytes. */
    private static final class Reader {
        private final byte[] data;
        private int at;
        private final int end;

        Reader(byte[] data, int from, int to) {
            this.data = data;
            this.at = from;
            this.end = Math.min(to, data.length);
        }

        boolean more() { return at < end; }

        long varint() {
            long value = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (at >= end) throw new IllegalStateException("truncated varint");
                byte b = data[at++];
                value |= (long) (b & 0x7f) << shift;
                if ((b & 0x80) == 0) return value;
            }
            throw new IllegalStateException("bad varint");
        }

        /** The [from, to) bytes of a length-delimited field. */
        int[] range() {
            long length = varint();
            if (length < 0 || length > end - at) throw new IllegalStateException("bad length");
            int[] range = {at, at + (int) length};
            at += (int) length;
            return range;
        }

        Reader sub() {
            int[] range = range();
            return new Reader(data, range[0], range[1]);
        }

        String string() {
            int[] range = range();
            return new String(data, range[0], range[1] - range[0], StandardCharsets.UTF_8);
        }

        void skip(int wire) {
            switch (wire) {
                case 0: varint(); break;
                case 1: at += 8; break;
                case 2: range(); break;
                case 5: at += 4; break;
                default: throw new IllegalStateException("wire type " + wire);
            }
            if (at > end) throw new IllegalStateException("truncated field");
        }
    }

    // ---- Android 11: text --------------------------------------------------------------------

    /**
     * A text tombstone starts with the crashing thread; its signal, abort and backtrace lines come
     * before the first other thread ("--- --- ---").
     */
    private static String text(byte[] data, int length) {
        String head = new String(data, 0, Math.min(length, 64), StandardCharsets.US_ASCII);
        if (!head.startsWith("*** ***") && !head.startsWith("Build fingerprint")) return null;
        String all = new String(data, 0, length, StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder();
        int frames = 0;
        for (String line : all.split("\n")) {
            if (line.startsWith("--- --- ---")) break;
            String trimmed = line.trim();
            if (trimmed.startsWith("pid:") || trimmed.startsWith("signal ") || trimmed.startsWith("Abort message")
                    || trimmed.startsWith("Cause:")) {
                out.append(trimmed).append('\n');
            } else if (trimmed.startsWith("#") && frames < MAX_FRAMES) {
                out.append("  ").append(trimmed.replaceAll("/data/app/\\S*/", "")).append('\n');
                frames++;
            }
        }
        return out.length() == 0 ? null : out.toString();
    }
}
