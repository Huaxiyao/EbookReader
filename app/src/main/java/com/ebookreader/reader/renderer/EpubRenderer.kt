package com.ebookreader.reader.renderer

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ebookreader.data.model.ReaderTheme
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * ePub 渲染器 — 使用 WebView 渲染 ePub 的 HTML 内容
 *
 * 手动解析 ePub（ZIP + XML），不依赖第三方库。
 *
 * ePub 结构：
 *   META-INF/container.xml  → 指向 .opf 文件
 *   *.opf                   → 元数据 + 目录(spine)
 *   *.xhtml / *.html        → 正文内容
 */
class EpubRenderer(private val context: Context) {

    private var webView: WebView? = null
    private var currentTheme: ReaderTheme = ReaderTheme.DAY
    private var currentFontSize: Int = 18
    private var pageChangeListener: ((Int, Int) -> Unit)? = null

    /**
     * 解析出的 ePub 内容
     */
    data class EpubContent(
        val title: String,
        val author: String,
        val chapters: List<String>,  // 每个章节的 HTML
    )

    fun initialize(webView: WebView, epubFilePath: String) {
        this.webView = webView

        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            defaultTextEncodingName = "UTF-8"
        }

        // 注入 JavaScript 接口用于滚动回调
        webView.addJavascriptInterface(
            object {
                @android.webkit.JavascriptInterface
                fun onScroll(percent: Float) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        pageChangeListener?.invoke(
                            (percent * 100).toInt(),
                            100,
                        )
                    }
                }
            },
            "EPUBCallback",
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(
                    """
                    (function() {
                        var lastPercent = 0;
                        window.onscroll = function() {
                            var st = document.documentElement.scrollTop || document.body.scrollTop;
                            var sh = document.documentElement.scrollHeight - document.documentElement.clientHeight;
                            if (sh > 0) {
                                var pct = st / sh;
                                if (Math.abs(pct - lastPercent) > 0.01) {
                                    lastPercent = pct;
                                    EPUBCallback.onScroll(pct);
                                }
                            }
                        };
                    })();
                    """.trimIndent(),
                    null,
                )
            }
        }

        // 解析并渲染
        val content = parseEpub(epubFilePath)
        val fullHtml = buildFullHtml(content)
        renderHtml(fullHtml)
    }

    /**
     * 解析 ePub 文件
     */
    private fun parseEpub(filePath: String): EpubContent {
        val file = File(filePath)
        if (!file.exists()) {
            return EpubContent("文件不存在", "", listOf("<p>ePub 文件未找到</p>"))
        }

        return try {
            val zipEntries = readZipEntries(file)
            val opfPath = findOpfPath(zipEntries)
                ?: return EpubContent("解析失败", "", listOf("<p>未找到 container.xml</p>"))

            val opfContent = zipEntries[opfPath]
                ?: return EpubContent("解析失败", "", listOf("<p>未找到 OPF 文件</p>"))

            val (title, author, contentFiles) = parseOpf(opfContent)
            val chapters = mutableListOf<String>()

            // 读取各章节内容
            for (contentPath in contentFiles) {
                // 处理相对路径（相对于 OPF 目录）
                val resolvedPath = resolvePath(opfPath, contentPath)
                val html = zipEntries[resolvedPath]
                if (html != null) {
                    chapters.add(extractBodyContent(html))
                }
            }

            if (chapters.isEmpty()) {
                chapters.add("<p>[无正文内容]</p>")
            }

            EpubContent(title, author, chapters)
        } catch (e: Exception) {
            EpubContent(
                "读取失败",
                "",
                listOf("<p>ePub 解析错误: ${escapeHtml(e.message ?: "未知错误")}</p>"),
            )
        }
    }

    /**
     * 读取 ZIP 文件的所有条目到内存
     */
    private fun readZipEntries(file: File): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val content = zis.readBytes().toString(Charsets.UTF_8)
                    entries[entry.name] = content
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return entries
    }

    /**
     * 从 META-INF/container.xml 找到 OPF 文件路径
     */
    private fun findOpfPath(entries: Map<String, String>): String? {
        val containerXml = entries["META-INF/container.xml"] ?: return null
        return try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(containerXml.reader())
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG &&
                    parser.name == "rootfile" &&
                    "application/oebps-package+xml" == parser.getAttributeValue(null, "media-type")
                ) {
                    return parser.getAttributeValue(null, "full-path")
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析 OPF 文件，提取书名、作者和内容文件列表
     */
    private fun parseOpf(opfContent: String): Triple<String, String, List<String>> {
        var title = "未知书名"
        var author = ""
        val contentFiles = mutableListOf<String>()

        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(opfContent.reader())

            var inMetadata = false
            var inManifest = false
            var inSpine = false
            var currentTag = ""
            val idToHref = mutableMapOf<String, String>()
            val spineIdRefs = mutableListOf<String>()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        when (currentTag) {
                            "metadata" -> inMetadata = true
                            "manifest" -> inManifest = true
                            "spine" -> inSpine = true
                            "item" -> {
                                if (inManifest) {
                                    val id = parser.getAttributeValue(null, "id") ?: ""
                                    val href = parser.getAttributeValue(null, "href") ?: ""
                                    val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                                    // 只保留 XHTML / HTML 文件
                                    if (mediaType.contains("xhtml") || mediaType.contains("html") || href.endsWith(".xhtml") || href.endsWith(".html")) {
                                        idToHref[id] = href
                                    }
                                }
                            }
                            "itemref" -> {
                                if (inSpine) {
                                    val idref = parser.getAttributeValue(null, "idref") ?: ""
                                    spineIdRefs.add(idref)
                                }
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotBlank()) {
                            when {
                                inMetadata && currentTag == "title" -> title = text
                                inMetadata && currentTag == "creator" && author.isBlank() -> author = text
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "metadata" -> inMetadata = false
                            "manifest" -> inManifest = false
                            "spine" -> inSpine = false
                        }
                    }
                }
                eventType = parser.next()
            }

            // 按 spine 顺序排列内容文件
            for (idref in spineIdRefs) {
                val href = idToHref[idref]
                if (href != null) {
                    contentFiles.add(href)
                }
            }
        } catch (_: Exception) {
            // 如果 XML 解析失败，用默认值
        }

        return Triple(title, author, contentFiles)
    }

    /**
     * 解析相对路径（相对于 OPF 文件所在目录）
     */
    private fun resolvePath(opfPath: String, contentPath: String): String {
        val opfDir = if (opfPath.contains('/')) {
            opfPath.substringBeforeLast('/') + "/"
        } else {
            ""
        }
        // 处理 "../" 路径
        val combined = opfDir + contentPath
        val parts = combined.split("/").toMutableList()
        var i = 0
        while (i < parts.size) {
            if (parts[i] == ".." && i > 0) {
                parts.removeAt(i)
                parts.removeAt(i - 1)
                i--
            } else {
                i++
            }
        }
        return parts.joinToString("/")
    }

    /**
     * 从完整 HTML 文档中提取 <body> 内的内容
     */
    private fun extractBodyContent(html: String): String {
        val bodyStart = html.indexOf("<body", ignoreCase = true)
        if (bodyStart == -1) return html
        val bodyStartEnd = html.indexOf('>', bodyStart)
        if (bodyStartEnd == -1) return html
        val bodyEnd = html.indexOf("</body>", ignoreCase = true)
        if (bodyEnd == -1) return html.substring(bodyStartEnd + 1)
        return html.substring(bodyStartEnd + 1, bodyEnd)
    }

    /**
     * 构建完整 HTML
     */
    private fun buildFullHtml(content: EpubContent): String {
        val css = buildThemeCss(currentTheme, currentFontSize)
        val sb = StringBuilder()
        sb.append("""<!DOCTYPE html><html><head><meta charset="UTF-8">""")
        sb.append("""<meta name="viewport" content="width=device-width, initial-scale=1.0">""")
        sb.append("<style>$css</style>")
        sb.append("</head><body>")

        // 书名 + 作者
        sb.append("<h1>${escapeHtml(content.title)}</h1>")
        if (content.author.isNotBlank()) {
            sb.append("<h3>${escapeHtml(content.author)}</h3>")
        }
        sb.append("<hr/>")

        // 章节内容
        for (chapter in content.chapters) {
            sb.append(chapter)
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    private fun renderHtml(html: String) {
        webView?.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    fun setTheme(theme: ReaderTheme) {
        currentTheme = theme
        applyCss()
    }

    fun setFontSize(size: Int) {
        currentFontSize = size
        applyCss()
    }

    fun seekTo(progress: Float) {
        webView?.evaluateJavascript(
            """
            (function() {
                var sh = document.documentElement.scrollHeight - document.documentElement.clientHeight;
                window.scrollTo(0, sh * $progress);
            })();
            """.trimIndent(),
            null,
        )
    }

    fun setOnPageChangeListener(listener: (Int, Int) -> Unit) {
        this.pageChangeListener = listener
    }

    fun destroy() {
        webView?.destroy()
        webView = null
    }

    private fun applyCss() {
        val css = buildThemeCss(currentTheme, currentFontSize)
        webView?.evaluateJavascript(
            """
            (function() {
                var s = document.getElementById('reader-theme');
                if (s) s.textContent = '${css.replace("'", "\\'").replace("\n", " ")}';
            })();
            """.trimIndent(),
            null,
        )
        // 重新加载以应用主题
        webView?.reload()
    }

    private fun buildThemeCss(theme: ReaderTheme, fontSizeSp: Int): String {
        val (bg, textColor, linkColor) = when (theme) {
            ReaderTheme.DAY -> Triple("#F8F9FA", "#202124", "#1A73E8")
            ReaderTheme.SEPIA -> Triple("#F5F0E8", "#3D3929", "#8B4513")
            ReaderTheme.NIGHT -> Triple("#0D1117", "#C9D1D9", "#58A6FF")
        }

        return """
            * { box-sizing: border-box; }
            body {
                background: $bg; color: $textColor;
                font-family: 'Noto Serif', 'Georgia', serif;
                font-size: ${fontSizeSp}px;
                line-height: 1.8;
                padding: 20px 24px; margin: 0; max-width: 100%;
            }
            a { color: $linkColor; }
            h1 { font-size: ${fontSizeSp + 8}px; text-align: center; margin: 32px 0 16px; }
            h2 { font-size: ${fontSizeSp + 4}px; margin: 24px 0 12px; }
            h3 { font-size: ${fontSizeSp + 2}px; margin: 20px 0 10px; }
            p { margin: 0 0 12px; text-indent: 2em; }
            img { max-width: 100%; height: auto; display: block; margin: 16px auto; }
            hr { border: none; border-top: 1px solid ${textColor}20; margin: 24px 0; }
            blockquote {
                border-left: 3px solid ${textColor}40;
                margin: 16px 0; padding: 8px 16px;
                background: ${textColor}08; font-style: italic;
            }
            pre, code {
                font-family: 'Consolas', monospace;
                background: ${textColor}10;
                border-radius: 4px; padding: 2px 6px; font-size: 90%;
            }
            pre { padding: 16px; overflow-x: auto; }
            table { border-collapse: collapse; width: 100%; margin: 16px 0; }
            th, td { border: 1px solid ${textColor}30; padding: 8px 12px; text-align: left; }
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#039;")
    }
}
