package com.forensim.app.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.forensim.app.R
import com.forensim.app.core.auth.PasswordPolicy
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.ForensimTheme
import com.forensim.app.core.designsystem.OutlinedTextField
import com.forensim.app.core.designsystem.PrimaryButton
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.TextButton
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.model.UserAccount
import com.forensim.app.ui.LocalAppContainer
import kotlinx.coroutines.launch

/**
 * Full-screen "Reset your password" page, opened by the reset link in the e-mail. The link has
 * already signed the user in; they type the new password twice, then continue into the app.
 */
@Composable
fun ResetPasswordScreen(user: UserAccount) {
    val c = LocalAppContainer.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var done by rememberSaveable { mutableStateOf(false) }

    val strong = PasswordPolicy.isValid(password)
    val matches = confirm.isNotEmpty() && confirm == password
    val canSave = strong && matches && !busy
    fun save() {
        if (!canSave) return
        focus.clearFocus(); busy = true; error = null
        scope.launch {
            try { c.auth.updatePassword(password); done = true }
            catch (e: Exception) {
                error = if (e.message.orEmpty().contains("different from the old", ignoreCase = true))
                    "Choose a password you haven't used before." else e.message ?: "Couldn't save your password. Try again."
            }
            finally { busy = false }
        }
    }
    BackHandler(enabled = !done) { c.auth.dismissPasswordRecovery() }

    Box(Modifier.fillMaxSize().background(cs.background).safeDrawingPadding().imePadding()) {
        AnimatedContent(done, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "reset") { finished ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
                    if (!finished) {
                        Image(painterResource(R.drawable.forensim_badge_dark), contentDescription = null, modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(Spacing.lg))
                        Text("Reset your password", style = MaterialTheme.typography.headlineLarge, color = cs.onSurface)
                        Spacer(Modifier.height(Spacing.sm))
                        Text(buildAnnotatedString {
                            append("Choose a new password for ")
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = cs.onSurface)) { append(user.email) }
                            append(". Type it twice to make sure it's right.")
                        }, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.xl))

                        val eye: @Composable () -> Unit = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password")
                            }
                        }
                        val transform = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
                        OutlinedTextField(
                            value = password, onValueChange = { password = it; error = null },
                            label = { Text("New password") }, singleLine = true, visualTransformation = transform, trailingIcon = eye,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        PasswordChecklist(password)
                        Spacer(Modifier.height(Spacing.md))
                        OutlinedTextField(
                            value = confirm, onValueChange = { confirm = it; error = null },
                            label = { Text("Confirm new password") }, singleLine = true, visualTransformation = transform, trailingIcon = eye,
                            isError = confirm.isNotEmpty() && !matches,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { save() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (confirm.isNotEmpty()) {
                            val fx = ForensimTheme.colors
                            Row(Modifier.padding(top = Spacing.sm, start = Spacing.xs), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Icon(if (matches) Icons.Outlined.Check else Icons.Outlined.Cancel, contentDescription = null,
                                    tint = if (matches) fx.success else cs.error, modifier = Modifier.size(18.dp))
                                Text(if (matches) "Passwords match" else "Passwords don't match",
                                    style = MaterialTheme.typography.bodyMedium, color = if (matches) cs.onSurface else cs.error)
                            }
                        }
                        error?.let { Spacer(Modifier.height(Spacing.md)); Banner(it, Tone.ERROR) }
                        Spacer(Modifier.height(Spacing.xl))
                        PrimaryButton(if (busy) "Saving" else "Reset password", onClick = { save() },
                            enabled = strong && matches, loading = busy, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Spacing.sm))
                        TextButton(onClick = { c.auth.dismissPasswordRecovery() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Not now")
                        }
                    } else {
                        // Success: a check that pops in, then straight into the app.
                        Spacer(Modifier.height(Spacing.xxl))
                        val pop = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0.3f) }
                        androidx.compose.runtime.LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                        Box(
                            Modifier.align(Alignment.CenterHorizontally).size(96.dp).scale(pop.value).clip(CircleShape)
                                .background(ForensimTheme.colors.successContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ForensimTheme.colors.success, modifier = Modifier.size(56.dp))
                        }
                        Spacer(Modifier.height(Spacing.lg))
                        Text("Password updated", style = MaterialTheme.typography.headlineMedium, color = cs.onSurface,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Spacing.sm))
                        Text("You're signed in. Use your new password the next time you sign in.",
                            style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Spacing.xl))
                        PrimaryButton("Continue to ForenSim", onClick = { c.auth.dismissPasswordRecovery() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
