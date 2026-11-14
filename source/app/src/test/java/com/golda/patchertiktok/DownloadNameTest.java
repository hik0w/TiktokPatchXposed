package com.golda.patchertiktok;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DownloadNameTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final long POSTED = 1709618828000L;
    private static final long NOW = 1730000000000L;
    private static final DownloadName.Info POST = new DownloadName.Info("@golda", "Golda M", "7412", POSTED);

    private static String file(String template, int index, int count) {
        return DownloadName.file(template, POST, DownloadFormat.Kind.VIDEO, "1080p", index, count, "batch", "mp4", NOW, UTC);
    }

    @Test public void emptyTemplateKeepsTheClassicName() {
        assertEquals(DownloadFormat.fileName("7412", "batch", 1, "mp4"), file("", 1, 1));
        assertEquals(DownloadFormat.fileName("7412", "batch", 2, "mp4"), file("  ", 2, 3));
        assertEquals(DownloadFormat.fileName("7412", "batch", 1, "mp4"), file(null, 1, 1));
    }

    @Test public void fillsEveryPlaceholder() {
        assertEquals("golda_2024-03-05_06-07-08_7412.mp4", file("{author}_{date}_{time}_{id}", 1, 1));
        assertEquals("Golda M video 1080p 03.mp4", file("{name} {type} {quality} {n}", 3, 5));
        assertEquals("golda_7412.mp4", file("{AUTHOR}_{Id}", 1, 1));
    }

    @Test public void setsStayNumberedWithoutN() {
        assertEquals("golda_02.mp4", file("{author}", 2, 4));
        assertEquals("golda.mp4", file("{author}", 1, 1));
        assertEquals("golda-02.mp4", file("{author}-{n}", 2, 4));
    }

    @Test public void unknownDateFallsBackToSaveTime() {
        DownloadName.Info avatar = new DownloadName.Info(null, null, "avatar_1", 0);
        assertEquals("2024-10-27_avatar_1.jpg", DownloadName.file("{date}_{id}", avatar, DownloadFormat.Kind.PHOTO,
                "", 1, 1, "b", "jpg", NOW, UTC));
    }

    @Test public void emptyPlaceholdersLeaveNoStraySeparators() {
        DownloadName.Info bare = new DownloadName.Info(null, "", "55", 0);
        assertEquals("55.mp4", DownloadName.file("{author}_{name}_{id}", bare, DownloadFormat.Kind.VIDEO, "", 1, 1,
                "b", "mp4", NOW, UTC));
        assertEquals(DownloadFormat.fileName("55", "b", 1, "mp4"), DownloadName.file("{author}", bare,
                DownloadFormat.Kind.VIDEO, "", 1, 1, "b", "mp4", NOW, UTC));
    }

    @Test public void namesCannotEscapeTheirFolder() {
        DownloadName.Info evil = new DownloadName.Info("../../x", "a/b\\c:d*e?f\"g<h>i|j\nk", "1", POSTED);
        String name = DownloadName.file("{author}/{name}", evil, DownloadFormat.Kind.VIDEO, "", 1, 1, "b", "../mp4", NOW, UTC);
        assertFalse(name, name.contains("/"));
        assertFalse(name, name.contains("\\"));
        assertFalse(name, name.contains("\n"));
        assertFalse(name, name.startsWith("."));
        assertTrue(name, name.endsWith(".mp4"));
    }

    @Test public void longNamesAreCutWithoutBreakingCharacters() {
        StringBuilder emoji = new StringBuilder();
        for (int i = 0; i < 200; i++) emoji.append("\uD83D\uDE00");
        DownloadName.Info loud = new DownloadName.Info("a", emoji.toString(), "1", POSTED);
        String name = DownloadName.file("{name}", loud, DownloadFormat.Kind.VIDEO, "", 12, 20, "b", "mp4", NOW, UTC);
        assertTrue(name.getBytes(StandardCharsets.UTF_8).length <= 255);
        assertTrue(name, name.endsWith("_12.mp4"));
        String stem = name.substring(0, name.length() - "_12.mp4".length());
        assertEquals(0, stem.length() % 2);
        assertTrue(Character.isHighSurrogate(stem.charAt(stem.length() - 2)));
    }

    @Test public void folderDefaultsToTikTokAndAllowsLevels() {
        assertEquals("TikTok", DownloadName.folder("", POST, NOW, UTC));
        assertEquals("TikTok", DownloadName.folder(null, POST, NOW, UTC));
        assertEquals("TikTok/golda", DownloadName.folder("TikTok/{author}", POST, NOW, UTC));
        assertEquals("Saved/2024-03-05", DownloadName.folder("\\Saved\\\\{date}/", POST, NOW, UTC));
        assertEquals("a/b/c/d", DownloadName.folder("a/b/c/d/e/f", POST, NOW, UTC));
    }

    @Test public void folderCannotClimbOrBeSplitByAPlaceholder() {
        DownloadName.Info evil = new DownloadName.Info("x/../../y", "", "1", POSTED);
        assertEquals("TikTok", DownloadName.folder("../..", evil, NOW, UTC));
        assertEquals("ok/x_.._.._y", DownloadName.folder("../ok/{author}", evil, NOW, UTC));
        assertEquals("TikTok", DownloadName.folder("{name}", evil, NOW, UTC));
    }
}
