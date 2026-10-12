package com.rivo.studio.core.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PathPolicyTest {
    @Test
    fun normalizesOnlySafeRelativeProjectPaths() {
        assertEquals("src/main.py", PathPolicy.normalizeRelativePath("src/main.py"))
        assertEquals("", PathPolicy.normalizeRelativePath("", allowEmpty = true))
        listOf("/absolute.py", "../escape.py", "src/../escape.py", "src//main.py", "src\\main.py", "C:/temp.py")
            .forEach { path -> assertThrows(IllegalArgumentException::class.java) { PathPolicy.normalizeRelativePath(path) } }
    }

    @Test
    fun validatesNamesAndArchiveTraversal() {
        assertEquals("new module.py", PathPolicy.validateFileName("new module.py"))
        assertThrows(IllegalArgumentException::class.java) { PathPolicy.validateFileName("../escape.py") }
        assertThrows(IllegalArgumentException::class.java) { PathPolicy.validateFileName("bad/name.py") }
        assertEquals("folder/main.py", PathPolicy.safeArchivePath("folder\\main.py"))
        assertThrows(IllegalArgumentException::class.java) { PathPolicy.safeArchivePath("../outside.txt") }
        assertThrows(IllegalArgumentException::class.java) { PathPolicy.safeArchivePath("/absolute.txt") }
    }

    @Test
    fun permitsEmptyPathOnlyWhenExplicitlyRequested() {
        assertFalse(runCatching { PathPolicy.normalizeRelativePath("") }.isSuccess)
        assertTrue(runCatching { PathPolicy.normalizeRelativePath("", allowEmpty = true) }.isSuccess)
    }
}
