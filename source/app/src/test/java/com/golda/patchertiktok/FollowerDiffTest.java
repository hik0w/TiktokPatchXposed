package com.golda.patchertiktok;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.golda.patchertiktok.FollowerDiff.Person;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class FollowerDiffTest {
    private static Person person(String uid) {
        return new Person(uid, "user" + uid, "Name " + uid, 0);
    }

    @Test
    public void missingFindsPeopleWhoLeft() {
        List<Person> before = Arrays.asList(person("1"), person("2"), person("3"));
        List<Person> now = Arrays.asList(person("1"), person("3"), person("4"));
        List<Person> left = FollowerDiff.missing(before, now);
        assertEquals(1, left.size());
        assertEquals("2", left.get(0).uid);
        assertEquals(1, FollowerDiff.missing(now, before).size());
    }

    @Test
    public void incompleteListsAreNotCompared() {
        assertTrue(FollowerDiff.complete(950, 1000, true));
        assertTrue(FollowerDiff.complete(900, 1000, true));
        assertFalse(FollowerDiff.complete(899, 1000, true));
        assertFalse(FollowerDiff.complete(1000, 1000, false));
        assertTrue(FollowerDiff.complete(0, 0, true));
    }

    @Test
    public void personSurvivesEncodingWithTabsInName() {
        Person original = new Person("42", "handle", "Tab\tName\nLine", 1234L);
        Person decoded = Person.decode(original.encode());
        assertEquals("42", decoded.uid);
        assertEquals("handle", decoded.handle);
        assertEquals("Tab Name Line", decoded.name);
        assertEquals(1234L, decoded.time);
        assertNull(Person.decode("broken"));
        assertNull(Person.decode("x\t1\ta\tb"));
    }

    @Test
    public void oldFilesWithoutSecUidStillLoad() {
        Person old = Person.decode("1234\t42\thandle\tSome Name");
        assertEquals("42", old.uid);
        assertEquals("Some Name", old.name);
        assertEquals("", old.secUid);
        Person current = Person.decode(new Person("42", "handle", "", 5L, "MS4wLjAB").encode());
        assertEquals("MS4wLjAB", current.secUid);
        assertEquals("", current.name);
        assertEquals("MS4wLjAB", current.at(9L).secUid);
    }

    @Test
    public void suspectsAreGuessedFromSeveralSigns() {
        assertTrue(FollowerDiff.suspicious(0, 0, 10, false, "user8273645512"));
        assertTrue(FollowerDiff.suspicious(0, 0, 10, true, "realname"));
        assertTrue(FollowerDiff.suspicious(0, 50, 5_000, false, "realname"));
        assertFalse(FollowerDiff.suspicious(0, 0, 10, false, "realname"));
        assertFalse(FollowerDiff.suspicious(12, 300, 5_000, false, "realname"));
        assertFalse(FollowerDiff.suspicious(FollowerDiff.UNKNOWN, FollowerDiff.UNKNOWN, FollowerDiff.UNKNOWN, false, "user8273645512"));
        assertTrue(FollowerDiff.defaultAvatar("tos-maliva-avt-0068/1594805258216454~c5_100x100.jpeg"));
        assertFalse(FollowerDiff.defaultAvatar("tos-maliva-avt-0068/7a1b2c3d"));
        assertFalse(FollowerDiff.defaultAvatar(null));
    }

    @Test
    public void suspectSurvivesEncoding() {
        FollowerDiff.Suspect suspect = new FollowerDiff.Suspect(new Person("7", "bot1", "Bot", 0, "sec"), 0, 1, 4_200);
        FollowerDiff.Suspect copy = FollowerDiff.Suspect.decode(suspect.encode());
        assertEquals("7", copy.person.uid);
        assertEquals("sec", copy.person.secUid);
        assertEquals(4_200, copy.following);
        assertNull(FollowerDiff.Suspect.decode("x\ty\tz\tbroken"));
    }

    @Test
    public void historyKeepsNewestFirstWithinLimit() {
        List<Person> history = Arrays.asList(person("old1"), person("old2"));
        List<Person> added = Arrays.asList(person("a"), person("b"));
        List<Person> result = FollowerDiff.newestFirst(history, added, 3);
        assertEquals(3, result.size());
        assertEquals("b", result.get(0).uid);
        assertEquals("a", result.get(1).uid);
        assertEquals("old1", result.get(2).uid);
        assertEquals(2, FollowerDiff.newestFirst(history, Collections.emptyList(), 10).size());
    }
}
