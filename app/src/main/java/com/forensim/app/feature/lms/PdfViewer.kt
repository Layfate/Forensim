package com.forensim.app.feature.lms

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.forensim.app.core.designsystem.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Thread-safe wrapper: PdfRenderer allows only one open page at a time. */
private class PdfDocument(file: File) {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val lock = Mutex()
    val pageCount: Int = renderer.pageCount
    @Volatile private var closed = false

    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.Default) {
        lock.withLock {
            if (closed) return@withLock null
            renderer.openPage(index).use { page ->
                val w = widthPx.coerceIn(320, 1800)
                val h = (w.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(AndroidColor.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    suspend fun close() = lock.withLock {
        if (!closed) { closed = true; renderer.close(); fd.close() }
    }
}

/**
 * Lesson PDF as a lazily rendered page list. Only pages near the viewport are rasterised, so long
 * documents scroll smoothly. [header] and [footer] scroll with the pages.
 */
@Composable
fun PdfPages(
    file: File,
    modifier: Modifier = Modifier,
    header: LazyListScope.() -> Unit = {},
    footer: LazyListScope.() -> Unit = {},
) {
    val doc = remember(file) { runCatching { PdfDocument(file) }.getOrNull() }
    DisposableEffect(doc) {
        onDispose { doc?.let(::closeLater) }
    }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val pageWidth = minOf(maxWidth, 900.dp)
        val widthPx = with(LocalDensity.current) { pageWidth.roundToPx() }
        LazyColumn(
            Modifier.widthIn(max = 900.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            header()
            if (doc == null) {
                item("pdf-error") {
                    Text("This PDF could not be opened. It may be damaged; ask your professor to upload it again.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            } else {
                items(doc.pageCount, key = { "page-$it" }, contentType = { "pdf-page" }) { index ->
                    val bitmap by produceState<Bitmap?>(null, doc, index, widthPx) { value = doc.render(index, widthPx) }
                    val shape = MaterialTheme.shapes.medium
                    Box(
                        Modifier.fillMaxWidth().clip(shape).background(androidx.compose.ui.graphics.Color.White)
                            .semantics { contentDescription = "Page ${index + 1} of ${doc.pageCount}" },
                    ) {
                        val bmp = bitmap
                        if (bmp == null) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.414f))
                        } else {
                            Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width / bmp.height.toFloat()))
                        }
                    }
                }
            }
            footer()
        }
    }
}

/** Closes after any in-flight page render finishes (the render holds the same lock). */
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
private fun closeLater(doc: PdfDocument) {
    kotlinx.coroutines.GlobalScope.launch(Dispatchers.Default) { runCatching { doc.close() } }
}
