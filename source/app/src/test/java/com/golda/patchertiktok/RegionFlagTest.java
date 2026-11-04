package com.golda.patchertiktok;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RegionFlagTest {
    @Test public void buildsFlagsFromCountryCodes() {
        assertEquals("🇩🇪", RegionFlag.of("de"));
        assertEquals(null, RegionFlag.of("D1"));
    }

    @Test public void replacesTheFlagAddedBefore() {
        String shown = "5 d " + RegionFlag.of("NP");
        assertEquals("5 d", RegionFlag.without(shown));
        assertEquals("5 d", RegionFlag.without("5 d"));
    }

    @Test public void findsWhereTheFlagStarts() {
        assertEquals(4, RegionFlag.at("5 d " + RegionFlag.of("SA")));
        assertEquals(-1, RegionFlag.at("5 d"));
        assertEquals(-1, RegionFlag.at(RegionFlag.of("SA")));
    }
}
