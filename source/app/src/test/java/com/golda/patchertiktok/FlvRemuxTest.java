package com.golda.patchertiktok;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlvRemuxTest {
    private static byte[] flv(byte[]... tags) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{'F', 'L', 'V', 1, 5, 0, 0, 0, 9, 0, 0, 0, 0});
        for (byte[] tag : tags) {
            out.write(new byte[]{9, 0, 0, (byte) tag.length, 0, 0, 40, 0, 0, 0, 0});
            out.write(tag);
            out.write(new byte[4]);
        }
        return out.toByteArray();
    }

    @Test public void readsTagsAndStopsAtACutOffOne() throws IOException {
        byte[] data = flv(new byte[]{1, 2, 3}, new byte[]{4, 5});
        FlvRemux.Tag tag = new FlvRemux.Tag();
        try (FlvRemux.Reader reader = new FlvRemux.Reader(new ByteArrayInputStream(data))) {
            assertTrue(reader.next(tag));
            assertEquals(3, tag.size);
            assertEquals(40, tag.timestamp);
            assertTrue(reader.next(tag));
            assertEquals(5, tag.data[1]);
            assertFalse(reader.next(tag));
        }
        byte[] cut = java.util.Arrays.copyOf(data, data.length - 6);
        try (FlvRemux.Reader reader = new FlvRemux.Reader(new ByteArrayInputStream(cut))) {
            assertTrue(reader.next(tag));
            assertFalse(reader.next(tag));
        }
    }

    @Test(expected = IOException.class) public void refusesWhatIsNotFlv() throws IOException {
        new FlvRemux.Reader(new ByteArrayInputStream(new byte[32])).close();
    }

    @Test public void startCodesReplaceLengths() {
        byte[] in = {0, 0, 0, 2, 7, 8, 0, 0, 0, 1, 9};
        byte[] out = new byte[64];
        int size = FlvRemux.annexB(in, 0, in.length, 4, out);
        assertArrayEquals(new byte[]{0, 0, 0, 1, 7, 8, 0, 0, 0, 1, 9}, java.util.Arrays.copyOf(out, size));
    }

    /** The frame buffer in remux() is length * 3 + 16; the worst stream must fit it. */
    @Test public void tinyUnitsFitTheFrameBuffer() {
        byte[] in = new byte[60_000];
        for (int i = 0; i < in.length; i += 2) in[i] = 1;
        byte[] out = new byte[in.length * 3 + 16];
        assertEquals(in.length / 2 * 5, FlvRemux.annexB(in, 0, in.length, 1, out));
    }

    @Test public void readsAvcAndAudioConfig() throws IOException {
        byte[] avc = {1, 100, 0, 31, (byte) 0xff, (byte) 0xe1, 0, 2, 0x67, 1, 1, 0, 1, 0x68};
        FlvRemux.VideoConfig video = FlvRemux.avcConfig(avc, 0, avc.length);
        assertEquals(4, video.lengthSize);
        assertArrayEquals(new byte[]{0, 0, 0, 1, 0x67, 1}, video.csd0);
        assertArrayEquals(new byte[]{0, 0, 0, 1, 0x68}, video.csd1);
        FlvRemux.AudioConfig audio = FlvRemux.audioConfig(new byte[]{0x12, 0x10}, 0, 2);
        assertEquals(44100, audio.sampleRate);
        assertEquals(2, audio.channels);
    }

    @Test public void metadataNumbers() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        out.write(5);
        for (char c : "width".toCharArray()) out.write(c);
        out.write(0);
        long bits = Double.doubleToLongBits(720);
        for (int i = 7; i >= 0; i--) out.write((int) (bits >> (i * 8)) & 0xff);
        byte[] data = out.toByteArray();
        assertEquals(720, FlvRemux.metaNumber(data, data.length, "width"), 0);
        assertEquals(0, FlvRemux.metaNumber(data, data.length - 1, "width"), 0);
        assertEquals(0, FlvRemux.metaNumber(data, data.length, "height"), 0);
    }

    /** Broken streams may end in IOException, never in anything else. */
    @Test public void randomStreamsOnlyEverThrowIoException() {
        Random random = new Random(5);
        for (int round = 0; round < 300_000; round++) {
            byte[] data = new byte[random.nextInt(80)];
            random.nextBytes(data);
            int size = data.length == 0 ? 0 : random.nextInt(data.length + 1);
            FlvRemux.parseVideo(data, size, new FlvRemux.Video());
            try { FlvRemux.avcConfig(data, 0, size); } catch (IOException expected) { continue; }
            try { FlvRemux.hevcConfig(data, 0, size); } catch (IOException expected) { continue; }
            try { FlvRemux.audioConfig(data, 0, size); } catch (IOException expected) { continue; }
            FlvRemux.metaNumber(data, size, "width");
            FlvRemux.annexB(data, 0, size, 1 + random.nextInt(4), new byte[size * 3 + 16]);
        }
    }
}
