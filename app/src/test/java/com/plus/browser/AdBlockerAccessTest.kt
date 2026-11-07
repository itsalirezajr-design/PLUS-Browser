package com.plus.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerAccessTest {
    @Test fun lockedProNeverRunsAdvancedFiltering() {
        assertFalse(canApplyAdvancedAdBlock(true, false))
    }

    @Test fun unlockedProRunsOnlyWhenUserEnablesIt() {
        assertTrue(canApplyAdvancedAdBlock(true, true))
        assertFalse(canApplyAdvancedAdBlock(false, true))
    }

    @Test fun disabledFilteringStaysOffEvenWhenProIsLocked() {
        assertFalse(canApplyAdvancedAdBlock(false, false))
    }
}
