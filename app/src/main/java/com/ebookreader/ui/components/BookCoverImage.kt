package com.ebookreader.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ebookreader.data.model.Book
import com.ebookreader.data.model.BookFormat
import java.io.File

/**
 * 书籍封面图（带加载失败时的兜底图标）
 */
@Composable
fun BookCoverImage(
    book: Book,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val coverFile = book.coverPath?.let { File(it) }

        if (coverFile?.exists() == true) {
            AsyncImage(
                model = coverFile,
                contentDescription = book.title,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // 没有封面时显示格式图标兜底
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatLabel(book.format),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun formatLabel(format: BookFormat): String = when (format) {
    BookFormat.EPUB -> "ePub"
    BookFormat.PDF -> "PDF"
    BookFormat.MOBI -> "MOBI"
    BookFormat.AZW3 -> "AZW3"
    BookFormat.FB2 -> "FB2"
    BookFormat.CBZ -> "CBZ"
    BookFormat.CBR -> "CBR"
}
