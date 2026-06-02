package com.ebookreader.reader.renderer

import android.content.Context
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy
import java.io.File

/**
 * PDF 渲染器 — 封装 AndroidPdfViewer 库
 */
class PdfRenderer(private val context: Context) {

    private var pdfView: PDFView? = null
    private var totalPages: Int = 0
    private var currentPage: Int = 0
    private var pageChangeListener: ((Int, Int) -> Unit)? = null

    /**
     * 初始化并加载 PDF 文件
     */
    fun initialize(pdfView: PDFView, filePath: String) {
        this.pdfView = pdfView

        val file = File(filePath)
        if (!file.exists()) return

        pdfView.fromFile(file)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .defaultPage(0)
            .scrollHandle(DefaultScrollHandle(context))
            .pageFitPolicy(FitPolicy.WIDTH)
            .spacing(8)
            .onPageChange { page, pageCount ->
                currentPage = page
                totalPages = pageCount
                pageChangeListener?.invoke(page + 1, pageCount)
            }
            .onLoad { nbPages ->
                totalPages = nbPages
                pageChangeListener?.invoke(1, nbPages)
            }
            .load()
    }

    /**
     * 跳转到指定页面
     */
    fun goToPage(page: Int) {
        pdfView?.jumpTo(page - 1)
    }

    /**
     * 跳转到进度百分比
     */
    fun seekTo(progress: Float) {
        if (totalPages > 0) {
            val page = (progress * totalPages).toInt().coerceIn(0, totalPages - 1)
            pdfView?.jumpTo(page)
        }
    }

    /**
     * 上一页
     */
    fun previousPage() {
        pdfView?.jumpTo(currentPage - 1)
    }

    /**
     * 下一页
     */
    fun nextPage() {
        pdfView?.jumpTo(currentPage + 1)
    }

    /**
     * 设置页面变化监听
     */
    fun setOnPageChangeListener(listener: (Int, Int) -> Unit) {
        this.pageChangeListener = listener
    }

    /**
     * 缩小
     */
    fun zoomOut() {
        pdfView?.zoomTo(pdfView?.currentZoom?.times(0.8f) ?: 1f)
    }

    /**
     * 放大
     */
    fun zoomIn() {
        pdfView?.zoomTo(pdfView?.currentZoom?.times(1.25f) ?: 1f)
    }

    /**
     * 获取当前页面
     */
    fun getCurrentPage(): Int = currentPage + 1

    /**
     * 获取总页数
     */
    fun getTotalPages(): Int = totalPages

    /**
     * 清理资源
     */
    fun destroy() {
        pdfView = null
    }
}
