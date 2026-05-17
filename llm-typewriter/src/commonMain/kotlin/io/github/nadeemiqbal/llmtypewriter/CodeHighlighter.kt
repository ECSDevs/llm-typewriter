package io.github.nadeemiqbal.llmtypewriter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/**
 * Spans a code-block string into ranges by token kind. The highlighter is intentionally tiny —
 * it covers the patterns most LLM coding-assistant snippets share (keywords, strings, numbers,
 * line comments, block comments) across a curated set of common languages. It's a stream-safe
 * regex-free pass: the same prefix always yields the same prefix of spans, so the live renderer
 * doesn't flicker as more code arrives.
 */
internal data class CodeSpan(val start: Int, val end: Int, val kind: CodeSpanKind)

internal enum class CodeSpanKind { Keyword, StringLit, Number, Comment, Plain }

/** Language-token sets we can recognise. */
internal enum class CodeLanguage(val keywords: Set<String>) {
    Kotlin(setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
        "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
        "true", "try", "typealias", "typeof", "val", "var", "when", "while", "abstract", "annotation",
        "by", "catch", "companion", "const", "constructor", "crossinline", "data", "delegate",
        "dynamic", "enum", "external", "final", "finally", "get", "import", "infix", "init",
        "inline", "inner", "internal", "lateinit", "noinline", "open", "operator", "out", "override",
        "private", "protected", "public", "reified", "sealed", "set", "suspend", "tailrec", "vararg",
    )),
    JavaScript(setOf(
        "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete",
        "do", "else", "export", "extends", "false", "finally", "for", "function", "if", "import",
        "in", "instanceof", "let", "new", "null", "of", "return", "super", "switch", "this",
        "throw", "true", "try", "typeof", "var", "void", "while", "with", "yield", "async",
        "await", "static",
    )),
    Python(setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
        "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
        "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
        "try", "while", "with", "yield",
    )),
    Plain(emptySet()),
    ;

    companion object {
        fun parse(name: String?): CodeLanguage = when (name?.lowercase()) {
            "kt", "kotlin", "kts" -> Kotlin
            "js", "javascript", "ts", "typescript", "jsx", "tsx" -> JavaScript
            "py", "python" -> Python
            else -> Plain
        }
    }
}

internal fun highlightCode(source: String, language: CodeLanguage): List<CodeSpan> {
    if (source.isEmpty()) return emptyList()
    val spans = mutableListOf<CodeSpan>()
    var i = 0
    val len = source.length

    while (i < len) {
        val c = source[i]
        // Block comment
        if (c == '/' && i + 1 < len && source[i + 1] == '*') {
            val end = source.indexOf("*/", startIndex = i + 2).let { if (it < 0) len else it + 2 }
            spans += CodeSpan(i, end, CodeSpanKind.Comment)
            i = end
            continue
        }
        // Line comment (// for kt/js, # for python)
        if (c == '/' && i + 1 < len && source[i + 1] == '/') {
            val end = source.indexOf('\n', startIndex = i).let { if (it < 0) len else it }
            spans += CodeSpan(i, end, CodeSpanKind.Comment)
            i = end
            continue
        }
        if (c == '#' && language == CodeLanguage.Python) {
            val end = source.indexOf('\n', startIndex = i).let { if (it < 0) len else it }
            spans += CodeSpan(i, end, CodeSpanKind.Comment)
            i = end
            continue
        }
        // String literal — double or single quote, escape-aware.
        if (c == '"' || c == '\'' || c == '`') {
            val end = closingQuote(source, i + 1, c)
            spans += CodeSpan(i, end, CodeSpanKind.StringLit)
            i = end
            continue
        }
        // Number literal
        if (c.isDigit()) {
            var j = i + 1
            while (j < len && (source[j].isDigit() || source[j] == '.' || source[j] == '_' || source[j] == 'x' ||
                    source[j] == 'L' || source[j] == 'f' || source[j] == 'F' || source[j] == 'b')) j++
            spans += CodeSpan(i, j, CodeSpanKind.Number)
            i = j
            continue
        }
        // Identifier — may be a keyword.
        if (c.isLetter() || c == '_') {
            var j = i + 1
            while (j < len && (source[j].isLetterOrDigit() || source[j] == '_')) j++
            val word = source.substring(i, j)
            if (word in language.keywords) {
                spans += CodeSpan(i, j, CodeSpanKind.Keyword)
            } else {
                spans += CodeSpan(i, j, CodeSpanKind.Plain)
            }
            i = j
            continue
        }
        // Plain single char — collapse into adjacent Plain run by post-processing.
        spans += CodeSpan(i, i + 1, CodeSpanKind.Plain)
        i++
    }

    return collapsePlain(spans)
}

private fun closingQuote(source: String, from: Int, quote: Char): Int {
    var j = from
    while (j < source.length) {
        val c = source[j]
        if (c == '\\' && j + 1 < source.length) { j += 2; continue }
        if (c == quote) return j + 1
        if (c == '\n' && quote != '`') return j // single/double quotes don't span lines in our subset
        j++
    }
    return source.length
}

private fun collapsePlain(spans: List<CodeSpan>): List<CodeSpan> {
    if (spans.size < 2) return spans
    val out = mutableListOf<CodeSpan>()
    for (sp in spans) {
        val last = out.lastOrNull()
        if (last != null && last.kind == CodeSpanKind.Plain && sp.kind == CodeSpanKind.Plain && last.end == sp.start) {
            out[out.lastIndex] = last.copy(end = sp.end)
        } else {
            out += sp
        }
    }
    return out
}

/** Render highlighted spans into an [AnnotatedString] using the given palette. */
internal fun highlightedAnnotated(
    source: String,
    language: CodeLanguage,
    keywordColor: Color,
    stringColor: Color,
    numberColor: Color,
    commentColor: Color,
    textColor: Color,
): AnnotatedString {
    val spans = highlightCode(source, language)
    return buildAnnotated(source, spans, keywordColor, stringColor, numberColor, commentColor, textColor)
}

internal fun buildAnnotated(
    source: String,
    spans: List<CodeSpan>,
    keywordColor: Color,
    stringColor: Color,
    numberColor: Color,
    commentColor: Color,
    textColor: Color,
): AnnotatedString {
    val builder = AnnotatedString.Builder(source)
    for (sp in spans) {
        val style = when (sp.kind) {
            CodeSpanKind.Keyword -> SpanStyle(color = keywordColor)
            CodeSpanKind.StringLit -> SpanStyle(color = stringColor)
            CodeSpanKind.Number -> SpanStyle(color = numberColor)
            CodeSpanKind.Comment -> SpanStyle(color = commentColor)
            CodeSpanKind.Plain -> SpanStyle(color = textColor)
        }
        builder.addStyle(style, sp.start, sp.end)
    }
    return builder.toAnnotatedString()
}
