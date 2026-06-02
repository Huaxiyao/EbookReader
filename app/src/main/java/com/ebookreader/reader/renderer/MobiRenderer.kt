package com.ebookreader.reader.renderer

import android.content.Context
import android.webkit.WebView
import com.ebookreader.data.model.ReaderTheme
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MOBI/AZW3 渲染器 — 解析 PalmDB/MOBI 格式并提取文本显示
 *
 * MOBI 格式结构：
 *   PalmDB Header (78 bytes)
 *   PDB Records (索引)
 *   MOBI Header (包含标题、作者等元数据)
 *   Raw text (通常是压缩的 HTML)
 *
 * 注意：完整的 MOBI 支持非常复杂（含多种压缩/加密方案），
 * 这里实现了解析基本 mobi-6 格式的通用方案。
 */
class MobiRenderer(private val context: Context) {

    private var webView: WebView? = null
    private var currentTheme: ReaderTheme = ReaderTheme.DAY
    private var currentFontSize: Int = 18
    private var totalCharacters: Int = 0
    private var pageChangeListener: ((Int, Int) -> Unit)? = null

    data class MobiMeta(
        val title: String,
        val author: String,
        val contentHtml: String,
    )

    fun initialize(webView: WebView, filePath: String) {
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

        webView.addJavascriptInterface(
            object {
                @android.webkit.JavascriptInterface
                fun onScroll(percent: Float) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val chars = (percent * totalCharacters).toInt()
                        pageChangeListener?.invoke(chars, totalCharacters)
                    }
                }
            },
            "MobiCallback",
        )

        val meta = parseMobi(filePath)
        totalCharacters = meta.contentHtml.length

        val css = buildThemeCss(currentTheme, currentFontSize)
        val fullHtml = """
            <!DOCTYPE html>
            <html><head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>$css</style>
            </head><body>
            <h1>${escapeHtml(meta.title)}</h1>
            ${if (meta.author.isNotBlank()) "<h3>${escapeHtml(meta.author)}</h3><hr/>" else ""}
            ${meta.contentHtml}
            </body></html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)

        // 注入滚动监听
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
                                    MobiCallback.onScroll(pct);
                                }
                            }
                        };
                    })();
                """.trimIndent(), null)
            }
        }
    }

    /**
     * 解析 MOBI 文件 — 提取标题、作者、正文
     */
    private fun parseMobi(filePath: String): MobiMeta {
        return try {
            val data = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath))
            val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

            // PalmDB Header: offset 0, 78 bytes
            // 略过 PalmDB header，直接找 MOBI header
            // PalmDB 的 lastRecord + recordCount 等细节我们走简化路径

            // 找 MOBI header 标识 "MOBI"
            val mobiStart = findPattern(data, "MOBI".toByteArray())
            if (mobiStart < 0) {
                return MobiMeta("未知书名", "", "<p>无法解析此 MOBI 文件</p>")
            }

            // 读取 MOBI header
            val mobiBuf = ByteBuffer.wrap(data, mobiStart, data.size - mobiStart)
                .order(ByteOrder.BIG_ENDIAN)

            // MOBI header 结构:
            // 0: "MOBI" (4 bytes)
            // 4: header length (4 bytes)
            // 8: MOBI type (4 bytes)
            // 12: text encoding (4 bytes)
            // 16: unique id (4 bytes)
            // 20: version (4 bytes)
            // ...
            // 68: first non-book index (4 bytes) — start of text
            // 72: full name offset (4 bytes) — title string offset in MOBI header
            // 76: full name length (4 bytes)
            // 80: locale (4 bytes)
            // ...
            // 112: drm offset (4 bytes)
            // 116: drm count (4 bytes)
            // 120: drm size (4 bytes)
            // 124: drm flags (4 bytes)
            // ...
            // 196: exth region? (not always)

            val headerLen = getInt(data, mobiStart + 4)
            val fullNameOffset = getInt(data, mobiStart + 68)
            val fullNameLen = getInt(data, mobiStart + 72)

            // 读取书名
            val title = if (fullNameOffset > 0 && fullNameLen > 0) {
                val nameStart = mobiStart + fullNameOffset
                val nameBytes = data.copyOfRange(nameStart, nameStart + fullNameLen)
                String(nameBytes, Charsets.UTF_8).trim('\u0000', ' ')
            } else {
                "未知书名"
            }

            // 找 EXTH header (扩展头，包含作者等元数据)
            val exthStart = findExthHeader(data, mobiStart + headerLen)
            var author = ""

            if (exthStart > 0) {
                // EXTH header: "EXTH" + length + count + records
                val exthCount = getInt(data, exthStart + 8)
                var offset = exthStart + 12
                for (i in 0 until exthCount) {
                    if (offset + 8 > data.size) break
                    val recordType = getInt(data, offset)       // 4 bytes
                    val recordLen = getInt(data, offset + 4)    // 4 bytes
                    if (recordType == 100) { // 100 = author
                        val authorBytes = data.copyOfRange(offset + 8, offset + recordLen)
                        author = String(authorBytes, Charsets.UTF_8).trim('\u0000', ' ')
                    }
                    offset += recordLen
                }
            }

            // 提取正文：尝试找 PalmDoc 压缩的文本区
            // 简化版 — 从文件末尾向上扫描找可读文本
            val contentHtml = extractTextContent(data)

            MobiMeta(title, author, contentHtml)
        } catch (e: Exception) {
            MobiMeta(
                "读取失败",
                "",
                "<p>MOBI 解析错误: ${escapeHtml(e.message ?: "未知错误")}</p>",
            )
        }
    }

    private fun extractTextContent(data: ByteArray): String {
        // 简化实现：提取可打印 ASCII/UTF-8 文本片段
        val sb = StringBuilder()
        var i = data.size - 1
        // 从后往前找文本密度高的区域
        val textBlocks = mutableListOf<String>()
        val currentBlock = StringBuilder()

        // 粗略提取：取文件后 60% 区域扫描
        val startPos = (data.size * 0.4).toInt()
        for (j in startPos until data.size) {
            val b = data[j].toInt() and 0xFF
            if (b in 0x20..0x7E || b in 0xA0..0xFF || b == 0x0A.toByte().toInt() || b == 0x0D.toByte().toInt() || b == 0x09) {
                currentBlock.append(b.toChar())
                if (currentBlock.length > 1000) {
                    textBlocks.add(currentBlock.toString())
                    currentBlock.clear()
                }
            } else {
                if (currentBlock.length > 50) {
                    textBlocks.add(currentBlock.toString())
                }
                currentBlock.clear()
            }
        }
        if (currentBlock.length > 50) {
            textBlocks.add(currentBlock.toString())
        }

        if (textBlocks.isEmpty()) return "<p>[无法提取正文]</p>"

        // 拼接为段落
        return textBlocks.joinToString("") { block ->
            block.split("\n")
                .filter { it.trim().isNotBlank() }
                .joinToString("") { "<p>${escapeHtml(it.trim())}</p>" }
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
            "(function() { var s = document.createElement('style'); s.id='reader-theme'; s.textContent = '${
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
            * { box-sizing: border-box; }
            body { background:$bg; color:$textColor; font-family:serif;
                   font-size:${fontSizeSp}px; line-height:1.8; padding:20px 24px; margin:0; }
            h1 { text-align:center; margin:32px 0; }
            p { margin:0 0 12px; text-indent:2em; }
            img { max-width:100%; height:auto; margin:16px auto; display:block; }
        """.trimIndent()
    }

    // ── 二进制工具 ──

    private fun getInt(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 24) or
               ((data[offset + 1].toInt() and 0xFF) shl 16) or
               ((data[offset + 2].toInt() and 0xFF) shl 8) or
               (data[offset + 3].toInt() and 0xFF)
    }

    private fun findPattern(data: ByteArray, pattern: ByteArray): Int {
        for (i in 0..data.size - pattern.size) {
            var match = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) { match = false; break }
            }
            if (match) return i
        }
        return -1
    }

    private fun findExthHeader(data: ByteArray, startFrom: Int): Int {
        val exth = "EXTH".toByteArray()
        for (i in startFrom until data.size - 8) {
            if (data[i] == 'E'.toByte() && data[i+1] == 'X'.toByte() &&
                data[i+2] == 'T'.toByte() && data[i+3] == 'H'.toByte()) {
                return i
            }
        }
        return -1
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")
    }
}
