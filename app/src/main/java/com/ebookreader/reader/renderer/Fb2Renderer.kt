package com.ebookreader.reader.renderer

import android.content.Context
import android.webkit.WebView
import com.ebookreader.data.model.ReaderTheme
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream

/**
 * FB2 (FictionBook) 渲染器 — FB2 是 XML 格式，直接解析并转为 HTML
 *
 * FB2 格式结构：
 *   <FictionBook>
 *     <description> 元数据（标题、作者等）
 *     <body> 正文内容（支持章节嵌套）
 *     <body name="notes"> 注释（可选）
 */
class Fb2Renderer(private val context: Context) {

    private var webView: WebView? = null
    private var currentTheme: ReaderTheme = ReaderTheme.DAY
    private var currentFontSize: Int = 18
    private var totalChars: Int = 0
    private var pageChangeListener: ((Int, Int) -> Unit)? = null

    fun initialize(webView: WebView, filePath: String) {
        this.webView = webView

        webView.settings.apply {
            javaScriptEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            defaultTextEncodingName = "UTF-8"
        }

        webView.addJavascriptInterface(
            object {
                @android.webkit.JavascriptInterface
                fun onScroll(percent: Float) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val chars = (percent * totalChars).toInt()
                        pageChangeListener?.invoke(chars, totalChars)
                    }
                }
            },
            "Fb2Callback",
        )

        val (title, author, bodyHtml) = parseFb2(filePath)
        totalChars = bodyHtml.length

        val css = buildThemeCss(currentTheme, currentFontSize)
        val fullHtml = """
            <!DOCTYPE html><html><head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>$css</style>
            </head><body>
            <h1>${escapeHtml(title)}</h1>
            ${if (author.isNotBlank()) "<h3>${escapeHtml(author)}</h3><hr/>" else ""}
            $bodyHtml
            </body></html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript("""
                    (function() {
                        var lastPct = 0;
                        window.onscroll = function() {
                            var st = document.documentElement.scrollTop || document.body.scrollTop;
                            var sh = document.documentElement.scrollHeight - document.documentElement.clientHeight;
                            if (sh > 0) {
                                var pct = st / sh;
                                if (Math.abs(pct - lastPct) > 0.01) {
                                    lastPct = pct;
                                    Fb2Callback.onScroll(pct);
                                }
                            }
                        };
                    })();
                """.trimIndent(), null)
            }
        }
    }

    /**
     * 使用 XmlPullParser 解析 FB2
     */
    private fun parseFb2(filePath: String): Triple<String, String, String> {
        return try {
            val file = File(filePath)
            if (!file.exists()) return Triple("文件不存在", "", "<p>文件未找到</p>")

            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(FileInputStream(file), "UTF-8")

            var title = ""
            var author = ""
            val bodyParts = StringBuilder()
            var depth = 0
            var inBody = false
            var inTitle = false
            var inAuthor = false
            var inFirstName = false
            var inLastName = false
            var firstName = ""
            var lastName = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name
                        when (tag) {
                            "body" -> {
                                val name = parser.getAttributeValue(null, "name")
                                if (name == null) inBody = true
                            }
                            "book-title" -> inTitle = true
                            "author" -> inAuthor = true
                            "first-name" -> if (inAuthor) inFirstName = true
                            "last-name" -> if (inAuthor) inLastName = true
                            "title" -> if (inBody) { bodyParts.append("<h2>"); depth++ }
                            "subtitle" -> if (inBody) { bodyParts.append("<h3>"); depth++ }
                            "p" -> if (inBody) { bodyParts.append("<p>"); depth++ }
                            "poem" -> if (inBody) { bodyParts.append("<div class='poem'>"); depth++ }
                            "stanza" -> if (inBody) { bodyParts.append("<div class='stanza'>"); depth++ }
                            "v" -> if (inBody) { bodyParts.append("<p>"); depth++ }
                            "cite" -> if (inBody) { bodyParts.append("<blockquote>"); depth++ }
                            "empty-line" -> if (inBody) bodyParts.append("<br/>")
                            "section" -> if (inBody) { bodyParts.append("<div class='section'>"); depth++ }
                            "strong" -> if (inBody) { bodyParts.append("<strong>"); depth++ }
                            "emphasis" -> if (inBody) { bodyParts.append("<em>"); depth++ }
                            "code" -> if (inBody) { bodyParts.append("<code>"); depth++ }
                            "image" -> {
                                val href = parser.getAttributeValue(null, "l:href") ?: parser.getAttributeValue(null, "href") ?: ""
                                if (href.isNotBlank() && inBody) {
                                    bodyParts.append("<img src='$href' alt='image'/>")
                                }
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotBlank()) {
                            when {
                                inTitle -> title += text
                                inFirstName -> firstName += text
                                inLastName -> lastName += text
                                inBody -> bodyParts.append(escapeHtml(text))
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val tag = parser.name
                        when (tag) {
                            "body" -> inBody = false
                            "book-title" -> inTitle = false
                            "author" -> {
                                inAuthor = false
                                author = "$firstName $lastName".trim()
                            }
                            "first-name" -> inFirstName = false
                            "last-name" -> inLastName = false
                            "title", "subtitle" -> if (inBody) { bodyParts.append("</h2>"); depth-- }
                            "p" -> if (inBody) { bodyParts.append("</p>"); depth-- }
                            "poem" -> if (inBody) { bodyParts.append("</div>"); depth-- }
                            "stanza" -> if (inBody) { bodyParts.append("</div>"); depth-- }
                            "v" -> if (inBody) { bodyParts.append("</p>"); depth-- }
                            "cite" -> if (inBody) { bodyParts.append("</blockquote>"); depth-- }
                            "section" -> if (inBody) { bodyParts.append("</div>"); depth-- }
                            "strong" -> if (inBody) { bodyParts.append("</strong>"); depth-- }
                            "emphasis" -> if (inBody) { bodyParts.append("</em>"); depth-- }
                            "code" -> if (inBody) { bodyParts.append("</code>"); depth-- }
                        }
                    }
                }
                eventType = parser.next()
            }

            Triple(title, author, bodyParts.toString())
        } catch (e: Exception) {
            Triple("解析失败", "", "<p>FB2 解析错误: ${escapeHtml(e.message ?: "未知错误")}</p>")
        }
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
        webView?.evaluateJavascript("""
            (function() {
                var sh = document.documentElement.scrollHeight - document.documentElement.clientHeight;
                window.scrollTo(0, sh * $progress);
            })();
        """.trimIndent(), null)
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
            "(function() { var s = document.createElement('style'); s.textContent = '${
                css.replace("'", "\\'").replace("\n", " ")
            }'; document.head.appendChild(s); })()",
            null,
        )
    }

    private fun buildThemeCss(theme: ReaderTheme, fontSizeSp: Int): String {
        val (bg, textColor) = when (theme) {
            ReaderTheme.DAY -> Pair("#F8F9FA", "#202124")
            ReaderTheme.SEPIA -> Pair("#F5F0E8", "#3D3929")
            ReaderTheme.NIGHT -> Pair("#0D1117", "#C9D1D9")
        }
        return """
            * { box-sizing:border-box; }
            body { background:$bg; color:$textColor; font-family:serif;
                   font-size:${fontSizeSp}px; line-height:1.8; padding:20px 24px; margin:0; }
            h1 { text-align:center; margin:32px 0; }
            h2 { margin:24px 0 12px; }
            h3 { margin:20px 0 10px; }
            p { margin:0 0 12px; text-indent:2em; }
            .poem { margin:16px 0; padding-left:24px; font-style:italic; }
            .stanza { margin:8px 0; }
            blockquote { border-left:3px solid ${textColor}40; margin:16px 0; padding:8px 16px; }
            img { max-width:100%; height:auto; margin:16px auto; display:block; }
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")
    }
}
