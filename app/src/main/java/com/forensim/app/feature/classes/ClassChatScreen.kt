package com.forensim.app.feature.classes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import com.forensim.app.core.designsystem.UserAvatar
import androidx.compose.material3.AlertDialog
import com.forensim.app.core.designsystem.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forensim.app.AppContainer
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.EmptyState
import com.forensim.app.core.designsystem.LoadingState
import com.forensim.app.core.designsystem.OutlinedTextField
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.StatusChip
import com.forensim.app.core.designsystem.TextButton
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.model.ClassMessage
import com.forensim.app.core.network.ApiException
import com.forensim.app.ui.appViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ChatStatus { LOADING, READY, UNAVAILABLE, ERROR }

/**
 * One class's chat. Messages arrive live over Supabase Realtime; a slow poll covers dropped
 * sockets. Every read goes through REST, so the server's row-level security decides who sees what.
 */
class ClassChatViewModel(private val c: AppContainer, private val classId: String) : ViewModel() {
    // Start from the last-known messages so the chat appears at once; the server refresh follows.
    private val peeked = c.chat.peek(classId)
    var messages by mutableStateOf(peeked.orEmpty()); private set
    var status by mutableStateOf(if (peeked != null) ChatStatus.READY else ChatStatus.LOADING); private set
    /** Messages being sent: shown straight away, greyed until the server confirms them. */
    var pending by mutableStateOf<List<ClassMessage>>(emptyList()); private set
    private var pendingSeq = -1L
    var error by mutableStateOf<String?>(null)
    var sending by mutableStateOf(false); private set
    val myId: String? get() = c.chat.myId
    private var live: Job? = null
    /** Profile photo bytes by user id (absent = initials). Loaded once per person. */
    var photos by mutableStateOf<Map<String, ByteArray>>(emptyMap()); private set
    private var membersLoaded = false

    /** Photos of everyone in the class chat. Best effort: without them the chat shows initials. */
    private fun loadPhotos() {
        if (membersLoaded) return
        membersLoaded = true
        viewModelScope.launch {
            val members = runCatching { c.chat.members(classId) }.getOrDefault(emptyList())
            // Photos already in memory show at once; the rest load in parallel.
            photos = photos + members.mapNotNull { m -> m.avatarPath?.let(c.chat::peekPhoto)?.let { m.userId to it } }
            members.filter { it.avatarPath != null && it.userId !in photos }.forEach { m ->
                launch {
                    runCatching { c.chat.photo(m.avatarPath!!) }.getOrNull()?.let { photos = photos + (m.userId to it) }
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            // Not in memory: try the on-device copy before the network answers.
            if (peeked == null) {
                val saved = c.chat.cached(classId)
                if (saved.isNotEmpty() && status == ChatStatus.LOADING) { messages = saved; status = ChatStatus.READY }
            }
            if (status == ChatStatus.READY) loadPhotos()
        }
        refresh(first = true)
    }

    fun refresh(first: Boolean = false) {
        viewModelScope.launch {
            try {
                messages = c.chat.messages(classId)
                status = ChatStatus.READY
                loadPhotos()
                if (first) error = null
            } catch (e: ApiException) {
                when {
                    e.isNetwork -> { if (status == ChatStatus.LOADING) status = ChatStatus.ERROR; error = "You're offline. Messages will load when you reconnect." }
                    // Table or policies missing: the server hasn't been updated for chat yet.
                    e.status == 404 || e.code == "PGRST205" || e.code == "42P01" -> status = ChatStatus.UNAVAILABLE
                    else -> { if (status == ChatStatus.LOADING) status = ChatStatus.ERROR; error = e.message }
                }
            }
        }
    }

    /** Called while the screen is visible: live updates plus a slow safety poll. */
    fun startLive() {
        if (live?.isActive == true) return
        live = viewModelScope.launch {
            launch {
                while (isActive) {
                    runCatching { c.chat.changes(classId).collect { refresh() } }
                    delay(5_000) // socket dropped: reconnect shortly
                }
            }
            while (isActive) {
                delay(15_000)
                if (status == ChatStatus.READY) refresh()
            }
        }
    }

    fun stopLive() { live?.cancel(); live = null }

    /** Shows the message immediately, then swaps in the server's copy (or restores the draft on failure). */
    fun send(text: String, onSent: () -> Unit, onFailed: (String) -> Unit = {}) {
        val body = text.trim()
        if (body.isEmpty()) return
        error = null
        val temp = ClassMessage(id = pendingSeq--, classId = classId, senderId = myId.orEmpty(), body = body,
            createdAt = OffsetDateTime.now().toString())
        pending = pending + temp
        onSent()
        viewModelScope.launch {
            try {
                val saved = c.chat.send(classId, body)
                if (messages.none { it.id == saved.id }) messages = (messages + saved).sortedBy { it.id }
                c.chat.remember(classId, messages)
            } catch (e: ApiException) {
                error = if (e.isNetwork) "Not sent: you're offline." else e.message
                onFailed(body)
            } finally {
                pending = pending - temp
            }
        }
    }

    fun delete(m: ClassMessage) {
        val before = messages
        messages = messages - m
        c.chat.remember(classId, messages)
        viewModelScope.launch {
            try { c.chat.delete(m.id) } catch (e: ApiException) { messages = before; error = e.message }
        }
    }

    // Reporting and blocking (Google Play User Generated Content policy) -------------------------

    /** People this user blocked: their messages are hidden on this device. */
    var blocked by mutableStateOf<Set<String>>(emptySet()); private set
    /** Short confirmation after a report or a block. */
    var notice by mutableStateOf<String?>(null)

    init { viewModelScope.launch { c.settings.blockedUsers.collect { blocked = it } } }

    /** Reports [m] (or, with [asUser], its sender) to the administrators and the class's professor. */
    fun report(m: ClassMessage, asUser: Boolean, reason: String, details: String) {
        viewModelScope.launch {
            try {
                c.reports.report(
                    kind = if (asUser) "user" else "chat_message", reason = reason, details = details,
                    classId = classId, messageId = if (asUser) null else m.id, reportedUserId = m.senderId,
                    excerpt = if (asUser) "User: ${m.senderName}" else "${m.senderName}: ${m.body}",
                )
                error = null
                notice = "Thanks. Your report was sent for review."
            } catch (e: ApiException) {
                error = if (e.isNetwork) "Report not sent: you're offline." else e.message
            }
        }
    }

    fun setBlocked(userId: String, name: String, on: Boolean) {
        viewModelScope.launch {
            c.settings.setBlocked(userId, on)
            notice = if (on) "You blocked $name. Their messages are hidden on this phone." else "You unblocked $name."
        }
    }

    override fun onCleared() { stopLive() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClassChatScreen(classId: String, title: String, canModerate: Boolean, onBack: () -> Unit) {
    val vm = appViewModel(key = "chat-$classId") { ClassChatViewModel(it, classId) }
    DisposableEffect(classId) { vm.startLive(); onDispose { vm.stopLive() } }
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var emojiOpen by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    // The emoji panel and the keyboard take turns: opening one closes the other.
    androidx.activity.compose.BackHandler(enabled = emojiOpen) { emojiOpen = false }
    var toDelete by remember { mutableStateOf<ClassMessage?>(null) }
    var menuFor by remember { mutableStateOf<ClassMessage?>(null) }
    /** Message to report, and whether the report is about its sender rather than the message. */
    var reportTarget by remember { mutableStateOf<Pair<ClassMessage, Boolean>?>(null) }
    var manageBlocked by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Newest message is at index 0 (reverse layout): keep it in view as messages arrive.
    LaunchedEffect(vm.messages.lastOrNull()?.id, vm.pending.size) {
        if (vm.messages.isNotEmpty() || vm.pending.isNotEmpty()) listState.animateScrollToItem(0)
    }

    ScreenScaffold(title = title.ifBlank { "Class chat" }, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).forensicWallpaper().imePadding()) {
            when (vm.status) {
                ChatStatus.LOADING -> Box(Modifier.weight(1f)) { LoadingState(label = "Loading messages") }
                ChatStatus.UNAVAILABLE -> Box(Modifier.weight(1f)) {
                    EmptyState(Icons.Outlined.Forum, "Class chat isn't switched on yet",
                        "Your school's ForenSim server needs an update before chat can be used.")
                }
                ChatStatus.ERROR -> Box(Modifier.weight(1f)) {
                    EmptyState(Icons.Outlined.Forum, "Couldn't load the chat", vm.error ?: "Try again.", actionLabel = "Try again", onAction = { vm.refresh(first = true) })
                }
                ChatStatus.READY -> {
                    if (vm.messages.isEmpty() && vm.pending.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            EmptyState(Icons.Outlined.Forum, "No messages yet",
                                "Ask your professor or classmates anything about this subject. Everyone in the class can see the chat.")
                        }
                    } else {
                        val today = LocalDate.now()
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth(), state = listState, reverseLayout = true,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Messages from people this user blocked are hidden (their own never are).
                            val newestFirst = (vm.messages + vm.pending)
                                .filter { it.senderId == vm.myId || it.senderId !in vm.blocked }.asReversed()
                            itemsIndexed(newestFirst, key = { _, m -> m.id }) { i, m ->
                                val mine = m.senderId == vm.myId
                                // Name and photo only on the first message of a run from the same person.
                                val firstOfRun = newestFirst.getOrNull(i + 1)?.senderId != m.senderId
                                val sendingNow = m.id < 0
                                Bubble(m, mine, today, firstOfRun, vm.photos[m.senderId],
                                    Modifier.alpha(if (sendingNow) 0.6f else 1f).combinedClickable(
                                        onClickLabel = null, onLongClickLabel = "Message options",
                                        onClick = {}, onLongClick = { if (!sendingNow) menuFor = m },
                                    ))
                            }
                        }
                    }
                }
            }
            vm.error?.takeIf { vm.status == ChatStatus.READY }?.let {
                Banner(it, Tone.ATTENTION, modifier = Modifier.padding(horizontal = Spacing.md))
            }
            vm.notice?.takeIf { vm.status == ChatStatus.READY && vm.error == null }?.let {
                LaunchedEffect(it) { delay(4_000); if (vm.notice == it) vm.notice = null }
                Banner(it, Tone.SUCCESS, modifier = Modifier.padding(horizontal = Spacing.md))
            }
            val hiddenSenders = vm.messages.filter { it.senderId != vm.myId && it.senderId in vm.blocked }
                .distinctBy { it.senderId }
            if (vm.status == ChatStatus.READY && hiddenSenders.isNotEmpty()) {
                TextButton(onClick = { manageBlocked = true }, modifier = Modifier.padding(horizontal = Spacing.sm)) {
                    Text(if (hiddenSenders.size == 1) "Messages from 1 blocked person are hidden · Manage"
                        else "Messages from ${hiddenSenders.size} blocked people are hidden · Manage",
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            if (vm.status == ChatStatus.READY) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.text.length <= 2000) draft = it },
                        placeholder = { Text("Message the class") }, maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = com.forensim.app.core.designsystem.forensimFieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) emojiOpen = false },
                    )
                    Spacer(Modifier.width(4.dp))
                    com.forensim.app.core.designsystem.IconButton(
                        onClick = {
                            if (emojiOpen) { emojiOpen = false; keyboard?.show() }
                            else { keyboard?.hide(); focus.clearFocus(); emojiOpen = true }
                        },
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(if (emojiOpen) Icons.Outlined.Keyboard else Icons.Outlined.EmojiEmotions,
                            contentDescription = if (emojiOpen) "Show keyboard" else "Emoji",
                            tint = if (emojiOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(4.dp))
                    FilledIconButton(
                        onClick = { vm.send(draft.text, onSent = { draft = TextFieldValue("") },
                            onFailed = { failed -> if (draft.text.isBlank()) draft = TextFieldValue(failed, TextRange(failed.length)) }) },
                        enabled = draft.text.isNotBlank(),
                        modifier = Modifier.size(52.dp),
                    ) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send") }
                }
                if (emojiOpen) EmojiPanel { emoji ->
                    // Insert at the cursor (replacing any selection), then put the cursor after it.
                    val t = draft.text
                    val start = draft.selection.min.coerceIn(0, t.length)
                    val end = draft.selection.max.coerceIn(0, t.length)
                    val text = t.substring(0, start) + emoji + t.substring(end)
                    if (text.length <= 2000) draft = TextFieldValue(text, TextRange(start + emoji.length))
                }
            }
        }
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete message?") },
            text = { Text("It will be removed for everyone in the class.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(m); toDelete = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }

    // Long-press options: report the message or its sender, block the sender, or delete.
    menuFor?.let { m ->
        val mine = m.senderId == vm.myId
        val name = m.senderName.ifBlank { "this person" }
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(if (mine) "Your message" else name) },
            text = {
                Column {
                    if (!mine) {
                        MenuOption("Report message") { reportTarget = m to false; menuFor = null }
                        MenuOption("Report $name") { reportTarget = m to true; menuFor = null }
                        MenuOption("Block $name") { vm.setBlocked(m.senderId, name, true); menuFor = null }
                    }
                    if (mine || canModerate) MenuOption("Delete message", danger = true) { toDelete = m; menuFor = null }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text("Close") } },
        )
    }

    reportTarget?.let { (m, asUser) ->
        val name = m.senderName.ifBlank { "this person" }
        com.forensim.app.feature.report.ReportDialog(
            title = if (asUser) "Report $name" else "Report message",
            explanation = "Your report goes to the administrators and your professor, who can remove the message or the person. $name won't see who reported them.",
            onDismiss = { reportTarget = null },
            onSubmit = { reason, details -> vm.report(m, asUser, reason, details); reportTarget = null },
        )
    }

    if (manageBlocked) {
        val people = vm.messages.filter { it.senderId != vm.myId && it.senderId in vm.blocked }.distinctBy { it.senderId }
        AlertDialog(
            onDismissRequest = { manageBlocked = false },
            title = { Text("Blocked people") },
            text = {
                Column {
                    if (people.isEmpty()) Text("No one in this chat is blocked.")
                    people.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.senderName.ifBlank { "Classmate" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { vm.setBlocked(p.senderId, p.senderName.ifBlank { "Classmate" }, false) }) { Text("Unblock") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { manageBlocked = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun MenuOption(label: String, danger: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

private val EMOJI_GROUPS: List<Pair<String, List<String>>> = listOf(
    "Smileys" to "😀 😃 😄 😁 😆 😅 😂 🤣 😊 🙂 😉 😍 🥰 😘 😎 🤓 🧐 🤔 🤨 😐 😑 😶 🙄 😏 😮 😯 😲 😳 🥺 😢 😭 😤 😠 😡 🤯 😱 😴 🤗 🤭 🤫 😇 🥳 😬 😵 🤐 😷 🤒 🤕".split(" "),
    "Gestures" to "👍 👎 👌 ✌️ 🤞 🤟 🤘 👏 🙌 👐 🙏 🤝 💪 ✋ 👋 🤚 👉 👈 👆 👇 ☝️ ✍️ 🫡 🫶 👀 🧠 🙋 🙆 🙅 🤷 🤦 🙇".split(" "),
    "Hearts and symbols" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💯 ✨ ⭐ 🌟 🔥 🎉 🎊 ✅ ❌ ❓ ❗ ⚠️ 💡".split(" "),
    "Forensics and study" to "🔍 🔎 🕵️ 🧪 🧬 🔬 🧫 🩸 💉 🧤 📷 📸 📏 📐 🗂️ 📁 📄 📝 📋 📚 📖 ✏️ 🖊️ 📌 📎 🔒 🔓 🚨 🚓 ⚖️ 🗝️ 💼 ⏰ 📅 🏫 🎓".split(" "),
)

/** Simple emoji picker: category tabs over a grid. Tapping an emoji inserts it at the cursor. */
@Composable
private fun EmojiPanel(onPick: (String) -> Unit) {
    var group by rememberSaveable { mutableStateOf(0) }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(cs.surfaceContainer).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EMOJI_GROUPS.forEachIndexed { i, (label, emojis) ->
                val isSelected = i == group
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) cs.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { group = i }
                        .semantics { selected = isSelected; contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { Text(emojis.first(), fontSize = 22.sp) }
            }
        }
        HorizontalDivider(color = cs.outlineVariant)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(44.dp),
            modifier = Modifier.fillMaxWidth().height(232.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.sm),
        ) {
            gridItems(EMOJI_GROUPS[group].second) { e ->
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).clickable { onPick(e) }.semantics { contentDescription = e },
                    contentAlignment = Alignment.Center,
                ) { Text(e, fontSize = 26.sp) }
            }
        }
    }
}

private val AVATAR_SIZE = 32.dp

/**
 * One message. Other people's messages show their photo (or initials) and name on the first
 * message of a run; follow-ups are indented to line up under it.
 */
@Composable
private fun Bubble(m: ClassMessage, mine: Boolean, today: LocalDate, firstOfRun: Boolean, photo: ByteArray?, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val time = runCatching {
        val t = OffsetDateTime.parse(m.createdAt).atZoneSameInstant(ZoneId.systemDefault())
        t.format(DateTimeFormatter.ofPattern(if (t.toLocalDate() == today) "HH:mm" else "d MMM, HH:mm"))
    }.getOrDefault("")
    val name = m.senderName.ifBlank { "Member" }
    Row(Modifier.fillMaxWidth().padding(top = if (firstOfRun) 6.dp else 0.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        if (!mine) {
            if (firstOfRun) UserAvatar(name, AVATAR_SIZE, photo, Modifier.padding(top = 2.dp))
            else Spacer(Modifier.width(AVATAR_SIZE))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.widthIn(max = 280.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (!mine && firstOfRun) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
                    Text(name, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    if (m.senderRole == "professor") { Spacer(Modifier.width(6.dp)); StatusChip("Professor", Tone.PRIMARY) }
                }
            }
            Box(
                modifier.background(
                    if (mine) cs.primary else cs.surfaceContainerHigh,
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp),
                ).padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(m.body, style = MaterialTheme.typography.bodyLarge, color = if (mine) cs.onPrimary else cs.onSurface)
            }
            Text(time, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}
