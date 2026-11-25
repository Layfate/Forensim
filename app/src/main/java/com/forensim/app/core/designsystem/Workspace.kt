package com.forensim.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

internal data class WorkspaceDestination(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val compactLabel: String = label,
    /** Drawn as a raised circular button in the centre of the bottom bar (e.g. the 3D lab). */
    val prominent: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
internal fun WorkspaceScaffold(
    destinations: List<WorkspaceDestination>,
    showNavigation: Boolean = true,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = showNavigation && maxWidth >= 600.dp && maxHeight >= 360.dp
        val showRailBrand = maxHeight >= 490.dp
        val showRailLabels = maxHeight >= 440.dp
        val compactLabels = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.5f
        val cs = MaterialTheme.colorScheme
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (showNavigation && !rail) WorkspaceBottomBar(destinations, compactLabels)
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (rail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = cs.surfaceContainerLow,
                        header = if (showRailBrand) {
                            { ForensimMark(36.dp, Modifier.padding(vertical = Spacing.sm)) }
                        } else null,
                    ) {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            destinations.forEach { destination ->
                                val showLabel = showRailLabels || destination.selected
                                NavigationRailItem(
                                    selected = destination.selected,
                                    onClick = destination.onClick,
                                    icon = { Icon(destination.icon, contentDescription = if (showLabel) null else destination.label) },
                                    label = if (showLabel) {
                                        {
                                            Text(destination.label, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    } else null,
                                    colors = NavigationRailItemDefaults.colors(
                                        indicatorColor = cs.primaryContainer,
                                        selectedIconColor = cs.onPrimaryContainer,
                                        selectedTextColor = cs.onSurface,
                                        unselectedIconColor = cs.onSurfaceVariant,
                                        unselectedTextColor = cs.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        }
    }
}

/**
 * Bottom navigation with an optional raised, circular centre action. Built by hand rather than with
 * NavigationBar so the prominent item can overflow the bar's top edge.
 */
@Composable
private fun WorkspaceBottomBar(destinations: List<WorkspaceDestination>, compactLabels: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(cs.surfaceContainerLow)) {
        HorizontalDivider(color = cs.outlineVariant)
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(NavigationBarDefaults.windowInsets).height(72.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val label = if (compactLabels) destination.compactLabel else destination.label
                // The whole cell is the touch target, but the ripple is drawn only inside the
                // indicator pill (or the centre circle) so the two shapes never stack.
                val interactions = remember { MutableInteractionSource() }
                Box(
                    Modifier.weight(1f).fillMaxHeight().selectable(
                        selected = destination.selected,
                        interactionSource = interactions,
                        indication = null,
                        role = Role.Tab,
                        onClick = destination.onClick,
                    ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (destination.prominent) ProminentNavItem(destination, label, interactions)
                    else NavItem(destination, label, interactions)
                }
            }
        }
    }
}

@Composable
private fun NavItem(destination: WorkspaceDestination, label: String, interactions: MutableInteractionSource) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val indicator by animateColorAsState(
        if (destination.selected) cs.primaryContainer else Color.Transparent,
        animationSpec = if (reduced) snap() else tween(Motion.ENTER_MS),
        label = "navIndicator",
    )
    Column(
        Modifier.fillMaxHeight().padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            Modifier.size(width = 56.dp, height = 32.dp).clip(CircleShape).background(indicator).indication(interactions, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(destination.icon, contentDescription = null, modifier = Modifier.size(24.dp),
                tint = if (destination.selected) cs.onPrimaryContainer else cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        NavLabel(destination, label)
    }
}

@Composable
private fun ProminentNavItem(destination: WorkspaceDestination, label: String, interactions: MutableInteractionSource) {
    val cs = MaterialTheme.colorScheme
    Column(
        // Taller than the bar on purpose: the circle rises above the bar's top edge.
        Modifier.wrapContentHeight(align = Alignment.Bottom, unbounded = true).padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp)
                .shadow(if (destination.selected) 10.dp else 6.dp, CircleShape)
                .clip(CircleShape)
                .background(cs.primary)
                .indication(interactions, ripple(color = cs.onPrimary))
                .border(4.dp, cs.surfaceContainerLow, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(destination.icon, contentDescription = null, tint = cs.onPrimary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(2.dp))
        NavLabel(destination, label)
    }
}

@Composable
private fun NavLabel(destination: WorkspaceDestination, label: String, selectedColor: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = label,
        modifier = Modifier.clearAndSetSemantics { contentDescription = destination.label },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (destination.selected) FontWeight.Medium else FontWeight.Normal,
        color = if (destination.selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun WorkspaceList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val gutter = if (maxWidth >= 600.dp) Spacing.xl else Spacing.md
        LazyColumn(
            modifier = Modifier.widthIn(max = 960.dp).fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(start = gutter, end = gutter, top = Spacing.md, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content,
        )
    }
}

@Composable
fun PageIntro(title: String, modifier: Modifier = Modifier, description: String? = null, category: String? = null) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(bottom = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        category?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.08.em), color = cs.onSurfaceVariant) }
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant) }
    }
}

@Composable
fun FeaturePanel(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    category: String? = null,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null,
    /** Tighter padding and a smaller title, for panels that sit above other content (Home). */
    compact: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    val panelModifier = modifier.fillMaxWidth()
    val border = BorderStroke(1.dp, cs.outlineVariant)
    val panelContent: @Composable () -> Unit = {
        Box {
            // Forensic motif: evidence-marker rings and a faint measurement grid, top-right.
            val line = cs.onSurface.copy(alpha = 0.05f)
            val ring = cs.onSurface.copy(alpha = 0.09f)
            Canvas(Modifier.align(Alignment.TopEnd).size(180.dp)) {
                val unit = 18.dp.toPx()
                for (i in 1..10) {
                    val p = i * unit
                    drawLine(line, Offset(p, 0f), Offset(p, size.height), strokeWidth = 1f)
                    drawLine(line, Offset(0f, p), Offset(size.width, p), strokeWidth = 1f)
                }
                val target = Offset(size.width * 0.66f, size.height * 0.34f)
                for (index in 0..3) {
                    val d = (36 + index * 30).dp.toPx()
                    drawOval(ring, target - Offset(d / 2, d / 2), Size(d, d), style = Stroke(1.5.dp.toPx()))
                }
                drawCircle(cs.onSurface.copy(alpha = 0.25f), 3.dp.toPx(), target)
            }
            Column(
                Modifier.padding(if (compact) Spacing.md + Spacing.xs else Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    IconTile(icon, size = if (compact) 32.dp else 40.dp, container = cs.surfaceContainerHighest, content = cs.onSurface)
                    category?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant) }
                }
                Spacer(Modifier.height(if (compact) 2.dp else Spacing.xs))
                Text(title, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    color = cs.onSurface, maxLines = if (compact) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() })
                Text(description, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                if (actionLabel != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    // The panel's one accent: a small maroon action pill.
                    Row(
                        Modifier.clip(CircleShape).background(cs.primary).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = cs.onPrimary)
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = cs.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
    if (onClick == null) {
        Surface(panelModifier, shape = shape, color = cs.surfaceContainer, contentColor = cs.onSurface, border = border, content = panelContent)
    } else {
        val interaction = remember { MutableInteractionSource() }
        Surface(onClick = onClick, interactionSource = interaction, modifier = panelModifier.pressScale(interaction, PressScale.CARD), shape = shape,
            color = cs.surfaceContainer, contentColor = cs.onSurface, border = border, content = panelContent)
    }
}

@Composable
fun DashboardStats(vararg stats: Pair<String, String>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val columnLimit = if (stats.size % 3 == 0) 3 else 2
        val columns = (maxWidth.value / (110f * fontScale)).toInt().coerceIn(1, columnLimit)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            stats.toList().chunked(columns).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    group.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
                    repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

