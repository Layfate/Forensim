package com.forensim.app.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.CheckCircle
import com.forensim.app.core.designsystem.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.forensim.app.core.designsystem.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Async screen state shared by every feature. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Error(val message: String) : Load<Nothing>
    data class Ready<T>(val data: T, val fromCache: Boolean = false) : Load<T>
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    /** Replaces the standard title bar (e.g. [HomeHeader] on Home tabs). */
    header: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Scaffold(
        floatingActionButton = floatingActionButton,
        topBar = {
            // White bar with a hairline under it, sitting on the #FAFAFA page.
            Column(Modifier.background(cs.surface)) {
                if (header != null) {
                    header()
                } else {
                    TopAppBar(
                        title = {
                            // Top-level tabs get a large title; pushed detail pages keep a compact one.
                            Text(title, style = if (onBack == null) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                        },
                        navigationIcon = {
                            if (onBack != null) {
                                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                            }
                        },
                        actions = actions,
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface, scrolledContainerColor = cs.surface),
                    )
                }
                androidx.compose.material3.HorizontalDivider(color = cs.outlineVariant)
            }
        },
        containerColor = cs.background,
        content = content,
    )
}

/** "Hi Robin Cortez," (or "Hi there," without a name) for [HomeHeader]. */
@Composable
fun headerGreeting(fullName: String): String = fullName.trim().let { name ->
    if (name.isBlank()) androidx.compose.ui.res.stringResource(com.forensim.app.R.string.greeting_hi_anon)
    else androidx.compose.ui.res.stringResource(com.forensim.app.R.string.greeting_hi, name)
}

/** Home header sizes: a slightly larger profile avatar and a smaller logo badge beside the greeting. */
val HeaderAvatarSize = 52.dp
val HeaderLogoSize = 44.dp

/**
 * Home tab header: logo badge, a two-line greeting ("Hi Robin," over "Welcome back") and the
 * profile avatar, kept compact (8 dp above, 12 dp below).
 */
@Composable
fun HomeHeader(
    greeting: String,
    title: String,
    profileName: String,
    onProfile: () -> Unit,
    photo: ByteArray? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
            .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ForensimMark(HeaderLogoSize, Modifier.semantics { contentDescription = "ForenSim" })
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
        ProfileAvatarButton(profileName, onClick = onProfile, photo = photo, avatarSize = HeaderAvatarSize)
    }
}

/** Renders loading / error / content consistently (plan section 10.2: no silent failures). */
@Composable
fun <T> LoadContent(state: Load<T>, onRetry: () -> Unit, modifier: Modifier = Modifier, content: @Composable (T, Boolean) -> Unit) {
    when (state) {
        Load.Loading -> LoadingState(modifier)
        is Load.Error -> ErrorState(state.message, onRetry, modifier)
        is Load.Ready -> content(state.data, state.fromCache)
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = "Loading") {
    Box(modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(contentAlignment = Alignment.Center) {
                GlowDisc(ForensimTheme.colors.glow, 150.dp)
                ForensimLottie(ForensimAnimations.SCAN, loop = true, size = 104.dp)
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val offline = message.contains("offline", ignoreCase = true)
    EmptyState(
        icon = if (offline) Icons.Outlined.CloudOff else Icons.Outlined.ErrorOutline,
        title = if (offline) "You're offline" else "Something went wrong",
        body = message,
        actionLabel = onRetry?.let { "Try again" },
        onAction = onRetry,
        modifier = modifier,
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            GlowDisc(ForensimTheme.colors.glow, 132.dp)
            IconTile(icon, size = 64.dp, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.lg))
            OutlinedButton(onClick = onAction, shape = CircleShape, modifier = Modifier.heightIn(min = Spacing.touchTarget)) { Text(actionLabel) }
        }
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    /** Gap above the heading; pass 0.dp for the first heading on a page, which the list already pads. */
    topPadding: androidx.compose.ui.unit.Dp = Spacing.lg,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(top = topPadding, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

/** Standard content card: tonal surface with a hairline border, optional click / long-press with ripple. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ForensimCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, borderColor)
    val color = containerColor
    if (onLongClick != null) {
        val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Surface(modifier = modifier.pressScale(interaction, PressScale.CARD).fillMaxWidth().heightIn(min = Spacing.touchTarget),
            shape = shape, color = color, border = border) {
            Column(
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = onClick ?: {},
                        onLongClick = onLongClick,
                        onLongClickLabel = onLongClickLabel,
                    )
                    .padding(contentPadding),
                content = content,
            )
        }
    } else if (onClick != null) {
        val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Surface(onClick = onClick, interactionSource = interaction,
            modifier = modifier.pressScale(interaction, PressScale.CARD).fillMaxWidth().heightIn(min = Spacing.touchTarget),
            shape = shape, color = color, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget), shape = shape, color = color, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

enum class Tone { NEUTRAL, PRIMARY, SUCCESS, ATTENTION, ERROR }

private data class ToneColors(val container: Color, val content: Color, val accent: Color)

/** Tinted background + neutral text; the semantic colour goes on the dot / icon only. */
@Composable
private fun toneColors(tone: Tone): ToneColors {
    val cs = MaterialTheme.colorScheme
    val fx = ForensimTheme.colors
    return when (tone) {
        Tone.NEUTRAL -> ToneColors(cs.surfaceContainerHighest, cs.onSurface, cs.onSurfaceVariant)
        Tone.PRIMARY -> ToneColors(cs.primaryContainer, cs.onPrimaryContainer, cs.primary)
        Tone.SUCCESS -> ToneColors(fx.successContainer, fx.onSuccessContainer, fx.success)
        Tone.ATTENTION -> ToneColors(fx.attentionContainer, fx.onAttentionContainer, fx.attention)
        Tone.ERROR -> ToneColors(cs.errorContainer, cs.onErrorContainer, cs.error)
    }
}

/** Small status label. Text always carries the meaning; colour only reinforces it. */
@Composable
fun StatusChip(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier) {
    val (bg, fg, accent) = toneColors(tone)
    Surface(color = bg, contentColor = fg, shape = CircleShape, modifier = modifier) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun Banner(text: String, tone: Tone = Tone.PRIMARY, icon: ImageVector = Icons.Outlined.Info, modifier: Modifier = Modifier) {
    val (bg, fg, accent) = toneColors(tone)
    // Success banners get the check icon by default, like other "done" confirmations.
    val glyph = if (tone == Tone.SUCCESS && icon == Icons.Outlined.Info) Icons.Outlined.CheckCircle else icon
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
        modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Icon(glyph, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(Spacing.sm))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun OfflineBanner(fromCache: Boolean) {
    if (fromCache) {
        Banner("Showing saved content. You're offline, so changes and graded work are unavailable until you reconnect.",
            Tone.ATTENTION, Icons.Outlined.CloudOff, Modifier.padding(bottom = Spacing.sm))
    }
}

@Composable
fun ProgressRow(
    label: String,
    fraction: Float,
    detail: String,
    modifier: Modifier = Modifier,
    /** Overrides for rows drawn on a photo or a colored card; default to the theme's. */
    detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    barColor: Color = MaterialTheme.colorScheme.onSurface,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val progress = animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        // Slow enough to see the bar glide right, easing out as it arrives.
        animationSpec = if (LocalReducedMotion.current) snap() else tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "Progress",
    )
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = Spacing.sm))
            Text(detail, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularNumbers), color = detailColor)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = barColor,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    ForensimCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TabularNumbers))
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Button(onClick = onClick, enabled = enabled && !loading, shape = CircleShape, interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = 14.dp), modifier = modifier.heightIn(min = 52.dp)) {
        if (loading && !LocalReducedMotion.current) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            Spacer(Modifier.size(Spacing.sm))
        }
        Text(if (loading) "$text…" else text, style = MaterialTheme.typography.buttonLarge)
    }
}

/** Centers content at a readable width on tablets (plan section 10.2 / pro-rules readable measure). */
@Composable
fun ReadableColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Spacing.readableWidth).fillMaxWidth(), content = content)
    }
}

fun Double.pct(max: Double?): String = if (max == null || max <= 0.0) "-" else "${(this / max * 100).toInt()}%"

/** Score as "2/10 · 20%": how many were right out of the total, then the percentage. */
fun Double.outOf(max: Double?): String = if (max == null || max <= 0.0) "-" else "${num(this)}/${num(max)} · ${pct(max)}"

private fun num(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(v)
