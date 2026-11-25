package com.forensim.app.feature.student

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forensim.app.R
import com.forensim.app.core.designsystem.ForensimTheme
import com.forensim.app.core.designsystem.OutlinedTextField
import com.forensim.app.core.designsystem.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * One "what is this?" exhibit: a bundled lesson image, its text (string resources, so it follows the
 * app language) and the answers that count as correct in either language.
 */
data class Exhibit(
    val image: String,
    @androidx.annotation.StringRes val question: Int,
    @androidx.annotation.StringRes val answer: Int,
    @androidx.annotation.StringRes val hint: Int,
    @androidx.annotation.StringRes val fact: Int,
    val accepted: List<String>,
)

/** Built from the pictures in assets/lesson-media. Answers are matched loosely (case, spacing, small typos). */
private val EXHIBITS = listOf(
    Exhibit("bal_fired_cases.webp", R.string.ex_cases_q, R.string.ex_cases_a, R.string.ex_cases_hint, R.string.ex_cases_fact,
        listOf("cartridge case", "cartridge casing", "casing", "shell casing", "bullet casing", "spent casing", "shell", "case", "cartridge", "empty shell", "basyo", "basyo ng bala")),
    Exhibit("bal_rifling_bore.webp", R.string.ex_rifling_q, R.string.ex_rifling_a, R.string.ex_rifling_hint, R.string.ex_rifling_fact,
        listOf("rifling", "rifle grooves", "lands and grooves", "grooves", "barrel rifling", "rifled barrel", "uka", "mga uka")),
    Exhibit("bal_comparison.webp", R.string.ex_comparison_q, R.string.ex_comparison_a, R.string.ex_comparison_hint, R.string.ex_comparison_fact,
        listOf("comparison microscope", "comparison scope", "forensic comparison microscope", "comparison mikroskopyo")),
    Exhibit("bal_bullets.webp", R.string.ex_bullets_q, R.string.ex_bullets_a, R.string.ex_bullets_hint, R.string.ex_bullets_fact,
        listOf("bullet", "bullets", "projectile", "projectiles", "ammunition", "ammo", "slugs", "slug", "bala", "mga bala", "punglo")),
    Exhibit("chem_evidence_bag.webp", R.string.ex_bag_q, R.string.ex_bag_a, R.string.ex_bag_hint, R.string.ex_bag_fact,
        listOf("evidence bag", "paper bag", "paper evidence bag", "bag", "evidence envelope", "brown bag", "envelope", "supot", "papel na supot", "supot na papel", "sobre")),
    Exhibit("chem_marquis.webp", R.string.ex_marquis_q, R.string.ex_marquis_a, R.string.ex_marquis_hint, R.string.ex_marquis_fact,
        listOf("marquis", "marquis test", "marquis reagent", "presumptive test", "color test", "colour test", "spot test", "reagent test", "drug test")),
    Exhibit("chem_tlc.webp", R.string.ex_tlc_q, R.string.ex_tlc_a, R.string.ex_tlc_hint, R.string.ex_tlc_fact,
        listOf("thin layer chromatography", "tlc", "chromatography", "tlc plate", "thin layer", "kromatograpiya")),
    Exhibit("photo_camera_obscura.webp", R.string.ex_obscura_q, R.string.ex_obscura_a, R.string.ex_obscura_hint, R.string.ex_obscura_fact,
        listOf("camera obscura", "pinhole camera", "pinhole", "obscura")),
    Exhibit("photo_sd_cards.webp", R.string.ex_cards_q, R.string.ex_cards_a, R.string.ex_cards_hint, R.string.ex_cards_fact,
        listOf("memory card", "memory cards", "sd card", "sd cards", "micro sd", "microsd", "mini sd", "flash card", "storage card")),
    Exhibit("photo_slr.webp", R.string.ex_slr_q, R.string.ex_slr_a, R.string.ex_slr_hint, R.string.ex_slr_fact,
        listOf("slr", "slr camera", "dslr", "single lens reflex", "single lens reflex camera", "reflex camera")),
    Exhibit("pid_bertillon_card.webp", R.string.ex_bertillon_q, R.string.ex_bertillon_a, R.string.ex_bertillon_hint, R.string.ex_bertillon_fact,
        listOf("bertillon card", "bertillon", "bertillonage", "anthropometry", "anthropometric card", "mugshot", "mug shot", "booking card", "arrest record", "criminal record")),
    Exhibit("poly_lafayette.webp", R.string.ex_polygraph_q, R.string.ex_polygraph_a, R.string.ex_polygraph_hint, R.string.ex_polygraph_fact,
        listOf("polygraph", "polygraph machine", "lie detector", "lie detector machine")),
    Exhibit("poly_keeler.webp", R.string.ex_polytest_q, R.string.ex_polytest_a, R.string.ex_polytest_hint, R.string.ex_polytest_fact,
        listOf("polygraph", "polygraph test", "polygraph exam", "polygraph examination", "lie detector", "lie detector test")),
    Exhibit("qde_signatures.webp", R.string.ex_signatures_q, R.string.ex_signatures_a, R.string.ex_signatures_hint, R.string.ex_signatures_fact,
        listOf("signature", "signatures", "handwriting", "forged signatures", "forgery", "forgeries", "autograph", "autographs", "lagda", "mga lagda", "pirma", "mga pirma", "sulat kamay")),
    Exhibit("qde_stereo.webp", R.string.ex_stereo_q, R.string.ex_stereo_a, R.string.ex_stereo_hint, R.string.ex_stereo_fact,
        listOf("stereo microscope", "stereomicroscope", "dissecting microscope", "microscope", "stereo zoom microscope", "mikroskopyo")),
    Exhibit("qde_typewriter.webp", R.string.ex_typewriter_q, R.string.ex_typewriter_a, R.string.ex_typewriter_hint, R.string.ex_typewriter_fact,
        listOf("typewriter", "type writer", "typing machine", "makinilya")),
    Exhibit("chem_paracelsus.webp", R.string.ex_paracelsus_q, R.string.ex_paracelsus_a, R.string.ex_paracelsus_hint, R.string.ex_paracelsus_fact,
        listOf("paracelsus", "theophrastus", "theophrastus von hohenheim")),
)

/** Today's exhibit; changes once a day and cycles through every exhibit. */
fun exhibitOfTheDay(epochDay: Long): Exhibit = EXHIBITS[Math.floorMod(epochDay * 7 + 3, EXHIBITS.size.toLong()).toInt()]

private fun normalize(s: String) = s.lowercase()
    .replace(Regex("[^a-z0-9 ]"), " ")
    .split(' ').filter { it.isNotBlank() && it !in setOf("a", "an", "the", "it", "is", "its", "this", "that") }
    .joinToString(" ") { it.removeSuffix("s") }

private fun editDistance(a: String, b: String): Int {
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = dp[0]; dp[0] = i
        for (j in 1..b.length) {
            val tmp = dp[j]
            dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = tmp
        }
    }
    return dp[b.length]
}

/** Correct when it matches any accepted answer, ignoring case, punctuation, plurals and a small typo or two. */
fun Exhibit.isCorrect(typed: String): Boolean {
    val t = normalize(typed)
    if (t.isEmpty()) return false
    return accepted.any { a ->
        val n = normalize(a)
        val allowed = when { n.length <= 4 -> 0; n.length <= 8 -> 1; else -> 2 }
        editDistance(t, n) <= allowed
    }
}

private const val PREFS = "daily_quiz"

/** Slide three of the home carousel: an evidence photo, typed answer, one try per day. */
@Composable
internal fun EvidenceOfTheDayCard(today: java.time.LocalDate, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val fx = ForensimTheme.colors
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val day = today.toEpochDay()
    val exhibit = remember(day) { exhibitOfTheDay(day) }
    // Today's answer lives on the student's account (DailyRepository), not on the phone.
    val daily = com.forensim.app.ui.LocalAppContainer.current.daily
    val entries by daily.entries.collectAsStateWithLifecycle()
    val submitted = entries[today.toString()]?.evidenceAnswer
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var typed by rememberSaveable(day) { mutableStateOf("") }
    val bitmap by produceState<ImageBitmap?>(null, exhibit.image) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("lesson-media/${exhibit.image}").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
        }
    }
    val submit = {
        if (typed.isNotBlank() && submitted == null) {
            val answer = typed.trim()
            scope.launch { daily.answerEvidence(today, answer, exhibit.isCorrect(answer)) }
            focus.clearFocus()
        }
    }

    val shape = MaterialTheme.shapes.large
    Column(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(cs.secondaryContainer, cs.surfaceContainer)))
            .border(1.dp, cs.outlineVariant, shape)
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The photo, framed like an exhibit with an evidence tag in the corner.
        Box(
            Modifier.fillMaxWidth().height(190.dp).clip(MaterialTheme.shapes.medium).background(Color.White)
                .border(1.dp, cs.outlineVariant, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let { Image(it, contentDescription = stringResource(R.string.evidence_photo), contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(190.dp).padding(Spacing.sm)) }
            Text(
                stringResource(R.string.evidence_exhibit, Math.floorMod(day, 1000L).toString().padStart(3, '0')),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A),
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm)
                    .clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFD54F))
                    .padding(horizontal = Spacing.sm, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Text(stringResource(exhibit.question), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Text(stringResource(exhibit.hint), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(Spacing.md))

        val answer = submitted
        if (answer == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it.take(60) }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.evidence_type_answer)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                com.forensim.app.core.designsystem.Button(onClick = { submit() }, enabled = typed.isNotBlank(), modifier = Modifier.height(56.dp)) {
                    Text(stringResource(R.string.evidence_submit))
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(stringResource(R.string.quiz_of_day_one_try), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        } else {
            val correct = exhibit.isCorrect(answer)
            val (bg, accent) = if (correct) fx.successContainer to fx.success else cs.errorContainer to cs.error
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(bg)
                    .border(1.dp, accent, MaterialTheme.shapes.medium).padding(Spacing.sm + Spacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(if (correct) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (correct) stringResource(R.string.evidence_correct, stringResource(exhibit.answer)) else stringResource(R.string.evidence_wrong, stringResource(exhibit.answer)),
                            style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                        )
                        Text(stringResource(R.string.evidence_you_typed, answer), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(exhibit.fact), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(stringResource(R.string.evidence_come_back), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
    }
}
