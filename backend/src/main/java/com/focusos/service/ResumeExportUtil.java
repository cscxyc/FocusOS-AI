package com.focusos.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 7-C-A: 简历导出工具类
 * <p>
 * 支持：
 * 1. PDF 导出（优先实现，使用 PDFBox 3.0.2）
 * 2. Word docx 导出（基础实现，使用 HTML 转 docx 方式）
 * 3. Markdown 直接输出（无需转换）
 * <p>
 * 中文字体支持：使用 PDFBox 加载 TTF 字体（如 SourceHanSans / NotoSansCJK），
 * 若字体文件不存在则降级为 PDFBox 内置 Helvetica（中文可能显示为方框）。
 */
@Slf4j
public class ResumeExportUtil {

    private static final float MARGIN = 50;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float LINE_HEIGHT = 18;
    private static final float TITLE_FONT_SIZE = 18;
    private static final float H2_FONT_SIZE = 14;
    private static final float BODY_FONT_SIZE = 11;

    private ResumeExportUtil() {}

    /**
     * 将 Markdown 简历内容转换为 PDF
     * <p>
     * Sprint 7-C-A 修复：Helvetica 无法编码中文字符会抛 IllegalArgumentException
     * （非 IOException），需捕获 Exception 并使用支持中文的字体。
     */
    public static byte[] markdownToPdf(String markdown, String title) {
        if (markdown == null || markdown.isBlank()) {
            return emptyPdf();
        }

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDFont font = loadChineseFont(doc);
            boolean isHelveticaFallback = (font instanceof PDType1Font);

            // 将 Markdown 解析为渲染行（带样式）
            List<RenderLine> lines = parseMarkdownToLines(markdown);

            // 分页渲染
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream content = new PDPageContentStream(doc, page);

            float y = PAGE_HEIGHT - MARGIN;
            int lineCount = 0;
            int maxLinesPerPage = (int) ((PAGE_HEIGHT - 2 * MARGIN) / LINE_HEIGHT) - 2;

            for (RenderLine line : lines) {
                if (lineCount >= maxLinesPerPage) {
                    // 换页
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    content = new PDPageContentStream(doc, page);
                    y = PAGE_HEIGHT - MARGIN;
                    lineCount = 0;
                }

                // Helvetica 降级时过滤非 Latin-1 字符，避免 IllegalArgumentException
                String renderText = isHelveticaFallback ? filterLatinOnly(line.text) : line.text;

                float fontSize = line.fontSize;
                // PDFBox 3.x: setNonStrokingColor 需要 3 个 0.0-1.0 的 float 参数（RGB），
                // 不能直接传 0xRRGGBB 整数（会被当作单值 grayscale，范围 0-1）
                float r = ((line.color >> 16) & 0xFF) / 255.0f;
                float g = ((line.color >> 8) & 0xFF) / 255.0f;
                float b = (line.color & 0xFF) / 255.0f;
                content.setNonStrokingColor(r, g, b);
                content.beginText();
                content.setFont(font, fontSize);
                content.newLineAtOffset(MARGIN, y);
                content.showText(renderText);
                content.endText();

                y -= LINE_HEIGHT;
                lineCount++;
            }

            content.close();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF: {}", e.getMessage(), e);
            return emptyPdf();
        }
    }

    /**
     * 过滤为 Latin-1 可编码字符（Helvetica 降级时使用）
     * 中文字符替换为 "?"，避免 IllegalArgumentException: U+XXXX is not available
     */
    private static String filterLatinOnly(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // WinAnsiEncoding 范围（基本 Latin-1）
            if (c <= 0xFF) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    /**
     * 将 Markdown 转换为 Word docx（基础实现：HTML 包装为 docx MIME）
     * <p>
     * 注意：真正的 docx 需要 Apache POI 库。此处使用简化的 HTML-as-docx 方案，
     * Word 可正常打开但非原生 docx 格式。如需原生 docx，需添加 poi-ooxml 依赖。
     */
    public static byte[] markdownToDocx(String markdown, String title) {
        if (markdown == null || markdown.isBlank()) {
            return new byte[0];
        }
        // 将 Markdown 转换为 HTML，再包装为 docx 兼容的 HTML
        String html = markdownToHtml(markdown, title);
        String docxHtml = "<!DOCTYPE html><html xmlns:o='urn:schemas-microsoft-com:office:office' "
                + "xmlns:w='urn:schemas-microsoft-com:office:word' "
                + "xmlns='http://www.w3.org/TR/REC-html40'><head><meta charset='utf-8'>"
                + "<title>" + escapeHtml(title != null ? title : "简历") + "</title>"
                + "<style>body{font-family:'宋体',SimSun,sans-serif;line-height:1.6;font-size:12pt;}"
                + "h1{font-size:20pt;color:#1a1a1a;}h2{font-size:14pt;color:#333;border-bottom:1px solid #ccc;}"
                + "h3{font-size:12pt;color:#444;}p{margin:6pt 0;}li{margin:3pt 0;}"
                + "</style></head><body>" + html + "</body></html>";
        return docxHtml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ============================================================
    // 内部辅助：Markdown 解析、字体加载、HTML 转换
    // ============================================================

    private static class RenderLine {
        String text;
        float fontSize;
        // RGB color as int (0xRRGGBB)
        int color;

        RenderLine(String text, float fontSize, int color) {
            this.text = text;
            this.fontSize = fontSize;
            this.color = color;
        }
    }

    private static List<RenderLine> parseMarkdownToLines(String markdown) {
        List<RenderLine> lines = new ArrayList<>();
        String[] rawLines = markdown.split("\n");
        for (String raw : rawLines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                lines.add(new RenderLine("", BODY_FONT_SIZE, 0x333333));
                continue;
            }
            // H1: # 标题
            if (line.startsWith("# ")) {
                lines.add(new RenderLine(strip(line.substring(2)), TITLE_FONT_SIZE, 0x1a1a1a));
            }
            // H2: ## 标题
            else if (line.startsWith("## ")) {
                lines.add(new RenderLine(strip(line.substring(3)), H2_FONT_SIZE, 0x2a2a2a));
            }
            // H3: ### 标题
            else if (line.startsWith("### ")) {
                lines.add(new RenderLine(strip(line.substring(4)), BODY_FONT_SIZE + 1, 0x3a3a3a));
            }
            // 分隔线（使用 ASCII 避免字体缺少 box-drawing 字符）
            else if (line.equals("---") || line.equals("***")) {
                lines.add(new RenderLine("-----------------------------------------------", BODY_FONT_SIZE, 0xcccccc));
            }
            // 列表项（使用 ASCII 减号避免字体缺少 bullet 字符）
            else if (line.startsWith("- ") || line.startsWith("* ")) {
                lines.add(new RenderLine("  - " + strip(line.substring(2)), BODY_FONT_SIZE, 0x333333));
            }
            // 引用
            else if (line.startsWith("> ")) {
                lines.add(new RenderLine("  " + strip(line.substring(2)), BODY_FONT_SIZE, 0x666666));
            }
            // 普通段落
            else {
                lines.add(new RenderLine(strip(line), BODY_FONT_SIZE, 0x333333));
            }
        }
        return lines;
    }

    /**
     * 去除 Markdown 强调标记（** **, * *, _ _）保留纯文本
     */
    private static String strip(String s) {
        return s.replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("\\*(.+?)\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                .replaceAll("_(.+?)_", "$1")
                .replaceAll("`(.+?)`", "$1");
    }

    /**
     * 加载中文字体
     * <p>
     * 优先级：
     * 1. classpath 下的字体文件（生产环境部署时放置到 resources/fonts/）
     * 2. Windows 系统字体（C:\Windows\Fonts\simhei.ttf 等）
     * 3. Linux/macOS 常见字体路径
     * 4. 降级为 PDFBox 内置 Helvetica（中文显示为 ?，但不会崩溃）
     */
    private static PDFont loadChineseFont(PDDocument doc) throws IOException {
        // 1. 尝试 classpath 字体
        String[] classpathFonts = {
                "fonts/SourceHanSansSC-Regular.ttf",
                "fonts/NotoSansCJK-Regular.ttf",
                "fonts/simsun.ttf",
                "fonts/simhei.ttf"
        };
        for (String path : classpathFonts) {
            try (InputStream is = ResumeExportUtil.class.getClassLoader().getResourceAsStream(path)) {
                if (is != null) {
                    log.info("Loaded Chinese font from classpath: {}", path);
                    return PDType0Font.load(doc, is);
                }
            } catch (Exception e) {
                // 继续尝试下一个字体
            }
        }

        // 2. 尝试系统字体文件（Windows / Linux / macOS 常见路径）
        String[] systemFontPaths = {
                "C:\\Windows\\Fonts\\simhei.ttf",
                "C:\\Windows\\Fonts\\simsun.ttc",
                "C:\\Windows\\Fonts\\msyh.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/System/Library/Fonts/PingFang.ttc"
        };
        for (String path : systemFontPaths) {
            File file = new File(path);
            if (file.exists() && file.canRead()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    log.info("Loaded Chinese font from system: {}", path);
                    return PDType0Font.load(doc, fis);
                } catch (Exception e) {
                    log.warn("Failed to load system font {}: {}", path, e.getMessage());
                }
            }
        }

        // 3. 降级：使用 PDFBox 内置 Helvetica（中文将被过滤为 ?）
        log.warn("No Chinese font found, falling back to Helvetica (Chinese chars will be replaced with '?')");
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private static byte[] emptyPdf() {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /**
     * Markdown 转换为 HTML（用于 docx 包装）
     */
    private static String markdownToHtml(String markdown, String title) {
        StringBuilder html = new StringBuilder();
        String[] lines = markdown.split("\n");
        boolean inList = false;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                continue;
            }
            if (t.startsWith("# ")) {
                if (inList) { html.append("</ul>"); inList = false; }
                html.append("<h1>").append(escapeHtml(strip(t.substring(2)))).append("</h1>");
            } else if (t.startsWith("## ")) {
                if (inList) { html.append("</ul>"); inList = false; }
                html.append("<h2>").append(escapeHtml(strip(t.substring(3)))).append("</h2>");
            } else if (t.startsWith("### ")) {
                if (inList) { html.append("</ul>"); inList = false; }
                html.append("<h3>").append(escapeHtml(strip(t.substring(4)))).append("</h3>");
            } else if (t.startsWith("- ") || t.startsWith("* ")) {
                if (!inList) { html.append("<ul>"); inList = true; }
                html.append("<li>").append(escapeHtml(strip(t.substring(2)))).append("</li>");
            } else if (t.equals("---") || t.equals("***")) {
                if (inList) { html.append("</ul>"); inList = false; }
                html.append("<hr/>");
            } else {
                if (inList) { html.append("</ul>"); inList = false; }
                html.append("<p>").append(escapeHtml(strip(t))).append("</p>");
            }
        }
        if (inList) html.append("</ul>");
        return html.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
