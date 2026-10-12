package com.rivo.studio.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentReviewTest {
    @Test
    fun parserAcceptsExactlyOneCompleteFileBlock() {
        val result = AgentChangeParser.parse(
            "Plan first.\n\n```rivo-file:src/main.py\nprint('hello')\n```\n",
        )
        assertEquals("src/main.py", result?.path)
        assertEquals("print('hello')", result?.content)
    }

    @Test
    fun parserRefusesAmbiguousOrPartialChangeBlocks() {
        assertNull(AgentChangeParser.parse("No replacement block."))
        assertNull(
            AgentChangeParser.parse(
                "```rivo-file:a.py\nprint(1)\n```\n```rivo-file:b.py\nprint(2)\n```",
            ),
        )
        assertNull(AgentChangeParser.parse("```rivo-file:a.py\nprint(1)"))
    }

    @Test
    fun localDiffLabelsAddedAndRemovedLines() {
        val diff = DiffPreview.render("first\nold\nlast", "first\nnew\nlast")
        assertTrue(diff.contains("- old"))
        assertTrue(diff.contains("+ new"))
    }
}
