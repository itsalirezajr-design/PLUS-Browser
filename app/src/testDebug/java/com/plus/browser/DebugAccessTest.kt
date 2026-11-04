package com.plus.browser
import org.junit.Assert.*
import org.junit.Test
class DebugAccessTest {
    @Test fun debugCodeAndRejection() { assertTrue(DebugAccess.enabled); assertTrue(DebugAccess.verify("25122569"));assertFalse(DebugAccess.verify("invalid")) }
}
