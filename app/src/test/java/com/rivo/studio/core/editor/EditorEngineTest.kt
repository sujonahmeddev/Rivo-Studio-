package com.rivo.studio.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorEngineTest {
    @Test
    fun autoIndentAddsPythonBlockIndentOnlyForTypedNewline() {
        val result = AutoIndent.apply("def build():", "def build():\n")
        assertEquals("def build():\n    ", result.text)
        assertEquals(4, result.insertedIndent)
        assertEquals("paste\ntext", AutoIndent.apply("", "paste\ntext").text)
    }

    @Test
    fun searchReplaceAndLineNavigationHandleBoundaries() {
        assertEquals(11, SearchReplace.findNext("alpha beta alpha", "alpha", 1))
        assertEquals(-1, SearchReplace.findNext("alpha", "", 0))
        assertEquals("one\nthree\n", SearchReplace.replaceAll("one\ntwo\ntwo\n", "two", "three").first)
        assertEquals(2, SearchReplace.replaceAll("one two two", "two", "two").second)
        assertEquals(0, SearchReplace.lineStart("a\nb", 1))
        assertEquals(2, SearchReplace.lineStart("a\nb", 2))
        assertNull(SearchReplace.lineStart("a\nb", 3))
    }

    @Test
    fun boundedUndoHistoryCoalescesAndCanRedo() {
        val history = EditorHistory(maxEntries = 4, coalesceWindowMs = 650)
        history.record("a", now = 1_000)
        history.record("ab", now = 1_100)
        assertTrue(history.canUndo())
        assertEquals("a", history.undo("abc"))
        assertTrue(history.canRedo())
        assertEquals("abc", history.redo("a"))
        history.reset()
        assertFalse(history.canUndo())
    }
}
