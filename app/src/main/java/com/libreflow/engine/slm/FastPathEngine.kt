package com.libreflow.engine.slm

import com.libreflow.data.local.entity.DictionaryWordEntity

/**
 * Deterministic fast-path cleanup & post-processing.
 * Bypasses the SLM for <= 3-word utterances without fillers (~50ms total latency),
 * applies custom dictionary replacements, formats cardinal/ordinal lists, and guards against
 * LLM moralizing/censorship refusals.
 */
object FastPathEngine {

    private val FILLER_REGEX = Regex(
        """\b(um+|uh+|om+|er+|ah+|hmm+|you know|i mean|sort of|kind of)\b[,\s]*""",
        RegexOption.IGNORE_CASE
    )

    private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")

    private val REFUSAL_PATTERNS = listOf(
        "i cannot",
        "i can't",
        "i am unable",
        "i'm unable",
        "i'm sorry",
        "sorry, i",
        "as an ai",
        "against my",
        "inappropriate",
        "offensive",
        "profanity",
        "cannot assist",
        "can't assist",
        "explicit content"
    )

    private val ENUMERATED_COLON_REGEX = Regex(
        """^([^:\n]{4,80}):\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val LIST_MARKER_REGEX = Regex(
        """(?:^|[.!?]\s+)(first(?:ly)?|second(?:ly)?|third(?:ly)?|fourth(?:ly)?|fifth(?:ly)?|one|two|three|four|five|number\s+(?:one|two|three|four|five|\d+))(?:\s+(?:is|was|are|thing\s+is|point\s+is))?[,:\s]+""",
        RegexOption.IGNORE_CASE
    )

    private val THINK_BLOCK_REGEX = Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE)

    fun shouldBypassSlm(rawText: String, fastPathEnabled: Boolean, styleId: String = ""): Boolean {
        if (!fastPathEnabled) return false
        if (styleId == "concise") return false
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return true
        val words = trimmed.split(Regex("""\s+"""))
        if (words.size > 8) return false
        if (trimmed.contains(":") || LIST_MARKER_REGEX.containsMatchIn(trimmed)) return false
        return !FILLER_REGEX.containsMatchIn(trimmed)
    }

    fun applyDeterministicCleanup(
        rawText: String,
        dictionary: List<DictionaryWordEntity>,
        formatLists: Boolean = false
    ): String {
        var text = rawText.trim()
        if (text.isEmpty()) return ""

        // Strip verbal fillers
        text = FILLER_REGEX.replace(text, " ").trim()

        // Apply custom dictionary replacements (exact case-insensitive word boundary match)
        for (entry in dictionary) {
            if (entry.word.isBlank()) continue
            val target = entry.word.trim()
            val hint = entry.phoneticHint.trim()
            if (hint.isNotEmpty()) {
                val hintPattern = Regex("""\b${Regex.escape(hint)}\b""", RegexOption.IGNORE_CASE)
                text = hintPattern.replace(text, target)
            } else {
                val wordPattern = Regex("""\b${Regex.escape(target)}\b""", RegexOption.IGNORE_CASE)
                text = wordPattern.replace(text, target)
            }
        }

        if (formatLists) {
            text = formatCardinalLists(text)
        }

        text = MULTI_SPACE_REGEX.replace(text, " ").trim()
        if (text.isNotEmpty() && text[0].isLowerCase()) {
            text = text[0].uppercaseChar() + text.substring(1)
        }
        return text
    }

    private val CHATBOT_REPLY_PREFIXES = listOf(
        "i am doing",
        "i'm doing",
        "doing well",
        "thank you, how",
        "sure, here",
        "here is the",
        "of course!",
        "hello! how can i"
    )

    fun sanitizeSlmOutput(slmOutput: String, verbatimFallback: String): String {
        val cleaned = THINK_BLOCK_REGEX.replace(slmOutput, "").trim()
        if (cleaned.isEmpty()) return verbatimFallback
        val lower = cleaned.lowercase()
        if (REFUSAL_PATTERNS.any { lower.contains(it) }) {
            return verbatimFallback
        }
        // Reject chatbot-style replies when the user dictated a question or short statement
        if (CHATBOT_REPLY_PREFIXES.any { lower.startsWith(it) } && !verbatimFallback.lowercase().startsWith(lower.take(10))) {
            return verbatimFallback
        }
        // Reject runaway hallucinated responses (> 2.2x length of verbatim)
        if (verbatimFallback.length >= 12 && cleaned.length > (verbatimFallback.length * 2.2f).toInt()) {
            return verbatimFallback
        }
        // Reject any newly introduced asterisk censoring (e.g. f**k, f***)
        if (!verbatimFallback.contains('*') && cleaned.contains('*')) {
            return verbatimFallback
        }
        return cleaned
    }

    private fun formatCardinalLists(input: String): String {
        val matches = LIST_MARKER_REGEX.findAll(input).toList()
        if (matches.size >= 2) {
            val sb = StringBuilder()
            val intro = input.substring(0, matches.first().range.first).trim().trimEnd('.', ',', ';')
            if (intro.isNotEmpty()) {
                sb.append(intro).append(":\n")
            }
            for (i in matches.indices) {
                val start = matches[i].range.last + 1
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else input.length
                val item = input.substring(start, end).trim().trimEnd('.')
                if (item.isNotEmpty()) {
                    val cap = item[0].uppercaseChar() + item.substring(1)
                    sb.append("• ").append(cap)
                    if (i + 1 < matches.size) sb.append("\n")
                }
            }
            return sb.toString().ifBlank { input }
        }

        // Also handle enumerated colon lists like "I want to do five things today: study, work, go to the gym."
        val colonMatch = ENUMERATED_COLON_REGEX.find(input.trim()) ?: return input
        val intro = colonMatch.groupValues[1].trim()
        val body = colonMatch.groupValues[2].trim().trimEnd('.')
        val rawItems = body.split(Regex("""\s*,\s*(?:and\s+)?|\s+and\s+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (rawItems.size < 2) return input

        return buildString {
            append(intro).append(":\n")
            rawItems.forEachIndexed { idx, item ->
                val cap = item[0].uppercaseChar() + item.substring(1)
                append("• ").append(cap)
                if (idx + 1 < rawItems.size) append("\n")
            }
        }
    }
}
