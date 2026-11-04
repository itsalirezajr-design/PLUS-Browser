package com.plus.browser
import org.junit.Assert.*
import org.junit.Test
class ProAccessTest {
    @Test fun actualDefaultUnlocksWithoutSavedValue() {
        assertTrue(proAccessAllowed(true,false,false))
        assertFalse(proAccessAllowed(false,false,false))
    }
    @Test fun overrideIsDebugOnly() {
        assertTrue(proAccessAllowed(false,true,true))
        assertFalse(proAccessAllowed(false,false,true))
        assertFalse(proAccessAllowed(false,true,false))
    }
}
