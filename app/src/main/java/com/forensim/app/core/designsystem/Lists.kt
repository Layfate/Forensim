package com.forensim.app.core.designsystem

import androidx.compose.ui.res.stringResource
import com.forensim.app.R
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One rounded card holding several [ListRow]s separated by hairlines, like a settings list. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

/** Hairline between rows of a [ListGroup], inset to line up with the row text. */
@Composable
fun ListDivider(inset: Dp = 68.dp) {
    HorizontalDivider(Modifier.padding(start = inset), color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * A single list row: optional icon tile, one-line title, optional one-line subtitle and a trailing
 * slot (defaults to a chevron when the row is clickable).
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    iconContent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = if (onClick != null) ({ Chevron() }) else null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (icon != null) IconTile(icon, size = 36.dp, container = iconContainer, content = iconContent)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun Chevron() {
    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Small right-aligned value for a [ListRow] trailing slot. */
@Composable
fun RowValue(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TabularNumbers), color = color)
}

/** Compact metric card: icon, big value, short label. Use three in a [StatCardRow]. */
@Composable
fun StatCard(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    ForensimCard(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        IconTile(icon, size = 32.dp)
        androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = TabularNumbers),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Two lines so longer translations ("Average sa quiz") wrap instead of being cut off.
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Equal-width, equal-height row of [StatCard]s. */
@Composable
fun StatCardRow(vararg stats: Triple<ImageVector, String, String>) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        stats.forEach { (icon, value, label) ->
            StatCard(icon, value, label, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** Profile photo when one is set, initials otherwise. */
@Composable
fun UserAvatar(name: String, size: Dp, photo: ByteArray?, modifier: Modifier = Modifier) {
    val bitmap = remember(photo) {
        photo?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
    }
    if (bitmap == null) {
        Avatar(name, size = size, modifier = modifier)
    } else {
        androidx.compose.foundation.Image(
            bitmap, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    }
}

/** Avatar (photo or initials) with a presence dot: green when the device is online, grey offline. */
@Composable
fun PresenceAvatar(name: String, size: Dp, online: Boolean, modifier: Modifier = Modifier, photo: ByteArray? = null) {
    val dot = (size.value * 0.28f).coerceIn(10f, 22f).dp
    Box(modifier.semantics(mergeDescendants = true) { contentDescription = "$name, ${if (online) "online" else "offline"}" }) {
        UserAvatar(name, size, photo)
        Box(
            Modifier.align(Alignment.BottomEnd).size(dot)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                .padding(2.dp)
                .background(if (online) ForensimTheme.colors.success else MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

/**
 * Header avatar button. Unlike IconButton it does not clip its content to a circle, so the
 * presence dot on the avatar's corner stays fully visible.
 */
@Composable
fun ProfileAvatarButton(name: String, onClick: () -> Unit, modifier: Modifier = Modifier, photo: ByteArray? = null, avatarSize: Dp = 40.dp) {
    val box = maxOf(48.dp, avatarSize + 4.dp)
    Box(
        // Unbounded circular ripple instead of a clip, so the corner presence dot is never cut off.
        modifier.padding(horizontal = Spacing.xs).size(box)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(bounded = false, radius = box / 2),
                onClickLabel = stringResource(R.string.open_profile), role = androidx.compose.ui.semantics.Role.Button, onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        PresenceAvatar(name, size = avatarSize, online = rememberIsOnline(), photo = photo)
    }
}

/** Live network state, for presence indicators. Starts from the current connection. */
@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var online by remember {
        mutableStateOf(cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
    }
    DisposableEffect(cm) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            override fun onLost(network: Network) { online = false }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return online
}
