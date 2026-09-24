package com.fta.senior

import com.fta.senior.core.CleanupPolicy
import org.junit.Assert.*
import org.junit.Test

class CleanupPolicyTest {
    @Test fun `critical packages and users are never eligible`() {
        assertNotNull(CleanupPolicy.permanentProtection(CleanupPolicy.OWN_PACKAGE, 10123, false, 0))
        assertNotNull(CleanupPolicy.permanentProtection(CleanupPolicy.SHIZUKU_PACKAGE, 10234, false, 0))
        assertNotNull(CleanupPolicy.permanentProtection("org.example.app", 10123, true, 0))
        assertNotNull(CleanupPolicy.permanentProtection("org.example.app", 110123, false, 0))
        assertNotNull(CleanupPolicy.permanentProtection("org.example.app", 1000, false, 0))
        assertNotNull(CleanupPolicy.permanentProtection("org.example.app", -1, false, 0))
        assertNull(CleanupPolicy.permanentProtection("org.example.app", 10123, false, 0))
    }

    @Test fun `reject malformed package identifiers`() {
        listOf("", "android", "com.example;reboot", "com.example\n", "../app", "com..app", "com.a-b").forEach {
            assertFalse(it, CleanupPolicy.validPackage(it))
        }
        assertTrue(CleanupPolicy.validPackage("org.example_app.mobile"))
    }

    @Test fun `all explicit protection rules override cleanup`() {
        val name = "org.example.app"
        fun reason(protected: Set<String> = emptySet(), visible: Set<String> = emptySet(), whitelist: Set<String> = emptySet(), importance: Int = 400, important: Boolean = true) =
            CleanupPolicy.skipReason(name, 10123, false, 0, protected, visible, whitelist, importance, important)
        assertNotNull(reason(protected = setOf(name)))
        assertNotNull(reason(visible = setOf(name), important = false))
        assertNotNull(reason(whitelist = setOf(name), important = false))
        assertNotNull(reason(importance = 125))
        assertNotNull(reason(importance = 230))
        assertNull(reason(importance = 400))
        assertNull(reason(importance = 125, important = false))
    }
}
