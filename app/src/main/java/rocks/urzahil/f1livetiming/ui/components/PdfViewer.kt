package rocks.urzahil.f1livetiming.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f
// Pages are re-rendered at twice the resolution once zoomed past this, so text stays sharp.
private const val SHARP_RENDER_ZOOM = 1.75f
// Upper bound for a page bitmap's width; a 2x render of an A4 page on a wide screen stays below ~35 MB.
private const val MAX_RENDER_WIDTH_PX = 2800

/**
 * Wraps [PdfRenderer], which renders one page at a time and is not thread-safe: every call is serialized
 * on [lock], so a page render never overlaps another one or [close].
 */
private class PdfDocument private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) {
    private val lock = Any()
    private var closed = false

    /** Width / height of each page, read once so placeholders keep the right shape before rendering. */
    val pageAspectRatios: List<Float> = (0 until renderer.pageCount).map { index ->
        renderer.openPage(index).use { page ->
            if (page.height > 0) page.width.toFloat() / page.height else 1f / 1.414f
        }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (closed) return@synchronized null
            renderer.openPage(index).use { page ->
                val height = (widthPx.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
                Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).apply {
                    // PDF pages have no background of their own; without this they render on transparent.
                    eraseColor(android.graphics.Color.WHITE)
                    page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            renderer.close()
            descriptor.close()
        }
    }

    companion object {
        /** Opens [file], or returns null when it is not a PDF the platform can render (damaged, encrypted). */
        fun open(file: File): PdfDocument? {
            val descriptor = try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (_: Exception) {
                return null
            }
            return try {
                PdfDocument(descriptor, PdfRenderer(descriptor))
            } catch (_: Exception) {
                descriptor.close()
                null
            }
        }
    }
}

/**
 * Scrollable, pinch-zoomable view of a local PDF file. Pinch or double-tap to zoom; once zoomed, pages
 * pan sideways as well. [onError] is called when the file cannot be opened.
 */
@Composable
fun PdfViewer(file: File, onError: () -> Unit, modifier: Modifier = Modifier) {
    val document = remember(file) { PdfDocument.open(file) }
    DisposableEffect(document) {
        onDispose { document?.close() }
    }
    if (document == null) {
        LaunchedEffect(file) { onError() }
        return
    }

    var zoom by remember(file) { mutableFloatStateOf(1f) }
    // Scroll corrections that keep the pinch focus in place, applied once the zoomed layout exists
    var pendingScroll by remember(file) { mutableStateOf<Pair<Float, Float>?>(null) }
    val listState = rememberLazyListState()
    val horizontalState = rememberScrollState()
    val density = LocalDensity.current

    LaunchedEffect(pendingScroll) {
        val (dx, dy) = pendingScroll ?: return@LaunchedEffect
        pendingScroll = null
        horizontalState.scrollBy(dx)
        listState.scrollBy(dy)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2A2F38))
            .testTag("pdf_viewer")
            .pointerInput(file) {
                detectTapGestures(onDoubleTap = { zoom = if (zoom > 1f) 1f else DOUBLE_TAP_ZOOM })
            }
            .pointerInput(file) {
                // Two-finger gestures are taken before the list sees them, so pinching never scrolls;
                // one finger is left to the list and the horizontal scroller.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val newZoom = (zoom * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            val factor = newZoom / zoom
                            if (factor != 1f) {
                                val focus = event.calculateCentroid()
                                val (dx, dy) = pendingScroll ?: (0f to 0f)
                                pendingScroll = (dx + (horizontalState.value + focus.x) * (factor - 1f)) to
                                    (dy + (listState.firstVisibleItemScrollOffset + focus.y) * (factor - 1f))
                                zoom = newZoom
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val pageWidth = (maxWidth - 16.dp) * zoom
        val renderWidthPx = with(density) {
            val base = (maxWidth - 16.dp).toPx()
            (if (zoom >= SHARP_RENDER_ZOOM) base * 2 else base).roundToInt().coerceIn(1, MAX_RENDER_WIDTH_PX)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalState, enabled = zoom > 1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(pageWidth + 16.dp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(document.pageAspectRatios.size) { index ->
                    PdfPage(
                        document = document,
                        index = index,
                        aspectRatio = document.pageAspectRatios[index],
                        renderWidthPx = renderWidthPx
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPage(document: PdfDocument, index: Int, aspectRatio: Float, renderWidthPx: Int) {
    // The previous bitmap stays on screen while a sharper or smaller one renders.
    var bitmap by remember(document, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(document, index, renderWidthPx) {
        document.render(index, renderWidthPx)?.let { bitmap = it }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(Color.White)
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
