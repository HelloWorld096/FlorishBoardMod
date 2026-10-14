package dev.patrickgold.florisboard.ime.nlp.latin

/** QWERTY geometry: which letters sit next to each other. Used to make "fat finger" typos cheap to correct. */
object KeyProximity {
    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val pos = HashMap<Char, Pair<Float, Int>>().apply {
        rows.forEachIndexed { r, row ->
            val shift = when (r) { 1 -> 0.25f; 2 -> 0.75f; else -> 0f }
            row.forEachIndexed { c, ch -> put(ch, (c + shift) to r) }
        }
    }

    fun areNeighbors(a: Char, b: Char): Boolean {
        val pa = pos[a] ?: return false
        val pb = pos[b] ?: return false
        val dx = pa.first - pb.first
        val dy = (pa.second - pb.second).toFloat()
        return dx * dx + dy * dy <= 1.3f * 1.3f && !(a == b)
    }
}
