package com.campuslink.common.markdown;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XSS 用例回归（技术方案 7.1 / Sprint 1 出口标准：含 &lt;script&gt; 的 Markdown 不产生 XSS）。
 * 这些用例是论坛安全生命线的回归底线，任何渲染实现调整都必须保持全绿。
 */
class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    @DisplayName("script 标签被整体移除")
    void scriptTagRemoved() {
        String out = renderer.render("hello <script>alert(1)</script> world");
        assertThat(out).doesNotContain("<script").doesNotContain("alert(1)");
    }

    @Test
    @DisplayName("javascript: 链接被清除")
    void javascriptHrefRemoved() {
        String out = renderer.render("[click](javascript:alert(1))");
        assertThat(out).doesNotContain("javascript:");
    }

    @Test
    @DisplayName("img 仅允许 https，http 源被清除")
    void imgHttpsOnly() {
        String out = renderer.render("![a](https://a.example/b.png) ![b](http://b.example/d.png)");
        assertThat(out).contains("https://a.example/b.png");
        assertThat(out).doesNotContain("http://b.example/d.png");
    }

    @Test
    @DisplayName("代码块保留 language-* 类（前端 highlight.js 着色输入）")
    void codeLanguageClassPreserved() {
        String out = renderer.render("```java\nSystem.out.println(1);\n```");
        assertThat(out).contains("language-java");
        assertThat(out).contains("<pre><code");
    }

    @Test
    @DisplayName("事件属性被清除")
    void eventAttributesRemoved() {
        String out = renderer.render("<img src=\"https://a.example/b.png\" onerror=\"alert(1)\">");
        assertThat(out).doesNotContain("onerror");
    }

    @Test
    @DisplayName("常规 Markdown 正常保留（标题 / 加粗 / 列表 / 表格）")
    void basicMarkdownPreserved() {
        String out = renderer.render("# 标题\n\n**加粗** 与 *斜体*\n\n- 列表项\n\n| A | B |\n|---|---|\n| 1 | 2 |");
        assertThat(out).contains("<h1>");
        assertThat(out).contains("<strong>");
        assertThat(out).contains("<li>");
        assertThat(out).contains("<table>");
    }

    @Test
    @DisplayName("搜索摘要：命中词在正文深处时窗口居中，并带前后省略号")
    void snippetCentersDeepKeyword() {
        String markdown = "前".repeat(120) + "命中词" + "后".repeat(120);

        String snippet = renderer.toPlainSnippet(markdown, "命中词", 120);

        assertThat(snippet).contains("命中词").startsWith("…").endsWith("…");
        assertThat(snippet.length()).isLessThanOrEqualTo(122);
    }

    @Test
    @DisplayName("搜索摘要：关键词不在纯文本里（或为空）时回落首段摘要")
    void snippetFallsBackWhenKeywordMissing() {
        String markdown = "甲".repeat(200);

        assertThat(renderer.toPlainSnippet(markdown, "找不到", 120))
                .isEqualTo(renderer.toPlainSummary(markdown, 120));
        assertThat(renderer.toPlainSnippet(markdown, null, 120))
                .isEqualTo(renderer.toPlainSummary(markdown, 120));
    }

    @Test
    @DisplayName("搜索摘要：短文本原样返回，不截断、不加省略号")
    void snippetKeepsShortText() {
        assertThat(renderer.toPlainSnippet("短正文", "正文", 120)).isEqualTo("短正文");
    }

    @Test
    @DisplayName("搜索摘要：英文关键词忽略大小写")
    void snippetIsCaseInsensitive() {
        String markdown = "x".repeat(150) + " Spring Boot " + "y".repeat(50);

        String snippet = renderer.toPlainSnippet(markdown, "spring", 120);

        assertThat(snippet).contains("Spring Boot");
    }

    @Test
    @DisplayName("搜索摘要：Markdown 记号先被纯文本化，命中词在加粗里也不露出 **")
    void snippetStripsMarkdownMarkup() {
        String markdown = "这是一段包含 **Spring Boot** 的正文。";

        String snippet = renderer.toPlainSnippet(markdown, "Spring Boot", 120);

        assertThat(snippet).contains("Spring Boot").doesNotContain("**");
    }
}
