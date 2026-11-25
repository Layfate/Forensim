package com.forensim.app.feature.settings
import androidx.compose.ui.res.stringResource
import com.forensim.app.R

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.forensim.app.core.designsystem.OutlinedTextField
import androidx.compose.material3.Text
import com.forensim.app.core.designsystem.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forensim.app.AppContainer
import com.forensim.app.core.auth.PasswordPolicy
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.ListDivider
import com.forensim.app.core.designsystem.ListGroup
import com.forensim.app.core.designsystem.ListRow
import com.forensim.app.core.designsystem.PrimaryButton
import com.forensim.app.core.designsystem.ReadableColumn
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.designsystem.UserAvatar
import com.forensim.app.core.model.Gender
import com.forensim.app.core.model.Role as UserRole
import com.forensim.app.core.model.UserAccount
import com.forensim.app.ui.LocalAppContainer
import com.forensim.app.ui.appViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.material.icons.outlined.DeleteForever
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class AccountViewModel(private val c: AppContainer) : ViewModel() {
    var busy by mutableStateOf(false); private set
    var photoBusy by mutableStateOf(false); private set
    /** Success message as a string resource + format argument (resolved in the UI's language). */
    var message by mutableStateOf<Pair<Int, String?>?>(null); private set
    var error by mutableStateOf<String?>(null); private set

    init { viewModelScope.launch { runCatching { c.profile.refresh() } } }

    fun clearMessages() { message = null; error = null }

    private fun run(onBusy: (Boolean) -> Unit, success: Pair<Int, String?>?, block: suspend () -> Unit) {
        clearMessages()
        onBusy(true)
        viewModelScope.launch {
            try { block(); message = success } catch (e: Exception) { error = e.message ?: "Something went wrong." } finally { onBusy(false) }
        }
    }

    fun save(name: String, gender: Gender?, studentId: String?) = run({ busy = it }, R.string.msg_profile_saved to null) {
        c.profile.updateDetails(name, gender, studentId)
        c.auth.refreshAccount() // header name everywhere
    }

    fun uploadPhoto(context: Context, uri: Uri) = run({ photoBusy = it }, R.string.msg_photo_updated to null) {
        c.profile.uploadAvatar(downscaleJpeg(context, uri))
    }

    fun removePhoto() = run({ photoBusy = it }, R.string.msg_photo_removed to null) { c.profile.removeAvatar() }

    fun changeEmail(newEmail: String, onSent: () -> Unit) {
        if (!EMAIL.matches(newEmail.trim())) { message = null; error = INVALID_EMAIL; return }
        run({ busy = it }, R.string.msg_email_link_sent to newEmail.trim()) {
            c.auth.requestEmailChange(newEmail)
            onSent()
        }
    }

    fun sendPasswordReset(email: String) = run({ busy = it }, R.string.msg_reset_link_sent to email) {
        c.auth.requestPasswordReset(email)
    }

    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        /** Marker replaced by the localised "invalid e-mail" text in the UI. */
        const val INVALID_EMAIL = "\u0000invalid_email"
    }
}

/** Photo, name, gender, student ID, e-mail and password for the signed-in user. */
@Composable
fun AccountScreen(user: UserAccount, onBack: () -> Unit) {
    val vm = appViewModel(key = "account") { AccountViewModel(it) }
    val c = LocalAppContainer.current
    val profile by c.profile.profile.collectAsStateWithLifecycle()
    val photo by c.profile.avatar.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val email = profile?.email ?: user.email

    var name by rememberSaveable(profile?.fullName) { mutableStateOf(profile?.fullName ?: user.fullName) }
    var gender by rememberSaveable(profile?.gender) { mutableStateOf(profile?.gender) }
    var studentId by rememberSaveable(profile?.studentId) { mutableStateOf(profile?.studentId.orEmpty()) }
    var photoMenu by rememberSaveable { mutableStateOf(false) }
    var emailDialog by rememberSaveable { mutableStateOf(false) }
    var passwordDialog by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    val isStudent = user.role == UserRole.STUDENT

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.uploadPhoto(context, uri)
    }
    val dirty = profile != null && (name.trim() != profile?.fullName || gender != profile?.gender ||
        (isStudent && studentId.trim().ifBlank { null } != profile?.studentId))

    ScreenScaffold(title = stringResource(R.string.title_account), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md)) {
            ReadableColumn {
                // Photo
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(112.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.change_photo)) {
                        if (photo == null) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else photoMenu = true
                    }) {
                        UserAvatar(name.ifBlank { email }, size = 112.dp, photo = photo)
                        if (vm.photoBusy) {
                            Box(Modifier.matchParentSizeCompat().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                            }
                        }
                    }
                    Box(Modifier.padding(top = Spacing.sm)) {
                        TextButton(onClick = {
                            if (photo == null) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else photoMenu = true
                        }) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(Spacing.xs))
                            Text(stringResource(if (photo == null) R.string.add_photo else R.string.change_photo))
                        }
                    }
                }

                vm.message?.let { (res, arg) ->
                    Banner(if (arg == null) stringResource(res) else stringResource(res, arg), Tone.SUCCESS); Spacer(Modifier.height(Spacing.sm))
                }
                vm.error?.let {
                    Banner(if (it == AccountViewModel.INVALID_EMAIL) stringResource(R.string.err_invalid_email) else it, Tone.ERROR)
                    Spacer(Modifier.height(Spacing.sm))
                }

                // Details
                SectionHeader(stringResource(R.string.personal_details))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(80) }, label = { Text(stringResource(R.string.full_name)) }, singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) }, shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (isStudent) {
                        OutlinedTextField(
                            value = studentId, onValueChange = { studentId = it.take(32) }, label = { Text(stringResource(R.string.student_id)) }, singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null) }, shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(stringResource(R.string.gender), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Gender.entries.forEach { g ->
                            FilterChip(selected = gender == g.key, onClick = { gender = if (gender == g.key) null else g.key },
                                label = { Text(stringResource(genderLabel(g))) }, modifier = Modifier.heightIn(min = Spacing.touchTarget))
                        }
                    }
                    PrimaryButton(stringResource(R.string.save_changes), onClick = { vm.save(name, Gender.from(gender), if (isStudent) studentId else profile?.studentId) },
                        enabled = dirty && name.isNotBlank(), loading = vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
                }

                // Sign-in
                SectionHeader(stringResource(R.string.sign_in_security))
                ListGroup {
                    ListRow(stringResource(R.string.email), subtitle = email, icon = Icons.Outlined.AlternateEmail, onClick = { vm.clearMessages(); emailDialog = true },
                        trailing = { Text(stringResource(R.string.change), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface) })
                    ListDivider()
                    ListRow(stringResource(R.string.password), subtitle = stringResource(R.string.password_row_sub), icon = Icons.Outlined.Lock,
                        onClick = { vm.clearMessages(); passwordDialog = true },
                        trailing = { Text(stringResource(R.string.change), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface) })
                }

                // Delete account (Google Play: in-app account deletion).
                SectionHeader(stringResource(R.string.delete_account))
                Text(stringResource(R.string.delete_account_intro), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.sm))
                androidx.compose.material3.OutlinedButton(
                    onClick = { deleteDialog = true },
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(Spacing.sm))
                    Text(stringResource(R.string.delete_account))
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }

    if (photoMenu) {
        AlertDialog(
            onDismissRequest = { photoMenu = false },
            title = { Text(stringResource(R.string.profile_photo)) },
            confirmButton = {
                TextButton(onClick = { photoMenu = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.choose_new_photo)) }
            },
            dismissButton = {
                TextButton(onClick = { photoMenu = false; vm.removePhoto() },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.remove)) }
            },
        )
    }
    if (emailDialog) {
        var newEmail by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { emailDialog = false },
            title = { Text(stringResource(R.string.change_email)) },
            text = {
                Column {
                    Text(stringResource(R.string.change_email_body),
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(value = newEmail, onValueChange = { newEmail = it.trim() }, label = { Text(stringResource(R.string.new_email)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.changeEmail(newEmail) { emailDialog = false } }, enabled = newEmail.isNotBlank() && !vm.busy) { Text(stringResource(R.string.send_link)) }
            },
            dismissButton = { TextButton(onClick = { emailDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deleteDialog) DeleteAccountDialog(email, onDismiss = { deleteDialog = false })
    if (passwordDialog) {
        AlertDialog(
            onDismissRequest = { passwordDialog = false },
            title = { Text(stringResource(R.string.change_password)) },
            text = { Text(stringResource(R.string.change_password_body, email)) },
            confirmButton = { TextButton(onClick = { passwordDialog = false; vm.sendPasswordReset(email) }) { Text(stringResource(R.string.send_link)) } },
            dismissButton = { TextButton(onClick = { passwordDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * "Delete your account?": lists what goes, and needs DELETE typed before the red button works.
 * On success the user is signed out and the sign-in screen confirms the deletion.
 */
@Composable
private fun DeleteAccountDialog(email: String, onDismiss: () -> Unit) {
    val c = LocalAppContainer.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var typed by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val word = stringResource(R.string.delete_confirm_word)
    val ready = typed.trim().equals(word, ignoreCase = true) && !busy
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.delete_account_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.delete_account_body, email), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.delete_account_list), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.delete_account_type, word), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = typed, onValueChange = { typed = it.take(20); error = null }, singleLine = true, enabled = !busy,
                    placeholder = { Text(word) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        try { c.auth.deleteMyAccount() } catch (e: Exception) { error = e.message; busy = false }
                    }
                },
            ) { Text(stringResource(if (busy) R.string.deleting else R.string.delete_forever)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * App-wide dialog that follows an e-mail link: the result of an e-mail change confirmation.
 * (A password-reset link opens the full ResetPasswordScreen instead.)
 */
@Composable
fun AccountLinkDialogs() {
    val c = LocalAppContainer.current
    val notice by c.auth.notice.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    notice?.let { text ->
        AlertDialog(
            onDismissRequest = { c.auth.dismissNotice() },
            title = { Text(stringResource(R.string.title_account)) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { c.auth.dismissNotice() }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

private fun genderLabel(g: Gender) = when (g) {
    Gender.FEMALE -> R.string.gender_female
    Gender.MALE -> R.string.gender_male
    Gender.PREFER_NOT_TO_SAY -> R.string.gender_prefer_not
}

// ----------------------------------------------------------------------------- photo helpers

/** Decodes the picked image (honouring EXIF rotation on API 28+), crops to a square, 512 px JPEG. */
private suspend fun downscaleJpeg(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.Default) {
    val max = 512
    val source: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val scale = max.toFloat() / minOf(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw IllegalStateException("Couldn't open that image.")
    }
    val side = minOf(source.width, source.height)
    val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    val scaled = if (side > max) Bitmap.createScaledBitmap(square, max, max, true) else square
    ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
}

/** Fills the parent Box (avoids importing BoxScope.matchParentSize in a non-scope helper). */
private fun Modifier.matchParentSizeCompat() = this.fillMaxSize()
