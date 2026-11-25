package id.vern.antigravity.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.vern.antigravity.R
import id.vern.antigravity.ui.theme.AntigravityBlue

sealed class MarkdownElement {
    data class Heading(val level: Int, val text: String) : MarkdownElement()
    data class CodeBlock(val language: String, val code: String) : MarkdownElement()
    data class ListItem(val bullet: String, val text: String) : MarkdownElement()
    data class BlockQuote(val text: String) : MarkdownElement()
    object Divider : MarkdownElement()
    data class Paragraph(val text: String) : MarkdownElement()
}

private val BULLET_REGEX = Regex("""^[-*+]\s+(.*)$""")
private val NUM_REGEX = Regex("""^(\d+)\.\s+(.*)$""")
private val BULLET_PREFIX_REGEX = Regex("""^[-*+]\s+""")
private val NUM_PREFIX_REGEX = Regex("""^\d+\.\s+""")
private val INLINE_TOKEN_REGEX = Regex("""(`[^`]+`|\*\*[^*]+\*\*|\*[^*]+\*|\[[^\]]+\]\([^)]+\))""")

fun parseMarkdown(content: String): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = content.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++
            elements.add(MarkdownElement.CodeBlock(language, codeLines.joinToString("\n")))
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            elements.add(MarkdownElement.Divider)
            i++
            continue
        }

        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..6 && trimmed.getOrNull(level) == ' ') {
                val text = trimmed.substring(level).trim()
                elements.add(MarkdownElement.Heading(level, text))
                i++
                continue
            }
        }

        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            elements.add(MarkdownElement.BlockQuote(quoteLines.joinToString(" ")))
            continue
        }

        val bulletMatch = BULLET_REGEX.find(trimmed)
        if (bulletMatch != null) {
            val text = bulletMatch.groupValues[1]
            elements.add(MarkdownElement.ListItem("•", text))
            i++
            continue
        }

        val numMatch = NUM_REGEX.find(trimmed)
        if (numMatch != null) {
            val num = numMatch.groupValues[1]
            val text = numMatch.groupValues[2]
            elements.add(MarkdownElement.ListItem("$num.", text))
            i++
            continue
        }

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        val paraLines = mutableListOf<String>()
        while (i < lines.size) {
            val cur = lines[i]
            val curTrim = cur.trim()
            if (curTrim.isEmpty() ||
                curTrim.startsWith("```") ||
                curTrim.startsWith("#") ||
                curTrim.startsWith(">") ||
                curTrim == "---" ||
                BULLET_PREFIX_REGEX.containsMatchIn(curTrim) ||
                NUM_PREFIX_REGEX.containsMatchIn(curTrim)
            ) {
                break
            }
            paraLines.add(curTrim)
            i++
        }
        if (paraLines.isNotEmpty()) {
            elements.add(MarkdownElement.Paragraph(paraLines.joinToString(" ")))
        }
    }
    return elements
}

fun parseInlineMarkdown(
    text: String,
    primaryColor: Color = AntigravityBlue,
    codeBgColor: Color = Color(0x2B888888),
    codeTextColor: Color = Color.Unspecified
): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val matches = INLINE_TOKEN_REGEX.findAll(text)

        for (match in matches) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }

            val token = match.value
            when {
                token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                    val code = token.substring(1, token.length - 1)
                    val start = length
                    append(" $code ")
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBgColor,
                            color = codeTextColor,
                            fontSize = 11.5.sp
                        ),
                        start,
                        length
                    )
                }
                token.startsWith("**") && token.endsWith("**") && token.length >= 4 -> {
                    val boldText = token.substring(2, token.length - 2)
                    val start = length
                    append(boldText)
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                }
                token.startsWith("*") && token.endsWith("*") && token.length >= 2 -> {
                    val italicText = token.substring(1, token.length - 1)
                    val start = length
                    append(italicText)
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                }
                token.startsWith("[") && token.contains("](") && token.endsWith(")") -> {
                    val label = token.substringAfter("[").substringBefore("](")
                    val url = token.substringAfter("](").substringBeforeLast(")")
                    val start = length
                    append(label)
                    addStyle(
                        SpanStyle(
                            color = primaryColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        ),
                        start,
                        length
                    )
                    addStringAnnotation(tag = "URL", annotation = url, start = start, end = length)
                }
                else -> {
                    append(token)
                }
            }
            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

@Composable
fun FormattedText(
    annotatedString: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasLinks = remember(annotatedString) {
        annotatedString.getStringAnnotations("URL", 0, annotatedString.length).isNotEmpty()
    }

    if (hasLinks) {
        @Suppress("DEPRECATION")
        ClickableText(
            text = annotatedString,
            style = style,
            modifier = modifier,
            onClick = { offset ->
                annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { annotation ->
                    try {
                        val uri = Uri.parse(annotation.item)
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open: ${annotation.item}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    } else {
        Text(
            text = annotatedString,
            style = style,
            modifier = modifier
        )
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 12.sp,
    lineHeight: TextUnit = 17.sp,
    selectable: Boolean = true
) {
    val context = LocalContext.current
    val elements = remember(markdown) { parseMarkdown(markdown) }

    val markdownBody = @Composable {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            elements.forEachIndexed { index, element ->
                key(index) {
                    when (element) {
                        is MarkdownElement.Heading -> {
                            val (size, weight) = when (element.level) {
                                1 -> 15.sp to FontWeight.Bold
                                2 -> 14.sp to FontWeight.Bold
                                3 -> 13.sp to FontWeight.SemiBold
                                else -> 12.sp to FontWeight.SemiBold
                            }
                            val annotated = remember(element.text) { parseInlineMarkdown(element.text) }
                            FormattedText(
                                annotatedString = annotated,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = size,
                                    fontWeight = weight,
                                    color = textColor,
                                    lineHeight = (size.value * 1.35f).sp
                                ),
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }

                        is MarkdownElement.CodeBlock -> {
                            CodeBlockCard(
                                language = element.language,
                                code = element.code,
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Code", element.code))
                                    Toast.makeText(context, context.getString(R.string.markdown_code_copied), Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        is MarkdownElement.ListItem -> {
                            val annotated = remember(element.text) { parseInlineMarkdown(element.text) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, top = 1.dp, bottom = 1.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = element.bullet,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AntigravityBlue
                                    ),
                                    modifier = Modifier.width(20.dp)
                                )
                                FormattedText(
                                    annotatedString = annotated,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = fontSize,
                                        lineHeight = lineHeight,
                                        color = textColor
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        is MarkdownElement.BlockQuote -> {
                            val annotated = remember(element.text) { parseInlineMarkdown(element.text) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(20.dp)
                                        .background(AntigravityBlue, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FormattedText(
                                    annotatedString = annotated,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontStyle = FontStyle.Italic,
                                        fontSize = fontSize,
                                        lineHeight = lineHeight,
                                        color = textColor.copy(alpha = 0.85f)
                                    )
                                )
                            }
                        }

                        is MarkdownElement.Divider -> {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }

                        is MarkdownElement.Paragraph -> {
                            val annotated = remember(element.text) { parseInlineMarkdown(element.text) }
                            FormattedText(
                                annotatedString = annotated,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = fontSize,
                                    lineHeight = lineHeight,
                                    color = textColor
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectable) {
        SelectionContainer {
            markdownBody()
        }
    } else {
        markdownBody()
    }
}

@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    onCopy: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onCopy)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.chat_copy_code),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.chat_btn_copy),
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

