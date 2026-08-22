package io.github.eonewg.gnome.ui.page.memoinput

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

private val tagOnlyDraftPattern = Regex("(?:#[^\\s#]*\\s*)+")

/** Pure tag selections are transient editor state, not useful memo drafts. */
internal fun restorableMemoInputDraft(content: String): String {
    val trimmed = content.trim()
    return if (trimmed.isNotEmpty() && tagOnlyDraftPattern.matches(trimmed)) "" else content
}

internal data class HashtagToken(
    val start: Int,
    val end: Int,
    val query: String,
)

/** Finds the hashtag being edited at the current cursor, including mid-text edits. */
internal fun findActiveHashtag(text: TextFieldValue): HashtagToken? {
    if (!text.selection.collapsed) return null

    val cursor = text.selection.start
    if (cursor < 1 || cursor > text.text.length) return null

    var hashPosition = -1
    for (index in cursor - 1 downTo 0) {
        when {
            text.text[index] == '#' -> {
                hashPosition = index
                break
            }
            text.text[index].isWhitespace() -> return null
        }
    }
    if (hashPosition < 0) return null

    val query = text.text.substring(hashPosition + 1, cursor)
    if (query.any { it == '#' || it.isWhitespace() }) return null

    var tokenEnd = cursor
    while (tokenEnd < text.text.length) {
        val character = text.text[tokenEnd]
        if (character == '#' || character.isWhitespace()) break
        tokenEnd++
    }
    return HashtagToken(hashPosition, tokenEnd, query)
}

internal fun hashtagSuggestions(
    token: HashtagToken?,
    tags: List<String>,
): List<String> {
    if (token == null) return emptyList()
    return tags
        .asSequence()
        .filter { it.startsWith(token.query, ignoreCase = true) }
        .distinct()
        .toList()
}

/** Replaces the whole active token and leaves the cursor immediately after one trailing space. */
internal fun replaceActiveHashtag(
    text: TextFieldValue,
    token: HashtagToken,
    tag: String,
): TextFieldValue {
    require(token.start in text.text.indices && token.end in token.start..text.text.length)
    val replacementEnd = if (token.end < text.text.length && text.text[token.end] == ' ') {
        token.end + 1
    } else {
        token.end
    }
    val replacement = "#$tag "
    val updatedText = text.text.replaceRange(token.start, replacementEnd, replacement)
    val cursor = token.start + replacement.length
    return text.copy(text = updatedText, selection = TextRange(cursor))
}
