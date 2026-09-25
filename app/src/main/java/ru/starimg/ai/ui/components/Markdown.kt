package ru.starimg.ai.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ru.starimg.ai.ui.theme.LocalStarPalette
import ru.starimg.ai.ui.theme.Mono
import ru.starimg.ai.ui.theme.StarDim

private val Keyword = Color(0xFFC792EA)
private val StringLit = Color(0xFFC3E88D)
private val NumberLit = Color(0xFFF78C6C)
private val CommentLit = Color(0xFF676E95)

private val keywords = setOf(
    "fun", "val", "var", "class", "object", "interface", "return", "if", "else", "when", "for", "while",
    "import", "package", "suspend", "override", "private", "public", "internal", "data", "sealed",
    "def", "lambda", "yield", "async", "await", "const", "let", "function", "new", "this", "null",
    "true", "false", "in", "is", "as", "from", "with", "try", "catch", "throw", "struct", "enum"
)

@Composable
fun RichText(text: String, color: Color, onQuote: ((String) -> Unit)? = null) {
    val lines = text.replace("\r", "").split("\n")
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(StarDim.xs)) {
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.trim().startsWith("```")) {
                val language = line.trim().removePrefix("```").lowercase()
                val end = (index + 1 until lines.size).firstOrNull { lines[it].trim().startsWith("```") } ?: lines.size
                val content = lines.subList(index + 1, end).joinToString("\n")
                if (language in setOf("chart", "graph", "график")) ChartBlock(content, color) else CodeBlock(content, language)
                index = if (end < lines.size) end + 1 else lines.size
                continue
            }
            if (line.trim() == "$$" || line.trim() == "\\[") {
                val marker = line.trim()
                val end = (index + 1 until lines.size).firstOrNull { lines[it].trim() == marker } ?: index
                if (end > index) {
                    FormulaBlock(lines.subList(index + 1, end).joinToString(" "))
                    index = end + 1
                    continue
                }
            }
            if (index + 1 < lines.size && line.contains("|") && lines[index + 1].matches(separator)) {
                val tableEnd = (index + 2 until lines.size).firstOrNull { !lines[it].contains("|") } ?: lines.size
                MarkdownTable(lines.subList(index, tableEnd), color)
                index = tableEnd
                continue
            }
            when {
                line.isBlank() -> Spacer(Modifier.height(StarDim.xs))
                line.matches(Regex("#{1,3}\\s+.*")) -> Text(line.substringAfter(" "), style = if (line.startsWith("# ")) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
                line.matches(Regex("\\s*[-*]\\s+.*")) -> MarkerRow("•", line.trim().drop(2), color)
                line.matches(Regex("\\s*\\d+[.)]\\s+.*")) -> MarkerRow(line.trim().substringBefore(" "), line.trim().substringAfter(" "), color)
                line.startsWith(">") -> QuoteRow(line.removePrefix(">").trim(), color)
                line.trim() == "---" || line.trim() == "***" -> HorizontalDivider(color = color.copy(alpha = .2f), modifier = Modifier.padding(vertical = StarDim.xs))
                else -> Text(inlineMarkdown(line, color), color = color, style = MaterialTheme.typography.bodyLarge)
            }
            index++
        }
        }
    }
}

@Composable
private fun MarkerRow(marker: String, body: String, color: Color) {
    Row(Modifier.padding(start = StarDim.xs)) {
        Text(marker, color = LocalStarPalette.current.accent, modifier = Modifier.width(StarDim.xl))
        Text(inlineMarkdown(body, color), color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun QuoteRow(body: String, color: Color) {
    val palette = LocalStarPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = StarDim.xxs)) {
        Surface(Modifier.width(StarDim.xs), color = palette.quote) {}
        Text(inlineMarkdown(body, color), color = color.copy(alpha = .85f), modifier = Modifier.padding(start = StarDim.md), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun inlineMarkdown(source: String, color: Color): AnnotatedString = buildAnnotatedString {
    val palette = LocalStarPalette.current
    val regex = Regex("""(\*\*[^*\n]+?\*\*)|(\*[^*\n]+?\*)|(`[^`\n]+`)|(\[[^\]\n]+?\]\([^)\n]+?\))|(\$[^$\n]+?\$)""")
    var end = 0
    regex.findAll(source).forEach { match ->
        append(source.substring(end, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) { append(token.removeSurrounding("**")) }
            token.startsWith("`") -> withStyle(SpanStyle(fontFamily = Mono, background = palette.codeBg, color = palette.codeFg)) { append(" ${token.removeSurrounding("`")} ") }
            token.startsWith("$") -> withStyle(SpanStyle(fontFamily = Mono, fontStyle = FontStyle.Italic, color = palette.quote)) { append(token.removeSurrounding("$")) }
            token.startsWith("[") -> withStyle(SpanStyle(color = palette.accent, textDecoration = TextDecoration.Underline)) { append(token.substringAfter("[").substringBefore("]")) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color)) { append(token.removeSurrounding("*")) }
        }
        end = match.range.last + 1
    }
    append(source.substring(end))
}

@Composable
fun CodeBlock(content: String, language: String = "") {
    val palette = LocalStarPalette.current
    val clipboard = LocalClipboardManager.current
    Surface(color = palette.codeBg, shape = RoundedCornerShape(StarDim.radius), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = StarDim.md)) {
                Text(language.ifBlank { "код" }, color = palette.faint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                IconButton({ clipboard.setText(AnnotatedString(content)) }, Modifier.size(StarDim.xxl)) {
                    Icon(Icons.Default.ContentCopy, "Копировать код", Modifier.size(StarDim.lg), tint = palette.faint)
                }
            }
            Text(
                highlight(content),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = StarDim.md).padding(bottom = StarDim.md),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, color = palette.codeFg)
            )
        }
    }
}

/** Keywords, strings, numbers and comments get their own color. Everything else stays plain. */
private fun highlight(code: String): AnnotatedString = buildAnnotatedString {
    code.split("\n").forEachIndexed { index, line ->
        if (index > 0) append("\n")
        val commentAt = line.indexOf("//").takeIf { it >= 0 } ?: line.indexOf("#").takeIf { line.trimStart().startsWith("#") } ?: -1
        val body = if (commentAt >= 0) line.substring(0, commentAt) else line
        paint(body)
        if (commentAt >= 0) withStyle(SpanStyle(color = CommentLit, fontStyle = FontStyle.Italic)) { append(line.substring(commentAt)) }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.paint(body: String) {
    val token = Regex("""("[^"]*"|'[^']*'|\b\d+(\.\d+)?\b|\b[A-Za-z_][A-Za-z0-9_]*\b)""")
    var end = 0
    token.findAll(body).forEach { match ->
        append(body.substring(end, match.range.first))
        val word = match.value
        val color = when {
            word.startsWith("\"") || word.startsWith("'") -> StringLit
            word[0].isDigit() -> NumberLit
            word in keywords -> Keyword
            else -> null
        }
        if (color == null) append(word) else withStyle(SpanStyle(color = color)) { append(word) }
        end = match.range.last + 1
    }
    append(body.substring(end))
}

@Composable
private fun FormulaBlock(content: String) {
    val palette = LocalStarPalette.current
    Surface(color = palette.codeBg, shape = RoundedCornerShape(StarDim.radius), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(StarDim.md), contentAlignment = Alignment.Center) {
            Text(content, color = palette.codeFg, fontFamily = Mono, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private val separator = Regex("""\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)+\|?\s*""")

@Composable
private fun MarkdownTable(lines: List<String>, color: Color) {
    val palette = LocalStarPalette.current
    val rows = lines.filterNot { it.matches(separator) }.map { it.trim().trim('|').split('|').map(String::trim) }
    Surface(color = palette.codeBg.copy(alpha = .6f), shape = RoundedCornerShape(StarDim.radiusSm), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.padding(StarDim.xs)) {
            rows.forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { cell ->
                        Text(inlineMarkdown(cell, color), modifier = Modifier.widthIn(min = 90.dp).padding(StarDim.sm), fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal, color = color)
                    }
                }
                if (rowIndex == 0) HorizontalDivider(color = palette.line)
            }
        }
    }
}

@Composable
private fun ChartBlock(content: String, color: Color) {
    val palette = LocalStarPalette.current
    val values = content.lines().mapNotNull { line ->
        val parts = line.split(":", limit = 2)
        parts.getOrNull(1)?.trim()?.toFloatOrNull()?.let { parts[0].trim() to it }
    }.take(12)
    if (values.isEmpty()) { CodeBlock(content); return }
    val max = values.maxOf { it.second }.coerceAtLeast(1f)
    Surface(color = palette.codeBg.copy(alpha = .6f), shape = RoundedCornerShape(StarDim.radius), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(StarDim.md), verticalArrangement = Arrangement.spacedBy(StarDim.sm)) {
            values.forEach { (label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = color, modifier = Modifier.width(86.dp), maxLines = 1)
                    Box(Modifier.weight(1f).height(StarDim.lg), contentAlignment = Alignment.CenterStart) {
                        Surface(Modifier.fillMaxWidth(value / max).height(StarDim.lg), RoundedCornerShape(StarDim.xs), color = palette.accent) {}
                    }
                    Text(if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value), color = color, modifier = Modifier.padding(start = StarDim.sm))
                }
            }
        }
    }
}
