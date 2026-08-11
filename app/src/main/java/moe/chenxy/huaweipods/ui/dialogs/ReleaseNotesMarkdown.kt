package moe.chenxy.huaweipods.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.chenxy.huaweipods.update.ReleaseNotesMarkdownBlock
import moe.chenxy.huaweipods.update.ReleaseNotesMarkdownSpan
import moe.chenxy.huaweipods.update.parseReleaseNotesMarkdown
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ReleaseNotesMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseReleaseNotesMarkdown(markdown) }
    val bodyColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val headingColor = MiuixTheme.colorScheme.onSurface
    val accentColor = MiuixTheme.colorScheme.primary
    val codeBackground = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val bodyStyle = MiuixTheme.textStyles.body2.copy(color = bodyColor)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is ReleaseNotesMarkdownBlock.Heading -> MarkdownText(
                    spans = block.content,
                    style = bodyStyle.copy(
                        color = headingColor,
                        fontSize = when (block.level) {
                            1 -> 20.sp
                            2 -> 18.sp
                            else -> 16.sp
                        },
                        fontWeight = FontWeight.SemiBold,
                    ),
                    accentColor = accentColor,
                    codeBackground = codeBackground,
                    modifier = Modifier.padding(top = if (block.level <= 2) 4.dp else 0.dp),
                )

                is ReleaseNotesMarkdownBlock.Paragraph -> MarkdownText(
                    spans = block.content,
                    style = bodyStyle,
                    accentColor = accentColor,
                    codeBackground = codeBackground,
                )

                is ReleaseNotesMarkdownBlock.ListItem -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (block.depth * 14).dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    MarkdownText(
                        spans = listOf(ReleaseNotesMarkdownSpan(block.marker)),
                        style = bodyStyle.copy(color = accentColor, fontWeight = FontWeight.SemiBold),
                        accentColor = accentColor,
                        codeBackground = codeBackground,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    MarkdownText(
                        spans = block.content,
                        style = bodyStyle,
                        accentColor = accentColor,
                        codeBackground = codeBackground,
                        modifier = Modifier.weight(1f),
                    )
                }

                is ReleaseNotesMarkdownBlock.Quote -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(width = 3.dp, height = 28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor.copy(alpha = 0.55f)),
                    )
                    MarkdownText(
                        spans = block.content,
                        style = bodyStyle.copy(fontStyle = FontStyle.Italic),
                        accentColor = accentColor,
                        codeBackground = codeBackground,
                        modifier = Modifier.weight(1f),
                    )
                }

                is ReleaseNotesMarkdownBlock.CodeBlock -> MarkdownText(
                    spans = listOf(ReleaseNotesMarkdownSpan(block.code, code = true)),
                    style = bodyStyle,
                    accentColor = accentColor,
                    codeBackground = codeBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(codeBackground)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )

                ReleaseNotesMarkdownBlock.Divider -> Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .size(height = 1.dp, width = 1.dp)
                        .background(bodyColor.copy(alpha = 0.22f)),
                )
            }
        }
    }
}

@Composable
private fun MarkdownText(
    spans: List<ReleaseNotesMarkdownSpan>,
    style: TextStyle,
    accentColor: Color,
    codeBackground: Color,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(spans, accentColor, codeBackground) {
        AnnotatedString.Builder().apply {
            spans.forEach { span ->
                val start = length
                append(span.text)
                val end = length
                if (start == end) return@forEach

                addStyle(
                    SpanStyle(
                        fontWeight = if (span.bold) FontWeight.Bold else null,
                        fontStyle = if (span.italic) FontStyle.Italic else null,
                        fontFamily = if (span.code) FontFamily.Monospace else null,
                        background = if (span.code) codeBackground else Color.Unspecified,
                        textDecoration = if (span.strikethrough) TextDecoration.LineThrough else null,
                    ),
                    start,
                    end,
                )
                span.url?.let { url ->
                    addLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = accentColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ),
                        start,
                        end,
                    )
                }
            }
        }.toAnnotatedString()
    }

    BasicText(
        text = annotated,
        modifier = modifier,
        style = style,
    )
}
