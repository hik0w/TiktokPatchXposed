package com.golda.patchertiktok;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stories watched with their views hidden still count as watched on this phone (#18). The server
 * never hears about them, so it keeps sending them as unseen and the ring around the avatar stays
 * colored. TikTok decides the ring from the "viewed" flags in a user's story metadata
 * (profile.model.UserAllStoryMetadata, a list of StoryLiteMetadata), reading the fields directly.
 * The ids of hidden views are kept here, and those flags are set whenever the metadata is taken
 * from a User or versioned for TikTok's status cache. A story not watched stays unseen, so a new
 * story colors the ring again. Only local objects change; nothing is sent. The newest 2 000 ids
 * are kept in a file, as longs.
 */
final class StoriesSeen {
    private static final String USER = "com.ss.android.ugc.aweme.profile.model.User";
    private static final String METADATA = "com.ss.android.ugc.aweme.profile.model.UserAllStoryMetadata";
    private static final String FILE = "tpx_stories_seen";
    private static final int LIMIT = 2_000;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(Safe.run("stories seen", task), "tpx-stories");
        thread.setDaemon(true);
        return thread;
    });
    private static final SeenIds SEEN = new SeenIds(LIMIT);

    private static volatile File file;
    private static int appended;

    private StoriesSeen() { }

    static void install(Context context, ClassLoader loader) {
        file = new File(context.getFilesDir(), FILE);
        IO.execute(Safe.run("stories seen", StoriesSeen::load));
        Class<?> user = Hooks.findClass(USER, loader);
        Class<?> metadata = Hooks.findClass(METADATA, loader);
        if (user == null || metadata == null) {
            RuntimeLog.log("stories seen unavailable: story metadata not found");
            return;
        }
        int hooks = Hooks.hookAll(user, "getAllStoryMetadata", new Hooks.Hook() {
            @Override protected void after(Hooks.Call param) {
                correct(param.getResult());
            }
        });
        // The status cache versions every update it stores, also ones that never pass the getter.
        hooks += Hooks.hookAll(metadata, "version", new Hooks.Hook() {
            @Override protected void before(Hooks.Call param) {
                correct(param.thisObject);
            }
        });
        RuntimeLog.log("stories seen: hooks=" + hooks);
    }

    /** Called with the id of a story whose view was just hidden. */
    static void mark(String id) {
        if (id == null) return;
        long value;
        try {
            value = Long.parseLong(id.trim());
        } catch (NumberFormatException notAnId) {
            return;
        }
        boolean added;
        synchronized (SEEN) {
            added = SEEN.add(value);
        }
        if (added) IO.execute(Safe.run("stories seen", () -> append(value)));
    }

    private static boolean active() {
        if (!Prefs.on(Prefs.GHOST_STORIES) && !Prefs.on(Prefs.GHOST_HIGHLIGHTS)) return false;
        synchronized (SEEN) {
            return SEEN.size() > 0;
        }
    }

    /** Sets the viewed flag of the listed stories whose view was hidden. */
    private static void correct(Object metadata) {
        if (metadata == null || !active()) return;
        Object stories = Reflect.get(metadata, "storyLiteMetadata");
        if (!(stories instanceof List)) return;
        List<?> list = (List<?>) stories;
        for (int i = 0; i < list.size(); i++) {
            Object story = list.get(i);
            if (story == null || Reflect.getBoolean(story, "viewed")) continue;
            Object id = Reflect.get(story, "itemID");
            if (!(id instanceof Long)) continue;
            boolean seen;
            synchronized (SEEN) {
                seen = SEEN.contains((Long) id);
            }
            if (!seen) continue;
            Field viewed = Reflect.field(story.getClass(), "viewed");
            try {
                if (viewed != null) viewed.setBoolean(story, true);
            } catch (IllegalAccessException | IllegalArgumentException ignored) {
                // The story just stays as TikTok sent it.
            }
        }
    }

    private static void load() {
        File current = file;
        if (current == null || !current.isFile()) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(current)))) {
            while (true) {
                long id = in.readLong();
                synchronized (SEEN) {
                    SEEN.add(id);
                }
            }
        } catch (EOFException end) {
            // The whole file is read; a cut-off last id is simply dropped.
        } catch (Exception error) {
            RuntimeLog.log("stories seen: list not read, " + error.getClass().getSimpleName());
        }
    }

    private static void append(long id) {
        File current = file;
        if (current == null) return;
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(current, true))) {
            out.writeLong(id);
        } catch (Exception error) {
            return;
        }
        // The file only grows; now and then it is rewritten with the ids memory still keeps.
        if (++appended > LIMIT / 2) compact(current);
    }

    private static void compact(File current) {
        long[] ids;
        synchronized (SEEN) {
            ids = SEEN.toArray();
        }
        File temp = new File(current.getPath() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(temp, false))) {
            for (long id : ids) out.writeLong(id);
        } catch (Exception error) {
            return;
        }
        if (temp.renameTo(current)) appended = 0;
    }
}
