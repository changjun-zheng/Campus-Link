package com.campuslink.common.markdown;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Markdown → HTML 的服务端唯一渲染出口（ADR-005，论坛安全生命线）。
 *
 * <p>净化用 jsoup 白名单（技术方案 7.1）：禁止 script / iframe / 事件属性 / style 属性，
 * a.href 仅 http(s) 与相对路径，img.src 仅 https；代码高亮由前端 highlight.js 对
 * 已净化的 {@code <pre><code class="language-*">} 着色（纯展示，不产生脚本执行）。
 *
 * <p>说明：0.64.x 起表格扩展更名为 flexmark-ext-tables（原 ext-gfm-tables 停更于 0.50.x）；
 * 已知边界：jsoup 白名单不能按正则约束属性值，{@code class} 属性的合法性由渲染来源保证
 * （flexmark 只在代码块上输出 {@code language-*} 类）。
 */
@Service
public class MarkdownRenderer {

    private final Parser parser = Parser.builder()
            .extensions(List.of(TablesExtension.create(), AutolinkExtension.create()))
            .build();

    // 扩展必须同时注册到解析器与渲染器（否则 TableBlock 等节点解析成功但渲染为空）
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder()
            .extensions(List.of(TablesExtension.create(), AutolinkExtension.create()))
            .build();

    private static final Safelist SAFELIST = new Safelist()
            .addTags("p", "br", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "code",
                    "ul", "ol", "li", "a", "img", "strong", "em", "del", "hr",
                    "table", "thead", "tbody", "tr", "th", "td")
            .addAttributes("a", "href", "title")
            .addAttributes("img", "src", "alt", "title")
            .addAttributes("code", "class")
            .addProtocols("a", "href", "http", "https")
            .addProtocols("img", "src", "https")
            .preserveRelativeLinks(true);

    public String render(String markdown) {
        Node document = parser.parse(markdown == null ? "" : markdown);
        return Jsoup.clean(htmlRenderer.render(document), SAFELIST);
    }

    /**
     * 列表摘要：Markdown → 纯文本后按字符数截断（服务端截断，前端不做兜底——sprint-2-design §3.2）。
     *
     * <p>走与 {@link #render} 同一条解析 + 净化链路再取纯文本，而不是自行剥 Markdown 记号：
     * 记号规则（表格 / 链接 / 代码块）只需在此维护一份，摘要与正文永不漂移。
     */
    public String toPlainSummary(String markdown, int maxChars) {
        return truncate(plainText(markdown), maxChars);
    }

    /**
     * 搜索命中摘要（F-FORUM-009 / CR-136）：纯文本化后把**首个命中词**放进窗口中心，
     * 前后按需加省略号；关键词不在纯文本里时回落首段摘要。
     *
     * <p>与 {@link #toPlainSummary} 共用同一条渲染 + 净化链路：Markdown 记号规则仍只有一份，
     * 摘要不会因为命中位置在表格 / 链接 / 代码块里而露出原始记号。
     * 关键词比较忽略大小写（英文），中文无需分词——调用方已保证 2 字以上。
     */
    public String toPlainSnippet(String markdown, String keyword, int maxChars) {
        String text = plainText(markdown);
        if (text.length() <= maxChars) {
            return text;
        }
        String needle = keyword == null ? "" : keyword.strip();
        int at = needle.isEmpty()
                ? -1
                : text.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
        if (at < 0) {
            return truncate(text, maxChars);
        }
        // 命中词前留 1/3 窗口作上下文；窗口贴尾时整体左移，保证长度不超 maxChars（省略号另计）
        int start = Math.max(0, at - maxChars / 3);
        int end = Math.min(text.length(), start + maxChars);
        start = Math.max(0, end - maxChars);
        String prefix = start > 0 ? "…" : "";
        String suffix = end < text.length() ? "…" : "";
        return prefix + text.substring(start, end) + suffix;
    }

    /** Markdown → 纯文本：渲染 + 净化后取 text（摘要 / 引用 / 搜索共用，避免自行剥记号导致口径漂移） */
    private String plainText(String markdown) {
        return Jsoup.parse(render(markdown)).text().trim();
    }

    private static String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }
}
