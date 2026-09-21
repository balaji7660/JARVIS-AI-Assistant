package com.jarvis.assistant.planner

import com.jarvis.assistant.context.ConversationContext
import com.jarvis.assistant.context.ReferenceResolver
import com.jarvis.assistant.context.ScreenElementContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskPlannerContextTest {

    private lateinit var planner: DefaultTaskPlanner

    @Before
    fun setUp() {
        planner = DefaultTaskPlanner(
            chatApiService = null,
            validator = TaskPlanValidator(),
            referenceResolver = ReferenceResolver()
        )
    }

    @Test
    fun plan_searchWithAppContext_createsSearchWorkflowWithoutUserRepeatingApp() = runBlocking {
        val context = ConversationContext(
            currentApp = "YouTube",
            currentPackage = "com.google.android.youtube"
        )

        val result = planner.plan(
            prompt = "Search Spring Boot",
            currentPackage = "com.google.android.youtube",
            context = context
        )

        assertTrue(result is PlanValidationResult.Valid)
        val plan = (result as PlanValidationResult.Valid).sanitizedPlan
        assertTrue(plan.steps.size >= 2)

        // Step 1: Click search
        assertEquals("click_text", plan.steps[0].action)
        assertEquals("Search", plan.steps[0].arguments["text"])

        // Step 2: Type search query
        assertEquals("type_text", plan.steps[1].action)
        assertEquals("Spring Boot", plan.steps[1].arguments["text"])
    }

    @Test
    fun plan_openFirstResult_resolvesFirstVisibleElement() = runBlocking {
        val context = ConversationContext(
            currentApp = "YouTube",
            detectedElements = listOf(
                ScreenElementContext(label = "Spring Boot in 100 Seconds", type = "result"),
                ScreenElementContext(label = "Spring Boot 3 Full Course", type = "result")
            )
        )

        val result = planner.plan(
            prompt = "Open the first result",
            context = context
        )

        assertTrue(result is PlanValidationResult.Valid)
        val plan = (result as PlanValidationResult.Valid).sanitizedPlan
        assertEquals(1, plan.steps.size)
        assertEquals("click_text", plan.steps[0].action)
        assertEquals("Spring Boot in 100 Seconds", plan.steps[0].arguments["text"])
    }

    @Test
    fun plan_ambiguousReference_rejectsPlanAndAsksForClarification() = runBlocking {
        val context = ConversationContext(
            detectedElements = listOf(
                ScreenElementContext(label = "Like", type = "button"),
                ScreenElementContext(label = "Subscribe", type = "button"),
                ScreenElementContext(label = "Share", type = "button")
            )
        )

        val result = planner.plan(
            prompt = "Click that button",
            context = context
        )

        assertTrue(result is PlanValidationResult.Invalid)
        val invalid = result as PlanValidationResult.Invalid
        assertTrue(invalid.reason.contains("Ambiguous reference"))
        assertTrue(invalid.reason.contains("multiple elements match"))
    }

    @Test
    fun plan_standardCommandWithoutContext_generatesExpectedPlan() = runBlocking {
        val result = planner.plan("Open YouTube and search for Kotlin")

        assertTrue(result is PlanValidationResult.Valid)
        val plan = (result as PlanValidationResult.Valid).sanitizedPlan
        assertEquals(4, plan.steps.size)
        assertEquals("open_app", plan.steps[0].action)
    }
}
