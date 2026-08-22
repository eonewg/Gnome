package io.github.eonewg.gnome.core.tag

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/**
 * One recognized `#tag` span inside a literal text run. [value] is the tag
 * identifier without the `#` introducer, per Memos ADR 0001.
 */
data class TagOccurrence(
    val start: Int,
    val endExclusive: Int,
    val value: String,
)

/**
 * The single source of Memos tag semantics in Gnome, implementing the lexical
 * grammar and Markdown-context rules of Memos ADR 0001 "Tag Syntax and
 * Recognition" (2026-08, targeting the post-0.30 normative parser):
 *
 *  - Introducer is ASCII `#` only; a `#` inside a matched emoji (e.g. the
 *    keycap `#️⃣`) is a value unit, not an introducer.
 *  - Tag units are Unicode XID_Continue code points (approximated with
 *    `Character` categories plus Other_ID_Continue), the Memos extensions
 *    `-` `+` `&`, fully-qualified emoji sequences, and contextual apostrophe
 *    joiners (`'` / `’`) between XID units.
 *  - `/` separates non-empty hierarchy segments; default-ignorable code
 *    points and leading combining marks are consumed but omitted.
 *  - No general left boundary (`hello#tag` works); a plain `#` terminates one
 *    tag and may start the next.
 *  - Candidates live entirely inside eligible Markdown text; code, links,
 *    images, autolinks, escapes and character references never produce tags.
 *  - A memo's tag set contains direct values plus their slash ancestors
 *    (`#book/fiction` contributes `book` and `book/fiction`).
 *
 * Known approximations (documented divergences from the ADR's pinned
 * Unicode 17 / Emoji 17 data): XID_Continue is approximated by JVM character
 * categories; the RGI fully-qualified emoji set is approximated by keycap,
 * flag, VS16-qualified, ZWJ-sequence and 0x1F000-plane singleton matching.
 */
object MemosTagParser {

    /** The memo tag set: direct values plus implied slash ancestors. */
    fun extractTags(content: String): Set<String> {
        if (content.none { it == '#' }) return emptySet()
        val parsed = MarkdownParser(GFMFlavourDescriptor())
            .parse(MarkdownElementTypes.MARKDOWN_FILE, content)
        val tags = LinkedHashSet<String>()
        for (occurrence in scanTagOccurrences(content)) {
            // The lexer may split one tag across adjacent leaf tokens (e.g.
            // `#tag's` becomes TEXT + `'` + TEXT), so the whole raw source is
            // scanned at once and only the introducer position is validated
            // against the Markdown AST.
            val node = parsed.findNodeAtPosition(occurrence.start) ?: continue
            if (!isTagEligibleNode(node)) continue
            tags.add(occurrence.value)
            addAncestorTags(occurrence.value, tags)
        }
        return tags
    }

    /**
     * Lexical scan of one literal run, for callers that already hold eligible
     * text (editor highlighting, annotators). No Markdown context check.
     */
    fun scanTagOccurrences(text: String): List<TagOccurrence> {
        val occurrences = mutableListOf<TagOccurrence>()
        var index = 0
        while (index < text.length) {
            val entityEnd = matchCharacterReference(text, index)
            if (entityEnd > index) {
                index = entityEnd
                continue
            }
            val emojiEnd = matchEmoji(text, index)
            if (emojiEnd > index) {
                index = emojiEnd
                continue
            }
            val escapedEnd = matchEscapedPunctuation(text, index)
            if (escapedEnd > index) {
                index = escapedEnd
                continue
            }
            val codePoint = text.codePointAt(index)
            if (codePoint == INTRODUCER) {
                val occurrence = scanCandidate(text, index)
                if (occurrence != null) {
                    occurrences.add(occurrence)
                    index = occurrence.endExclusive
                    continue
                }
            }
            index += Character.charCount(codePoint)
        }
        return occurrences
    }

    /** True when the node's Markdown context may contain tag occurrences. */
    fun isTagEligibleNode(node: ASTNode): Boolean {
        return !hasAncestorOfType(node, excludedNodeTypes)
    }

    // -------------------------------------------------------------------
    // Candidate scanning (ADR 0001 lexical grammar, maximal prefix)
    // -------------------------------------------------------------------

    private fun scanCandidate(text: String, introducerIndex: Int): TagOccurrence? {
        val emitted = StringBuilder()
        var index = introducerIndex + 1
        var segments = 0
        var lastUnitWasXid = false

        while (true) {
            // Ignored prefix before each segment's starter.
            while (index < text.length) {
                val cp = text.codePointAt(index)
                if (isDefaultIgnorable(cp) || (isCombiningMark(cp) && isXidContinue(cp))) {
                    index += Character.charCount(cp)
                } else {
                    break
                }
            }

            // Segment starter: emoji first, then single-code-point units.
            val emojiEnd = matchEmoji(text, index)
            if (emojiEnd > index) {
                emitted.append(text, index, emojiEnd)
                index = emojiEnd
                lastUnitWasXid = false
            } else if (index < text.length) {
                val cp = text.codePointAt(index)
                if (isSegmentStarter(cp)) {
                    emitted.appendCodePoint(cp)
                    index += Character.charCount(cp)
                    lastUnitWasXid = isXidContinue(cp)
                } else if (segments > 0) {
                    return occurrence(introducerIndex, index, emitted)
                } else {
                    return null
                }
            } else {
                return if (segments > 0) occurrence(introducerIndex, index, emitted) else null
            }
            segments++

            // Continuation units of the current segment.
            while (true) {
                if (index >= text.length) {
                    return occurrence(introducerIndex, index, emitted)
                }
                val entityEnd = matchCharacterReference(text, index)
                if (entityEnd > index) {
                    return occurrence(introducerIndex, index, emitted)
                }
                val nextEmojiEnd = matchEmoji(text, index)
                if (nextEmojiEnd > index) {
                    emitted.append(text, index, nextEmojiEnd)
                    index = nextEmojiEnd
                    lastUnitWasXid = false
                    continue
                }
                val cp = text.codePointAt(index)
                when {
                    isDefaultIgnorable(cp) -> index += Character.charCount(cp)
                    isXidContinue(cp) || cp == HYPHEN || cp == PLUS || cp == AMPERSAND -> {
                        emitted.appendCodePoint(cp)
                        index += Character.charCount(cp)
                        lastUnitWasXid = isXidContinue(cp)
                    }
                    (cp == ASCII_APOSTROPHE || cp == RIGHT_SINGLE_QUOTE) -> {
                        if (lastUnitWasXid && nextUnitCanFollowApostrophe(text, index + 1)) {
                            emitted.appendCodePoint(cp)
                            index += 1
                            lastUnitWasXid = false
                        } else {
                            return occurrence(introducerIndex, index, emitted)
                        }
                    }
                    cp == SLASH && nextSegmentHasStarter(text, index + 1) -> {
                        emitted.appendCodePoint(cp)
                        index += 1
                        break // continue with the next segment
                    }
                    else -> return occurrence(introducerIndex, index, emitted)
                }
            }
        }
    }

    private fun occurrence(start: Int, end: Int, emitted: StringBuilder): TagOccurrence =
        TagOccurrence(start, end, emitted.toString())

    /** Emoji-first lookahead: the apostrophe must join XID units on both sides. */
    private fun nextUnitCanFollowApostrophe(text: String, index: Int): Boolean {
        if (index >= text.length) return false
        if (matchCharacterReference(text, index) > index) return false
        if (matchEmoji(text, index) > index) return false
        val cp = text.codePointAt(index)
        return isXidContinue(cp) && !isCombiningMark(cp)
    }

    private fun nextSegmentHasStarter(text: String, index: Int): Boolean {
        var cursor = index
        while (cursor < text.length) {
            val cp = text.codePointAt(cursor)
            if (isDefaultIgnorable(cp) || (isCombiningMark(cp) && isXidContinue(cp))) {
                cursor += Character.charCount(cp)
            } else {
                break
            }
        }
        if (cursor >= text.length) return false
        if (matchEmoji(text, cursor) > cursor) return true
        return isSegmentStarter(text.codePointAt(cursor))
    }

    private fun addAncestorTags(value: String, into: MutableSet<String>) {
        var slash = value.indexOf('/')
        while (slash != -1) {
            into.add(value.substring(0, slash))
            slash = value.indexOf('/', slash + 1)
        }
    }

    // -------------------------------------------------------------------
    // Character classes
    // -------------------------------------------------------------------

    private const val INTRODUCER = 0x23 // #
    private const val HYPHEN = 0x2D
    private const val PLUS = 0x2B
    private const val AMPERSAND = 0x26
    private const val SLASH = 0x2F
    private const val ASCII_APOSTROPHE = 0x27
    private const val RIGHT_SINGLE_QUOTE = 0x2019
    private const val VARIATION_SELECTOR_16 = 0xFE0F
    private const val ZWJ = 0x200D
    private const val KEYCAP_TERMINATOR = 0x20E3

    // Java's Character category constants are Bytes; normalize to Int once.
    private val xidContinueCategories = setOf(
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
        Character.MODIFIER_LETTER, Character.OTHER_LETTER, Character.NON_SPACING_MARK,
        Character.COMBINING_SPACING_MARK, Character.DECIMAL_DIGIT_NUMBER,
        Character.LETTER_NUMBER, Character.CONNECTOR_PUNCTUATION,
    ).map { it.toInt() }.toSet()

    private val combiningMarkCategories = setOf(
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK,
    ).map { it.toInt() }.toSet()

    /** XID_Continue minus combining marks — valid segment starters. */
    private fun isSegmentStarter(cp: Int): Boolean {
        return !isCombiningMark(cp) && (isXidContinue(cp) || cp == HYPHEN || cp == PLUS || cp == AMPERSAND)
    }

    /** XID_Continue approximation: letters, digits, marks, connectors + Other_ID_Continue. */
    private fun isXidContinue(cp: Int): Boolean {
        return Character.getType(cp) in xidContinueCategories ||
            cp == 0x00B7 || cp == 0x0387 || cp in 0x1369..0x1371 || cp == 0x19DA
    }

    private fun isCombiningMark(cp: Int): Boolean {
        return Character.getType(cp) in combiningMarkCategories
    }

    /** Default_Ignorable_Code_Point approximation covering the common invisibles. */
    private fun isDefaultIgnorable(cp: Int): Boolean {
        return cp == 0x00AD || cp == 0x061C || cp == 0x180E ||
            cp in 0x200B..0x200F || cp in 0x202A..0x202E || cp in 0x2060..0x2064 ||
            cp in 0x2066..0x206F || cp in 0xFE00..0xFE0F || cp == 0xFEFF || cp in 0xFFF0..0xFFF8
    }

    // -------------------------------------------------------------------
    // Emoji matching (RGI fully-qualified approximation)
    // -------------------------------------------------------------------

    private fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF

    private fun isSkinTone(cp: Int): Boolean = cp in 0x1F3FB..0x1F3FF

    private fun isEmojiBase(cp: Int): Boolean {
        if (isRegionalIndicator(cp) || isSkinTone(cp)) return false
        return cp in 0x1F000..0x1FAFF || cp in bmpEmojiBases
    }

    /**
     * Returns the end index (exclusive) of a fully-qualified emoji sequence at
     * [start], or -1 / [start] when there is none. Accepted shapes: keycaps,
     * regional-flag pairs, VS16-qualified symbols, skin-tone composites, ZWJ
     * sequences, and singletons on the emoji-default 0x1F000 plane.
     */
    private fun matchEmoji(text: String, start: Int): Int {
        if (start >= text.length) return -1
        val cp = text.codePointAt(start)
        val afterBase = start + Character.charCount(cp)

        // Keycap: [0-9#*] + FE0F + 20E3 (FE0F required — bare keycaps are unqualified).
        if ((cp in 0x30..0x39 || cp == INTRODUCER || cp == 0x2A) && afterBase + 2 <= text.length &&
            text.codePointAt(afterBase) == VARIATION_SELECTOR_16 &&
            text.codePointAt(afterBase + 1) == KEYCAP_TERMINATOR
        ) {
            return afterBase + 2
        }

        // Regional-indicator flag pairs.
        if (isRegionalIndicator(cp) && afterBase < text.length && isRegionalIndicator(text.codePointAt(afterBase))) {
            return afterBase + Character.charCount(text.codePointAt(afterBase))
        }

        if (!isEmojiBase(cp)) return -1

        var index = afterBase
        var qualified = false
        if (index < text.length && text.codePointAt(index) == VARIATION_SELECTOR_16) {
            qualified = true
            index++
        }
        while (index < text.length && isSkinTone(text.codePointAt(index))) {
            index += Character.charCount(text.codePointAt(index))
        }
        var extended = false
        while (index + 1 < text.length && text.codePointAt(index) == ZWJ && isEmojiBase(text.codePointAt(index + 1))) {
            var cursor = index + 1
            cursor += Character.charCount(text.codePointAt(cursor))
            if (cursor < text.length && text.codePointAt(cursor) == VARIATION_SELECTOR_16) cursor++
            while (cursor < text.length && isSkinTone(text.codePointAt(cursor))) {
                cursor += Character.charCount(text.codePointAt(cursor))
            }
            index = cursor
            extended = true
        }
        val planeSingleton = cp in 0x1F000..0x1FAFF
        return if (qualified || extended || planeSingleton) index else -1
    }

    /** Common BMP emoji bases (text-presentation symbols need VS16 to qualify). */
    private val bmpEmojiBases = intArrayOf(
        0x00A9, 0x00AE, 0x203C, 0x2049, 0x2122, 0x2139, 0x2194, 0x2195, 0x2196, 0x2197,
        0x2198, 0x2199, 0x21A9, 0x21AA, 0x2328, 0x23CF, 0x23E9, 0x23EA, 0x23EB, 0x23EC,
        0x23ED, 0x23EE, 0x23EF, 0x23F0, 0x23F1, 0x23F2, 0x23F3, 0x23FA, 0x24C2, 0x25AA,
        0x25AB, 0x25B6, 0x25C0, 0x25FB, 0x25FC, 0x2600, 0x2601, 0x2602, 0x2603, 0x2604,
        0x260E, 0x2611, 0x2614, 0x2615, 0x2618, 0x261D, 0x2620, 0x2622, 0x2623, 0x2626,
        0x262A, 0x262E, 0x262F, 0x2638, 0x2639, 0x263A, 0x2640, 0x2642, 0x2648, 0x2649,
        0x264A, 0x264B, 0x264C, 0x264D, 0x264E, 0x264F, 0x2650, 0x2651, 0x2652, 0x2653,
        0x2660, 0x2663, 0x2665, 0x2666, 0x2668, 0x267B, 0x267F, 0x2693, 0x2699, 0x26A0,
        0x26A1, 0x26AA, 0x26AB, 0x26BD, 0x26BE, 0x26C4, 0x26C5, 0x26CE, 0x26D4, 0x26EA,
        0x26F2, 0x26F3, 0x26F5, 0x26FA, 0x26FD, 0x2702, 0x2705, 0x2708, 0x2709, 0x270A,
        0x270B, 0x270C, 0x270F, 0x2712, 0x2714, 0x2716, 0x271D, 0x2721, 0x2728, 0x2733,
        0x2734, 0x2744, 0x2747, 0x274C, 0x274E, 0x2753, 0x2754, 0x2755, 0x2757, 0x2764,
        0x2795, 0x2796, 0x2797, 0x27A1, 0x27B0, 0x27BF, 0x2934, 0x2935, 0x2B05, 0x2B06,
        0x2B07, 0x2B1B, 0x2B1C, 0x2B50, 0x2B55, 0x3030, 0x303D, 0x3297, 0x3299,
    )

    // -------------------------------------------------------------------
    // Markdown context (GFM literal-source runs)
    // -------------------------------------------------------------------

    private val excludedNodeTypes: Set<IElementType> = setOf(
        MarkdownElementTypes.CODE_BLOCK,
        MarkdownElementTypes.CODE_FENCE,
        MarkdownElementTypes.CODE_SPAN,
        MarkdownTokenTypes.CODE_LINE,
        MarkdownTokenTypes.CODE_FENCE_CONTENT,
        MarkdownElementTypes.LINK_DEFINITION,
        MarkdownElementTypes.INLINE_LINK,
        MarkdownElementTypes.FULL_REFERENCE_LINK,
        MarkdownElementTypes.SHORT_REFERENCE_LINK,
        MarkdownElementTypes.LINK_DESTINATION,
        MarkdownElementTypes.LINK_TEXT,
        MarkdownElementTypes.LINK_LABEL,
        MarkdownElementTypes.LINK_TITLE,
        MarkdownElementTypes.AUTOLINK,
        MarkdownElementTypes.IMAGE,
        MarkdownTokenTypes.URL,
        MarkdownTokenTypes.AUTOLINK,
        MarkdownTokenTypes.EMAIL_AUTOLINK,
        GFMTokenTypes.GFM_AUTOLINK,
    )

    private fun hasAncestorOfType(node: ASTNode, types: Set<IElementType>): Boolean {
        var current: ASTNode? = node
        while (current != null) {
            if (current.type in types) {
                return true
            }
            current = current.parent
        }
        return false
    }

    /** Deepest AST node containing [position], or null when outside the tree. */
    private fun ASTNode.findNodeAtPosition(position: Int): ASTNode? {
        if (position !in startOffset until endOffset) return null
        for (child in children) {
            val childNode = child.findNodeAtPosition(position)
            if (childNode != null) return childNode
        }
        return this
    }

    /** GFM backslash escapes: the escaped punctuation can never introduce a tag. */
    private fun matchEscapedPunctuation(text: String, start: Int): Int {
        if (start + 1 >= text.length || text[start] != '\\') return -1
        val escaped = text[start + 1]
        return if (!escaped.isLetterOrDigit() && escaped.code in 0x21..0x7E) start + 2 else -1
    }

    private val namedCharacterReference = Regex("&[A-Za-z][A-Za-z0-9]{1,31};")
    private val numericCharacterReference = Regex("&#([0-9]{1,7}|[xX][0-9A-Fa-f]{1,6});")

    /** HTML/SGML character references are syntax boundaries, never tag units. */
    private fun matchCharacterReference(text: String, start: Int): Int {
        if (start >= text.length || text[start] != '&') return -1
        val namedMatch = namedCharacterReference.matchAt(text, start)
        if (namedMatch != null) {
            return namedMatch.range.last + 1
        }
        val numericMatch = numericCharacterReference.matchAt(text, start)
        if (numericMatch != null) {
            return numericMatch.range.last + 1
        }
        return -1
    }
}
