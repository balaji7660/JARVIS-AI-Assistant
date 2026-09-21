package com.jarvis.assistant

import com.jarvis.assistant.ai.LocalResponseEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LocalResponseEngineTest {

    private lateinit var engine: LocalResponseEngine

    @Before
    fun setUp() {
        engine = LocalResponseEngine()
    }

    @Test
    fun greetingInputs_returnReadyResponse() = runTest {
        assertEquals("Hello boss. I'm ready.", engine.generateResponse("hello"))
        assertEquals("Hello boss. I'm ready.", engine.generateResponse("Hello Jarvis"))
        assertEquals("Hello boss. I'm ready.", engine.generateResponse("hey"))
        assertEquals("Hello boss. I'm ready.", engine.generateResponse("hi jarvis"))
        assertEquals("Hello boss. I'm ready.", engine.generateResponse("  hello, JARVIS!  "))
    }

    @Test
    fun identityQuery_returnsJarvisIntroduction() = runTest {
        assertEquals(
            "I am JARVIS, your personal AI assistant.",
            engine.generateResponse("who are you")
        )
        assertEquals(
            "I am JARVIS, your personal AI assistant.",
            engine.generateResponse("Who are you, Jarvis?")
        )
        assertEquals(
            "I am JARVIS, your personal AI assistant.",
            engine.generateResponse("what is your name")
        )
    }

    @Test
    fun capabilitiesQuery_returnsCapabilitiesDescription() = runTest {
        assertEquals(
            "I can listen to your voice and respond. Soon I'll be able to control supported Android tasks.",
            engine.generateResponse("what can you do")
        )
        assertEquals(
            "I can listen to your voice and respond. Soon I'll be able to control supported Android tasks.",
            engine.generateResponse("What can you do, Jarvis?")
        )
    }

    @Test
    fun unknownCommand_returnsNextMilestoneFallback() = runTest {
        assertEquals(
            "I heard you, boss. AI capabilities will be connected in the next milestone.",
            engine.generateResponse("Open YouTube")
        )
        assertEquals(
            "I heard you, boss. AI capabilities will be connected in the next milestone.",
            engine.generateResponse("Play some music")
        )
        assertEquals(
            "I heard you, boss. AI capabilities will be connected in the next milestone.",
            engine.generateResponse("What is the weather today?")
        )
    }

    @Test
    fun emptyOrWhitespaceInput_returnsClarificationPrompt() = runTest {
        assertEquals(
            "I didn't catch that, boss. How can I assist you?",
            engine.generateResponse("")
        )
        assertEquals(
            "I didn't catch that, boss. How can I assist you?",
            engine.generateResponse("    ")
        )
    }
}
