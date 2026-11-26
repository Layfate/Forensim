package com.forensim.app.feature.professor

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.LoadContent
import com.forensim.app.core.designsystem.OutlinedButton
import com.forensim.app.core.designsystem.OutlinedTextField
import com.forensim.app.core.designsystem.PageIntro
import com.forensim.app.core.designsystem.PrimaryButton
import com.forensim.app.core.designsystem.ReadableColumn
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.StatusChip
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.designsystem.pct
import com.forensim.app.core.model.CaseSession
import com.forensim.app.core.model.SimulationAttempt
import com.forensim.app.core.network.ApiException
import com.forensim.app.engine.scene.CaseBriefings
import com.forensim.app.engine.scene.CaseFactory
import com.forensim.app.engine.scene.CaseLocations
import com.forensim.app.engine.scene.CaseType
import com.forensim.app.engine.scene.EvidenceType
import com.forensim.app.engine.scene.SceneGenerationException
import com.forensim.app.engine.scene.Victim
import com.forensim.app.engine.scene.VictimPose
import com.forensim.app.engine.scene.VictimSex
import com.forensim.app.feature.simulation.CaseCode
import com.forensim.app.feature.simulation.qrImage
import com.forensim.app.ui.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ----------------------------------------------------------------------------- generate a case

/**
 * Professor: generate a case for one class. The scene (seed + evidence) is built and validated on
 * the phone, so it always generates identically and solvably for every student; the server stores
 * it and issues the code. The case type is kept from students: they must work it out.
 */
@Composable
fun CaseBuilderScreen(vm: ProfessorViewModel, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    ScreenScaffold(title = "Generate a case", onBack = onBack) { inner ->
        LoadContent(vm.state, vm::load, Modifier.padding(inner)) { d, _ ->
            var classId by rememberSaveable { mutableStateOf(d.sortedClasses.firstOrNull()?.id) }
            var typeKey by rememberSaveable { mutableStateOf(CaseType.BURGLARY.key) }
            var locationId by rememberSaveable { mutableStateOf(CaseLocations.RESIDENCE.id) }
            var count by rememberSaveable { mutableIntStateOf(4) }
            var redHerrings by rememberSaveable { mutableIntStateOf(0) }
            // Victim's body in the scene: none, male or female; face up or face down.
            var victimSex by rememberSaveable { mutableStateOf<String?>(null) }
            var victimPose by rememberSaveable { mutableStateOf(VictimPose.FACE_DOWN.key) }
            val victim = Victim.from(victimSex, victimPose)
            var graded by rememberSaveable { mutableStateOf(false) }
            var title by rememberSaveable { mutableStateOf("") }
            var briefing by rememberSaveable { mutableStateOf(CaseBriefings.random(CaseType.BURGLARY, CaseLocations.RESIDENCE, null)) }
            var briefingEdited by rememberSaveable { mutableStateOf(false) }
            // The choices the current briefing was written for, so it is rewritten only when they
            // change (not when the screen rotates).
            var briefingFor by rememberSaveable { mutableStateOf("${CaseType.BURGLARY.key}|${CaseLocations.RESIDENCE.id}|null") }
            var busy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            val type = CaseType.from(typeKey) ?: CaseType.BURGLARY
            val location = CaseLocations.byId(locationId) ?: CaseLocations.RESIDENCE
            val briefingKey = "$typeKey|$locationId|${victim?.label}"
            LaunchedEffect(briefingKey) {
                if (!briefingEdited && briefingFor != briefingKey) {
                    briefing = CaseBriefings.random(type, location, victim); briefingFor = briefingKey
                }
            }
            val ready = classId != null && title.trim().length >= 3 && briefing.trim().length >= 10 && !busy

            Column(Modifier.fillMaxSize().padding(inner).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md)) {
                ReadableColumn {
                    PageIntro("New case", Modifier.padding(top = Spacing.md),
                        description = "Students open it with a code or QR, process the scene, then decide what happened.")

                    ClassPicker(d, classId) { classId = it }

                    // What students see first: the title and briefing, then how the scene is built.
                    SectionHeader("What students see")
                    OutlinedTextField(title, { title = it.take(80) }, label = { Text("Case title") },
                        placeholder = { Text("e.g. Case 1: Maple Street") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Don't name the crime in the title. Working it out is part of the task.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(briefing, { briefing = it.take(600); briefingEdited = true }, label = { Text("Briefing / summary") },
                        minLines = 3, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (briefingEdited) "Your own text. New summary replaces it with one written for this case."
                            else "Written for the case type, location and victim below. Edit it or get another.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(Spacing.xs))
                        OutlinedButton(onClick = {
                            briefing = CaseBriefings.random(type, location, victim, avoid = briefing)
                            briefingEdited = false; briefingFor = briefingKey
                        }, modifier = Modifier.heightIn(min = Spacing.touchTarget)) {
                            Icon(Icons.Outlined.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.xs)); Text("New summary")
                        }
                    }

                    SectionHeader("Case type")
                    ChoiceDropdown("Case type", CaseType.entries, type, label = { it.label }) { typeKey = it.key }
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Evidence pool: " + type.pool.joinToString(", ") { p ->
                        (EvidenceType.from(p.type)?.displayName ?: p.type).lowercase() + if (p.required) " (always)" else ""
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    SectionHeader("Victim")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        FilterChip(selected = victimSex == null, onClick = { victimSex = null }, label = { Text("No victim") })
                        VictimSex.entries.forEach { v ->
                            FilterChip(selected = victimSex == v.key, onClick = { victimSex = v.key }, label = { Text(v.label) })
                        }
                    }
                    if (victim != null) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text("Position of the body", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            VictimPose.entries.forEach { v ->
                                FilterChip(selected = victimPose == v.key, onClick = { victimPose = v.key }, label = { Text(v.label) })
                            }
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        VictimPreview(victim, Modifier.fillMaxWidth().height(200.dp))
                        Text("The body lies on the floor of the scene. Students walk round it and process the evidence around it.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (briefing.contains("hospital", ignoreCase = true)) {
                            Text("The briefing says the person was taken to hospital. Edit it so it matches a body at the scene.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Text("Add a body for cases like homicide or assault.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    SectionHeader("Location")
                    ChoiceDropdown("Location", CaseLocations.all, location, label = { it.label }) { locationId = it.id }

                    SectionHeader("Evidence items")
                    ChoiceDropdown("Evidence items", (CaseFactory.MIN_ITEMS..CaseFactory.MAX_ITEMS).toList(), count,
                        label = { "$it items" }) { count = it }
                    if (count == CaseFactory.MIN_ITEMS) {
                        Text("Only the two key items for the case type. Add more for a fuller scene.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (count > 5) {
                        Text("Larger cases may include a second print or impression, like real scenes.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    SectionHeader("Red herrings")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        (0..CaseFactory.MAX_RED_HERRINGS).forEach { n ->
                            FilterChip(selected = redHerrings == n, onClick = { redHerrings = n }, label = { Text(if (n == 0) "None" else "$n") })
                        }
                    }
                    Text(
                        "Everyday objects (a mug, keys, a receipt...) placed among the evidence. Students can process them " +
                            "without penalty, but citing one as proof in their report costs points. They look like any other item.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    SectionHeader("Mode")
                    ForensimCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (graded) "Graded" else "Practice", style = MaterialTheme.typography.titleMedium)
                                Text(if (graded) "One submission per student, no hints. Feedback stays hidden until you release it."
                                    else "Hints while they work; students can retry and see feedback straight away.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = graded, onCheckedChange = { graded = it }, modifier = Modifier.semantics { contentDescription = "Graded case" })
                        }
                    }

                    error?.let { Spacer(Modifier.height(Spacing.sm)); Banner(it, Tone.ERROR) }
                    Spacer(Modifier.height(Spacing.lg))
                    PrimaryButton(
                        text = if (busy) "Generating scene" else "Generate case",
                        loading = busy, enabled = ready, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        onClick = {
                            val k = classId ?: return@PrimaryButton
                            busy = true; error = null
                            scope.launch {
                                try {
                                    val draft = withContext(Dispatchers.Default) { CaseFactory.draft(type, location, count, victim = victim, redHerrings = redHerrings) }
                                    val session = c.staff.createCase(k, title.trim(), type.key, location.id, location.version, briefing.trim(),
                                        draft.seed, draft.manifest, graded, victim)
                                    onCreated(session.id)
                                } catch (e: ApiException) {
                                    error = e.message
                                } catch (e: SceneGenerationException) {
                                    error = "${e.message}. Try another location or fewer items."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(Spacing.xl))
                }
            }
        }
    }
}

/** Read-only dropdown for picking one of [options] (same look as [ClassPicker]). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceDropdown(fieldLabel: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.material3.ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = label(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(fieldLabel) },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label(option), style = MaterialTheme.typography.bodyLarge) },
                    onClick = { onSelect(option); open = false },
                    trailingIcon = if (option == selected) ({ Icon(androidx.compose.material.icons.Icons.Outlined.Check, contentDescription = "Selected") }) else null,
                    contentPadding = androidx.compose.material3.ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------- case detail

/** Professor: the case's code + QR to share, open/close, and each student's result. */
@Composable
fun CaseDetailScreen(vm: ProfessorViewModel, caseId: String, onBack: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<CaseSession?>(null) }
    var attempts by remember { mutableStateOf<List<SimulationAttempt>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    fun reload() {
        scope.launch {
            try {
                session = c.staff.caseSessions().firstOrNull { it.id == caseId }
                attempts = c.staff.simulationAttempts().filter { it.caseSessionId == caseId }
                error = if (session == null) "This case no longer exists." else null
            } catch (e: ApiException) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(caseId) { reload() }

    ScreenScaffold(title = session?.title ?: "Case", onBack = onBack) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md)) {
            ReadableColumn {
                error?.let { Spacer(Modifier.height(Spacing.sm)); Banner(it, Tone.ERROR) }
                val k = session
                if (k == null) {
                    if (loading) com.forensim.app.core.designsystem.LoadingState()
                    return@ReadableColumn
                }
                Spacer(Modifier.height(Spacing.md))
                // Code + QR, large enough to scan from the back of a classroom when projected.
                ForensimCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        val qr = remember(k.code) { qrImage(CaseCode.qrContent(k.code)) }
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                            Image(qr, contentDescription = "QR code for case ${k.code}", modifier = Modifier.size(240.dp))
                        }
                        Spacer(Modifier.height(Spacing.md))
                        Text("Case code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(k.code, style = MaterialTheme.typography.displayLarge.copy(letterSpacing = TextUnit(0.2f, TextUnitType.Em)),
                            textAlign = TextAlign.Center, modifier = Modifier.semantics { contentDescription = "Case code ${k.code.toCharArray().joinToString(" ")}" })
                        Text("Students: Simulate → Join a case → type the code or scan the QR.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(Spacing.md))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedButton(onClick = {
                                val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                                clip?.setPrimaryClip(ClipData.newPlainText("Case code", k.code))
                                android.widget.Toast.makeText(context, "Case code ${k.code} copied", android.widget.Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.heightIn(min = Spacing.touchTarget)) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null); Spacer(Modifier.width(Spacing.xs)); Text("Copy")
                            }
                            PrimaryButton("Share", onClick = {
                                val text = "ForenSim case \"${k.title}\": open Simulate → Join a case and enter ${k.code}."
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share case code"))
                            })
                        }
                    }
                }

                SectionHeader("Case")
                ForensimCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (k.active) "Open" else "Closed", style = MaterialTheme.typography.titleMedium)
                            Text(if (k.active) "Students with the code can open it." else "The code no longer opens this case.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = k.active, onCheckedChange = { on ->
                            scope.launch {
                                try { session = c.staff.setCaseActive(k.id, on) } catch (e: ApiException) { error = e.message }
                            }
                        }, modifier = Modifier.semantics { contentDescription = "Case open to students" })
                    }
                    HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
                    val classLabel = vm.state.let { st -> (st as? com.forensim.app.core.designsystem.Load.Ready)?.data?.let { d ->
                        d.classById(k.classId)?.let { cl -> d.moduleOf(cl)?.title ?: cl.name } } }
                    InfoRow("Answer (hidden from students)", CaseType.from(k.caseType)?.label ?: k.caseType)
                    InfoRow("Location", CaseLocations.byId(k.location)?.label ?: k.location)
                    InfoRow("Victim", Victim.from(k.victimSex, k.victimPose)?.label ?: "None")
                    classLabel?.let { InfoRow("Class", it) }
                    InfoRow("Mode", if (k.graded) "Graded" else "Practice")
                    InfoRow("Evidence", k.manifest.filter { EvidenceType.from(it.type)?.redHerring != true }
                        .joinToString(", ") { EvidenceType.from(it.type)?.displayName ?: it.type })
                    val herrings = k.manifest.filter { EvidenceType.from(it.type)?.redHerring == true }
                    if (herrings.isNotEmpty()) {
                        InfoRow("Red herrings", herrings.joinToString(", ") { EvidenceType.from(it.type)?.displayName ?: it.type })
                    }
                }

                SectionHeader("Results (${attempts.count { it.status == "scored" }} submitted)")
                val names = (vm.state as? com.forensim.app.core.designsystem.Load.Ready)?.data
                if (attempts.isEmpty()) {
                    Text("No student has opened this case yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                attempts.forEach { a ->
                    ForensimCard(Modifier.padding(bottom = Spacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.studentId?.let { names?.name(it) } ?: "Student", style = MaterialTheme.typography.titleSmall)
                                val conclusion = a.feedback?.conclusion
                                Text(
                                    when {
                                        a.status != "scored" -> "In progress"
                                        conclusion == null -> "Submitted"
                                        else -> "Said ${CaseType.from(conclusion.chosenType)?.label?.lowercase() ?: "nothing"} · " +
                                            if (conclusion.typeCorrect) "correct" else "incorrect"
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (a.status == "scored") StatusChip(a.score?.pct(a.maxScore) ?: "-", if (a.feedback?.passed == true) Tone.SUCCESS else Tone.ATTENTION)
                        }
                    }
                }
                if (k.graded && attempts.any { it.status == "scored" && !it.feedbackReleased }) {
                    Text("Release detailed feedback for graded attempts from the class page.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 160.dp).weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
    }
}
