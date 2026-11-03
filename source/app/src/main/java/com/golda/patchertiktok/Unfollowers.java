package com.golda.patchertiktok;

import android.content.Context;

import com.golda.patchertiktok.FollowerDiff.Person;
import com.golda.patchertiktok.FollowerDiff.Suspect;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class Unfollowers {
    enum Outcome { SAVED, FIRST, COMPARED, INCOMPLETE }

    interface Progress {
        void loaded(int count);
    }

    static final class Report {
        Outcome outcome = Outcome.SAVED;
        long checked;
        long previous;
        int followers;
        int left;
        int joined;
        int got;
        long expected;
        List<Person> history = Collections.emptyList();
        List<Person> joinedHistory = Collections.emptyList();
        List<Suspect> suspects = Collections.emptyList();
        List<Person> notBack = Collections.emptyList();
    }

    private static final String FOLLOW_API = "com.ss.android.ugc.aweme.userservice.CommonFollowApi";
    private static final long REMOVE_PAUSE_MS = 2_000L;
    private static final long REMOVE_JITTER_MS = 800L;
    private static final int MAX_FAILURES = 3;

    private static final int PAGE = 40;
    private static final int MAX_PAGES = 500;
    private static final int BARREN_PAGES = 3;
    private static final long PAGE_PAUSE_MS = 800L;
    private static final int HISTORY = 500;

    private Unfollowers() { }

    static Report saved(Context context) {
        Report report = new Report();
        String uid;
        try {
            uid = ApiCalls.text(ApiCalls.me(context.getClassLoader()), "getUid");
        } catch (Exception notSignedIn) {
            return report;
        }
        fill(report, context.getFilesDir(), uid);
        return report;
    }

    static Report check(Context context, Progress progress) throws Exception {
        ClassLoader loader = context.getClassLoader();
        Object me = ApiCalls.me(loader);
        String uid = ApiCalls.text(me, "getUid");
        String secUid = ApiCalls.text(me, "getSecUid");
        File dir = context.getFilesDir();
        Fetch followers = fetch(loader, true, uid, secUid, progress, 0);
        long expected = followers.total > 0 ? followers.total : ApiCalls.count(me, "getFollowerCount");
        if (!FollowerDiff.complete(followers.people.size(), expected, followers.ended)) {
            Report report = new Report();
            fill(report, dir, uid);
            report.outcome = Outcome.INCOMPLETE;
            report.got = followers.people.size();
            report.expected = expected;
            return report;
        }
        Fetch following = fetch(loader, false, uid, secUid, progress, followers.people.size());
        Snapshot before = read(file(dir, "followers", uid));
        long now = System.currentTimeMillis();
        List<Person> left = before == null ? Collections.emptyList() : FollowerDiff.missing(before.people, followers.people);
        List<Person> newcomers = before == null ? Collections.emptyList() : FollowerDiff.missing(followers.people, before.people);
        int joined = newcomers.size();
        List<Person> history = FollowerDiff.newestFirst(readList(file(dir, "unfollowed", uid)), stamp(left, now), HISTORY);
        List<Person> joinedHistory = FollowerDiff.newestFirst(readList(file(dir, "newfollowers", uid)), stamp(newcomers, now), HISTORY);
        Set<String> followed = FollowerDiff.uids(following.people);
        List<Suspect> suspects = new ArrayList<>();
        for (Suspect suspect : followers.suspects) if (!followed.contains(suspect.person.uid)) suspects.add(suspect);
        write(file(dir, "followers", uid), now, followers.people);
        write(file(dir, "following", uid), now, following.people);
        write(file(dir, "unfollowed", uid), 0, history);
        write(file(dir, "newfollowers", uid), 0, joinedHistory);
        writeSuspects(file(dir, "suspects", uid), suspects);
        Report report = new Report();
        fill(report, dir, uid);
        report.outcome = before == null ? Outcome.FIRST : Outcome.COMPARED;
        report.previous = before == null ? 0 : before.time;
        report.left = left.size();
        report.joined = joined;
        RuntimeLog.log("unfollowers: " + followers.people.size() + " followers, " + following.people.size()
                + " following, " + left.size() + " left, " + joined + " joined, " + suspects.size() + " suspicious");
        return report;
    }

    static int removeFollowers(Context context, List<Suspect> chosen, RepostCleaner.Progress progress) throws Exception {
        ClassLoader loader = context.getClassLoader();
        String uid = ApiCalls.text(ApiCalls.me(loader), "getUid");
        Object api = null;
        for (Field field : Reflect.fields(Class.forName(FOLLOW_API, true, loader))) {
            if (!Modifier.isStatic(field.getModifiers()) || !field.getType().isInterface()) continue;
            field.setAccessible(true);
            api = field.get(null);
            if (api != null) break;
        }
        Method remove = null;
        if (api != null) {
            for (Method method : Reflect.publicMethods(api.getClass())) {
                Class<?>[] p = method.getParameterTypes();
                if ("remove".equals(method.getName()) && p.length == 2 && p[0] == String.class && p[1] == String.class) remove = method;
            }
        }
        if (remove == null) throw new java.io.IOException("remove follower call not found");
        remove.setAccessible(true);
        Set<String> removed = new HashSet<>();
        Random random = new Random();
        int failures = 0;
        try {
            for (Suspect suspect : chosen) {
                if (!progress.wanted()) break;
                try {
                    RepostCleaner.check(RepostCleaner.await(remove.invoke(api, suspect.person.uid, suspect.person.secUid)));
                    failures = 0;
                    removed.add(suspect.person.uid);
                    progress.removed(removed.size());
                } catch (java.io.IOException | java.lang.reflect.InvocationTargetException error) {
                    RuntimeLog.log("unfollowers: removal refused, " + error);
                    if (++failures >= MAX_FAILURES) break;
                }
                Thread.sleep(REMOVE_PAUSE_MS + (long) (random.nextDouble() * REMOVE_JITTER_MS));
            }
        } finally {
            forget(context.getFilesDir(), uid, removed);
        }
        RuntimeLog.log("unfollowers: removed " + removed.size() + " of " + chosen.size() + " followers");
        return removed.size();
    }

    private static void forget(File dir, String uid, Set<String> removed) throws java.io.IOException {
        if (removed.isEmpty()) return;
        Snapshot followers = read(file(dir, "followers", uid));
        if (followers != null) write(file(dir, "followers", uid), followers.time, without(followers.people, removed));
        List<Suspect> kept = new ArrayList<>();
        for (Suspect suspect : readSuspects(file(dir, "suspects", uid))) if (!removed.contains(suspect.person.uid)) kept.add(suspect);
        writeSuspects(file(dir, "suspects", uid), kept);
    }

    private static List<Person> without(Collection<Person> people, Set<String> removed) {
        List<Person> result = new ArrayList<>(people.size());
        for (Person person : people) if (!removed.contains(person.uid)) result.add(person);
        return result;
    }

    private static List<Person> stamp(List<Person> people, long now) {
        List<Person> result = new ArrayList<>(people.size());
        for (Person person : people) result.add(person.at(now));
        return result;
    }

    private static void fill(Report report, File dir, String uid) {
        Snapshot followers = read(file(dir, "followers", uid));
        Snapshot following = read(file(dir, "following", uid));
        report.history = readList(file(dir, "unfollowed", uid));
        report.joinedHistory = readList(file(dir, "newfollowers", uid));
        report.suspects = readSuspects(file(dir, "suspects", uid));
        if (followers == null) return;
        report.checked = followers.time;
        report.followers = followers.people.size();
        if (following != null) report.notBack = FollowerDiff.missing(following.people, followers.people);
    }

    private static final class Fetch {
        final List<Person> people;
        final List<Suspect> suspects;
        final boolean ended;
        final long total;

        Fetch(List<Person> people, List<Suspect> suspects, boolean ended, long total) {
            this.people = people;
            this.suspects = suspects;
            this.ended = ended;
            this.total = total;
        }
    }

    private static Fetch fetch(ClassLoader loader, boolean followers, String uid, String secUid, Progress progress, int shown)
            throws Exception {
        String base = followers ? "/aweme/v1/user/follower/list/" : "/aweme/v1/user/following/list/";
        String key = followers ? "followers" : "followings";
        Map<String, Person> found = new LinkedHashMap<>();
        Map<String, Suspect> suspects = new LinkedHashMap<>();
        long maxTime = 0, total = 0;
        int offset = 0, barren = 0;
        String token = "";
        boolean ended = false;
        for (int number = 0; number < MAX_PAGES; number++) {
            StringBuilder path = new StringBuilder(base)
                    .append("?user_id=").append(ApiCalls.encode(uid))
                    .append("&sec_user_id=").append(ApiCalls.encode(secUid))
                    .append("&max_time=").append(maxTime)
                    .append("&count=").append(PAGE)
                    .append("&offset=").append(offset)
                    .append("&source_type=1&address_book_access=2");
            if (!token.isEmpty()) path.append("&page_token=").append(ApiCalls.encode(token));
            JSONObject page = ApiCalls.get(loader, path.toString());
            total = Math.max(total, page.optLong("total", 0));
            int before = found.size();
            JSONArray users = page.optJSONArray(key);
            int received = users == null ? 0 : users.length();
            if (number == 0 && received > 0 && users.optJSONObject(0) != null) {
                RuntimeLog.log("unfollowers: " + key + " fields " + users.optJSONObject(0).names());
            }
            for (int index = 0; index < received; index++) {
                JSONObject user = users.optJSONObject(index);
                if (user == null) continue;
                Person person = new Person(user.optString("uid", ""), user.optString("unique_id", ""), user.optString("nickname", ""), 0,
                        user.optString("sec_uid", ""));
                if (person.uid.isEmpty() || "0".equals(person.uid)) continue;
                found.put(person.uid, person);
                if (!followers) continue;
                long videos = number(user, "aweme_count");
                long fans = number(user, "follower_count");
                long follows = number(user, "following_count");
                if (FollowerDiff.suspicious(videos, fans, follows, FollowerDiff.defaultAvatar(avatar(user)), person.handle)) {
                    suspects.put(person.uid, new Suspect(person, videos, fans, follows));
                }
            }
            progress.loaded(shown + found.size());
            if (!ApiCalls.flag(page, "has_more")) {
                ended = true;
                break;
            }
            barren = found.size() == before ? barren + 1 : 0;
            if (barren >= BARREN_PAGES) break;
            maxTime = page.optLong("min_time", maxTime);
            offset = page.optInt("offset", offset + received);
            token = page.optString("next_page_token", "");
            Thread.sleep(PAGE_PAUSE_MS);
        }
        return new Fetch(new ArrayList<>(found.values()), new ArrayList<>(suspects.values()), ended, total);
    }

    private static long number(JSONObject user, String key) {
        return user.has(key) && !user.isNull(key) ? user.optLong(key, FollowerDiff.UNKNOWN) : FollowerDiff.UNKNOWN;
    }

    private static String avatar(JSONObject user) {
        JSONObject thumb = user.optJSONObject("avatar_thumb");
        if (thumb == null) return null;
        String uri = thumb.optString("uri", "");
        if (!uri.isEmpty()) return uri;
        JSONArray urls = thumb.optJSONArray("url_list");
        return urls == null || urls.length() == 0 ? null : urls.optString(0, null);
    }

    private static List<Suspect> readSuspects(File file) {
        List<Suspect> result = new ArrayList<>();
        if (!file.isFile()) return result;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                Suspect suspect = Suspect.decode(line);
                if (suspect != null) result.add(suspect);
            }
        } catch (Exception error) {
            RuntimeLog.log("unfollowers: " + file.getName() + " not read, " + error.getClass().getSimpleName());
        }
        return result;
    }

    private static void writeSuspects(File file, List<Suspect> suspects) throws java.io.IOException {
        File temp = new File(file.getPath() + ".tmp");
        try (Writer out = new OutputStreamWriter(new FileOutputStream(temp, false), StandardCharsets.UTF_8)) {
            for (Suspect suspect : suspects) out.write(suspect.encode() + "\n");
        }
        if (!temp.renameTo(file)) throw new java.io.IOException("not saved: " + file.getName());
    }

    private static final class Snapshot {
        final long time;
        final List<Person> people;

        Snapshot(long time, List<Person> people) {
            this.time = time;
            this.people = people;
        }
    }

    private static File file(File dir, String kind, String uid) {
        return new File(dir, "tpx_" + kind + "_" + uid + ".txt");
    }

    private static Snapshot read(File file) {
        if (!file.isFile()) return null;
        List<Person> people = new ArrayList<>();
        long time = 0;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#")) {
                    time = Long.parseLong(line.substring(1).trim());
                    continue;
                }
                Person person = Person.decode(line);
                if (person != null) people.add(person);
            }
        } catch (Exception error) {
            RuntimeLog.log("unfollowers: " + file.getName() + " not read, " + error.getClass().getSimpleName());
            return null;
        }
        return time > 0 ? new Snapshot(time, people) : null;
    }

    private static List<Person> readList(File file) {
        if (!file.isFile()) return new ArrayList<>();
        List<Person> people = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                Person person = line.startsWith("#") ? null : Person.decode(line);
                if (person != null) people.add(person);
            }
        } catch (Exception error) {
            RuntimeLog.log("unfollowers: " + file.getName() + " not read, " + error.getClass().getSimpleName());
        }
        return people;
    }

    private static void write(File file, long time, List<Person> people) throws java.io.IOException {
        File temp = new File(file.getPath() + ".tmp");
        try (Writer out = new OutputStreamWriter(new FileOutputStream(temp, false), StandardCharsets.UTF_8)) {
            if (time > 0) out.write("#" + time + "\n");
            for (Person person : people) out.write(person.encode() + "\n");
        }
        if (!temp.renameTo(file)) throw new java.io.IOException("not saved: " + file.getName());
    }
}
