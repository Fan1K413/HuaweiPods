package moe.chenxy.huaweipods.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesMarkdownParserTest {
    @Test
    fun `parses headings nested lists and inline emphasis`() {
        val blocks = parseReleaseNotesMarkdown(
            """
            # HuaweiPods 1.5.0

            - **FreeClip 2** 空间音频
              - 修复 *固定* 和 `头部跟踪`
            """.trimIndent(),
        )

        val heading = blocks[0] as ReleaseNotesMarkdownBlock.Heading
        assertEquals(1, heading.level)
        assertEquals("HuaweiPods 1.5.0", heading.content.single().text)

        val parent = blocks[1] as ReleaseNotesMarkdownBlock.ListItem
        assertEquals(0, parent.depth)
        assertTrue(parent.content.first().bold)
        assertEquals("FreeClip 2", parent.content.first().text)

        val child = blocks[2] as ReleaseNotesMarkdownBlock.ListItem
        assertEquals(1, child.depth)
        assertTrue(child.content.any { it.text == "固定" && it.italic })
        assertTrue(child.content.any { it.text == "头部跟踪" && it.code })
    }

    @Test
    fun `parses quotes dividers fenced code and task lists`() {
        val blocks = parseReleaseNotesMarkdown(
            """
            > 请先重启作用域

            ---

            - [x] 已完成
            - [ ] 待测试

            ```text
            11-1.5.0
            ```
            """.trimIndent(),
        )

        assertTrue(blocks[0] is ReleaseNotesMarkdownBlock.Quote)
        assertTrue(blocks[1] is ReleaseNotesMarkdownBlock.Divider)
        assertEquals("☑", (blocks[2] as ReleaseNotesMarkdownBlock.ListItem).marker)
        assertEquals("☐", (blocks[3] as ReleaseNotesMarkdownBlock.ListItem).marker)
        assertEquals("11-1.5.0", (blocks[4] as ReleaseNotesMarkdownBlock.CodeBlock).code)
    }

    @Test
    fun `keeps safe links clickable and rejects unsafe schemes`() {
        val spans = parseReleaseNotesInlineMarkdown(
            "查看 [Release](https://github.com/Nshpiter/HuaweiPods) 或 [危险链接](javascript:alert(1))",
        )

        val release = spans.first { it.text == "Release" }
        assertEquals("https://github.com/Nshpiter/HuaweiPods", release.url)
        assertTrue(spans.joinToString("") { it.text }.contains("危险链接"))
        assertTrue(spans.filter { it.text.contains("危险链接") }.all { it.url == null })
        assertFalse(spans.any { it.url?.startsWith("javascript:") == true })
    }

    @Test
    fun `preserves malformed markdown as readable text`() {
        val spans = parseReleaseNotesInlineMarkdown("修复 **未闭合 和 \\*字面星号")
        assertEquals("修复 **未闭合 和 *字面星号", spans.joinToString("") { it.text })
    }
}
