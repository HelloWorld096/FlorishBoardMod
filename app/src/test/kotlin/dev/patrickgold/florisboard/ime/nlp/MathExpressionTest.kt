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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MathExpressionTest {
    private fun at(text: String) = MathExpression.find(text, text.length)

    private fun result(text: String) = at(text)?.result

    @Test
    fun `plain and spaced expressions`() {
        val cases = mapOf(
            "23+60+17" to "100",
            "23 + 60 + 17" to "100",
            "23 +60+  17" to "100",
            " 23+60 +17" to "100",
            "5*4" to "20",
            "5 × 3" to "15",
            "10 ÷ 4" to "2.5",
            "10/3" to "3.33333333",
            "1/3" to "0.33333333",
            "2^10" to "1024",
            "2 ^ -2" to "0.25",
            "-2^2" to "-4",
            "2(3+4)" to "14",
            "(1+2)(3+4)" to "21",
            "(1+2)*3" to "9",
            "-5+3" to "-2",
            "5 - -3" to "8",
            "1,000+500" to "1500",
            "3x4" to "12",
            "3 x 4" to "12",
            "1.5+2.25" to "3.75",
            "0.1+0.2" to "0.3",
            "200 + 10%" to "220",
            "200 - 10%" to "180",
            "50*10%" to "5",
            "100/8" to "12.5",
            "7−2" to "5",
        )
        for ((text, expected) in cases) {
            assertEquals(expected, result(text), "for `$text`")
        }
    }

    @Test
    fun `takes the expression that ends at the cursor`() {
        val m = assertNotNull(at("total is 23+60+17"))
        assertEquals("23+60+17", m.expression)
        assertEquals(9, m.start)
        assertEquals(17, m.end)
        assertEquals("100", m.result)

        // Trailing spaces stay outside the replaced range.
        val spaced = assertNotNull(at("23+60 + 17  "))
        assertEquals("23+60 + 17", spaced.expression)
        assertEquals(10, spaced.end)

        // The calculation starts after text that isn't part of it.
        assertEquals("20", result("buy 4 apples 5*4"))
        assertEquals("8", result("foo 4+4"))
    }

    @Test
    fun `typed equals sign is part of the replaced range`() {
        val m = assertNotNull(at("23+60+17="))
        assertEquals(0, m.start)
        assertEquals(9, m.end)
        assertEquals("23+60+17", m.expression)
        assertEquals("23+60+17=100", m.withEquals)

        val spaced = assertNotNull(at("23 + 60 ="))
        assertEquals("23 + 60", spaced.expression)
        assertEquals(9, spaced.end)
        assertEquals("23 + 60 = 83", spaced.withEquals)
    }

    @Test
    fun `labels`() {
        assertEquals("23+60+17=100", assertNotNull(at("23+60+17")).label())
        assertEquals("23 + 60 = 83", assertNotNull(at("23 + 60")).label())
        val long = assertNotNull(at("1+2+3+4+5+6+7+8+9+10+11+12+13"))
        assertEquals("91", long.result)
        assertEquals(true, long.label().startsWith("…"))
        assertEquals(true, long.label().endsWith("=91"))
    }

    @Test
    fun `no suggestion when it isn't a calculation`() {
        val none = listOf(
            "", "42", "abc", "50%", "5+", "+", "5 +", "(5+3", "5+3)", "1/0", "0/0",
            "2024-10-15", "15/10/2026", "1-800-555-1234",
            "23 60", "1.2.3+1", "max 5", "0x1F", "a5+5", "hello world", "(5)", "-5",
        )
        for (text in none) {
            assertNull(at(text), "for `$text`")
        }
    }

    @Test
    fun `no suggestion when the cursor is inside something`() {
        assertNull(MathExpression.find("23+60", 4)) // 23+6|0
        assertNull(MathExpression.find("5+5abc", 3)) // 5+5|abc
        assertNull(MathExpression.find("5+5+3", 3)) // 5+5|+3
        assertNotNull(MathExpression.find("5+5 and more", 3)) // 5+5| and more
        assertNotNull(MathExpression.find("5+5)", 3))
    }

    @Test
    fun `large and small results`() {
        assertEquals("1000000", result("1000*1000"))
        assertEquals("1E+20", result("10^20"))
        assertEquals("1.41421356", result("2^0.5"))
        assertEquals("0", result("1-1"))
    }
}
