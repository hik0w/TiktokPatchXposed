package com.golda.patchertiktok;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class FollowerDiff {
    static final class Person {
        final String uid;
        final String handle;
        final String name;
        final long time;
        final String secUid;

        Person(String uid, String handle, String name, long time) {
            this(uid, handle, name, time, "");
        }

        Person(String uid, String handle, String name, long time, String secUid) {
            this.uid = clean(uid);
            this.handle = clean(handle);
            this.name = clean(name);
            this.time = time;
            this.secUid = clean(secUid);
        }

        String encode() {
            return time + "\t" + uid + "\t" + handle + "\t" + secUid + "\t" + name;
        }

        static Person decode(String line) {
            String[] parts = line.split("\t", 5);
            if (parts.length < 4 || parts[1].isEmpty()) return null;
            try {
                long time = Long.parseLong(parts[0]);
                return parts.length == 5
                        ? new Person(parts[1], parts[2], parts[4], time, parts[3])
                        : new Person(parts[1], parts[2], parts[3], time);
            } catch (NumberFormatException broken) {
                return null;
            }
        }

        Person at(long when) {
            return new Person(uid, handle, name, when, secUid);
        }
    }

    static final class Suspect {
        final Person person;
        final long videos;
        final long followers;
        final long following;

        Suspect(Person person, long videos, long followers, long following) {
            this.person = person;
            this.videos = videos;
            this.followers = followers;
            this.following = following;
        }

        String encode() {
            return videos + "\t" + followers + "\t" + following + "\t" + person.encode();
        }

        static Suspect decode(String line) {
            String[] parts = line.split("\t", 4);
            if (parts.length < 4) return null;
            Person person = Person.decode(parts[3]);
            if (person == null) return null;
            try {
                return new Suspect(person, Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]));
            } catch (NumberFormatException broken) {
                return null;
            }
        }
    }

    static final long UNKNOWN = -1;
    private static final Pattern GENERATED = Pattern.compile("user\\d{6,}");
    private static final long MANY_FOLLOWING = 2_000;

    private FollowerDiff() { }

    static boolean suspicious(long videos, long followers, long following, boolean defaultAvatar, String handle) {
        boolean noVideos = videos == 0;
        boolean noFollowers = followers == 0;
        boolean followsMany = following >= MANY_FOLLOWING && (followers == UNKNOWN || followers * 10 < following);
        boolean generated = handle != null && GENERATED.matcher(handle).matches();
        if (followsMany && (noVideos || defaultAvatar)) return true;
        int signals = (noVideos ? 1 : 0) + (noFollowers ? 1 : 0) + (defaultAvatar ? 1 : 0) + (generated ? 1 : 0);
        return signals >= 3;
    }

    static boolean defaultAvatar(String uri) {
        return uri != null && (uri.contains("1594805258216454") || uri.toLowerCase(java.util.Locale.ROOT).contains("default"));
    }

    static List<Person> missing(Collection<Person> from, Collection<Person> in) {
        Set<String> present = uids(in);
        List<Person> result = new ArrayList<>();
        for (Person person : from) if (!present.contains(person.uid)) result.add(person);
        return result;
    }

    static boolean complete(int fetched, long expected, boolean ended) {
        return ended && (expected <= 0 || fetched * 10L >= expected * 9L);
    }

    static List<Person> newestFirst(List<Person> history, List<Person> added, int limit) {
        List<Person> result = new ArrayList<>(added.size() + history.size());
        for (int index = added.size() - 1; index >= 0; index--) result.add(added.get(index));
        result.addAll(history);
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    static Set<String> uids(Collection<Person> people) {
        Set<String> result = new HashSet<>(people.size() * 2);
        for (Person person : people) result.add(person.uid);
        return result;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }
}
