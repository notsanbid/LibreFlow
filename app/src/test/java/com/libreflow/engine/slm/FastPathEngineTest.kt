package com.libreflow.engine.slm

import com.libreflow.data.local.entity.DictionaryWordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastPathEngineTest {

    @Test
    fun `fast path bypasses short utterance without fillers`() {
        assertTrue(FastPathEngine.shouldBypassSlm("Sounds good", fastPathEnabled = true))
        assertFalse(FastPathEngine.shouldBypassSlm("Um sounds good", fastPathEnabled = true))
        assertFalse(FastPathEngine.shouldBypassSlm("One two three four", fastPathEnabled = true))
    }

    @Test
    fun `dictionary replaces phonetic hint`() {
        val dict = listOf(DictionaryWordEntity(word = "LibreFlow", phoneticHint = "libre flow"))
        val out = FastPathEngine.applyDeterministicCleanup("um try libre flow today", dict)
        assertEquals("Try LibreFlow today", out)
    }

    @Test
    fun `formats cardinal and ordinal list items`() {
        val input = "Here is my plan. One is doing my homework. Second is running errands."
        val out = FastPathEngine.applyDeterministicCleanup(input, emptyList(), formatLists = true)
        assertTrue(out.contains("Here is my plan:\n"))
        assertTrue(out.contains("• Doing my homework"))
        assertTrue(out.contains("• Running errands"))
    }

    @Test
    fun `refusal or censorship falls back to verbatim`() {
        val raw = "what the fuck"
        assertEquals(raw, FastPathEngine.sanitizeSlmOutput("I cannot fulfill this request.", raw))
        assertEquals(raw, FastPathEngine.sanitizeSlmOutput("what the f**k", raw))
        assertEquals("Hey how are you", FastPathEngine.sanitizeSlmOutput("I'm doing well, thank you!", "Hey how are you"))
    }

    @Test
    fun `formats enumerated colon list into bullets`() {
        val input = "I want to do five things today: study, work, go to the gym."
        val out = FastPathEngine.applyDeterministicCleanup(input, emptyList(), formatLists = true)
        assertTrue(out.contains("I want to do five things today:\n"))
        assertTrue(out.contains("• Study\n"))
        assertTrue(out.contains("• Work\n"))
        assertTrue(out.contains("• Go to the gym"))
    }
}
