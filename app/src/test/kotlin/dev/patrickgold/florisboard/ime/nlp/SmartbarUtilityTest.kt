/*
 * Copyright 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.nlp

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartbarUtilityTest {
    // --- DateTimeUtility Tests ---

    @Test
    fun `dateTimeUtility out of bounds does not crash and returns null`() {
        assertNull(DateTimeUtility.find("today + 99999999999 days", "today + 99999999999 days".length))
        assertNull(DateTimeUtility.find("today + 99999999999 y", "today + 99999999999 y".length))
        assertNull(DateTimeUtility.find("today - 99999999999 d", "today - 99999999999 d".length))
        assertNull(DateTimeUtility.find("now + 99999999999 hours", "now + 99999999999 hours".length))
    }

    @Test
    fun `dateTimeUtility rejects sub-day units for today`() {
        assertNull(DateTimeUtility.find("today + 30 min", "today + 30 min".length))
        assertNull(DateTimeUtility.find("today + 5 hours", "today + 5 hours".length))
        assertNull(DateTimeUtility.find("today + 2 h", "today + 2 h".length))
        assertNull(DateTimeUtility.find("today - 15 m", "today - 15 m".length))
        assertNull(DateTimeUtility.find("today + 45 minutes", "today + 45 minutes".length))
    }

    @Test
    fun `dateTimeUtility relative dates with today`() {
        val today = LocalDate.now()
        val expectedPlus5 = today.plusDays(5).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val matchPlus5 = DateTimeUtility.find("today + 5 days", "today + 5 days".length)
        assertNotNull(matchPlus5)
        assertEquals(expectedPlus5, matchPlus5.result)

        val expectedPlus2Weeks = today.plusWeeks(2).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val matchPlus2Weeks = DateTimeUtility.find("today + 2 weeks", "today + 2 weeks".length)
        assertNotNull(matchPlus2Weeks)
        assertEquals(expectedPlus2Weeks, matchPlus2Weeks.result)

        val expectedMinus1Month = today.minusMonths(1).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val matchMinus1Month = DateTimeUtility.find("today - 1 month", "today - 1 month".length)
        assertNotNull(matchMinus1Month)
        assertEquals(expectedMinus1Month, matchMinus1Month.result)
    }

    @Test
    fun `dateTimeUtility relative time with now`() {
        val matchMin = DateTimeUtility.find("now + 30 min", "now + 30 min".length)
        assertNotNull(matchMin)
        assertTrue(matchMin.result.matches(Regex("""\d{2}:\d{2}""")))

        val matchHour = DateTimeUtility.find("now - 2 hours", "now - 2 hours".length)
        assertNotNull(matchHour)
        assertTrue(matchHour.result.matches(Regex("""\d{2}:\d{2}""")))
    }

    @Test
    fun `dateTimeUtility days until and timestamp`() {
        val daysMatch = DateTimeUtility.find("days until christmas", "days until christmas".length)
        assertNotNull(daysMatch)
        assertTrue(daysMatch.result.endsWith("days"))

        val tsMatch = DateTimeUtility.find("timestamp", "timestamp".length)
        assertNotNull(tsMatch)
        assertTrue(tsMatch.result.toLongOrNull() != null)
    }

    // --- UnitConverter Tests ---

    @Test
    fun `unitConverter digital storage byte vs bit case sensitivity`() {
        // 8 Bytes to bit = 64 bit
        val matchByteToBit = UnitConverter.find("8 B to bit", "8 B to bit".length)
        assertNotNull(matchByteToBit)
        assertEquals("64 bit", matchByteToBit.result)

        // 64 bits to Byte = 8 B
        val matchBitToByte = UnitConverter.find("64 bit to B", "64 bit to B".length)
        assertNotNull(matchBitToByte)
        assertEquals("8 B", matchBitToByte.result)

        // 8 bits to bit = 8 bit (lowercase 'b' is bit)
        val matchBitToBit = UnitConverter.find("8 b to bit", "8 b to bit".length)
        assertNotNull(matchBitToBit)
        assertEquals("8 bit", matchBitToBit.result)

        // 1 Megabyte to Kilobytes = 1024 KB
        val matchMBtoKB = UnitConverter.find("1 MB to KB", "1 MB to KB".length)
        assertNotNull(matchMBtoKB)
        assertEquals("1024 KB", matchMBtoKB.result)

        // 1 Megabyte to Megabits = 8 Mb
        val matchMBtoMb = UnitConverter.find("1 MB to Mb", "1 MB to Mb".length)
        assertNotNull(matchMBtoMb)
        assertEquals("8 Mb", matchMBtoMb.result)

        // 1024 bytes to KB = 1 KB
        val matchBytesToKB = UnitConverter.find("1024 bytes to KB", "1024 bytes to KB".length)
        assertNotNull(matchBytesToKB)
        assertEquals("1 KB", matchBytesToKB.result)

        // 16 bits to byte = 2 byte
        val matchBitsToByte = UnitConverter.find("16 bits to byte", "16 bits to byte".length)
        assertNotNull(matchBitsToByte)
        assertEquals("2 byte", matchBitsToByte.result)
    }

    @Test
    fun `unitConverter general units`() {
        val kmToM = UnitConverter.find("1 km to m", "1 km to m".length)
        assertNotNull(kmToM)
        assertEquals("1000 m", kmToM.result)

        val kgToG = UnitConverter.find("2.5 kg to g", "2.5 kg to g".length)
        assertNotNull(kgToG)
        assertEquals("2500 g", kgToG.result)

        val cToF = UnitConverter.find("0 c to f", "0 c to f".length)
        assertNotNull(cToF)
        assertEquals("32 °F", cToF.result)
    }

    // --- SnippetManager Tests ---

    @Test
    fun `snippetManager expands cursor macro and tracks offset`() {
        val expanded = SnippetManager.expandWithCursor("console.log({cursor})")
        assertEquals("console.log()", expanded.text)
        assertEquals(12, expanded.cursorOffset)

        val noCursor = SnippetManager.expandWithCursor("hello world")
        assertEquals("hello world", noCursor.text)
        assertNull(noCursor.cursorOffset)
    }

    @Test
    fun `snippetManager expands date and time macros`() {
        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val expandedDate = SnippetManager.expand("Date: {date}")
        assertEquals("Date: $todayStr", expandedDate)

        val expandedUuid = SnippetManager.expand("UUID: {uuid}")
        assertTrue(expandedUuid.startsWith("UUID: "))
        assertEquals(42, expandedUuid.length) // "UUID: " (6) + 36 uuid chars
    }
}
