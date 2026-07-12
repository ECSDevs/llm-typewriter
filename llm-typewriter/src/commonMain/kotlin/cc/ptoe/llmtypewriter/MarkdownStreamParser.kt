package cc.ptoe.llmtypewriter

/**
 * Token classes emitted by [parseStreamingMarkdown]. The parser is **prefix-stable**: the same
 * prefix of the input string always produces the same prefix of the token list, modulo a trailing
 * "open" token (e.g. a `**bold` with no closing `**` yet) which gets re-classified once the close
 * arrives. That's what lets the live renderer paint a partial stream without flicker.
 */
sealed class MdToken {

    /** Plain unstyled text. */
    data class Plain(val text: String) : MdToken()

    /** Emphasis: `*italic*` / `_italic_`. */
    data class Italic(val text: String) : MdToken()

    /** Strong: `**bold**` / `__bold__`. */
    data class Bold(val text: String) : MdToken()

    /** Strong+emphasis: `***both***`. */
    data class BoldItalic(val text: String) : MdToken()

    /** Inline code: `` `snippet` ``. */
    data class InlineCode(val text: String) : MdToken()

    /** Strikethrough: `~~text~~`. */
    data class Strikethrough(val text: String) : MdToken()

    /** Link: `[label](url)`. */
    data class Link(val label: String, val url: String) : MdToken()

    /** Heading: `# ...` / `## ...` etc. Level is 1..6, capped. */
    data class Heading(val level: Int, val text: String) : MdToken()

    /** Newline — emitted as its own token for layout purposes. */
    data object Newline : MdToken()

    /** A fenced code block. `closed` flips to `true` once the trailing ``` is seen — until then
     * the block is rendering progressively (every new char extends [content]).
     */
    data class CodeBlock(
        val language: String?,
        val content: String,
        val closed: Boolean,
    ) : MdToken()

    /**
     * Inline math `$...$`. [content] is the raw TeX fragment between the delimiters. [closed] is
     * `false` while the trailing `$` hasn't arrived yet — the renderer paints [content] as plain
     * text in that case, then re-classifies to math once the close lands.
     */
    data class InlineMath(val content: String, val closed: Boolean) : MdToken()

    /**
     * Display math `$$...$$` — block-level, renders larger and centered with a background.
     * [closed] flips to `true` once the closing `$$` arrives.
     */
    data class DisplayMath(val content: String, val closed: Boolean) : MdToken()

    /**
     * A list item — `-`, `*`, `+`, `1.`, `2.` etc. at line start (optionally indented for nesting).
     *
     * - [ordered] is `true` for `1.` / `1)` markers, `false` for `-` / `*` / `+`.
     * - [number] carries the marker number for ordered lists (e.g. `1`, `2`, `3`); `0` for
     *    unordered. The renderer uses the **first** item's number as the list's start number.
     * - [indent] is the leading-space count divided by 2 (so `  - sub` has `indent = 1`).
     *    Two-space indent unit mirrors the most common LLM output; larger indents still nest one
     *    level per [indent] step.
     * - [inline] is the inline content parsed from the rest of the line after the marker. Single
     *    line only — multi-line list items (continuation lines) are not yet supported; the parser
     *    falls back to treating each non-marker line as its own paragraph.
     *
     * Prefix-stability note: as the line grows, [inline] is re-parsed from scratch (just like a
     * [CodeBlock]'s content grows). Earlier inline tokens stay stable because
     * [parseStreamingMarkdown] is itself prefix-stable; only the trailing "open" token
     * re-classifies once its close arrives.
     */
    data class ListItem(
        val ordered: Boolean,
        val number: Int,
        val indent: Int,
        val inline: List<MdToken>,
    ) : MdToken()
}

/**
 * A best-effort, prefix-stable streaming markdown parser.
 *
 * This isn't a full CommonMark implementation — it's a deliberately small subset chosen so the
 * tokens stay stable as the input grows. The set of patterns covered:
 *
 *   - Headings (`#` … `######`) on their own line
 *   - Fenced code blocks (` ``` `) with optional language
 *   - Inline code (backticks)
 *   - Bold (`**` / `__`)
 *   - Italic (`*` / `_`)
 *   - Bold+italic (`***` / `___`)
 *   - Strikethrough (`~~`)
 *   - Links (`[label](url)`)
 *   - Unordered lists (`-` / `*` / `+` at line start, optionally indented for nesting)
 *   - Ordered lists (`1.` / `1)` at line start, optionally indented for nesting)
 *
 * Open spans at the tail of the input are rendered as plain text until they close — but the
 * tokens before them are stable, so the live renderer doesn't reflow earlier text.
 */
internal fun parseStreamingMarkdown(input: String): List<MdToken> {
    if (input.isEmpty()) return emptyList()
    val out = mutableListOf<MdToken>()
    var i = 0
    val len = input.length

    // Track newline boundaries so heading parsing only fires at the start of a line.
    var atLineStart = true

    while (i < len) {
        val c = input[i]

        // Code blocks have to be checked before everything else — they suppress all other parsing
        // until the close fence is seen.
        if (atLineStart && i + 2 < len && input[i] == '`' && input[i + 1] == '`' && input[i + 2] == '`') {
            i = consumeCodeBlock(input, i, out)
            atLineStart = false
            continue
        }

        if (c == '\n') {
            out += MdToken.Newline
            atLineStart = true
            i++
            continue
        }

        if (atLineStart && c == '#') {
            val headingEnd = consumeHeading(input, i, out)
            if (headingEnd > i) {
                i = headingEnd
                atLineStart = false
                continue
            }
        }

        // List items — must fire before `atLineStart = false` so the leading `*` / `+` / `-`
        // markers win over inline emphasis parsing, and before the inline `Plain` run so the
        // marker isn't swallowed into a plain token. Heading check above already exited, so a
        // `#`-prefixed line never reaches here.
        if (atLineStart) {
            val listEnd = consumeListItemIfAny(input, i, out)
            if (listEnd > i) {
                i = listEnd
                // We consumed through the trailing newline (if any), so the next iteration is at
                // the start of a new line — list items chain without an intervening Newline
                // token, which is what lets `groupIntoParagraphs` treat consecutive items as one
                // list group.
                atLineStart = true
                continue
            }
        }

        atLineStart = false

        if (c == '`') {
            val close = input.indexOf('`', startIndex = i + 1)
            if (close >= 0 && close > i + 1) {
                out += MdToken.InlineCode(input.substring(i + 1, close))
                i = close + 1
                continue
            } else {
                // Unclosed backtick — render the rest as plain so we don't lose anything.
                out += MdToken.Plain(input.substring(i))
                i = len
                continue
            }
        }

        // Math delimiters — `$$...$$` (display) takes priority over `$...$` (inline). The inline
        // form requires no space immediately after the opening `$` or before the closing `$` so
        // that currency like `$5` doesn't false-positive into a math span. Unclosed delimiters
        // degrade to plain text (prefix-stable: the trailing plain run re-classifies to a math
        // token once the close arrives).
        if (c == '$') {
            val isDisplay = i + 1 < len && input[i + 1] == '$'
            if (isDisplay) {
                val close = findDoubleDollar(input, i + 2)
                if (close >= 0) {
                    out += MdToken.DisplayMath(input.substring(i + 2, close), closed = true)
                    i = close + 2
                    continue
                }
                // Unclosed `$$...` — emit `$$` as plain and reparse the rest at top level so we
                // don't swallow everything as a giant unclosed block.
                out += MdToken.Plain("$$")
                i += 2
                continue
            }
            // Inline `$...$`. Reject if next char is a space (e.g. "$ word") — that's almost
            // certainly currency or stray punctuation, not a math opener.
            if (i + 1 < len && !input[i + 1].isWhitespace()) {
                val close = findInlineDollar(input, i + 1)
                if (close > i + 1) {
                    out += MdToken.InlineMath(input.substring(i + 1, close), closed = true)
                    i = close + 1
                    continue
                }
            }
            // No close (or empty content) — emit `$` as plain and continue.
            out += MdToken.Plain("$")
            i++
            continue
        }

        // Order matters: triple-star before double, double before single.
        if (matchesAt(input, i, "***") || matchesAt(input, i, "___")) {
            val delim = input.substring(i, i + 3)
            val close = indexOfDelim(input, i + 3, delim)
            if (close >= 0) {
                out += MdToken.BoldItalic(input.substring(i + 3, close))
                i = close + 3
                continue
            } else {
                out += MdToken.Plain(input.substring(i))
                i = len
                continue
            }
        }
        if (matchesAt(input, i, "**") || matchesAt(input, i, "__")) {
            val delim = input.substring(i, i + 2)
            val close = indexOfDelim(input, i + 2, delim)
            if (close >= 0) {
                out += MdToken.Bold(input.substring(i + 2, close))
                i = close + 2
                continue
            } else {
                out += MdToken.Plain(input.substring(i))
                i = len
                continue
            }
        }
        if (matchesAt(input, i, "~~")) {
            val close = indexOfDelim(input, i + 2, "~~")
            if (close >= 0) {
                out += MdToken.Strikethrough(input.substring(i + 2, close))
                i = close + 2
                continue
            } else {
                out += MdToken.Plain(input.substring(i))
                i = len
                continue
            }
        }
        if (c == '*' || c == '_') {
            val delim = c.toString()
            val close = indexOfDelim(input, i + 1, delim)
            if (close >= 0 && close > i + 1) {
                out += MdToken.Italic(input.substring(i + 1, close))
                i = close + 1
                continue
            } else {
                // Treat as a plain character (it might be a literal '*').
                out += MdToken.Plain(c.toString())
                i++
                continue
            }
        }
        if (c == '[') {
            val linkEnd = consumeLink(input, i, out)
            if (linkEnd > i) {
                i = linkEnd
                continue
            }
        }

        // Plain run — accumulate until we hit a special char or the end.
        val runEnd = nextSpecial(input, i + 1)
        out += MdToken.Plain(input.substring(i, runEnd))
        i = runEnd
    }
    return mergeAdjacentPlain(out)
}

private fun nextSpecial(s: String, from: Int): Int {
    var j = from
    while (j < s.length) {
        val c = s[j]
        if (c == '\n' || c == '`' || c == '*' || c == '_' || c == '~' || c == '[' || c == '#' || c == '$') break
        j++
    }
    return j
}

private fun matchesAt(s: String, at: Int, pattern: String): Boolean {
    if (at + pattern.length > s.length) return false
    for (k in pattern.indices) if (s[at + k] != pattern[k]) return false
    return true
}

private fun indexOfDelim(s: String, from: Int, delim: String): Int {
    var j = from
    val end = s.length - delim.length
    while (j <= end) {
        if (matchesAt(s, j, delim)) return j
        j++
    }
    return -1
}

private fun consumeHeading(input: String, start: Int, out: MutableList<MdToken>): Int {
    var level = 0
    var j = start
    while (j < input.length && input[j] == '#' && level < 6) {
        j++
        level++
    }
    // Require a space after the hashes to qualify as a heading (matches CommonMark).
    if (j >= input.length || input[j] != ' ') return start
    j++ // consume the space
    val lineEnd = input.indexOf('\n', startIndex = j).let { if (it < 0) input.length else it }
    out += MdToken.Heading(level, input.substring(j, lineEnd).trimEnd())
    return lineEnd
}

/**
 * Tries to consume a list item starting at [start] (which is at a line start, possibly with
 * leading spaces). On match, emits a [MdToken.ListItem] to [out] and returns the index past the
 * trailing newline (so the next iteration is at line start again). On no match, returns [start]
 * unchanged so the caller falls through to other parsing.
 *
 * Indentation is counted in units of 2 spaces (`  - sub` → indent 1) — the most common LLM
 * convention. A single leading space is treated as indent 0 (some style guides use one-space
 * indents); 3 spaces round down to 1, 4 to 2, etc. (CommonMark treats tabs and varying spaces
 * specially; we deliberately stay simpler to keep prefix-stability tractable).
 */
private fun consumeListItemIfAny(input: String, start: Int, out: MutableList<MdToken>): Int {
    val len = input.length
    var j = start
    // Skip leading spaces — capped at 8 to avoid pathological inputs.
    var spaces = 0
    while (j < len && input[j] == ' ' && spaces < 8) {
        spaces++
        j++
    }
    val indent = spaces / 2

    val marker = matchListMarker(input, j) ?: return start
    val contentStart = marker.contentStart
    val lineEnd = input.indexOf('\n', startIndex = contentStart).let { if (it < 0) len else it }
    val content = input.substring(contentStart, lineEnd).trimEnd()
    // Recursively parse the inline content of the line. The recursive call sees no `\n`, so it
    // can't itself emit a CodeBlock fence or a Heading (those require `\n` boundaries) — only
    // inline spans (bold / italic / inline code / inline math / links).
    val inline = parseStreamingMarkdown(content)
    out += MdToken.ListItem(
        ordered = marker.ordered,
        number = marker.number,
        indent = indent,
        inline = inline,
    )
    // Consume through the trailing newline so the next iteration starts at the next line — list
    // items chain without an intervening Newline token.
    return if (lineEnd < len) lineEnd + 1 else len
}

private data class ListMarkerMatch(val ordered: Boolean, val number: Int, val contentStart: Int)

/**
 * Matches a list marker at position [at] in [input]. Supports:
 *   - Unordered: `-`, `*`, `+` followed by a space (or end-of-line / end-of-input — empty item).
 *   - Ordered:   `\d+(\.|\))` followed by a space (or end-of-line / end-of-input — empty item).
 *
 * Returns `null` if no marker matches. CommonMark requires the space-after-marker rule, so
 * `1.text` (no space) does NOT parse as a list item — it falls through to plain text. This is
 * important for things like dates (`2024.01.01`) and dotted version numbers.
 */
private fun matchListMarker(input: String, at: Int): ListMarkerMatch? {
    val len = input.length
    if (at >= len) return null
    val c = input[at]

    // Unordered: - * +
    if (c == '-' || c == '*' || c == '+') {
        if (at + 1 < len && input[at + 1] == ' ') {
            return ListMarkerMatch(ordered = false, number = 0, contentStart = at + 2)
        }
        // Allow an empty item at end-of-line / end-of-input: `-` or `-\n`.
        if (at + 1 >= len || input[at + 1] == '\n') {
            return ListMarkerMatch(ordered = false, number = 0, contentStart = minOf(at + 1, len))
        }
        return null
    }

    // Ordered: \d+(\.|\))
    if (c.isDigit()) {
        var j = at
        var num = 0
        var digits = 0
        while (j < len && input[j].isDigit()) {
            num = num * 10 + (input[j] - '0')
            j++
            digits++
            // 9+ digits is almost certainly not a list marker (e.g. a large number) — bail.
            if (digits > 9) return null
        }
        if (j < len && (input[j] == '.' || input[j] == ')')) {
            if (j + 1 < len && input[j + 1] == ' ') {
                return ListMarkerMatch(ordered = true, number = num, contentStart = j + 2)
            }
            // Allow an empty item at end-of-line / end-of-input: `1.` or `1.\n`.
            if (j + 1 >= len || input[j + 1] == '\n') {
                return ListMarkerMatch(ordered = true, number = num, contentStart = minOf(j + 1, len))
            }
        }
    }

    return null
}

private fun consumeCodeBlock(input: String, start: Int, out: MutableList<MdToken>): Int {
    var j = start + 3
    val langEnd = input.indexOf('\n', startIndex = j).let { if (it < 0) input.length else it }
    val language = input.substring(j, langEnd).trim().ifEmpty { null }
    j = if (langEnd < input.length) langEnd + 1 else input.length
    val close = findCodeFenceClose(input, j)
    return if (close < 0) {
        // Open fence — emit unclosed code block containing everything up to the current tail.
        out += MdToken.CodeBlock(language, input.substring(j), closed = false)
        input.length
    } else {
        out += MdToken.CodeBlock(language, input.substring(j, close), closed = true)
        // Skip past the closing ``` and the trailing newline if present.
        var after = close + 3
        if (after < input.length && input[after] == '\n') after++
        after
    }
}

private fun findCodeFenceClose(input: String, from: Int): Int {
    // The closing fence must be at line start.
    var line = from
    while (line < input.length) {
        if (matchesAt(input, line, "```")) return line
        val nl = input.indexOf('\n', startIndex = line)
        if (nl < 0) return -1
        line = nl + 1
    }
    return -1
}

private fun consumeLink(input: String, start: Int, out: MutableList<MdToken>): Int {
    val labelClose = input.indexOf(']', startIndex = start + 1)
    if (labelClose < 0 || labelClose + 1 >= input.length || input[labelClose + 1] != '(') return start
    val urlClose = input.indexOf(')', startIndex = labelClose + 2)
    if (urlClose < 0) return start
    val label = input.substring(start + 1, labelClose)
    val url = input.substring(labelClose + 2, urlClose)
    out += MdToken.Link(label, url)
    return urlClose + 1
}

internal fun mergeAdjacentPlain(input: List<MdToken>): List<MdToken> {
    if (input.size < 2) return input
    val out = mutableListOf<MdToken>()
    for (tok in input) {
        val last = out.lastOrNull()
        if (last is MdToken.Plain && tok is MdToken.Plain) {
            out[out.lastIndex] = MdToken.Plain(last.text + tok.text)
        } else {
            out += tok
        }
    }
    return out
}

/**
 * Finds the closing `$$` for a display-math span that starts at [from] (i.e. just past the
 * opening `$$`). Skips escaped `\$$` sequences. Returns -1 if no close is present.
 */
private fun findDoubleDollar(input: String, from: Int): Int {
    var j = from
    val len = input.length
    while (j + 1 < len) {
        if (input[j] == '\\' && j + 1 < len) { j += 2; continue }
        if (input[j] == '$' && input[j + 1] == '$') return j
        j++
    }
    return -1
}

/**
 * Finds the closing `$` for an inline-math span. Starts at [from] (the first char after the
 * opening `$`). Rejects whitespace-immediately-before candidates — the close must not be
 * preceded by whitespace, and it must not be a `$$` (which is display math). Returns -1 if no
 * valid close is present.
 */
private fun findInlineDollar(input: String, from: Int): Int {
    var j = from
    val len = input.length
    while (j < len) {
        val c = input[j]
        if (c == '\\' && j + 1 < len) { j += 2; continue }
        if (c == '$') {
            // Reject `$$` — that's display math, not an inline close.
            if (j + 1 < len && input[j + 1] == '$') return -1
            // Reject if the previous char is whitespace — `$ word $` is not a math span.
            if (j > 0 && input[j - 1].isWhitespace()) return -1
            return j
        }
        j++
    }
    return -1
}
