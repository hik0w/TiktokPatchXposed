package com.golda.patchertiktok;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class RegionLookup {
    private static final String POSTS = "/aweme/v1/aweme/post/";
    private static final int QUEUE = 15;
    private static final long GAP_MS = 400;
    private static final long PAUSE_MS = 60_000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final ArrayDeque<Ask> PENDING = new ArrayDeque<>();
    private static final Set<String> ASKED = new HashSet<>();
    private static final Map<String, String> FOUND = new LinkedHashMap<String, String>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) { return size() > 512; }
    };
    private static boolean running;
    private static long pausedUntil;

    private RegionLookup() { }

    private static final class Ask {
        final ClassLoader loader;
        final String handle;
        final String uid;
        final String secUid;
        final Runnable found;

        Ask(ClassLoader loader, String handle, String uid, String secUid, Runnable found) {
            this.loader = loader;
            this.handle = handle;
            this.uid = uid;
            this.secUid = secUid;
            this.found = found;
        }
    }

    static String found(String handle) {
        if (handle == null) return null;
        synchronized (FOUND) {
            return FOUND.get(handle);
        }
    }

    static void request(ClassLoader loader, String handle, String uid, String secUid, Runnable found) {
        if (handle == null || handle.isEmpty() || secUid == null || secUid.isEmpty()) return;
        synchronized (PENDING) {
            if (SystemClock.uptimeMillis() < pausedUntil || !ASKED.add(handle)) return;
            PENDING.addLast(new Ask(loader, handle, uid == null ? "" : uid, secUid, found));
            while (PENDING.size() > QUEUE) ASKED.remove(PENDING.pollFirst().handle);
            if (running) return;
            running = true;
        }
        Thread worker = new Thread(Safe.run("comment region", RegionLookup::drain), "patcher-region");
        worker.setDaemon(true);
        worker.start();
    }

    private static void drain() {
        while (true) {
            Ask ask;
            synchronized (PENDING) {
                ask = PENDING.pollFirst();
                if (ask == null) {
                    running = false;
                    return;
                }
            }
            try {
                fetch(ask);
                Thread.sleep(GAP_MS);
            } catch (Throwable error) {
                Safe.log("comment region", error);
                synchronized (PENDING) {
                    pausedUntil = SystemClock.uptimeMillis() + PAUSE_MS;
                    for (Ask dropped : PENDING) ASKED.remove(dropped.handle);
                    PENDING.clear();
                    ASKED.remove(ask.handle);
                    running = false;
                }
                return;
            }
        }
    }

    private static void fetch(Ask ask) throws Exception {
        JSONObject json = ApiCalls.get(ask.loader, POSTS + "?sec_user_id=" + ApiCalls.encode(ask.secUid)
                + "&user_id=" + ApiCalls.encode(ask.uid) + "&count=1&max_cursor=0");
        JSONArray videos = json.optJSONArray("aweme_list");
        JSONObject first = videos == null ? null : videos.optJSONObject(0);
        JSONObject author = first == null ? null : first.optJSONObject("author");
        if (author == null || !ask.handle.equals(author.optString("unique_id"))) return;
        String region = first.optString("region", "").trim().toUpperCase(Locale.ROOT);
        if (RegionFlag.of(region) == null) return;
        synchronized (FOUND) {
            FOUND.put(ask.handle, region);
        }
        MAIN.post(Safe.run("comment region", ask.found));
    }
}
