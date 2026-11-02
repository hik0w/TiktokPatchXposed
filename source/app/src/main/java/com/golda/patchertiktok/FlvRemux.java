package com.golda.patchertiktok;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class FlvRemux {
    static final String AVC = "video/avc";
    static final String HEVC = "video/hevc";
    private static final String AAC = "audio/mp4a-latm";
    private static final byte[] START = {0, 0, 0, 1};
    private static final int[] RATES = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};
    private static final int SCAN_TAGS = 2000;

    interface Source {
        InputStream open() throws IOException;
    }

    static final class VideoConfig {
        final String mime;
        final byte[] csd0;
        final byte[] csd1;
        final int lengthSize;

        VideoConfig(String mime, byte[] csd0, byte[] csd1, int lengthSize) {
            this.mime = mime;
            this.csd0 = csd0;
            this.csd1 = csd1;
            this.lengthSize = lengthSize;
        }
    }

    static final class AudioConfig {
        final byte[] asc;
        final int sampleRate;
        final int channels;

        AudioConfig(byte[] asc, int sampleRate, int channels) {
            this.asc = asc;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }

    static final class Video {
        String mime;
        boolean config;
        boolean key;
        int cts;
        int offset;
    }

    static final class Tag {
        int type;
        long timestamp;
        int size;
        byte[] data = new byte[64 * 1024];
    }

    private FlvRemux() { }

    private static final class Probe {
        VideoConfig video;
        AudioConfig audio;
        int width;
        int height;
    }

    private static Probe probe(Source source) throws IOException {
        Probe probe = new Probe();
        boolean hasVideo = false;
        boolean hasAudio = false;
        Video video = new Video();
        Tag tag = new Tag();
        try (Reader reader = new Reader(source.open())) {
            for (int seen = 0; seen < SCAN_TAGS && reader.next(tag); seen++) {
                if (tag.type == 18) {
                    if (probe.width == 0) probe.width = (int) metaNumber(tag.data, tag.size, "width");
                    if (probe.height == 0) probe.height = (int) metaNumber(tag.data, tag.size, "height");
                } else if (tag.type == 9 && parseVideo(tag.data, tag.size, video)) {
                    hasVideo = true;
                    if (video.mime == null) throw new IOException("unsupported video codec");
                    if (video.config && probe.video == null) probe.video = videoConfig(video.mime, tag.data, video.offset, tag.size);
                } else if (tag.type == 8 && tag.size >= 2) {
                    hasAudio = true;
                    if ((tag.data[0] & 0xFF) >> 4 != 10) throw new IOException("unsupported audio codec");
                    if (tag.data[1] == 0 && probe.audio == null) probe.audio = audioConfig(tag.data, 2, tag.size);
                }
                if (probe.video != null && probe.audio != null) break;
                if (seen > 300 && (!hasVideo || probe.video != null) && (!hasAudio || probe.audio != null)) break;
            }
        }
        if (hasVideo && probe.video == null) throw new IOException("no video configuration");
        if (hasAudio && probe.audio == null) throw new IOException("no audio configuration");
        if (probe.video == null && probe.audio == null) throw new IOException("no media");
        return probe;
    }

    static void remux(Source source, FileDescriptor out, int width, int height) throws IOException {
        Probe probe = probe(source);
        VideoConfig videoConfig = probe.video;
        AudioConfig audioConfig = probe.audio;
        if (width <= 0 || height <= 0) {
            width = probe.width;
            height = probe.height;
        }
        if (width <= 0 || height <= 0) {
            width = 720;
            height = 1280;
        }

        Video video = new Video();
        Tag tag = new Tag();
        MediaMuxer muxer = new MediaMuxer(out, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        boolean started = false;
        try {
            int videoTrack = -1;
            int audioTrack = -1;
            if (videoConfig != null) {
                MediaFormat format = MediaFormat.createVideoFormat(videoConfig.mime, width, height);
                format.setByteBuffer("csd-0", ByteBuffer.wrap(videoConfig.csd0));
                if (videoConfig.csd1 != null) format.setByteBuffer("csd-1", ByteBuffer.wrap(videoConfig.csd1));
                videoTrack = muxer.addTrack(format);
            }
            if (audioConfig != null) {
                MediaFormat format = MediaFormat.createAudioFormat(AAC, audioConfig.sampleRate, audioConfig.channels);
                format.setByteBuffer("csd-0", ByteBuffer.wrap(audioConfig.asc));
                audioTrack = muxer.addTrack(format);
            }
            muxer.start();
            started = true;

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            byte[] frame = new byte[256 * 1024];
            long base = -1;
            long lastVideo = Long.MIN_VALUE;
            long lastAudio = Long.MIN_VALUE;
            int written = 0;
            try (Reader reader = new Reader(source.open())) {
                while (reader.next(tag)) {
                    if (tag.type == 9 && videoTrack >= 0) {
                        if (!parseVideo(tag.data, tag.size, video) || video.config || !videoConfig.mime.equals(video.mime)) continue;
                        if (base < 0 && !video.key) continue;
                        if (base < 0) base = tag.timestamp;
                        long dts = Math.max(tag.timestamp, lastVideo == Long.MIN_VALUE ? tag.timestamp : lastVideo + 1);
                        lastVideo = dts;
                        int length = tag.size - video.offset;
                        if (length <= 0) continue;
                        // Each 2-byte unit (1-byte length) can grow to 5 bytes with its start code.
                        if (frame.length < length * 3 + 16) frame = new byte[length * 3 + 16];
                        int size = annexB(tag.data, video.offset, tag.size, videoConfig.lengthSize, frame);
                        if (size <= 0) continue;
                        info.set(0, size, Math.max(0, dts + video.cts - base) * 1000, video.key ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                        muxer.writeSampleData(videoTrack, ByteBuffer.wrap(frame), info);
                        written++;
                    } else if (tag.type == 8 && audioTrack >= 0 && tag.size > 2 && tag.data[1] == 1) {
                        if (base < 0 && videoTrack >= 0) continue;
                        if (base < 0) base = tag.timestamp;
                        if (tag.timestamp < base) continue;
                        long time = Math.max(tag.timestamp, lastAudio == Long.MIN_VALUE ? tag.timestamp : lastAudio + 1);
                        lastAudio = time;
                        info.set(2, tag.size - 2, (time - base) * 1000, MediaCodec.BUFFER_FLAG_KEY_FRAME);
                        muxer.writeSampleData(audioTrack, ByteBuffer.wrap(tag.data), info);
                        written++;
                    }
                }
            }
            if (written == 0) throw new IOException("no samples");
            muxer.stop();
            started = false;
        } finally {
            if (started) {
                try { muxer.stop(); } catch (RuntimeException ignored) { }
            }
            muxer.release();
        }
    }

    static boolean parseVideo(byte[] d, int size, Video out) {
        if (size < 1) return false;
        int first = d[0] & 0xFF;
        out.mime = null;
        out.config = false;
        out.cts = 0;
        if ((first & 0x80) != 0) {
            if (size < 5) return false;
            int frame = (first >> 4) & 0x07;
            int packet = first & 0x0F;
            String fourcc = new String(d, 1, 4, StandardCharsets.US_ASCII);
            out.mime = "hvc1".equals(fourcc) ? HEVC : "avc1".equals(fourcc) ? AVC : null;
            out.key = frame == 1;
            if (frame == 5) return false;
            if (packet == 0) {
                out.config = true;
                out.offset = 5;
            } else if (packet == 1) {
                if (size < 8) return false;
                out.cts = signed24(d, 5);
                out.offset = 8;
            } else if (packet == 3) {
                out.offset = 5;
            } else {
                return false;
            }
            return true;
        }
        int frame = first >> 4;
        int codec = first & 0x0F;
        if (frame == 5 || size < 5) return false;
        out.mime = codec == 7 ? AVC : codec == 12 ? HEVC : null;
        out.key = frame == 1;
        int packet = d[1];
        if (packet == 0) {
            out.config = true;
        } else if (packet == 1) {
            out.cts = signed24(d, 2);
        } else {
            return false;
        }
        out.offset = 5;
        return true;
    }

    static VideoConfig videoConfig(String mime, byte[] d, int offset, int end) throws IOException {
        return AVC.equals(mime) ? avcConfig(d, offset, end) : hevcConfig(d, offset, end);
    }

    static VideoConfig avcConfig(byte[] d, int offset, int end) throws IOException {
        if (end - offset < 7) throw new IOException("short avcC");
        int lengthSize = (d[offset + 4] & 0x03) + 1;
        int position = offset + 5;
        ByteArrayOutputStream sps = new ByteArrayOutputStream();
        ByteArrayOutputStream pps = new ByteArrayOutputStream();
        int count = d[position++] & 0x1F;
        for (int i = 0; i < count; i++) position = copyUnit(d, position, end, sps);
        if (position >= end) throw new IOException("avcC without PPS");
        count = d[position++] & 0xFF;
        for (int i = 0; i < count; i++) position = copyUnit(d, position, end, pps);
        if (sps.size() == 0 || pps.size() == 0) throw new IOException("avcC without parameter sets");
        return new VideoConfig(AVC, sps.toByteArray(), pps.toByteArray(), lengthSize);
    }

    static VideoConfig hevcConfig(byte[] d, int offset, int end) throws IOException {
        if (end - offset < 23) throw new IOException("short hvcC");
        int lengthSize = (d[offset + 21] & 0x03) + 1;
        int arrays = d[offset + 22] & 0xFF;
        int position = offset + 23;
        ByteArrayOutputStream units = new ByteArrayOutputStream();
        for (int a = 0; a < arrays; a++) {
            if (position + 3 > end) throw new IOException("short hvcC array");
            int type = d[position] & 0x3F;
            int count = u16(d, position + 1);
            position += 3;
            for (int i = 0; i < count; i++) {
                if (type >= 32 && type <= 34) {
                    position = copyUnit(d, position, end, units);
                } else {
                    if (position + 2 > end) throw new IOException("short hvcC unit");
                    position += 2 + u16(d, position);
                }
            }
        }
        if (units.size() == 0) throw new IOException("hvcC without parameter sets");
        return new VideoConfig(HEVC, units.toByteArray(), null, lengthSize);
    }

    static AudioConfig audioConfig(byte[] d, int offset, int end) throws IOException {
        if (end - offset < 2) throw new IOException("short AudioSpecificConfig");
        byte[] asc = new byte[end - offset];
        System.arraycopy(d, offset, asc, 0, asc.length);
        Bits bits = new Bits(asc);
        int type = bits.read(5);
        if (type == 31) bits.read(6);
        int index = bits.read(4);
        int rate = index == 15 ? bits.read(24) : index < RATES.length ? RATES[index] : 0;
        int channels = bits.read(4);
        if (rate <= 0) throw new IOException("bad AAC sample rate");
        return new AudioConfig(asc, rate, channels > 0 && channels < 8 ? channels : 2);
    }

    static int annexB(byte[] d, int offset, int end, int lengthSize, byte[] out) {
        int written = 0;
        int position = offset;
        while (position + lengthSize <= end) {
            int length = 0;
            for (int i = 0; i < lengthSize; i++) length = (length << 8) | (d[position + i] & 0xFF);
            position += lengthSize;
            if (length <= 0 || position + length > end) break;
            System.arraycopy(START, 0, out, written, 4);
            System.arraycopy(d, position, out, written + 4, length);
            written += 4 + length;
            position += length;
        }
        return written;
    }

    static double metaNumber(byte[] d, int size, String key) {
        byte[] name = key.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i + 2 + name.length + 9 <= size; i++) {
            if (d[i] != 0 || (d[i + 1] & 0xFF) != name.length) continue;
            boolean match = true;
            for (int j = 0; j < name.length && match; j++) match = d[i + 2 + j] == name[j];
            if (!match || d[i + 2 + name.length] != 0) continue;
            long bits = 0;
            for (int j = 0; j < 8; j++) bits = (bits << 8) | (d[i + 3 + name.length + j] & 0xFF);
            double value = Double.longBitsToDouble(bits);
            return value > 0 && value < 100_000 ? value : 0;
        }
        return 0;
    }

    private static int copyUnit(byte[] d, int position, int end, ByteArrayOutputStream out) throws IOException {
        if (position + 2 > end) throw new IOException("short parameter set");
        int length = u16(d, position);
        position += 2;
        if (position + length > end) throw new IOException("short parameter set");
        out.write(START, 0, 4);
        out.write(d, position, length);
        return position + length;
    }

    private static int u16(byte[] d, int offset) {
        return ((d[offset] & 0xFF) << 8) | (d[offset + 1] & 0xFF);
    }

    private static int signed24(byte[] d, int offset) {
        int value = ((d[offset] & 0xFF) << 16) | ((d[offset + 1] & 0xFF) << 8) | (d[offset + 2] & 0xFF);
        return (value & 0x800000) != 0 ? value - 0x1000000 : value;
    }

    static final class Reader implements Closeable {
        private final InputStream in;
        private final byte[] header = new byte[11];

        Reader(InputStream raw) throws IOException {
            if (raw == null) throw new IOException("no input");
            in = new BufferedInputStream(raw, 256 * 1024);
            byte[] head = new byte[9];
            if (!fill(head, 9)) throw new IOException("short FLV header");
            if (head[0] != 'F' || head[1] != 'L' || head[2] != 'V') throw new IOException("not an FLV file");
            long offset = ((head[5] & 0xFFL) << 24) | ((head[6] & 0xFF) << 16) | ((head[7] & 0xFF) << 8) | (head[8] & 0xFF);
            if (offset < 9 || offset > 1 << 20) throw new IOException("bad FLV header");
            skipFully((int) offset - 9 + 4);
        }

        boolean next(Tag tag) throws IOException {
            if (!fill(header, 11)) return false;
            int size = ((header[1] & 0xFF) << 16) | ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
            long time = ((header[4] & 0xFF) << 16) | ((header[5] & 0xFF) << 8) | (header[6] & 0xFF) | ((header[7] & 0xFFL) << 24);
            if (tag.data.length < size) tag.data = new byte[size];
            if (!fill(tag.data, size)) return false;
            skipFully(4);
            tag.type = header[0] & 0x1F;
            tag.timestamp = time;
            tag.size = size;
            return true;
        }

        private boolean fill(byte[] buffer, int length) throws IOException {
            int read = 0;
            while (read < length) {
                int count = in.read(buffer, read, length - read);
                if (count < 0) return false;
                read += count;
            }
            return true;
        }

        private void skipFully(int count) throws IOException {
            while (count > 0) {
                long skipped = in.skip(count);
                if (skipped <= 0) {
                    if (in.read() < 0) return;
                    skipped = 1;
                }
                count -= (int) skipped;
            }
        }

        @Override public void close() throws IOException {
            in.close();
        }
    }

    private static final class Bits {
        private final byte[] data;
        private int position;

        Bits(byte[] data) {
            this.data = data;
        }

        int read(int count) {
            int value = 0;
            for (int i = 0; i < count; i++) {
                int index = position >> 3;
                int bit = index < data.length ? (data[index] >> (7 - (position & 7))) & 1 : 0;
                value = (value << 1) | bit;
                position++;
            }
            return value;
        }
    }
}
