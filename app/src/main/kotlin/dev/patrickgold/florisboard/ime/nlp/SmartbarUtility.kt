package dev.patrickgold.florisboard.ime.nlp

import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ColorMatch(
    val start: Int,
    val end: Int,
    val originalText: String,
    val colorInt: Int,
)

data class GeneratorMatch(
    val start: Int,
    val end: Int,
    val originalText: String,
    val generatedText: String,
    val label: String,
)

/**
 * Color preview detection: detects #RGB, #RGBA, #RRGGBB, #RRGGBBAA and rgb()/rgba() colors at the cursor.
 */
object ColorUtility {
    private val HEX_COLOR_REGEX = Regex("""#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$""")
    private val RGB_COLOR_REGEX = Regex(
        """rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})(?:\s*,\s*([0-9.]+))?\s*\)$""",
        RegexOption.IGNORE_CASE
    )

    fun find(text: String, cursor: Int): ColorMatch? {
        if (cursor <= 0 || cursor > text.length) return null
        val windowStart = (cursor - 40).coerceAtLeast(0)
        val candidate = text.substring(windowStart, cursor)

        HEX_COLOR_REGEX.find(candidate)?.let { match ->
            val colorStr = match.value
            val colorInt = parseHexColor(colorStr) ?: return null
            val matchStart = windowStart + match.range.first
            val matchEnd = windowStart + match.range.last + 1
            return ColorMatch(matchStart, matchEnd, colorStr, colorInt)
        }

        RGB_COLOR_REGEX.find(candidate)?.let { match ->
            val r = match.groupValues[1].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val g = match.groupValues[2].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val b = match.groupValues[3].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val aFloat = match.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
            val a = (aFloat * 255).toInt().coerceIn(0, 255)
            val colorInt = (a shl 24) or (r shl 16) or (g shl 8) or b
            val matchStart = windowStart + match.range.first
            val matchEnd = windowStart + match.range.last + 1
            return ColorMatch(matchStart, matchEnd, match.value, colorInt)
        }

        return null
    }

    private fun parseHexColor(hex: String): Int? {
        val raw = hex.removePrefix("#")
        return when (raw.length) {
            3 -> { // #RGB
                val r = raw[0].digitToInt(16) * 17
                val g = raw[1].digitToInt(16) * 17
                val b = raw[2].digitToInt(16) * 17
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            4 -> { // #RGBA
                val r = raw[0].digitToInt(16) * 17
                val g = raw[1].digitToInt(16) * 17
                val b = raw[2].digitToInt(16) * 17
                val a = raw[3].digitToInt(16) * 17
                (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            6 -> { // #RRGGBB
                val rgb = raw.toLongOrNull(16)?.toInt() ?: return null
                (0xFF shl 24) or rgb
            }
            8 -> { // #RRGGBBAA
                val r = raw.substring(0, 2).toIntOrNull(16) ?: return null
                val g = raw.substring(2, 4).toIntOrNull(16) ?: return null
                val b = raw.substring(4, 6).toIntOrNull(16) ?: return null
                val a = raw.substring(6, 8).toIntOrNull(16) ?: return null
                (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            else -> null
        }
    }
}

/**
 * Generator utility: UUID v4, random secure passwords (with uppercase, lowercase, numbers, symbols, avoiding
 * ambiguous characters like 1, l, 0, O), and dynamic date/time insertion.
 */
object GeneratorUtility {
    private val UUID_REGEX = Regex("""!uuid$""", RegexOption.IGNORE_CASE)
    private val PASS_REGEX = Regex("""!(?:pass(?:word)?)(?:\s+(\d{1,2}))?$""", RegexOption.IGNORE_CASE)
    private val DATE_REGEX = Regex("""!date$""", RegexOption.IGNORE_CASE)
    private val TIME_REGEX = Regex("""!time$""", RegexOption.IGNORE_CASE)
    private val DATETIME_REGEX = Regex("""!datetime$""", RegexOption.IGNORE_CASE)
    private val LOREM_REGEX = Regex("""!lorem(?:\s+(\d{1,2}))?$""", RegexOption.IGNORE_CASE)

    private val LOREM_SENTENCES = listOf(
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
        "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.",
        "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.",
        "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.",
        "Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium.",
        "Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit.",
        "Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit.",
        "Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam.",
    )

    private val SECURE_RANDOM = SecureRandom()
    // Avoid ambiguous characters: l, 1, I, 0, O
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#$%^&*"
    private const val ALL_CHARS = LOWER + UPPER + DIGITS + SYMBOLS

    fun find(text: String, cursor: Int): GeneratorMatch? {
        if (cursor <= 0 || cursor > text.length) return null
        val windowStart = (cursor - 30).coerceAtLeast(0)
        val candidate = text.substring(windowStart, cursor)

        UUID_REGEX.find(candidate)?.let { m ->
            val uuid = UUID.randomUUID().toString()
            return GeneratorMatch(windowStart + m.range.first, cursor, m.value, uuid, "UUID: $uuid")
        }

        PASS_REGEX.find(candidate)?.let { m ->
            val requestedLen = m.groupValues.getOrNull(1)?.toIntOrNull() ?: 16
            val len = requestedLen.coerceIn(8, 64)
            val pass = generatePassword(len)
            return GeneratorMatch(windowStart + m.range.first, cursor, m.value, pass, "Password: $pass")
        }

        DATE_REGEX.find(candidate)?.let { m ->
            val d = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            return GeneratorMatch(windowStart + m.range.first, cursor, m.value, d, "Date: $d")
        }

        TIME_REGEX.find(candidate)?.let { m ->
            val t = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            return GeneratorMatch(windowStart + m.range.first, cursor, m.value, t, "Time: $t")
        }

        DATETIME_REGEX.find(candidate)?.let { m ->
            val dt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
            return GeneratorMatch(windowStart + m.range.first, cursor, m.value, dt, "Date & Time: $dt")
        }

        LOREM_REGEX.find(candidate)?.let { m ->
            val requestedCount = m.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            val count = requestedCount.coerceIn(1, 8)
            val loremText = LOREM_SENTENCES.take(count).joinToString(" ")
            val label = if (count == 1) "Lorem Ipsum (1 sentence)" else "Lorem Ipsum ($count sentences)"
            return GeneratorMatch(windowStart + m.range.first, cursor, m.value, loremText, label)
        }

        return null
    }

    fun generatePassword(length: Int): String {
        val chars = ArrayList<Char>(length)
        chars.add(LOWER[SECURE_RANDOM.nextInt(LOWER.length)])
        chars.add(UPPER[SECURE_RANDOM.nextInt(UPPER.length)])
        chars.add(DIGITS[SECURE_RANDOM.nextInt(DIGITS.length)])
        chars.add(SYMBOLS[SECURE_RANDOM.nextInt(SYMBOLS.length)])
        for (i in 4 until length) {
            chars.add(ALL_CHARS[SECURE_RANDOM.nextInt(ALL_CHARS.length)])
        }
        chars.shuffle(SECURE_RANDOM)
        return chars.joinToString("")
    }
}

/**
 * Smart date and time arithmetic:
 *  - "today + 5 days", "today - 2 weeks", "now + 3 hours", "now - 30 min"
 *  - "days until 2026-12-25", "days until Christmas", "days until new year"
 *  - "unix:now", "timestamp"
 */
object DateTimeUtility {
    private val RELATIVE_DATE_REGEX = Regex(
        """(today|now)\s*([+-])\s*(\d+)\s*(days?|d|weeks?|w|months?|mo|years?|y|hours?|h|hrs?|minutes?|min|mins?|m)\s*(?:=\s*)?$""",
        RegexOption.IGNORE_CASE
    )
    private val DAYS_UNTIL_REGEX = Regex(
        """days\s+(?:until|to)\s+(\d{4}-\d{2}-\d{2}|christmas|new\s*year|halloween)\s*(?:=\s*)?$""",
        RegexOption.IGNORE_CASE
    )
    private val TIMESTAMP_REGEX = Regex("""(?:unix:now|timestamp)\s*(?:=\s*)?$""", RegexOption.IGNORE_CASE)

    fun find(text: String, cursor: Int): MathMatch? {
        if (cursor <= 0 || cursor > text.length) return null
        val windowStart = (cursor - 50).coerceAtLeast(0)
        val candidate = text.substring(windowStart, cursor)

        RELATIVE_DATE_REGEX.find(candidate)?.let { m ->
            val base = m.groupValues[1].lowercase()
            val sign = m.groupValues[2]
            val amount = m.groupValues[3].toLongOrNull() ?: return null
            if (amount < 0 || amount > 100_000) return null
            val unit = m.groupValues[4].lowercase()
            val factor = if (sign == "-") -amount else amount

            val resultStr = try {
                if (base == "now" && unit in listOf("hours", "h", "hr", "hrs", "minutes", "min", "mins", "m")) {
                    val now = LocalDateTime.now()
                    val target = when (unit) {
                        "hours", "h", "hr", "hrs" -> now.plusHours(factor)
                        else -> now.plusMinutes(factor)
                    }
                    target.format(DateTimeFormatter.ofPattern("HH:mm"))
                } else {
                    if (base == "today" && unit in listOf("hours", "h", "hr", "hrs", "minutes", "min", "mins", "m")) {
                        return null
                    }
                    val today = LocalDate.now()
                    val target = when (unit) {
                        "days", "day", "d" -> today.plusDays(factor)
                        "weeks", "week", "w" -> today.plusWeeks(factor)
                        "months", "month", "mo" -> today.plusMonths(factor)
                        "years", "year", "y" -> today.plusYears(factor)
                        else -> return null
                    }
                    target.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                }
            } catch (_: Exception) {
                return null
            }

            val start = windowStart + m.range.first
            val end = cursor
            val expr = m.value.replace(Regex("""\s*=\s*$"""), "").trim()
            return MathMatch(start, end, expr, resultStr)
        }

        DAYS_UNTIL_REGEX.find(candidate)?.let { m ->
            return try {
                val targetStr = m.groupValues[1].lowercase()
                val today = LocalDate.now()
                val targetDate: LocalDate = when {
                    targetStr.startsWith("20") -> {
                        try { LocalDate.parse(targetStr) } catch (_: Exception) { return null }
                    }
                    targetStr == "christmas" -> {
                        var x = LocalDate.of(today.year, 12, 25)
                        if (x.isBefore(today)) x = x.plusYears(1)
                        x
                    }
                    targetStr.contains("new") -> {
                        LocalDate.of(today.year + 1, 1, 1)
                    }
                    targetStr == "halloween" -> {
                        var x = LocalDate.of(today.year, 10, 31)
                        if (x.isBefore(today)) x = x.plusYears(1)
                        x
                    }
                    else -> return null
                }
                val days = ChronoUnit.DAYS.between(today, targetDate)
                val result = "$days days"
                val start = windowStart + m.range.first
                val expr = m.value.replace(Regex("""\s*=\s*$"""), "").trim()
                MathMatch(start, cursor, expr, result)
            } catch (_: Exception) {
                null
            }
        }

        TIMESTAMP_REGEX.find(candidate)?.let { m ->
            val ts = (System.currentTimeMillis() / 1000).toString()
            val start = windowStart + m.range.first
            val expr = m.value.replace(Regex("""\s*=\s*$"""), "").trim()
            return MathMatch(start, cursor, expr, ts)
        }

        return null
    }
}

/**
 * Unit conversion utility: converts between length, mass, digital storage, time, volume, speed, area, pressure,
 * energy, and temperature units.
 * Syntax: "<number> <from_unit> (to|in) <to_unit>"
 */
object UnitConverter {
    private val CONVERT_REGEX = Regex(
        """(-?\d+(?:\.\d+)?)\s*([a-zA-Z/°²³]+)\s+(?:to|in)\s+([a-zA-Z/°²³]+)\s*(?:=\s*)?$""",
        RegexOption.IGNORE_CASE
    )

    // Length (base: meter)
    private val LENGTH = mapOf(
        "nm" to 1e-9, "um" to 1e-6, "µm" to 1e-6,
        "mm" to 0.001, "millimeter" to 0.001, "millimeters" to 0.001,
        "cm" to 0.01, "centimeter" to 0.01, "centimeters" to 0.01,
        "m" to 1.0, "meter" to 1.0, "meters" to 1.0,
        "km" to 1000.0, "kilometer" to 1000.0, "kilometers" to 1000.0,
        "in" to 0.0254, "inch" to 0.0254, "inches" to 0.0254,
        "ft" to 0.3048, "foot" to 0.3048, "feet" to 0.3048,
        "yd" to 0.9144, "yard" to 0.9144, "yards" to 0.9144,
        "mi" to 1609.344, "mile" to 1609.344, "miles" to 1609.344,
        "nmi" to 1852.0,
    )

    // Mass (base: gram)
    private val MASS = mapOf(
        "mg" to 0.001, "milligram" to 0.001, "milligrams" to 0.001,
        "g" to 1.0, "gram" to 1.0, "grams" to 1.0,
        "kg" to 1000.0, "kilogram" to 1000.0, "kilograms" to 1000.0,
        "t" to 1e6, "tonne" to 1e6, "tonnes" to 1e6,
        "oz" to 28.349523125, "ounce" to 28.349523125, "ounces" to 28.349523125,
        "lb" to 453.59237, "lbs" to 453.59237, "pound" to 453.59237, "pounds" to 453.59237,
        "ton" to 907184.74, "tons" to 907184.74,
    )

    // Digital Storage (base: byte)
    // Note: Digital storage units are case-sensitive to distinguish bits ('b') from bytes ('B').
    private val STORAGE_CASE_SENSITIVE = mapOf(
        "b" to 0.125,
        "kb" to 128.0, "kbit" to 128.0,
        "mb" to 131072.0, "mbit" to 131072.0, "Mb" to 131072.0,
        "gb" to 134217728.0, "gbit" to 134217728.0, "Gb" to 134217728.0,
        "tb" to 137438953472.0, "tbit" to 137438953472.0, "Tb" to 137438953472.0,
        "B" to 1.0,
        "KB" to 1024.0, "kB" to 1024.0, "KiB" to 1024.0, "kib" to 1024.0,
        "MB" to 1024.0 * 1024.0, "MiB" to 1024.0 * 1024.0, "mib" to 1024.0 * 1024.0,
        "GB" to 1024.0 * 1024.0 * 1024.0, "GiB" to 1024.0 * 1024.0 * 1024.0, "gib" to 1024.0 * 1024.0 * 1024.0,
        "TB" to 1024.0 * 1024.0 * 1024.0 * 1024.0, "TiB" to 1024.0 * 1024.0 * 1024.0 * 1024.0, "tib" to 1024.0 * 1024.0 * 1024.0 * 1024.0,
    )

    private val STORAGE_CASE_INSENSITIVE = mapOf(
        "bit" to 0.125, "bits" to 0.125,
        "byte" to 1.0, "bytes" to 1.0,
        "kilobit" to 128.0, "kilobits" to 128.0,
        "megabit" to 131072.0, "megabits" to 131072.0,
        "gigabit" to 134217728.0, "gigabits" to 134217728.0,
        "terabit" to 137438953472.0, "terabits" to 137438953472.0,
        "kilobyte" to 1024.0, "kilobytes" to 1024.0,
        "megabyte" to 1024.0 * 1024.0, "megabytes" to 1024.0 * 1024.0,
        "gigabyte" to 1024.0 * 1024.0 * 1024.0, "gigabytes" to 1024.0 * 1024.0 * 1024.0,
        "terabyte" to 1024.0 * 1024.0 * 1024.0 * 1024.0, "terabytes" to 1024.0 * 1024.0 * 1024.0 * 1024.0,
    )

    private fun getStorageFactor(unit: String): Double? {
        val trimmed = unit.trim()
        STORAGE_CASE_SENSITIVE[trimmed]?.let { return it }
        return STORAGE_CASE_INSENSITIVE[trimmed.lowercase()]
    }

    // Time (base: second)
    private val TIME = mapOf(
        "ms" to 0.001, "millisecond" to 0.001, "milliseconds" to 0.001,
        "s" to 1.0, "sec" to 1.0, "second" to 1.0, "seconds" to 1.0,
        "min" to 60.0, "minute" to 60.0, "minutes" to 60.0,
        "h" to 3600.0, "hr" to 3600.0, "hour" to 3600.0, "hours" to 3600.0,
        "d" to 86400.0, "day" to 86400.0, "days" to 86400.0,
        "w" to 604800.0, "wk" to 604800.0, "week" to 604800.0, "weeks" to 604800.0,
        "mo" to 2629746.0, "month" to 2629746.0, "months" to 2629746.0,
        "y" to 31556952.0, "yr" to 31556952.0, "year" to 31556952.0, "years" to 31556952.0,
    )

    // Volume (base: liter)
    private val VOLUME = mapOf(
        "ml" to 0.001, "milliliter" to 0.001, "milliliters" to 0.001,
        "l" to 1.0, "liter" to 1.0, "liters" to 1.0,
        "tsp" to 0.00492892, "teaspoon" to 0.00492892, "teaspoons" to 0.00492892,
        "tbsp" to 0.0147868, "tablespoon" to 0.0147868, "tablespoons" to 0.0147868,
        "floz" to 0.0295735, "fl oz" to 0.0295735,
        "cup" to 0.236588, "cups" to 0.236588,
        "pt" to 0.473176, "pint" to 0.473176, "pints" to 0.473176,
        "qt" to 0.946353, "quart" to 0.946353, "quarts" to 0.946353,
        "gal" to 3.78541, "gallon" to 3.78541, "gallons" to 3.78541,
    )

    // Speed (base: m/s)
    private val SPEED = mapOf(
        "m/s" to 1.0, "mps" to 1.0,
        "km/h" to (1.0 / 3.6), "kph" to (1.0 / 3.6), "kmh" to (1.0 / 3.6),
        "mph" to 0.44704,
        "knot" to 0.514444, "knots" to 0.514444,
    )

    // Area (base: sq m)
    private val AREA = mapOf(
        "sqm" to 1.0, "m2" to 1.0, "sq m" to 1.0,
        "sqkm" to 1e6, "km2" to 1e6, "sq km" to 1e6,
        "sqft" to 0.092903, "ft2" to 0.092903, "sq ft" to 0.092903,
        "sqyd" to 0.836127, "yd2" to 0.836127, "sq yd" to 0.836127,
        "sqmi" to 2589988.11, "mi2" to 2589988.11, "sq mi" to 2589988.11,
        "acre" to 4046.8564224, "acres" to 4046.8564224,
        "ha" to 10000.0, "hectare" to 10000.0, "hectares" to 10000.0,
    )

    // Pressure (base: Pa)
    private val PRESSURE = mapOf(
        "pa" to 1.0, "kpa" to 1000.0, "bar" to 100000.0, "psi" to 6894.757, "atm" to 101325.0,
    )

    // Energy / Power
    private val ENERGY = mapOf(
        "j" to 1.0, "joule" to 1.0, "joules" to 1.0,
        "kj" to 1000.0,
        "cal" to 4.184, "calorie" to 4.184, "calories" to 4.184,
        "kcal" to 4184.0,
        "wh" to 3600.0, "kwh" to 3.6e6,
        "w" to 1.0, "watt" to 1.0, "watts" to 1.0,
        "kw" to 1000.0, "hp" to 745.7,
    )

    private val TEMPERATURE_UNITS = setOf("c", "celsius", "°c", "f", "fahrenheit", "°f", "k", "kelvin")

    fun find(text: String, cursor: Int): MathMatch? {
        if (cursor <= 0 || cursor > text.length) return null
        val windowStart = (cursor - 60).coerceAtLeast(0)
        val candidate = text.substring(windowStart, cursor)

        val match = CONVERT_REGEX.find(candidate) ?: return null
        val valStr = match.groupValues[1]
        val fromRaw = match.groupValues[2].trim()
        val toRaw = match.groupValues[3].trim()
        val value = valStr.toDoubleOrNull() ?: return null

        val resultValue = convert(value, fromRaw, toRaw) ?: return null
        val formattedResult = formatResult(resultValue, toRaw)
        val start = windowStart + match.range.first
        val expr = match.value.replace(Regex("""\s*=\s*$"""), "").trim()
        return MathMatch(start, cursor, expr, formattedResult)
    }

    private fun convert(value: Double, fromRaw: String, toRaw: String): Double? {
        val fromStorage = getStorageFactor(fromRaw)
        val toStorage = getStorageFactor(toRaw)
        if (fromStorage != null && toStorage != null) {
            val base = value * fromStorage
            return base / toStorage
        }

        val from = fromRaw.lowercase()
        val to = toRaw.lowercase()

        if (from in TEMPERATURE_UNITS && to in TEMPERATURE_UNITS) {
            val fromNorm = normTemp(from)
            val toNorm = normTemp(to)
            val c = when (fromNorm) {
                "c" -> value
                "f" -> (value - 32.0) * 5.0 / 9.0
                "k" -> value - 273.15
                else -> return null
            }
            return when (toNorm) {
                "c" -> c
                "f" -> c * 9.0 / 5.0 + 32.0
                "k" -> c + 273.15
                else -> null
            }
        }

        val tables = listOf(LENGTH, MASS, TIME, VOLUME, SPEED, AREA, PRESSURE, ENERGY)
        for (table in tables) {
            val fromFactor = table[from]
            val toFactor = table[to]
            if (fromFactor != null && toFactor != null) {
                val base = value * fromFactor
                return base / toFactor
            }
        }
        return null
    }

    private fun normTemp(unit: String): String = when (unit) {
        "c", "celsius", "°c" -> "c"
        "f", "fahrenheit", "°f" -> "f"
        "k", "kelvin" -> "k"
        else -> unit
    }

    private fun formatResult(value: Double, toUnit: String): String {
        val rounded = BigDecimal(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
        val str = rounded.toPlainString()
        val dispUnit = when (toUnit.lowercase()) {
            "c", "°c" -> "°C"
            "f", "°f" -> "°F"
            "k" -> "K"
            else -> toUnit
        }
        return "$str $dispUnit"
    }
}

/**
 * Main coordinator for all smartbar live utility evaluations.
 */
object SmartbarUtility {
    fun findCandidate(content: EditorContent): SuggestionCandidate? {
        return try {
            findCandidateInternal(content)
        } catch (_: Throwable) {
            null
        }
    }

    private fun findCandidateInternal(content: EditorContent): SuggestionCandidate? {
        val sel = content.localSelection
        if (sel.isNotValid) return null

        if (sel.isSelectionMode) {
            val selected = content.selectedText
            if (selected.isNotEmpty()) {
                var words = 0
                var lines = 1
                var charsNoSpaces = 0
                var inWord = false
                val chars = selected.length
                for (i in 0 until chars) {
                    val ch = selected[i]
                    if (ch == '\n') {
                        lines++
                    }
                    if (ch.isWhitespace()) {
                        inWord = false
                    } else {
                        charsNoSpaces++
                        if (!inWord) {
                            words++
                            inWord = true
                        }
                    }
                }
                return SelectionCountCandidate(
                    words = words,
                    chars = chars,
                    lines = lines,
                    charsNoSpaces = charsNoSpaces,
                )
            }
            return null
        }

        if (!sel.isCursorMode) return null
        val text = content.text
        val cursor = sel.start.coerceIn(0, text.length)
        val offset = content.offset

        // 1. Color Preview
        ColorUtility.find(text, cursor)?.let { match ->
            return ColorSuggestionCandidate(
                colorInt = match.colorInt,
                originalText = match.originalText,
                replaceRange = EditorRange(match.start + offset, match.end + offset),
                text = match.originalText,
                secondaryText = "Color preview",
            )
        }

        // 2. Unit Converter
        UnitConverter.find(text, cursor)?.let { match ->
            return MathSuggestionCandidate(
                match = match,
                originalText = text.substring(match.start, match.end),
                replaceRange = EditorRange(match.start + offset, match.end + offset),
            )
        }

        // 3. Date & Time Arithmetic
        DateTimeUtility.find(text, cursor)?.let { match ->
            return MathSuggestionCandidate(
                match = match,
                originalText = text.substring(match.start, match.end),
                replaceRange = EditorRange(match.start + offset, match.end + offset),
            )
        }

        // 4. Generators (!uuid, !pass, !date, !time)
        GeneratorUtility.find(text, cursor)?.let { match ->
            return RangedWordCandidate(
                text = match.generatedText,
                originalText = match.originalText,
                replaceRange = EditorRange(match.start + offset, match.end + offset),
                secondaryText = match.label,
            )
        }

        // 5. Snippets
        SnippetManager.findCandidate(text, cursor, offset)?.let {
            return it
        }

        return null
    }
}
