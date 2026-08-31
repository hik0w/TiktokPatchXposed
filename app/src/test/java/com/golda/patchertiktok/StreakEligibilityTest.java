package com.golda.patchertiktok;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StreakEligibilityTest {
    @Test
    public void sendsOnlyInsideAtRiskWindowInSeconds() {
        long nowMillis = System.currentTimeMillis();
        long now = nowMillis / 1000L;

        assertTrue(StreakEligibility.isAtRisk(
                true, true, now - 3600L, now - 30L, now + 3600L, nowMillis));
        assertFalse(StreakEligibility.isAtRisk(
                true, true, now - 3600L, now + 60L, now + 3600L, nowMillis));
        assertFalse(StreakEligibility.isAtRisk(
                true, true, now - 3600L, now - 60L, now - 1L, nowMillis));
    }

    @Test
    public void supportsMillisecondTimestamps() {
        long now = System.currentTimeMillis();
        assertTrue(StreakEligibility.isAtRisk(
                true, true, now - 3_600_000L, now - 30_000L, now + 3_600_000L, now));
    }

    @Test
    public void rejectsMissingStreakOrPeer() {
        long nowMillis = System.currentTimeMillis();
        long now = nowMillis / 1000L;

        assertFalse(StreakEligibility.isAtRisk(
                false, true, now - 3600L, now - 30L, now + 3600L, nowMillis));
        assertFalse(StreakEligibility.isAtRisk(
                true, false, now - 3600L, now - 30L, now + 3600L, nowMillis));
    }
}
