package com.forensim.app.feature.lms

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Icon
import com.forensim.app.core.designsystem.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.forensim.app.core.designsystem.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded lesson images, kept small so revisiting a lesson is instant (≈24 MB budget). */
private object LessonImageCache : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

/**
 * Lesson images ship inside the app (assets/lesson-media) so the reviewer works offline.
 * [path] is a file name such as "pid_patterns.webp".
 */
@Composable
fun LessonImage(path: String, caption: String?, credit: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState(LessonImageCache.get(path), path) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("lesson-media/$path").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                }.getOrNull()
            }?.also { LessonImageCache.put(path, it) }
        }
    }
    var zoomed by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(top = Spacing.md)) {
        val img = bitmap
        Surface(
            onClick = { if (img != null) zoomed = true },
            shape = MaterialTheme.shapes.medium,
            color = Color.White,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = caption ?: "Lesson image" },
        ) {
            Box {
                if (img != null) {
                    Image(img, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(img.width.toFloat() / img.height.coerceAtLeast(1)))
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ZoomIn, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                }
            }
        }
        if (caption != null) {
            Spacer(Modifier.height(6.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
        if (credit != null) {
            Text(credit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val img = bitmap
    if (zoomed && img != null) ZoomableImageDialog(img, caption) { zoomed = false }
}

/** Full-screen viewer: pinch to zoom, drag to pan, double-tap to toggle 2.5x. */
@Composable
private fun ZoomableImageDialog(image: ImageBitmap, caption: String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(
                image, contentDescription = caption, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        })
                    }
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(Spacing.sm)) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
            }
            if (caption != null) {
                Text(caption, color = Color.White, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(Spacing.md))
            }
        }
    }
}

/**
 * A YouTube video that plays inside the app. Nothing is loaded until the student taps play, so a
 * lesson with several videos still opens instantly. Full-screen playback is supported.
 */
@Composable
fun LessonVideo(videoId: String, title: String?, modifier: Modifier = Modifier) {
    var playing by rememberSaveable(videoId) { mutableStateOf(false) }
    var askData by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val container = com.forensim.app.ui.LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = com.forensim.app.core.data.AppSettings())
    // Advanced > "Ask before playing videos on mobile data".
    fun requestPlay() {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (settings.askBeforeMobileVideo && cm.isActiveNetworkMetered) askData = true else playing = true
    }
    if (askData) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askData = false },
            title = { Text(androidx.compose.ui.res.stringResource(com.forensim.app.R.string.mobile_video_title)) },
            text = { Text(androidx.compose.ui.res.stringResource(com.forensim.app.R.string.mobile_video_body)) },
            confirmButton = {
                com.forensim.app.core.designsystem.TextButton(onClick = { askData = false; playing = true }) {
                    Text(androidx.compose.ui.res.stringResource(com.forensim.app.R.string.play_anyway))
                }
            },
            dismissButton = {
                com.forensim.app.core.designsystem.TextButton(onClick = { askData = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.forensim.app.R.string.cancel))
                }
            },
        )
    }
    Column(modifier.fillMaxWidth().padding(top = Spacing.md)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium).background(Color.Black)) {
            if (playing) {
                YouTubePlayer(videoId)
            } else {
                Box(
                    Modifier.fillMaxSize().clickable { requestPlay() }
                        .semantics { contentDescription = "Play video: ${title ?: "lesson video"}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(64.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(36.dp)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title ?: "Video", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
        Text("Video · plays in the app · needs internet", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlayer(videoId: String) {
    var fullScreenView by remember { mutableStateOf<View?>(null) }
    var fullScreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                        fullScreenView = view; fullScreenCallback = callback
                    }
                    override fun onHideCustomView() {
                        fullScreenView = null; fullScreenCallback = null
                    }
                }
                // YouTube's embedded player needs an HTTP referrer that identifies the app.
                loadDataWithBaseURL("https://com.forensim.app", playerHtml(videoId), "text/html", "utf-8", null)
                webView = this
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
    DisposableEffect(Unit) {
        onDispose {
            webView?.apply { stopLoading(); loadUrl("about:blank"); destroy() }
            webView = null
        }
    }
    val fs = fullScreenView
    if (fs != null) {
        Dialog(onDismissRequest = { fullScreenCallback?.onCustomViewHidden() },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            AndroidView(factory = { (fs.parent as? ViewGroup)?.removeView(fs); fs }, modifier = Modifier.fillMaxSize().background(Color.Black))
        }
    }
}

private fun playerHtml(id: String): String {
    val safe = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>
</head><body><iframe src="https://www.youtube-nocookie.com/embed/$safe?autoplay=1&playsinline=1&rel=0"
allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe></body></html>"""
}
