package io.github.subhaneetshrestha.afnopadd.library

object SheetTitles {

    const val MAX_LENGTH = 80

    /** The trimmed title, or null when it is blank or longer than [MAX_LENGTH]. */
    fun normalize(input: String): String? = input.trim().takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }

    /** "<prefix> N" with N one more than the highest number already used with that prefix. */
    fun nextDefault(prefix: String, existing: Collection<String>): String {
        val pattern = Regex("^" + Regex.escape(prefix) + " (\\d+)$")
        val highest = existing.mapNotNull { pattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        return "$prefix ${highest + 1}"
    }
}
