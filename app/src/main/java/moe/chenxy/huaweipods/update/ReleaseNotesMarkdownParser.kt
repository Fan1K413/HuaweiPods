package moe.chenxy.huaweipods.update

import java.net.URI

internal sealed interface ReleaseNotesMarkdownBlock {
    data class Heading(
        val level: Int,
        val content: List<ReleaseNotesMarkdownSpan>,
    ) : ReleaseNotesMarkdownBlock

    data class Paragraph(
        val content: List<ReleaseNotesMarkdownSpan>,
    ) : ReleaseNotesMarkdownBlock

    data class ListItem(
        val marker: String,
        val depth: Int,
        val content: List<ReleaseNotesMarkdownSpan>,
    ) : ReleaseNotesMarkdownBlock

    data class Quote(
        val content: List<ReleaseNotesMarkdownSpan>,
    ) : ReleaseNotesMarkdownBlock

    data class CodeBlock(val code: String) : ReleaseNotesMarkdownBlock

    data object Divider : ReleaseNotesMarkdownBlock
}

internal data class ReleaseNotesMarkdownSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strikethrough: Boolean = false,
    val url: String? = null,
)

private val HEADING_PATTERN = Regex("^(#{1,6})\\s+(.+)$")
private val UNORDERED_LIST_PATTERN = Regex("^(\\s*)[-*+]\\s+(.+)$")
private val ORDERED_LIST_PATTERN = Regex("^(\\s*)(\\d+)[.)]\\s+(.+)$")
private val DIVIDER_PATTERN = Regex("^\\s{0,3}(([-*_])\\s*){3,}$")

internal fun parseReleaseNotesMarkdown(source: String): List<ReleaseNotesMarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
    val blocks = mutableListOf<ReleaseNotesMarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            index++
            continue
        }

        if (line.trimStart().startsWith("```")) {
            val code = mutableListOf<String>()
            index++
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                code += lines[index]
                index++
            }
            if (index < lines.size) index++
            blocks += ReleaseNotesMarkdownBlock.CodeBlock(code.joinToString("\n"))
            continue
        }

        HEADING_PATTERN.matchEntire(line.trim())?.let { match ->
            blocks += ReleaseNotesMarkdownBlock.Heading(
                level = match.groupValues[1].length,
                content = parseReleaseNotesInlineMarkdown(match.groupValues[2]),
            )
            index++
            continue
        }

        if (DIVIDER_PATTERN.matches(line)) {
            blocks += ReleaseNotesMarkdownBlock.Divider
            index++
            continue
        }

        UNORDERED_LIST_PATTERN.matchEntire(line)?.let { match ->
            val rawContent = match.groupValues[2]
            val task = taskListContent(rawContent)
            blocks += ReleaseNotesMarkdownBlock.ListItem(
                marker = task?.first ?: "•",
                depth = indentationDepth(match.groupValues[1]),
                content = parseReleaseNotesInlineMarkdown(task?.second ?: rawContent),
            )
            index++
            continue
        }

        ORDERED_LIST_PATTERN.matchEntire(line)?.let { match ->
            blocks += ReleaseNotesMarkdownBlock.ListItem(
                marker = "${match.groupValues[2]}.",
                depth = indentationDepth(match.groupValues[1]),
                content = parseReleaseNotesInlineMarkdown(match.groupValues[3]),
            )
            index++
            continue
        }

        if (line.trimStart().startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                quoteLines += lines[index].trimStart().removePrefix(">").trimStart()
                index++
            }
            blocks += ReleaseNotesMarkdownBlock.Quote(
                parseReleaseNotesInlineMarkdown(quoteLines.joinToString("\n")),
            )
            continue
        }

        val paragraph = mutableListOf(line.trim())
        index++
        while (index < lines.size && lines[index].isNotBlank() && !startsMarkdownBlock(lines[index])) {
            paragraph += lines[index].trim()
            index++
        }
        blocks += ReleaseNotesMarkdownBlock.Paragraph(
            parseReleaseNotesInlineMarkdown(paragraph.joinToString(" ")),
        )
    }

    return blocks
}

private fun startsMarkdownBlock(line: String): Boolean {
    val trimmed = line.trimStart()
    return trimmed.startsWith("```") ||
        trimmed.startsWith(">") ||
        HEADING_PATTERN.matches(line.trim()) ||
        UNORDERED_LIST_PATTERN.matches(line) ||
        ORDERED_LIST_PATTERN.matches(line) ||
        DIVIDER_PATTERN.matches(line)
}

private fun indentationDepth(indentation: String): Int =
    indentation.replace("\t", "  ").length.div(2).coerceIn(0, 4)

private fun taskListContent(content: String): Pair<String, String>? {
    if (content.length < 3 || content[0] != '[' || content[2] != ']') return null
    val marker = when (content[1].lowercaseChar()) {
        'x' -> "☑"
        ' ' -> "☐"
        else -> return null
    }
    return marker to content.drop(3).trimStart()
}

internal fun parseReleaseNotesInlineMarkdown(source: String): List<ReleaseNotesMarkdownSpan> =
    InlineMarkdownParser(source).parse()

private class InlineMarkdownParser(private val source: String) {
    fun parse(): List<ReleaseNotesMarkdownSpan> = parseRange(0, source.length, InlineStyle())

    private fun parseRange(start: Int, end: Int, style: InlineStyle): List<ReleaseNotesMarkdownSpan> {
        val spans = mutableListOf<ReleaseNotesMarkdownSpan>()
        val plain = StringBuilder()
        var cursor = start

        fun flushPlain() {
            if (plain.isEmpty()) return
            spans.appendSpan(plain.toString(), style)
            plain.clear()
        }

        while (cursor < end) {
            if (source[cursor] == '\\' && cursor + 1 < end) {
                plain.append(source[cursor + 1])
                cursor += 2
                continue
            }

            val codeEnd = if (source[cursor] == '`') source.indexOf('`', cursor + 1) else -1
            if (codeEnd in (cursor + 1)..<end) {
                flushPlain()
                spans.appendSpan(source.substring(cursor + 1, codeEnd), style.copy(code = true))
                cursor = codeEnd + 1
                continue
            }

            val link = parseLink(cursor, end)
            if (link != null) {
                flushPlain()
                val linked = parseRange(link.labelStart, link.labelEnd, style)
                    .map { it.copy(url = link.url) }
                spans.appendMerged(linked)
                cursor = link.end
                continue
            }

            val pairedMarker = pairedMarkerAt(cursor, end)
            if (pairedMarker != null) {
                val closing = findClosingMarker(
                    marker = pairedMarker.marker,
                    fromIndex = cursor + pairedMarker.marker.length,
                    end = end,
                )
                if (closing > cursor + pairedMarker.marker.length && closing < end) {
                    flushPlain()
                    spans.appendMerged(
                        parseRange(
                            cursor + pairedMarker.marker.length,
                            closing,
                            pairedMarker.apply(style),
                        ),
                    )
                    cursor = closing + pairedMarker.marker.length
                    continue
                }
                plain.append(pairedMarker.marker)
                cursor += pairedMarker.marker.length
                continue
            }

            plain.append(source[cursor])
            cursor++
        }

        flushPlain()
        return spans
    }

    private fun parseLink(cursor: Int, end: Int): ParsedLink? {
        val image = source.startsWith("![", cursor)
        val labelStart = cursor + if (image) 2 else 1
        if ((!image && source[cursor] != '[') || labelStart >= end) return null
        val labelEnd = source.indexOf(']', labelStart)
        if (labelEnd !in labelStart..<end || labelEnd + 2 >= end || source[labelEnd + 1] != '(') return null
        val urlEnd = source.indexOf(')', labelEnd + 2)
        if (urlEnd !in (labelEnd + 3)..<end) return null
        val url = source.substring(labelEnd + 2, urlEnd).trim()
        val safeUrl = url.takeIf(::isSafeMarkdownUrl)
        return ParsedLink(
            labelStart = labelStart,
            labelEnd = labelEnd,
            url = safeUrl,
            end = urlEnd + 1,
        )
    }

    private fun findClosingMarker(marker: String, fromIndex: Int, end: Int): Int {
        var candidate = source.indexOf(marker, fromIndex)
        while (candidate in fromIndex..<end) {
            var backslashes = 0
            var index = candidate - 1
            while (index >= 0 && source[index] == '\\') {
                backslashes++
                index--
            }
            if (backslashes % 2 == 0) return candidate
            candidate = source.indexOf(marker, candidate + marker.length)
        }
        return -1
    }

    private fun pairedMarkerAt(cursor: Int, end: Int): PairedMarker? = when {
        cursor + 2 <= end && source.startsWith("**", cursor) -> PairedMarker("**") { it.copy(bold = true) }
        cursor + 2 <= end && source.startsWith("__", cursor) -> PairedMarker("__") { it.copy(bold = true) }
        cursor + 2 <= end && source.startsWith("~~", cursor) -> PairedMarker("~~") { it.copy(strikethrough = true) }
        source[cursor] == '*' -> PairedMarker("*") { it.copy(italic = true) }
        source[cursor] == '_' -> PairedMarker("_") { it.copy(italic = true) }
        else -> null
    }
}

private data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strikethrough: Boolean = false,
)

private data class ParsedLink(
    val labelStart: Int,
    val labelEnd: Int,
    val url: String?,
    val end: Int,
)

private data class PairedMarker(
    val marker: String,
    val apply: (InlineStyle) -> InlineStyle,
)

private fun MutableList<ReleaseNotesMarkdownSpan>.appendSpan(text: String, style: InlineStyle) {
    if (text.isEmpty()) return
    val next = ReleaseNotesMarkdownSpan(
        text = text,
        bold = style.bold,
        italic = style.italic,
        code = style.code,
        strikethrough = style.strikethrough,
    )
    val previous = lastOrNull()
    if (previous != null && previous.copy(text = "") == next.copy(text = "")) {
        this[lastIndex] = previous.copy(text = previous.text + text)
    } else {
        add(next)
    }
}

private fun MutableList<ReleaseNotesMarkdownSpan>.appendMerged(items: List<ReleaseNotesMarkdownSpan>) {
    items.forEach { item ->
        val previous = lastOrNull()
        if (previous != null && previous.copy(text = "") == item.copy(text = "")) {
            this[lastIndex] = previous.copy(text = previous.text + item.text)
        } else {
            add(item)
        }
    }
}

private fun isSafeMarkdownUrl(value: String): Boolean = runCatching {
    URI(value).scheme?.lowercase() in setOf("https", "http")
}.getOrDefault(false)
