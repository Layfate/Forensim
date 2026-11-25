package com.forensim.app.feature.classes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.forensim.app.core.data.CLASS_COVERS
import com.forensim.app.core.network.SupabaseClient
import com.forensim.app.ui.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Class cover photos. Each upload gets a new random path, so a path's bytes never change and can
 * be cached forever: in memory for scrolling, on disk for offline and cold starts.
 */
object ClassCovers {
    private val memory = LruCache<String, ImageBitmap>(24)

    private fun dir(context: Context) = File(context.applicationContext.cacheDir, "class_covers")
    private fun file(context: Context, path: String) = File(dir(context), path.replace('/', '_'))

    fun cached(path: String): ImageBitmap? = memory.get(path)

    /** Puts freshly uploaded bytes in the caches so the professor sees the cover at once. */
    suspend fun prime(context: Context, path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        runCatching { dir(context).mkdirs(); file(context, path).writeBytes(bytes) }
        decode(bytes)?.let { memory.put(path, it) }
    }

    suspend fun load(context: Context, client: SupabaseClient, path: String): ImageBitmap? {
        memory.get(path)?.let { return it }
        val bytes = withContext(Dispatchers.IO) { file(context, path).takeIf { it.exists() }?.readBytes() }
            ?: runCatching { client.downloadObject(CLASS_COVERS, path) }.getOrNull()?.also { b ->
                withContext(Dispatchers.IO) { runCatching { dir(context).mkdirs(); file(context, path).writeBytes(b) } }
            }
            ?: return null
        return withContext(Dispatchers.Default) { decode(bytes) }?.also { memory.put(path, it) }
    }

    private fun decode(bytes: ByteArray): ImageBitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()

    /** Shape of the stored cover: wide like the class card. The card still crops to fit at any height. */
    private const val COVER_W = 1280
    private const val COVER_H = 720
    private const val MAX_BYTES = 1_900_000 // bucket limit is 2 MB

    /**
     * Turns any picked picture (portrait, panorama, huge camera photo, PNG with transparency, HEIC,
     * screenshot...) into a 1280x720 JPEG cropped around its centre, under the bucket's size limit.
     */
    suspend fun prepareCover(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.Default) {
        val source = decode(context, uri) ?: throw IllegalStateException("Couldn't read image $uri")
        val cropped = centerCrop(source, COVER_W, COVER_H)
        if (cropped !== source) source.recycle()
        // A 1280x720 JPEG is ~200-500 KB, but step the quality down just in case.
        var bytes = ByteArray(0)
        for (quality in intArrayOf(85, 70, 55, 40)) {
            bytes = ByteArrayOutputStream().use { out -> cropped.compress(Bitmap.CompressFormat.JPEG, quality, out); out.toByteArray() }
            if (bytes.size <= MAX_BYTES) break
        }
        cropped.recycle()
        bytes
    }

    /** Decodes small enough to save memory (at least the cover size), upright, as plain ARGB_8888. */
    private fun decode(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val (w, h) = info.size.width to info.size.height
                    // Shrink only as far as the crop still covers 1280x720.
                    val scale = maxOf(COVER_W.toFloat() / w, COVER_H.toFloat() / h)
                    if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                }.let(::toArgb)
            }.onFailure { android.util.Log.w(TAG, "ImageDecoder failed, trying BitmapFactory", it) }
        }
        // Fallback (and older Android): sampled BitmapFactory decode, rotated per EXIF.
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= COVER_W && bounds.outHeight / (sample * 2) >= COVER_H) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } ?: return null
        val degrees = runCatching {
            resolver.openInputStream(uri)?.use { android.media.ExifInterface(it).rotationDegreesCompat() } ?: 0
        }.getOrDefault(0)
        if (degrees == 0) return bitmap
        val m = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true).also { if (it !== bitmap) bitmap.recycle() }
    }

    /** Copies wide-gamut / HDR / odd-config bitmaps to plain ARGB_8888, which JPEG always accepts. */
    private fun toArgb(b: Bitmap): Bitmap =
        if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, false).also { b.recycle() }

    /** Scales [src] to cover [w]x[h] and cuts off the overflow evenly on both sides. */
    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(android.graphics.Color.BLACK) // transparent PNG areas become black, not garbage
        val dx = (w - src.width * scale) / 2f
        val dy = (h - src.height * scale) / 2f
        val m = android.graphics.Matrix().apply { postScale(scale, scale); postTranslate(dx, dy) }
        canvas.drawBitmap(src, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun android.media.ExifInterface.rotationDegreesCompat(): Int =
        when (getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90, android.media.ExifInterface.ORIENTATION_TRANSPOSE -> 90
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            android.media.ExifInterface.ORIENTATION_ROTATE_270, android.media.ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }

    private const val TAG = "ClassCovers"
}

/** The cover at [path], loading it in the background; null while loading or when there is none. */
@Composable
fun rememberClassCover(path: String?): ImageBitmap? {
    val context = LocalContext.current
    val client = LocalAppContainer.current.supabase
    var image by remember(path) { mutableStateOf(path?.let { ClassCovers.cached(it) }) }
    LaunchedEffect(path) {
        if (path != null && image == null) image = ClassCovers.load(context, client, path)
    }
    return image
}

/** Colors for what sits on a class card: white on a cover photo, the theme's on a plain tint. */
@Immutable
data class ClassCardColors(
    val content: Color,
    val variant: Color,
    val label: Color,
    /** Background for pieces inside the card (icon tile, class code box). */
    val inner: Color,
    val track: Color,
    val onPhoto: Boolean,
)

/**
 * The class card's frame: the professor's cover photo filling the whole card under a dark scrim,
 * or the plain class tint when there is no photo (or while it loads). [content] gets the colors
 * to draw with, so text stays readable on either.
 */
@Composable
fun ClassCardBackground(
    coverPath: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(ClassCardColors) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val cover = rememberClassCover(coverPath)
    val colors = if (cover != null) {
        ClassCardColors(
            content = Color.White, variant = Color.White.copy(alpha = 0.82f), label = Color.White.copy(alpha = 0.9f),
            inner = Color.Black.copy(alpha = 0.34f), track = Color.White.copy(alpha = 0.3f), onPhoto = true,
        )
    } else {
        ClassCardColors(
            content = cs.onSurface, variant = cs.onSurfaceVariant, label = cs.onPrimaryContainer,
            inner = classInnerTint(tint), track = cs.surfaceContainerHighest, onPhoto = false,
        )
    }
    Box(modifier.fillMaxWidth().background(tint)) {
        if (cover != null) {
            Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            // Darker toward the bottom, where the code and stats sit.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.62f)))))
        }
        CompositionLocalProvider(LocalContentColor provides colors.content) {
            Column(Modifier.fillMaxWidth()) { content(colors) }
        }
    }
}
