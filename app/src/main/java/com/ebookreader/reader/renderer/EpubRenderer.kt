package com.ebookreader.reader.renderer

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ebookreader.data.model.ReaderTheme
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * ePub 渲染器 — 使用 WebView 渲染 ePub 的 HTML 内容
 *
 * 工作方式：
 * 1. 用 epublib 解析 ePub，提取所有章节 HTML
 * 2. 合并为一个完整的 HTML 文档
 * 3. 加载到 WebView，配合自定义 CSS 实现主题切换
 */
class EpubRenderer(private val context: Context) {

    private var webView: WebView? = null
    private var fullHtml: String = ""
    private var currentTheme: ReaderTheme = ReaderTheme.DAY
    private var currentFontSize: Int = 18
    private var pageChangeListener: ((Int, Int) -> Unit)? = null

    /**
     * 初始化 WebView 并加载 ePub
     */
    fun initialize(webView: WebView, epubFilePath: String) {
        this.webView = webView

        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            defaultTextEncodingName = "UTF-8"
        }

        webView.addJavascriptInterface(
            ScrollCallback { scrollPercent ->
                // JS 回调：当前滚动百分比
                pageChangeListener?.invoke(
                    (scrollPercent * 100).toInt(),
                    100,
                )
            },
            "ScrollCallback",
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 注入滚动监听
                webView.evaluateJavascript(
                    """
                    (function() {
                        var lastPercent = 0;
                        window.onscroll = function() {
                            var scrollTop = document.documentElement.scrollTop || document.body.scrollTop;
                            var scrollHeight = document.documentElement.scrollHeight - document.documentElement.clientHeight;
                            if (scrollHeight > 0) {
                                var percent = scrollTop / scrollHeight;
                                if (Math.abs(percent - lastPercent) > 0.01) {
                                    lastPercent = percent;
                                    ScrollCallback.onScroll(percent);
                                }
                            }
                        };
                    })();
                    """.trimIndent(),
                    null,
                )
            }
        }

        // 解析并加载
        val htmlContent = parseEpub(epubFilePath)
        fullHtml = htmlContent
        applyThemeAndRender()
    }

    /**
     * 使用 epublib 解析 ePub 文件
     */
    private fun parseEpub(filePath: String): String {
        return try {
            val file = File(filePath)
            if (!file.exists()) return "<html><body><h1>文件不存在</h1></body></html>"

            // 使用 epublib 库解析
            val epubBook = nl.siegmann.epublib.domain.Book()
            val book = nl.siegmann.epublib.epub.EpubReader().readEpub(
                FileInputStream(filePath)
            )

            val sb = StringBuilder()
            sb.append("""<!DOCTYPE html><html><head><meta charset="UTF-8">""")
            sb.append("""<meta name="viewport" content="width=device-width, initial-scale=1.0">""")
            sb.append("""</head><body>""")

            // 书名 + 作者
            sb.append("<h1>${escapeHtml(book.title)}</h1>")
            if (book.metadata.authors.isNotEmpty()) {
                val author = book.metadata.authors.joinToString(", ") { it.firstname + " " + it.lastname }
                sb.append("<h3>${escapeHtml(author)}</h3>")
            }
            sb.append("<hr/>")

            // 遍历所有章节
            val toc = book.tableOfContents
            val spine = book.spine
            for (i in 0 until spine.size) {
                val resource = spine.getResource(i)
                try {
                    val content = String(resource.data, Charsets.UTF_8)
                    // 提取 <body> 内容或直接使用
                    val bodyContent = extractBodyContent(content)
                    sb.append(bodyContent)
                } catch (e: Exception) {
                    sb.append("<p><em>[章节解析错误]</em></p>")
                }
            }

            sb.append("</body></html>")
            sb.toString()
        } catch (e: Exception) {
            """<html><body style="padding:20px">
               <h1>读取失败</h1>
               <p>${escapeHtml(e.message ?: "未知错误")}</p>
               <p>请确认文件是有效的 ePub 格式。</p>
               </body></html>"""
        }
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
     * 切换阅读主题并刷新
     */
    fun setTheme(theme: ReaderTheme) {
        currentTheme = theme
        applyThemeAndRender()
    }

    /**
     * 设置字号并刷新
     */
    fun setFontSize(sizeSp: Int) {
        currentFontSize = sizeSp
        applyThemeAndRender()
    }

    /**
     * 设置滚动监听
     */
    fun setOnPageChangeListener(listener: (currentPage: Int, totalPages: Int) -> Unit) {
        this.pageChangeListener = listener
    }

    /**
     * 跳转到指定进度位置
     */
    fun seekTo(progress: Float) {
        webView?.evaluateJavascript(
            """
            (function() {
                var scrollHeight = document.documentElement.scrollHeight - document.documentElement.clientHeight;
                window.scrollTo(0, scrollHeight * $progress);
            })();
            """.trimIndent(),
            null,
        )
    }

    /** 获取当前使用的 WebView */
    fun getWebView(): WebView? = webView

    /**
     * 释放资源
     */
    fun destroy() {
        webView?.destroy()
        webView = null
    }

    // ── 私有方法 ──────────────────────────────────────────

    private fun applyThemeAndRender() {
        val css = buildThemeCss(currentTheme, currentFontSize)
        val styledHtml = fullHtml.replace(
            "</head>",
            "<style>$css</style></head>"
        )
        webView?.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
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
                background-color: $bg;
                color: $textColor;
                font-family: 'Noto Serif', 'Georgia', serif;
                font-size: ${fontSizeSp}px;
                line-height: 1.8;
                padding: 20px 24px;
                margin: 0;
                max-width: 100%;
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
                margin: 16px 0;
                padding: 8px 16px;
                background: ${textColor}08;
                font-style: italic;
            }
            pre, code {
                font-family: 'JetBrains Mono', 'Consolas', monospace;
                background: ${textColor}10;
                border-radius: 4px;
                padding: 2px 6px;
                font-size: 90%;
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

    /**
     * JavaScript 接口 — 接收滚动回调
     */
    private class ScrollCallback(
        private val onScroll: (Float) -> Unit,
    ) {
        @android.webkit.JavascriptInterface
        fun onScroll(percent: Float) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onScroll(percent)
            }
        }
    }
}
