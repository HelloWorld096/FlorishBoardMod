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

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * A calculable expression found directly before the cursor.
 *
 * All offsets are local to the text that was passed to [MathExpression.find].
 *
 * @property start Start of the expression.
 * @property end End (exclusive) of the range that gets replaced. Includes a trailing `=` the user already typed.
 * @property expression The expression exactly as typed (inner spacing kept), without trailing spaces or `=`.
 * @property result The formatted result, e.g. `100` or `0.3333333333`.
 */
data class MathMatch(
    val start: Int,
    val end: Int,
    val expression: String,
    val result: String,
) {
    /** `23+60+17=100`, or `23 + 60 + 17 = 100` if the user types with spaces. */
    val withEquals: String
        get() = if (expression.contains(' ')) "$expression = $result" else "$expression=$result"

    /** Text for the suggestion chip. Long expressions are shortened from the left so the result is always visible. */
    fun label(maxExpressionLength: Int = 20): String {
        val shown = if (expression.length > maxExpressionLength) {
            "…" + expression.takeLast(maxExpressionLength - 1).trimStart()
        } else {
            expression
        }
        return if (shown.contains(' ')) "$shown = $result" else "$shown=$result"
    }
}

/**
 * Finds and evaluates an arithmetic expression that ends at the cursor.
 *
 * Supports `+ - * / × ÷ x ^ ( ) %`, decimals, thousands separators (`1,000`), unary minus, implicit multiplication
 * (`2(3+4)`) and any amount of spaces around operators and operands. A percentage on the right of `+`/`-` is taken
 * relative to the left side (`200 + 10%` is `220`), everywhere else it is divided by 100.
 */
object MathExpression {
    private const val MAX_SCAN = 96
    private const val MAX_NUMBER_DIGITS = 30
    private val MC = MathContext(20, RoundingMode.HALF_UP)

    // Things like 2024-10-15, 15/10/2026 or 1-800-555-1234 are dates/phone numbers, not calculations.
    private val dateOrPhoneLike = Regex("""\d+([-/]\d+){2,}""")

    private val spanChars = "0123456789.,+-−*/×÷xX^%() =".toSet()

    /**
     * Looks at the text before [cursor] and returns the longest expression that ends there and can be evaluated, or
     * null if there is none. An expression needs at least one operator, so a lone number never triggers.
     */
    fun find(text: String, cursor: Int): MathMatch? {
        if (cursor <= 0 || cursor > text.length) return null
        // Don't offer a result when the cursor sits in the middle of something (23+6|0, 5+5|abc).
        if (cursor < text.length) {
            val next = text[cursor]
            if (next.isLetterOrDigit() || next in "+-−*/×÷^%=.,(") return null
        }

        val limit = maxOf(0, cursor - MAX_SCAN)
        var spanStart = cursor
        while (spanStart > limit && text[spanStart - 1] in spanChars) spanStart--

        var end = cursor
        while (end > spanStart && text[end - 1] == ' ') end--
        var rangeEnd = end
        if (end > spanStart && text[end - 1] == '=') {
            end--
            rangeEnd = end + 1
            while (end > spanStart && text[end - 1] == ' ') end--
        }
        if (end - spanStart < 3) return null

        for (s in spanStart until end) {
            val c = text[s]
            if (!(c.isDigit() || c == '(' || c == '.' || c == '-' || c == '−' || c == '+')) continue
            if (s > 0) {
                val prev = text[s - 1]
                // Never start in the middle of a number or a word, and never right behind an operator or an open
                // parenthesis (that would cut dates like 2024-10-15 or an unfinished "(5+3" down to a tail).
                if (prev.isLetterOrDigit() || prev == '.' || prev == ',' || prev in "+-−*/×÷^%(") continue
            }
            val expr = text.substring(s, end)
            val result = evaluate(expr) ?: continue
            return MathMatch(start = s, end = rangeEnd, expression = expr, result = result)
        }
        return null
    }

    /** Evaluates [expr] and formats the result. Null if it is not a valid expression with at least one operator. */
    fun evaluate(expr: String): String? {
        if (dateOrPhoneLike.matches(expr)) return null
        val tokens = tokenize(expr) ?: return null
        return try {
            val parser = Parser(tokens)
            val value = parser.parse()
            if (parser.operators == 0) null else format(value)
        } catch (_: ArithmeticException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    private class Token(val kind: Char, val op: Char = ' ', val num: BigDecimal? = null) {
        // kind: 'n' number, 'o' operator, '(' and ')'
        fun isOp(vararg ops: Char) = kind == 'o' && op in ops
    }

    private fun tokenize(s: String): List<Token>? {
        val out = ArrayList<Token>()
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            when {
                c == ' ' -> i++
                c.isDigit() || c == '.' -> {
                    val sb = StringBuilder()
                    var j = i
                    while (j < n && s[j].isDigit()) { sb.append(s[j]); j++ }
                    val intDigits = sb.length
                    // Thousands separators: 1,000 / 12,345,678 (exactly three digits after each comma).
                    if (intDigits in 1..3) {
                        while (j + 3 < n && s[j] == ',' &&
                            s[j + 1].isDigit() && s[j + 2].isDigit() && s[j + 3].isDigit() &&
                            (j + 4 >= n || !s[j + 4].isDigit())
                        ) {
                            sb.append(s, j + 1, j + 4)
                            j += 4
                        }
                    }
                    var fracDigits = 0
                    if (j < n && s[j] == '.') {
                        sb.append('.')
                        j++
                        while (j < n && s[j].isDigit()) { sb.append(s[j]); j++; fracDigits++ }
                    }
                    if (intDigits == 0 && fracDigits == 0) return null
                    if (sb.length > MAX_NUMBER_DIGITS) return null
                    out.add(Token('n', num = BigDecimal(sb.toString())))
                    i = j
                }
                c == '(' || c == ')' -> { out.add(Token(c)); i++ }
                c == '+' -> { out.add(Token('o', '+')); i++ }
                c == '-' || c == '−' -> { out.add(Token('o', '-')); i++ }
                c == '*' || c == '×' -> { out.add(Token('o', '*')); i++ }
                c == '/' || c == '÷' -> { out.add(Token('o', '/')); i++ }
                c == '^' -> { out.add(Token('o', '^')); i++ }
                c == '%' -> { out.add(Token('o', '%')); i++ }
                c == 'x' || c == 'X' -> {
                    // Only a multiplication sign between two operands: "3x4", "3 x (2+1)", never inside words/hex.
                    val prev = out.lastOrNull() ?: return null
                    if (!(prev.kind == 'n' || prev.kind == ')')) return null
                    val nextChar = s.drop(i + 1).firstOrNull { it != ' ' } ?: return null
                    if (!(nextChar.isDigit() || nextChar == '.' || nextChar == '(' || nextChar == '-' || nextChar == '−')) return null
                    out.add(Token('o', '*'))
                    i++
                }
                else -> return null
            }
        }
        return out
    }

    private class Value(val n: BigDecimal, val percent: Boolean = false)

    private class Parser(private val t: List<Token>) {
        private var p = 0

        /** Number of binary operators seen; an expression without any is not worth a suggestion. */
        var operators = 0
            private set

        fun parse(): BigDecimal {
            val v = expr()
            if (p != t.size) fail()
            return v.n
        }

        private fun fail(): Nothing = throw IllegalArgumentException("bad expression")

        private fun expr(): Value {
            var left = term()
            while (p < t.size && t[p].isOp('+', '-')) {
                val op = t[p++].op
                val right = term()
                operators++
                // "200 + 10%" means 200 + 10% of 200.
                val r = if (right.percent) left.n.multiply(right.n, MC) else right.n
                left = Value(if (op == '+') left.n.add(r, MC) else left.n.subtract(r, MC))
            }
            return left
        }

        private fun term(): Value {
            var left = unary()
            while (p < t.size) {
                val tok = t[p]
                if (tok.isOp('*', '/')) {
                    p++
                    val right = unary()
                    operators++
                    left = if (tok.op == '*') {
                        Value(left.n.multiply(right.n, MC))
                    } else {
                        if (right.n.signum() == 0) fail()
                        Value(left.n.divide(right.n, MC))
                    }
                } else if (tok.kind == '(') {
                    // Implicit multiplication: 2(3+4), (1+2)(3+4)
                    val right = unary()
                    operators++
                    left = Value(left.n.multiply(right.n, MC))
                } else {
                    break
                }
            }
            return left
        }

        private fun unary(): Value {
            if (p < t.size && t[p].isOp('-')) {
                p++
                val v = unary()
                return Value(v.n.negate(), v.percent)
            }
            if (p < t.size && t[p].isOp('+')) {
                p++
                return unary()
            }
            return power()
        }

        private fun power(): Value {
            val base = postfix()
            if (p < t.size && t[p].isOp('^')) {
                p++
                val exp = unary()
                operators++
                return Value(pow(base.n, exp.n))
            }
            return base
        }

        private fun postfix(): Value {
            var v = primary()
            while (p < t.size && t[p].isOp('%')) {
                p++
                v = Value(v.n.divide(BigDecimal(100), MC), percent = true)
            }
            return v
        }

        private fun primary(): Value {
            if (p >= t.size) fail()
            val tok = t[p]
            if (tok.kind == 'n') {
                p++
                return Value(tok.num!!)
            }
            if (tok.kind == '(') {
                p++
                val inner = expr()
                if (p >= t.size || t[p].kind != ')') fail()
                p++
                return Value(inner.n, inner.percent)
            }
            fail()
        }

        private fun pow(base: BigDecimal, exp: BigDecimal): BigDecimal {
            val e = try { exp.intValueExact() } catch (_: ArithmeticException) { null }
            if (e != null && e in -64..64) {
                if (e >= 0) return base.pow(e, MC)
                if (base.signum() == 0) fail()
                return BigDecimal.ONE.divide(base.pow(-e, MC), MC)
            }
            val d = Math.pow(base.toDouble(), exp.toDouble())
            if (d.isNaN() || d.isInfinite()) fail()
            return BigDecimal(d, MC)
        }
    }

    private fun format(value: BigDecimal): String {
        val rounded = value.round(MathContext(15, RoundingMode.HALF_UP))
        val scaled = if (rounded.scale() > 8) rounded.setScale(8, RoundingMode.HALF_UP) else rounded
        return when {
            rounded.signum() == 0 -> "0"
            // Smaller than the 8 decimals we show: switch to scientific so it doesn't read as 0.
            scaled.signum() == 0 -> rounded.round(MathContext(6)).stripTrailingZeros().toString()
            scaled.abs() >= BigDecimal("1E15") -> scaled.round(MathContext(10)).stripTrailingZeros().toString()
            else -> scaled.stripTrailingZeros().toPlainString()
        }
    }
}
