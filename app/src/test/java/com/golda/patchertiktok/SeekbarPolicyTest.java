package com.golda.patchertiktok;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SeekbarPolicyTest {
    @Test
    public void convertsOnlyHiddenShowTypesToAlwaysVisible() {
        assertEquals(0, SeekbarPolicy.normalizeShowType(3));
        assertEquals(0, SeekbarPolicy.normalizeShowType(4));
        assertEquals(0, SeekbarPolicy.normalizeShowType(0));
        assertEquals(1, SeekbarPolicy.normalizeShowType(1));
        assertEquals(2, SeekbarPolicy.normalizeShowType(2));
        assertEquals(100, SeekbarPolicy.normalizeShowType(100));
    }

    @Test
    public void forcesOnlyRegularVideoModels() {
        assertTrue(SeekbarPolicy.shouldForceShow(false, true, false, false, false));
        assertFalse(SeekbarPolicy.shouldForceShow(false, false, false, false, false));
        assertFalse(SeekbarPolicy.shouldForceShow(false, true, true, false, false));
        assertFalse(SeekbarPolicy.shouldForceShow(false, true, false, true, false));
        assertFalse(SeekbarPolicy.shouldForceShow(false, true, false, false, true));
    }

    @Test
    public void preservesAnExistingPositiveDecision() {
        assertTrue(SeekbarPolicy.shouldForceShow(true, false, true, true, true));
    }
}
