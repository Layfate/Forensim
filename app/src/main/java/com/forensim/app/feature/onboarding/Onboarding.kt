package com.forensim.app.feature.onboarding

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined._3dRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forensim.app.R
import androidx.compose.ui.res.stringResource
import com.forensim.app.core.designsystem.LocalReducedMotion
import com.forensim.app.core.designsystem.OutlinedTextField
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.model.UserAccount
import com.forensim.app.core.network.ApiException
import com.forensim.app.ui.LocalAppContainer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * First-run setup for brand-new student accounts. The sign-up form marks the e-mail as pending
 * just before the account is created; the student app shows the flow once and then clears it.
 */
object Onboarding {
    private const val PREFS = "onboarding"
    private const val PENDING = "pending_emails"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(email: String) = email.trim().lowercase()

    fun markPending(context: Context, email: String) {
        val p = prefs(context)
        p.edit().putStringSet(PENDING, p.getStringSet(PENDING, emptySet()).orEmpty() + key(email)).apply()
    }

    fun clearPending(context: Context, email: String) {
        val p = prefs(context)
        p.edit().putStringSet(PENDING, p.getStringSet(PENDING, emptySet()).orEmpty() - key(email)).apply()
    }

    fun isPending(context: Context, email: String) = key(email) in prefs(context).getStringSet(PENDING, emptySet()).orEmpty()

    /** True once this account has finished (or skipped) onboarding on this device. */
    fun isDone(context: Context, userId: String) = prefs(context).getBoolean("done_$userId", false)

    /** Accounts younger than this still get onboarding (covers Google / Facebook sign-ups too). */
    val NEW_ACCOUNT_WINDOW: java.time.Duration = java.time.Duration.ofDays(1)

    /** Saves the answers (kept on the device) and ends onboarding for this account. */
    fun complete(context: Context, user: UserAccount, answers: OnboardingAnswers) {
        prefs(context).edit()
            .putInt("mood_${user.id}", answers.mood)
            .putFloat("familiarity_${user.id}", answers.familiarity)
            .putStringSet("focus_${user.id}", answers.focus)
            .putBoolean("done_${user.id}", true)
            .apply()
        clearPending(context, user.email)
    }
}

data class OnboardingAnswers(val mood: Int, val familiarity: Float, val focus: Set<String>)

private enum class Step { WELCOME, MOOD, FAMILIARITY, REFLECTION, FOCUS, FOCUS_DONE, CLASS_CODE, AI_PRIVACY }

private data class FocusArea(val key: String, @androidx.annotation.StringRes val label: Int, val icon: ImageVector)

@Suppress("DEPRECATION")
private val FOCUS_AREAS = listOf(
    FocusArea("csi", R.string.ob_area_csi, Icons.Outlined.ManageSearch),
    FocusArea("fingerprints", R.string.ob_area_fingerprints, Icons.Outlined.Fingerprint),
    FocusArea("ballistics", R.string.ob_area_ballistics, Icons.Outlined.GpsFixed),
    FocusArea("toxicology", R.string.ob_area_toxicology, Icons.Outlined.Science),
    FocusArea("documents", R.string.ob_area_documents, Icons.Outlined.Description),
    FocusArea("photography", R.string.ob_area_photography, Icons.Outlined.CameraAlt),
    FocusArea("polygraph", R.string.ob_area_polygraph, Icons.Outlined.MonitorHeart),
    FocusArea("lab3d", R.string.ob_area_lab3d, Icons.Outlined._3dRotation),
    FocusArea("ar", R.string.ob_area_ar, Icons.Outlined.ViewInAr),
)

private val MOOD_LABELS = listOf(R.string.ob_mood_nervous, R.string.ob_mood_unsure, R.string.ob_mood_okay, R.string.ob_mood_curious, R.string.ob_mood_excited)

private fun familiarityWords(v: Float) = when {
    v < 0.2f -> R.string.ob_fam_0
    v < 0.4f -> R.string.ob_fam_1
    v < 0.6f -> R.string.ob_fam_2
    v < 0.8f -> R.string.ob_fam_3
    else -> R.string.ob_fam_4
}

/** Calm, monochrome palette like a notebook page; follows light and dark theme. */
private data class Ink(val page: Color, val card: Color, val ink: Color, val onInk: Color, val muted: Color, val line: Color)

@Composable
private fun ink(): Ink {
    val cs = MaterialTheme.colorScheme
    return Ink(page = cs.surfaceContainer, card = cs.surface, ink = cs.onSurface, onInk = cs.surface,
        muted = cs.onSurfaceVariant, line = cs.outlineVariant)
}

@Composable
fun OnboardingFlow(user: UserAccount, onDone: (OnboardingAnswers) -> Unit) {
    val ink = ink()
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var mood by rememberSaveable { mutableIntStateOf(-1) }
    var familiarity by rememberSaveable { mutableFloatStateOf(0.5f) }
    var focus by rememberSaveable { mutableStateOf(setOf<String>()) }
    val finish = { onDone(OnboardingAnswers(mood, familiarity, focus)) }
    // Last step: ask once for notifications so the streak reminder can work, then finish either way.
    val context = LocalContext.current
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { finish() }
    val finishWithPermission = {
        val needs = android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needs) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS) else finish()
    }
    fun next() { if (step == Step.entries.last()) finish() else step = Step.entries[step.ordinal + 1] }
    BackHandler(enabled = step != Step.WELCOME) { step = Step.entries[step.ordinal - 1] }
    val reducedMotion = LocalReducedMotion.current

    Box(Modifier.fillMaxSize().background(ink.page).safeDrawingPadding()) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                if (reducedMotion) fadeIn(tween(220)) togetherWith fadeOut(tween(150))
                else (
                    // Material "shared axis": the old page eases out quickly, the new one glides in a
                    // short distance and settles; its contents then rise in one after another (see rise()).
                    slideInHorizontally(tween(480, delayMillis = 70, easing = EmphasizedDecelerate)) { (it * 0.14f * dir).toInt() } +
                        fadeIn(tween(320, delayMillis = 90, easing = LinearOutSlowInEasing))
                    ) togetherWith (
                    slideOutHorizontally(tween(260, easing = EmphasizedAccelerate)) { -(it * 0.08f * dir).toInt() } +
                        fadeOut(tween(160, easing = FastOutLinearInEasing))
                    )
            },
            label = "onboarding",
            modifier = Modifier.fillMaxSize(),
        ) { s ->
            Box(Modifier.fillMaxSize().padding(horizontal = Spacing.lg), contentAlignment = Alignment.Center) {
                when (s) {
                    Step.WELCOME -> Interstitial(
                        illustration = { OrbitIllustration(Icons.Outlined.Fingerprint, listOf(Icons.Outlined.Search, Icons.Outlined.Science, Icons.Outlined.CameraAlt)) },
                        text = bold(stringResource(R.string.ob_welcome, user.fullName.substringBefore(' ').trim())),
                    )
                    Step.MOOD -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.rise(0)) { Question(stringResource(R.string.ob_q_mood)) }
                        Spacer(Modifier.height(Spacing.lg))
                        Box(Modifier.rise(1)) { FaceScale(mood) { mood = it } }
                    }
                    Step.FAMILIARITY -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                        Spacer(Modifier.weight(0.6f))
                        Text(stringResource(R.string.ob_q_familiar), style = MaterialTheme.typography.titleMedium,
                            color = ink.ink, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 260.dp).rise(0))
                        Spacer(Modifier.weight(1f))
                        Column(Modifier.rise(1), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.ob_im), style = MaterialTheme.typography.headlineSmall, color = ink.ink)
                            // The phrase rolls up as the slider crosses into a new level.
                            AnimatedContent(familiarityWords(familiarity), transitionSpec = {
                                (slideInVertically(tween(220, easing = EmphasizedDecelerate)) { it / 2 } + fadeIn(tween(180))) togetherWith
                                    (slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(120)))
                            }, label = "words") { words ->
                                Text(stringResource(words), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ink.ink, textAlign = TextAlign.Center)
                            }
                        }
                        Spacer(Modifier.height(Spacing.lg))
                        PillSlider(familiarity, { familiarity = it }, Modifier.widthIn(max = 300.dp).rise(2), hint = true)
                        Row(Modifier.widthIn(max = 300.dp).fillMaxWidth().padding(top = 6.dp, start = 6.dp, end = 6.dp).rise(2)) {
                            Text(stringResource(R.string.ob_not_at_all), style = MaterialTheme.typography.labelSmall, color = ink.muted, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.ob_very), style = MaterialTheme.typography.labelSmall, color = ink.muted)
                        }
                        Spacer(Modifier.weight(1.4f))
                    }
                    Step.REFLECTION -> Interstitial(
                        illustration = { OrbitIllustration(Icons.Outlined.Search, emptyList()) },
                        text = reflection(mood, familiarity),
                    )
                    Step.FOCUS -> FocusPicker(focus) { focus = it }
                    Step.FOCUS_DONE -> Interstitial(
                        illustration = {
                            val picked = FOCUS_AREAS.filter { it.key in focus }.map { it.icon }.take(3)
                            OrbitIllustration(Icons.Outlined.ManageSearch, picked.ifEmpty { listOf(Icons.Outlined.Fingerprint, Icons.Outlined.GpsFixed, Icons.Outlined.Science) })
                        },
                        text = bold(stringResource(if (focus.isEmpty()) R.string.ob_focus_none else R.string.ob_focus_some)),
                    )
                    Step.CLASS_CODE -> ClassCodeStep(onJoined = { next() })
                    Step.AI_PRIVACY -> AiPrivacyStep(onAccept = finishWithPermission)
                }
            }
        }

        // Top: progress line + close.
        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            val progress by animateFloatAsState((step.ordinal + 1f) / Step.entries.size,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow), label = "progress")
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(ink.line)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(ink.ink))
            }
            Spacer(Modifier.size(Spacing.md))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(ink.card).clickable(onClickLabel = stringResource(R.string.ob_skip_setup)) { finish() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.ob_skip_setup), tint = ink.ink, modifier = Modifier.size(18.dp)) }
        }

        // Bottom: Skip + round next button (the last two steps have their own buttons).
        if (step != Step.AI_PRIVACY) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                val skippable = step in setOf(Step.MOOD, Step.FAMILIARITY, Step.FOCUS, Step.CLASS_CODE)
                if (skippable) Text(if (step == Step.CLASS_CODE) stringResource(R.string.ob_skip_for_now) else stringResource(R.string.ob_skip), style = MaterialTheme.typography.labelLarge, color = ink.muted,
                    modifier = Modifier.clip(CircleShape).clickable { next() }.padding(horizontal = Spacing.sm, vertical = Spacing.sm))
                if (step != Step.CLASS_CODE) {
                    val enabled = step != Step.MOOD || mood >= 0
                    val bg by animateColorAsState(if (enabled) ink.ink else ink.line, label = "nextBg")
                    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val pressed by press.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "nextScale")
                    Box(
                        Modifier.size(56.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(bg)
                            .clickable(interactionSource = press, indication = androidx.compose.foundation.LocalIndication.current,
                                enabled = enabled, onClickLabel = stringResource(R.string.ob_next)) { next() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = stringResource(R.string.ob_next), tint = ink.onInk) }
                }
            }
        }
    }
}

@Composable
private fun reflection(mood: Int, familiarity: Float): AnnotatedString = bold(stringResource(when {
    familiarity < 0.4f -> if (mood in 0..1) R.string.ob_reflect_low_nervous else R.string.ob_reflect_low
    familiarity < 0.7f -> R.string.ob_reflect_mid
    else -> R.string.ob_reflect_high
}))

/** Turns "*bold* rest" (from a string resource) into text with the starred part in bold. */
private fun bold(text: String): AnnotatedString = buildAnnotatedString {
    text.split('*').forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

@Composable
private fun Question(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, color = ink().ink,
        textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
}

@Composable
private fun Interstitial(illustration: @Composable () -> Unit, text: AnnotatedString) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 340.dp)) {
        Box(Modifier.rise(0)) { illustration() }
        Spacer(Modifier.height(Spacing.xl))
        Text(text, style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 34.sp), fontWeight = FontWeight.Normal,
            color = ink().ink, textAlign = TextAlign.Center, modifier = Modifier.rise(1))
    }
}

/** Five round faces from frowning to beaming; the chosen one fills in. */
@Composable
private fun FaceScale(selected: Int, onSelect: (Int) -> Unit) {
    val ink = ink()
    val moodNames = MOOD_LABELS.map { stringResource(it) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(5) { i ->
                val on = i == selected
                val bg by animateColorAsState(if (on) ink.ink else ink.card, label = "faceBg")
                val fg by animateColorAsState(if (on) ink.onInk else ink.ink, label = "faceFg")
                val scale by animateFloatAsState(if (on) 1.08f else 1f, label = "faceScale")
                Canvas(
                    Modifier.size((52 * scale).dp).clip(CircleShape).background(bg)
                        .clickable { onSelect(i) }
                        .semantics { role = Role.RadioButton; this.selected = on; contentDescription = moodNames[i] },
                ) {
                    val w = size.width; val h = size.height
                    val stroke = Stroke(width = w * 0.045f, cap = StrokeCap.Round)
                    // Eyes
                    drawCircle(fg, radius = w * 0.035f, center = Offset(w * 0.38f, h * 0.42f))
                    drawCircle(fg, radius = w * 0.035f, center = Offset(w * 0.62f, h * 0.42f))
                    // Mouth: curvature from frown (-) to smile (+)
                    val curve = (i - 2) * 0.07f
                    val y = h * (0.62f - curve * 0.3f)
                    val path = Path().apply {
                        moveTo(w * 0.38f, y)
                        quadraticTo(w * 0.5f, y + h * curve, w * 0.62f, y)
                    }
                    drawPath(path, fg, style = stroke)
                }
            }
        }
        Row(Modifier.padding(top = Spacing.sm).widthIn(min = 300.dp)) {
            Text(stringResource(R.string.ob_mood_nervous), style = MaterialTheme.typography.labelSmall, color = ink.muted, modifier = Modifier.weight(1f))
            Text(if (selected >= 0) moodNames[selected] else "", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = ink.ink)
            Text(stringResource(R.string.ob_mood_excited), style = MaterialTheme.typography.labelSmall, color = ink.muted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Black pill track that fills up to a white knob. Drag it (the knob follows your finger and grows
 * a little) or tap anywhere (it glides there). A light tick plays as it crosses into a new level.
 * With [hint], the knob nudges right and back once so students see it can be moved.
 */
@Composable
private fun PillSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, hint: Boolean = false) {
    val ink = ink()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val reducedMotion = LocalReducedMotion.current
    val levelText = stringResource(familiarityWords(value)).trimEnd('.')
    var dragging by remember { mutableStateOf(false) }
    var touched by rememberSaveable { mutableStateOf(false) }
    val shown = remember { Animatable(value) }
    LaunchedEffect(value, dragging) {
        if (dragging || reducedMotion) shown.snapTo(value)
        else shown.animateTo(value, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    if (hint && !reducedMotion) LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(900)
        if (!touched) {
            shown.animateTo((value + 0.12f).coerceAtMost(1f), tween(420, easing = EmphasizedDecelerate))
            shown.animateTo(value, spring(dampingRatio = 0.45f, stiffness = 260f))
        }
    }
    // Haptic tick when the level (and its phrase) changes.
    val level = (value / 0.2f).toInt().coerceAtMost(4)
    var lastLevel by remember { mutableIntStateOf(level) }
    LaunchedEffect(level) { if (level != lastLevel) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastLevel = level } }
    val knobScale by animateFloatAsState(if (dragging) 1.15f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "knob")

    BoxWithConstraints(
        modifier.fillMaxWidth().height(40.dp).clip(CircleShape).background(ink.card)
            .semantics {
                stateDescription = levelText
                setProgress { onChange(it.coerceIn(0f, 1f)); true }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val knob = 30.dp; val pad = 5.dp
        val knobPx = with(density) { knob.toPx() }; val padPx = with(density) { pad.toPx() }
        val travel = widthPx - knobPx - padPx * 2
        fun toValue(x: Float) = ((x - padPx - knobPx / 2) / travel).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize()
            .pointerInput(travel) { detectTapGestures { touched = true; onChange(toValue(it.x)) } }
            .pointerInput(travel) {
                detectHorizontalDragGestures(
                    onDragStart = { touched = true; dragging = true; onChange(toValue(it.x)) },
                    onDragEnd = { dragging = false }, onDragCancel = { dragging = false },
                ) { change, _ -> change.consume(); onChange(toValue(change.position.x)) }
            }) {
            val knobX = padPx + travel * shown.value
            Box(Modifier.size(width = with(density) { (knobX + knobPx + padPx).toDp() }, height = 40.dp)
                .clip(CircleShape).background(ink.ink))
            Box(Modifier.offset { IntOffset(knobX.roundToInt(), padPx.roundToInt()) }.size(knob)
                .graphicsLayer { scaleX = knobScale; scaleY = knobScale; shadowElevation = if (dragging) 6f else 2f; shape = CircleShape; clip = true }
                .background(ink.onInk))
        }
    }
}

/** Fades and lifts an element in shortly after its page appears; [index] staggers siblings. */
@Composable
private fun Modifier.rise(index: Int): Modifier {
    if (LocalReducedMotion.current) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(140L + index * 80L)
        p.animateTo(1f, tween(520, easing = EmphasizedDecelerate))
    }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 18.dp.toPx()
    }
}

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

@Composable
private fun FocusPicker(selected: Set<String>, onChange: (Set<String>) -> Unit) {
    val ink = ink()
    var showAll by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 72.dp, bottom = 104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.rise(0)) { Question(stringResource(R.string.ob_q_focus)) }
        Text(stringResource(R.string.ob_pick_many), style = MaterialTheme.typography.bodySmall, color = ink.muted, modifier = Modifier.padding(top = 6.dp).rise(0))
        Spacer(Modifier.height(Spacing.lg))
        val visible = FOCUS_AREAS.take(6)
        val extra = FOCUS_AREAS.drop(6)
        Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            visible.chunked(3).forEachIndexed { i, row -> Box(Modifier.rise(1 + i)) { FocusRow(row, selected, onChange) } }
            AnimatedVisibility(showAll, enter = expandVertically() + fadeIn(), exit = fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { extra.chunked(3).forEach { row -> FocusRow(row, selected, onChange) } }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Column(
            Modifier.clip(CircleShape).clickable { showAll = !showAll }.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (showAll) stringResource(R.string.ob_show_less) else stringResource(R.string.ob_show_all), style = MaterialTheme.typography.labelLarge, color = ink.ink)
            Icon(if (showAll) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = ink.muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FocusRow(row: List<FocusArea>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    val ink = ink()
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        row.forEach { area ->
            val on = area.key in selected
            val bg by animateColorAsState(if (on) ink.ink else ink.card, label = "tileBg")
            val fg by animateColorAsState(if (on) ink.onInk else ink.ink, label = "tileFg")
            Column(
                Modifier.weight(1f).aspectRatio(1f).clip(MaterialTheme.shapes.large).background(bg)
                    .clickable { onChange(if (on) selected - area.key else selected + area.key) }
                    .semantics { role = Role.Checkbox; this.selected = on },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(area.icon, contentDescription = null, tint = fg, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(area.label), style = MaterialTheme.typography.labelMedium, color = if (on) fg else ink.muted, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * A big icon in a white circle with tilted orbit rings, a few small icons travelling around it,
 * and a scatter of dots, in the spirit of a hand-drawn case-file sketch.
 */
@Composable
private fun OrbitIllustration(center: ImageVector, satellites: List<ImageVector>) {
    val ink = ink()
    val animate = !LocalReducedMotion.current
    val spin by rememberInfiniteTransition(label = "orbit").animateFloat(0f, 360f,
        infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val angle = if (animate) spin else 0f
    Box(Modifier.size(width = 260.dp, height = 200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val s = Stroke(width = 1.5.dp.toPx())
            rotate(-12f, c) {
                drawOval(ink.ink, topLeft = Offset(c.x - size.width * 0.48f, c.y - size.height * 0.16f), size = Size(size.width * 0.96f, size.height * 0.32f), style = s)
                drawOval(ink.ink, topLeft = Offset(c.x - size.width * 0.44f, c.y - size.height * 0.12f), size = Size(size.width * 0.88f, size.height * 0.24f), style = s)
            }
            // Evidence dots and a little spark, like the reference sketches.
            drawCircle(ink.ink, 5.dp.toPx(), Offset(c.x - size.width * 0.36f, c.y + size.height * 0.08f))
            drawCircle(ink.ink, 3.dp.toPx(), Offset(c.x - size.width * 0.29f, c.y + size.height * 0.13f))
            val sp = Offset(c.x + size.width * 0.27f, c.y - size.height * 0.38f)
            repeat(8) { k ->
                val a = Math.toRadians(k * 45.0)
                val r1 = 5.dp.toPx(); val r2 = 11.dp.toPx()
                drawLine(ink.ink, Offset(sp.x + (r1 * Math.cos(a)).toFloat(), sp.y + (r1 * Math.sin(a)).toFloat()),
                    Offset(sp.x + (r2 * Math.cos(a)).toFloat(), sp.y + (r2 * Math.sin(a)).toFloat()), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Box(Modifier.size(96.dp).clip(CircleShape).background(ink.card).border(1.5.dp, ink.ink, CircleShape), contentAlignment = Alignment.Center) {
            Icon(center, contentDescription = null, tint = ink.ink, modifier = Modifier.size(48.dp))
        }
        satellites.forEachIndexed { i, icon ->
            val a = Math.toRadians((angle + i * 360.0 / satellites.size))
            val x = (110 * Math.cos(a)).toFloat(); val y = (34 * Math.sin(a)).toFloat() - (x * 0.21f)
            Box(
                Modifier.offset(x.dp, y.dp).size(44.dp).clip(CircleShape).background(ink.card).border(1.dp, ink.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = ink.ink, modifier = Modifier.size(22.dp)) }
        }
    }
}

@Composable
private fun ClassCodeStep(onJoined: () -> Unit) {
    val ink = ink()
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var joined by rememberSaveable { mutableStateOf(false) }
    val joinFailed = stringResource(R.string.ob_join_failed)
    fun join() {
        if (code.isBlank() || busy) return
        focus.clearFocus(); busy = true; error = null
        scope.launch {
            try { c.learning.joinClass(code.trim()); joined = true }
            catch (e: ApiException) { error = e.message }
            catch (e: Exception) { error = joinFailed }
            finally { busy = false }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 340.dp)) {
        OrbitIllustration(Icons.Outlined.Groups, emptyList())
        Spacer(Modifier.height(Spacing.lg))
        if (!joined) {
            Question(stringResource(R.string.ob_q_class))
            Text(stringResource(R.string.ob_class_body), style = MaterialTheme.typography.bodySmall,
                color = ink.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(Spacing.lg))
            OutlinedTextField(
                value = code, onValueChange = { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(16); error = null },
                placeholder = { Text(stringResource(R.string.ob_class_code)) }, singleLine = true, isError = error != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { join() }),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(Spacing.md))
            PillButton(if (busy) stringResource(R.string.ob_joining) else stringResource(R.string.ob_join_class), enabled = code.isNotBlank() && !busy) { join() }
        } else {
            Text(bold(stringResource(R.string.ob_request_sent)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Normal, color = ink.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.lg))
            PillButton(stringResource(R.string.ob_continue)) { onJoined() }
        }
    }
}

@Composable
private fun PillButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val ink = ink()
    Box(
        Modifier.heightIn(min = 48.dp).widthIn(min = 200.dp).clip(CircleShape).background(if (enabled) ink.ink else ink.line)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = Spacing.lg, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = ink.onInk) }
}

/** Final sheet: what the AI tutor does with what you type (matches the Privacy Policy). */
@Composable
private fun AiPrivacyStep(onAccept: () -> Unit) {
    val ink = ink()
    Box(Modifier.fillMaxSize().padding(top = 72.dp)) {
        Column(
            Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraLarge).background(ink.card)
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(ink.page), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = ink.ink, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(R.string.ob_ai_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ink.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.md))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
                PrivacyPoint(Icons.Outlined.AutoAwesome, stringResource(R.string.ob_ai_1))
                PrivacyPoint(Icons.Outlined.Description, stringResource(R.string.ob_ai_2))
                PrivacyPoint(Icons.Outlined.Shield, stringResource(R.string.ob_ai_3))
                PrivacyPoint(Icons.Outlined.Search, stringResource(R.string.ob_ai_4))
            }
            Spacer(Modifier.height(Spacing.md))
            PillButton(stringResource(R.string.ob_ai_accept), onClick = onAccept)
            Text(stringResource(R.string.ob_ai_footer),
                style = MaterialTheme.typography.labelSmall, color = ink.muted, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun PrivacyPoint(icon: ImageVector, text: String) {
    val ink = ink()
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = ink.ink, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = ink.ink)
    }
}
