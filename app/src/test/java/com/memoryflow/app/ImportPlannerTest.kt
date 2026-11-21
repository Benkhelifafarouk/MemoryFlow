package com.memoryflow.app

import com.memoryflow.app.io.ImportPlanner
import com.memoryflow.app.io.ParsedCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPlannerTest {

    private fun term(front: String, back: String = "") = ParsedCard(front = front, back = back)

    @Test
    fun `existing terms are counted as duplicates`() {
        val existing = setOf(ImportPlanner.key("der Tisch"), ImportPlanner.key("der Stuhl"))
        val plan = ImportPlanner.plan(existing, listOf(term("der Tisch"), term("der Stuhl"), term("das Fenster")))
        assertEquals(3, plan.total)
        assertEquals(2, plan.duplicates)
        assertEquals(1, plan.toImport.size)
        assertEquals("das Fenster", plan.toImport[0].front)
    }

    @Test
    fun `case and extra spaces are ignored when comparing`() {
        val existing = setOf(ImportPlanner.key("der Tisch"))
        val plan = ImportPlanner.plan(
            existing,
            listOf(term("  der  tisch "), term("DER TISCH"), term("der Tisch "))
        )
        assertEquals(3, plan.duplicates)
        assertEquals(0, plan.toImport.size)
    }

    @Test
    fun `duplicates inside the same file are only imported once`() {
        val plan = ImportPlanner.plan(emptySet(), listOf(term("das Haus"), term("das Haus"), term("die Tür")))
        assertEquals(1, plan.duplicates)
        assertEquals(2, plan.toImport.size)
        assertEquals("das Haus", plan.toImport[0].front)
        assertEquals("die Tür", plan.toImport[1].front)
    }

    @Test
    fun `blank terms are counted as invalid, not as duplicates`() {
        val plan = ImportPlanner.plan(emptySet(), listOf(term("   "), term(""), term("Buch")))
        assertEquals(2, plan.invalid)
        assertEquals(1, plan.toImport.size)
        assertEquals(0, plan.duplicates)
    }

    @Test
    fun `arabic terms are compared without corruption`() {
        val existing = setOf(ImportPlanner.key("كتاب"))
        val plan = ImportPlanner.plan(existing, listOf(term("كتاب"), term("مدرسة")))
        assertEquals(1, plan.duplicates)
        assertEquals("مدرسة", plan.toImport[0].front)
    }

    @Test
    fun `already trimmed and collapsed text is preserved exactly`() {
        val plan = ImportPlanner.plan(emptySet(), listOf(term("  die   Straße  ", "the street")))
        assertEquals("die Straße", plan.toImport[0].front)
        assertEquals("the street", plan.toImport[0].back)
    }

    @Test
    fun `five thousand terms are planned quickly`() {
        val existing = (1..500).map { ImportPlanner.key("Wort$it") }.toSet()
        val parsed = (1..5000).map { term("Wort$it") }
        val started = System.currentTimeMillis()
        val plan = ImportPlanner.plan(existing, parsed)
        val elapsed = System.currentTimeMillis() - started
        assertEquals(500, plan.duplicates)
        assertEquals(4500, plan.toImport.size)
        assertTrue("planning 5000 terms took ${elapsed}ms", elapsed < 3000)
    }

    @Test
    fun `empty input plans nothing`() {
        val plan = ImportPlanner.plan(emptySet(), emptyList())
        assertEquals(0, plan.total)
        assertEquals(0, plan.toImport.size)
    }
}
