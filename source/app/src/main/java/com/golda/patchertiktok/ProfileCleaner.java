package com.golda.patchertiktok;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class ProfileCleaner {
    enum Kind { LIKES, FAVORITES }

    private static final String FEED_ACTIONS = "com.ss.android.ugc.aweme.feed.api.FeedActionApi";
    private static final int PAGE = 20;
    private static final int MAX_PAGES = 1000;
    private static final int BARREN_PAGES = 3;
    private static final long PAGE_PAUSE_MS = 500L;
    private static final long PAUSE_MS = 1_600L;
    private static final long JITTER_MS = 700L;
    private static final long ANSWER_MS = 30_000L;
    private static final int MAX_FAILURES = 3;
    private static final String PROFILE_CHANNEL = "3";

    private ProfileCleaner() { }

    static List<String> list(Context context, Kind kind) throws Exception {
        ClassLoader loader = context.getClassLoader();
        String secUid = ApiCalls.text(ApiCalls.me(loader), "getSecUid");
        if (kind == Kind.LIKES && secUid.isEmpty()) throw new IOException("no sec uid");
        Set<String> found = new LinkedHashSet<>();
        long cursor = 0;
        int barren = 0;
        for (int number = 0; number < MAX_PAGES; number++) {
            String path = kind == Kind.LIKES
                    ? "/aweme/v1/aweme/favorite/?invalid_item_count=0&is_hiding_invalid_item=1&max_cursor=" + cursor
                            + "&sec_user_id=" + ApiCalls.encode(secUid) + "&count=" + PAGE
                    : "/aweme/v1/aweme/listcollection/?cursor=" + cursor + "&count=" + PAGE;
            JSONObject page = ApiCalls.get(loader, path);
            int before = found.size();
            JSONArray items = page.optJSONArray("aweme_list");
            if (items != null) {
                for (int index = 0; index < items.length(); index++) {
                    JSONObject item = items.optJSONObject(index);
                    String id = item == null ? "" : item.optString("aweme_id", "");
                    if (!id.isEmpty()) found.add(id);
                }
            }
            barren = found.size() == before ? barren + 1 : 0;
            long next = kind == Kind.LIKES ? page.optLong("max_cursor", page.optLong("max_time", 0)) : page.optLong("cursor", 0);
            if (!ApiCalls.flag(page, "has_more") || barren >= BARREN_PAGES || next == cursor) break;
            cursor = next;
            Thread.sleep(PAGE_PAUSE_MS);
        }
        RuntimeLog.log("profile cleaner: " + found.size() + " " + kind);
        return new ArrayList<>(found);
    }

    static int remove(Context context, Kind kind, List<String> ids, RepostCleaner.Progress progress) throws Exception {
        ClassLoader loader = context.getClassLoader();
        Unlike unlike = kind == Kind.LIKES ? new Unlike(loader) : null;
        Random random = new Random();
        int done = 0, failures = 0;
        for (String id : ids) {
            if (!progress.wanted()) break;
            try {
                if (unlike != null) {
                    unlike.send(id);
                } else {
                    ApiCalls.get(loader, "/aweme/v1/aweme/collect/?aweme_id=" + ApiCalls.encode(id) + "&action=0");
                }
                failures = 0;
                progress.removed(++done);
            } catch (IOException | InvocationTargetException | java.util.concurrent.ExecutionException
                     | java.util.concurrent.TimeoutException error) {
                RuntimeLog.log("profile cleaner: one refused, " + error);
                if (++failures >= MAX_FAILURES) break;
            }
            Thread.sleep(PAUSE_MS + (long) (random.nextDouble() * JITTER_MS));
        }
        RuntimeLog.log("profile cleaner: removed " + done + " of " + ids.size() + " " + kind);
        return done;
    }

    private static final class Unlike {
        private final Object api;
        private final Method digg;

        Unlike(ClassLoader loader) throws Exception {
            Object instance = null;
            for (Field field : Reflect.fields(Class.forName(FEED_ACTIONS, true, loader))) {
                if (!Modifier.isStatic(field.getModifiers()) || !field.getType().isInterface()) continue;
                field.setAccessible(true);
                instance = field.get(null);
                if (instance != null) break;
            }
            Method found = null;
            if (instance != null) {
                for (Method method : Reflect.publicMethods(instance.getClass())) {
                    Class<?>[] p = method.getParameterTypes();
                    if (p.length == 1 && p[0] == Map.class && Future.class.isAssignableFrom(method.getReturnType())) found = method;
                }
            }
            if (found == null) throw new IOException("like call not found");
            found.setAccessible(true);
            api = instance;
            digg = found;
        }

        void send(String id) throws Exception {
            Map<String, String> query = new HashMap<>();
            query.put("aweme_id", id);
            query.put("type", "0");
            query.put("channel_id", PROFILE_CHANNEL);
            Object answer = ((Future<?>) digg.invoke(api, query)).get(ANSWER_MS, TimeUnit.MILLISECONDS);
            Object code = Reflect.get(answer, "status_code");
            if (code instanceof Number && ((Number) code).intValue() != 0) throw new IOException("status " + code);
        }
    }
}
