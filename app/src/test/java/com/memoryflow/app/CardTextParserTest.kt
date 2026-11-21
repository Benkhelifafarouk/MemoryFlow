package com.memoryflow.app

import com.memoryflow.app.io.CardTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTextParserTest {

    @Test
    fun `equals separator produces front and back`() {
        val parsed = CardTextParser.parseLine("die Herausforderung = التحدي")
        assertNotNull(parsed)
        assertEquals("die Herausforderung", parsed!!.front)
        assertEquals("التحدي", parsed.back)
    }

    @Test
    fun `pipe separators become hints`() {
        val parsed = CardTextParser.parseLine("die Herausforderung = التحدي | شيء صعب | موقف يحتاج صبرا")
        assertNotNull(parsed)
        assertEquals("التحدي", parsed!!.back)
        assertEquals("شيء صعب", parsed.hint1)
        assertEquals("موقف يحتاج صبرا", parsed.hint2)
    }

    @Test
    fun `tab separated lines are supported`() {
        val parsed = CardTextParser.parseLine("Haus\tالبيت\tمنزل\tمبنى")
        assertNotNull(parsed)
        assertEquals("Haus", parsed!!.front)
        assertEquals("البيت", parsed.back)
        assertEquals("منزل", parsed.hint1)
    }

    @Test
    fun `a bare term becomes a vocabulary with an empty answer`() {
        val parsed = CardTextParser.parseLine("der Tisch")
        assertNotNull(parsed)
        assertEquals("der Tisch", parsed!!.front)
        assertEquals("", parsed.back)
    }

    @Test
    fun `an equals sign with nothing after it still produces a term`() {
        val parsed = CardTextParser.parseLine("der Stuhl =")
        assertNotNull(parsed)
        assertEquals("der Stuhl", parsed!!.front)
        assertEquals("", parsed.back)
    }

    @Test
    fun `bulk parsing skips comments and blank lines`() {
        val text = """
            # German A2 vocabulary
            die Herausforderung = التحدي

            die Entscheidung = القرار
            // another comment
            die Verantwortung = المسؤولية
        """.trimIndent()
        val cards = CardTextParser.parseBulk(text)
        assertEquals(3, cards.size)
        assertEquals("die Entscheidung", cards[1].front)
    }

    @Test
    fun `one term per line is supported with trimming`() {
        val text = buildString {
            append("der Tisch\n")
            append("der Stuhl\n")
            append("  das Fenster  \n")
            append("\n")
            append("   \n")
            append("die Tür\n")
            append("das Haus\n")
            append("arbeiten\n")
            append("lernen\n")
            append("sprechen\n")
        }
        val cards = CardTextParser.parseBulk(text)
        assertEquals(8, cards.size)
        assertEquals("der Tisch", cards[0].front)
        assertEquals("das Fenster", cards[2].front)
        assertEquals("", cards[0].back)
    }

    @Test
    fun `mixed formats in one file are supported`() {
        val text = "der Tisch\nHaus = البيت\ndie Tür | تلميح أول\n"
        val cards = CardTextParser.parseBulk(text)
        assertEquals(3, cards.size)
        assertEquals("", cards[0].back)
        assertEquals("البيت", cards[1].back)
        assertEquals("تلميح أول", cards[2].back)
    }

    @Test
    fun `german umlauts and eszett survive`() {
        val cards = CardTextParser.parseBulk("die Straße\nÄpfel\nÖl\nÜbung\nä ö ü Ä Ö Ü ß\n")
        assertEquals(5, cards.size)
        assertEquals("die Straße", cards[0].front)
        assertEquals("Äpfel", cards[1].front)
        assertEquals("Übung", cards[3].front)
        assertEquals("ä ö ü Ä Ö Ü ß", cards[4].front)
    }

    @Test
    fun `arabic terms survive byte for byte`() {
        val cards = CardTextParser.parseBulk("كتاب\nمدرسة\nسيارة\nجامعة\n")
        assertEquals(4, cards.size)
        assertEquals("كتاب", cards[0].front)
        assertEquals("جامعة", cards[3].front)
    }

    @Test
    fun `english french and spanish terms survive`() {
        val cards = CardTextParser.parseBulk("the window\nla fenêtre\nla ventana\noù\nseñor\n")
        assertEquals(5, cards.size)
        assertEquals("la fenêtre", cards[1].front)
        assertEquals("où", cards[3].front)
        assertEquals("señor", cards[4].front)
    }

    @Test
    fun `empty text produces no cards`() {
        assertTrue(CardTextParser.parseBulk("").isEmpty())
        assertTrue(CardTextParser.parseCsv("").isEmpty())
    }

    @Test
    fun `a file with only whitespace produces no cards`() {
        assertTrue(CardTextParser.parseBulk("\n\n   \n\t\n").isEmpty())
    }

    @Test
    fun `binary junk is skipped`() {
        val text = "der Tisch\n\u0000\u0001PK\u0003\u0004\n der Stuhl\n"
        val cards = CardTextParser.parseBulk(text)
        assertEquals(2, cards.size)
        assertEquals("der Tisch", cards[0].front)
    }

    @Test
    fun `five thousand lines parse without pathological cost`() {
        val text = (1..5000).joinToString("\n") { "Wort$it" }
        val started = System.currentTimeMillis()
        val cards = CardTextParser.parseBulk(text)
        val elapsed = System.currentTimeMillis() - started
        assertEquals(5000, cards.size)
        assertEquals("Wort5000", cards[4999].front)
        assertTrue("parsing 5000 lines took ${elapsed}ms", elapsed < 3000)
    }

    @Test
    fun `csv handles quoted commas and escaped quotes`() {
        val csv = "front,back\n\"Hello, world\",\"مرحبا\"\n\"He said \"\"hi\"\"\",\"قال مرحبا\"\n"
        val cards = CardTextParser.parseCsv(csv)
        assertEquals(2, cards.size)
        assertEquals("Hello, world", cards[0].front)
        assertEquals("مرحبا", cards[0].back)
        assertEquals("He said \"hi\"", cards[1].front)
    }

    @Test
    fun `csv header order is respected`() {
        val csv = "back,front,hint1\nالتحدي,die Herausforderung,شيء صعب\n"
        val cards = CardTextParser.parseCsv(csv)
        assertEquals(1, cards.size)
        assertEquals("die Herausforderung", cards[0].front)
        assertEquals("التحدي", cards[0].back)
        assertEquals("شيء صعب", cards[0].hint1)
    }

    @Test
    fun `semicolon csv is detected`() {
        val csv = "front;back\nHaus;البيت\n"
        val cards = CardTextParser.parseCsv(csv)
        assertEquals(1, cards.size)
        assertEquals("البيت", cards[0].back)
    }

    @Test
    fun `csv export round trips`() {
        val rows = listOf(listOf("front", "back"), listOf("a, b", "c\"d"))
        val text = CardTextParser.toCsv(rows)
        val parsed = CardTextParser.parseDelimited(text)
        assertEquals(2, parsed.size)
        assertEquals("a, b", parsed[1][0])
        assertEquals("c\"d", parsed[1][1])
    }

    @Test
    fun `slash separated translations become alternative answers`() {
        val parsed = CardTextParser.parseLine("laufen = يمشي / يجري")
        assertNotNull(parsed)
        assertEquals("يمشي", parsed!!.back)
        assertEquals("يجري", parsed.altAnswers)
    }

    @Test
    fun `short slash fragments are not split`() {
        val parsed = CardTextParser.parseLine("der Termin = 24/7")
        assertNotNull(parsed)
        assertEquals("24/7", parsed!!.back)
        assertEquals("", parsed.altAnswers)
    }

    @Test
    fun `labelled pipe parts become explanation instead of hints`() {
        val parsed = CardTextParser.parseLine("die Erfahrung = الخبرة | الجمع: die Erfahrungen")
        assertNotNull(parsed)
        assertEquals("الخبرة", parsed!!.back)
        assertEquals("", parsed.hint1)
        assertTrue(parsed.explanation.contains("Erfahrungen"))
    }

    @Test
    fun `multi line blocks keep plural and translation`() {
        val text = """
            die Erfahrung
            Plural: die Erfahrungen
            العربية:
            الخبرة

            der Tisch = الطاولة
            laufen = يمشي / يجري
        """.trimIndent()
        val cards = CardTextParser.parseBulk(text)
        assertEquals(3, cards.size)
        assertEquals("die Erfahrung", cards[0].front)
        assertEquals("الخبرة", cards[0].back)
        assertTrue(cards[0].explanation.contains("die Erfahrungen"))
        assertEquals("الطاولة", cards[1].back)
        assertEquals("يجري", cards[2].altAnswers)
    }

    @Test
    fun `verb labels are kept in the explanation`() {
        val text = "sich entscheiden\nPräteritum: entschied sich\nPartizip II: hat sich entschieden\nالعربية: يقرر\n"
        val cards = CardTextParser.parseBulk(text)
        assertEquals(1, cards.size)
        assertEquals("يقرر", cards[0].back)
        assertTrue(cards[0].explanation.contains("entschied sich"))
        assertTrue(cards[0].explanation.contains("hat sich entschieden"))
    }

    @Test
    fun `plain term lists are unaffected by the block parser`() {
        val cards = CardTextParser.parseBulk("der Tisch\nder Stuhl\ndas Fenster\n")
        assertEquals(3, cards.size)
        assertTrue(cards.all { it.back == "" })
        assertTrue(cards.all { it.explanation == "" })
    }
}
