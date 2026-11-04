package com.golda.patchertiktok;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DownloadName {
    static final String DEFAULT_FOLDER = "TikTok";
    private static final Pattern TOKEN = Pattern.compile("\\{([A-Za-z]+)\\}");
    private static final Pattern FORBIDDEN = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final int MAX_NAME_BYTES = 180;
    private static final int MAX_SEGMENT_BYTES = 64;
    private static final int MAX_FOLDER_DEPTH = 4;
    private static final String EDGES = " ._-";

    static final class Info {
        final String author;
        final String nickname;
        final String id;
        final long postedMillis;

        Info(String author, String nickname, String id, long postedMillis) {
            this.author = text(author).startsWith("@") ? text(author).substring(1) : text(author);
            this.nickname = text(nickname);
            this.id = text(id);
            this.postedMillis = Math.max(0, postedMillis);
        }

        private static String text(String value) {
            return value == null || "null".equals(value) ? "" : value.trim();
        }
    }

    private DownloadName() { }

    static String file(String template, Info info, DownloadFormat.Kind kind, String quality, int index, int count,
            String batch, String extension, long now, TimeZone zone) {
        String type = extension == null ? "" : extension.replaceAll("[^A-Za-z0-9]", "");
        if (type.isEmpty()) type = "bin";
        if (template == null || template.trim().isEmpty()) return DownloadFormat.fileName(info.id, batch, index, type);
        String name = limit(clean(fill(template, info, kind, quality, index, now, zone)), MAX_NAME_BYTES);
        if (name.isEmpty()) return DownloadFormat.fileName(info.id, batch, index, type);
        if (count > 1 && !numbered(template)) name = name + '_' + two(index);
        return name + '.' + type;
    }

    static String folder(String template, Info info, long now, TimeZone zone) {
        String source = template == null || template.trim().isEmpty() ? DEFAULT_FOLDER : template;
        List<String> parts = new ArrayList<>();
        for (String segment : source.split("[/\\\\]+")) {
            String part = limit(clean(fill(segment, info, null, "", 1, now, zone)), MAX_SEGMENT_BYTES);
            if (part.isEmpty()) continue;
            parts.add(part);
            if (parts.size() == MAX_FOLDER_DEPTH) break;
        }
        return parts.isEmpty() ? DEFAULT_FOLDER : String.join("/", parts);
    }

    private static boolean numbered(String template) {
        Matcher matcher = TOKEN.matcher(template);
        while (matcher.find()) if ("n".equalsIgnoreCase(matcher.group(1))) return true;
        return false;
    }

    private static String fill(String template, Info info, DownloadFormat.Kind kind, String quality, int index,
            long now, TimeZone zone) {
        Date when = new Date(info.postedMillis > 0 ? info.postedMillis : now);
        Matcher matcher = TOKEN.matcher(template);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String value;
            switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
                case "author": value = info.author; break;
                case "name": value = info.nickname; break;
                case "id": value = info.id; break;
                case "date": value = format("yyyy-MM-dd", when, zone); break;
                case "time": value = format("HH-mm-ss", when, zone); break;
                case "type": value = kind == null ? "" : kind.name().toLowerCase(Locale.ROOT); break;
                case "quality": value = quality == null ? "" : quality; break;
                case "n": value = two(index); break;
                default: value = matcher.group(0);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(FORBIDDEN.matcher(value).replaceAll("_")));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String format(String pattern, Date when, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
        format.setTimeZone(zone == null ? TimeZone.getDefault() : zone);
        return format.format(when);
    }

    private static String clean(String value) {
        String clean = FORBIDDEN.matcher(value).replaceAll("_").replaceAll("\\s+", " ")
                .replaceAll("_{2,}", "_").replaceAll("-{2,}", "-");
        return trim(clean);
    }

    private static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && EDGES.indexOf(value.charAt(start)) >= 0) start++;
        while (end > start && EDGES.indexOf(value.charAt(end - 1)) >= 0) end--;
        return value.substring(start, end);
    }

    private static String limit(String value, int bytes) {
        if (value.getBytes(StandardCharsets.UTF_8).length <= bytes) return value;
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (int offset = 0; offset < value.length();) {
            int point = value.codePointAt(offset);
            String character = new String(Character.toChars(point));
            int size = character.getBytes(StandardCharsets.UTF_8).length;
            if (used + size > bytes) break;
            out.append(character);
            used += size;
            offset += Character.charCount(point);
        }
        return trim(out.toString());
    }

    private static String two(int index) {
        return String.format(Locale.ROOT, "%02d", Math.max(1, index));
    }
}
