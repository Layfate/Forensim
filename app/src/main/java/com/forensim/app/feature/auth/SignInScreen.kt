package com.forensim.app.feature.auth

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.forensim.app.core.designsystem.ForensimLogo
import com.forensim.app.core.designsystem.LocalReducedMotion
import com.forensim.app.core.designsystem.Motion
import com.forensim.app.core.designsystem.PageIntro
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.heading
import com.forensim.app.core.designsystem.buttonLarge
import com.forensim.app.core.designsystem.ForensimMark
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.forensim.app.core.designsystem.IconButton
import androidx.compose.material3.MaterialTheme
import com.forensim.app.core.designsystem.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.forensim.app.core.designsystem.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forensim.app.AppContainer
import com.forensim.app.R
import com.forensim.app.core.auth.PasswordPolicy
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.ForensimTheme
import com.forensim.app.core.designsystem.PrimaryButton
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.ui.appViewModel
import kotlinx.coroutines.launch

class SignInViewModel(private val c: AppContainer) : ViewModel() {
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var info by mutableStateOf<String?>(null); private set
    private var providers: Set<String>? = null

    init { viewModelScope.launch { providers = c.auth.enabledProviders() } }

    fun clearMessages() { error = null; info = null; c.auth.clearOAuthError() }

    /** [markNew] flags a brand-new account for first-run onboarding (true) or un-flags it if sign-up failed (false). */
    fun submit(createAccount: Boolean, email: String, password: String, name: String, markNew: (Boolean) -> Unit = {}) {
        clearMessages()
        when {
            !EMAIL.matches(email.trim()) -> { error = "Enter a valid e-mail address."; return }
            createAccount && name.isBlank() -> { error = "Enter your full name."; return }
            createAccount && !PasswordPolicy.isValid(password) -> { error = "Your password doesn't meet all the requirements yet."; return }
            password.isEmpty() -> { error = "Enter your password."; return }
        }
        busy = true
        viewModelScope.launch {
            try {
                if (createAccount) {
                    // Flag before signing up: a successful sign-up switches straight to the app.
                    markNew(true)
                    if (!c.auth.signUp(email, password, name)) info = "Check your inbox to confirm your e-mail, then sign in."
                } else {
                    c.auth.signIn(email, password)
                }
            } catch (e: Exception) {
                if (createAccount) markNew(false)
                error = e.message ?: "Sign-in failed."
            } finally {
                busy = false
            }
        }
    }

    /** Opens the provider's sign-in page in a secure browser tab (Custom Tabs + PKCE). */
    fun social(provider: String, label: String, open: (String) -> Unit) {
        clearMessages()
        busy = true
        viewModelScope.launch {
            try {
                val enabled = providers ?: c.auth.enabledProviders().also { providers = it }
                if (provider !in enabled) {
                    info = "$label sign-in isn't switched on for this school yet. Use e-mail for now, or ask your administrator."
                } else {
                    open(c.auth.oauthUrl(provider))
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    /** True while a reset e-mail is being requested: blocks double taps, which would send two
     *  links and make Supabase invalidate both. */
    var resetBusy by mutableStateOf(false); private set

    fun resetPassword(email: String, onDone: () -> Unit) {
        if (resetBusy) return
        if (!EMAIL.matches(email.trim())) { error = "Enter a valid e-mail address."; return }
        resetBusy = true
        viewModelScope.launch {
            try {
                c.auth.requestPasswordReset(email)
                info = "A reset link is on its way to $email if that account exists. Open the newest e-mail and tap its link on this phone."
                onDone()
            } catch (e: Exception) {
                // Supabase allows one reset e-mail per minute per account.
                error = if (e.message.orEmpty().contains("seconds", ignoreCase = true) || e.message.orEmpty().contains("rate", ignoreCase = true))
                    "A reset link was just sent. Wait a minute before asking for another one, and use the newest e-mail."
                else e.message ?: "Couldn't send the reset link. Try again."
                onDone()
            } finally {
                resetBusy = false
            }
        }
    }

    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

@Composable
fun SignInScreen() {
    val vm = appViewModel { SignInViewModel(it) }
    val container = com.forensim.app.ui.LocalAppContainer.current
    val oauthError by container.auth.oauthError.collectAsStateWithLifecycle()
    val signedOutMessage by container.auth.signedOutMessage.collectAsStateWithLifecycle()
    val oauthBusy by container.auth.oauthBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var createAccount by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val markNew: (Boolean) -> Unit = { new ->
        if (new) com.forensim.app.feature.onboarding.Onboarding.markPending(context, email)
        else com.forensim.app.feature.onboarding.Onboarding.clearPending(context, email)
    }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var resetOpen by rememberSaveable { mutableStateOf(false) }
    var legal by rememberSaveable { mutableStateOf<com.forensim.app.feature.legal.LegalDocument?>(null) }
    legal?.let { doc ->
        com.forensim.app.feature.legal.LegalScreen(doc, onBack = { legal = null })
        return
    }
    val busy = vm.busy || oauthBusy
    val openTab: (String) -> Unit = { url ->
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    }
    val switchMode: (Boolean) -> Unit = { create -> createAccount = create; vm.clearMessages() }
    androidx.activity.compose.BackHandler(enabled = createAccount) { switchMode(false) }
    val cs = MaterialTheme.colorScheme
    val fieldShape = RoundedCornerShape(10.dp)

    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().background(cs.background).statusBarsPadding().navigationBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val screenHeight = maxHeight
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = screenHeight).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                // Top bar: back arrow on Sign up; Sign in is the root screen, so it gets breathing room instead.
                Box(Modifier.height(if (createAccount) 56.dp else Spacing.md).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (createAccount) {
                        IconButton(onClick = { switchMode(false) }, modifier = Modifier.offset(x = (-12).dp)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.auth_back_to_sign_in))
                        }
                    }
                }
                if (!createAccount) {
                    Spacer(Modifier.height(Spacing.lg))
                    ForensimMark(72.dp)
                    Spacer(Modifier.height(Spacing.lg))
                    Text(stringResource(R.string.auth_welcome), style = MaterialTheme.typography.displaySmall, color = cs.onBackground,
                        modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(Spacing.sm))
                    Text(stringResource(R.string.auth_sign_in_prompt), style = MaterialTheme.typography.bodyLarge, color = cs.onBackground)
                    Spacer(Modifier.height(Spacing.xl))
                } else {
                    Text(stringResource(R.string.auth_join_title), style = MaterialTheme.typography.headlineLarge, color = cs.onBackground,
                        modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(Spacing.xs))
                    Text(stringResource(R.string.new_accounts_note), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.lg))
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    OutlinedTextField(
                        value = email, onValueChange = { email = it.trim() }, label = { Text(stringResource(R.string.email_label)) }, singleLine = true,
                        shape = fieldShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().semantics { contentType = if (createAccount) ContentType.NewUsername else ContentType.Username },
                    )
                    if (createAccount) {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.full_name)) }, singleLine = true,
                            shape = fieldShape,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.PersonFullName },
                        )
                    }
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPassword) stringResource(R.string.hide_password) else stringResource(R.string.show_password))
                            }
                        },
                        shape = fieldShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.submit(createAccount, email, password, name, markNew) }),
                        modifier = Modifier.fillMaxWidth().semantics { contentType = if (createAccount) ContentType.NewPassword else ContentType.Password },
                    )
                    if (!createAccount) {
                        Box(Modifier.fillMaxWidth().offset(y = (-8).dp), contentAlignment = Alignment.CenterEnd) {
                            TextButton(onClick = { resetOpen = true }) { Text(stringResource(R.string.forgot_password)) }
                        }
                    }
                    if (createAccount) PasswordChecklist(password)
                }

                (vm.error ?: oauthError)?.let { Spacer(Modifier.height(Spacing.md)); Banner(it, Tone.ERROR) }
                vm.info?.let { Spacer(Modifier.height(Spacing.md)); Banner(it, Tone.PRIMARY) }
                if (vm.info == null) signedOutMessage?.let { Spacer(Modifier.height(Spacing.md)); Banner(it, Tone.SUCCESS) }
                // Sign in already has the "Forgot password?" row above, so it needs less space here.
                Spacer(Modifier.height(if (createAccount) Spacing.lg else Spacing.xs))
                AuthButton(
                    text = stringResource(if (createAccount) R.string.auth_sign_up else R.string.auth_continue),
                    enabled = email.isNotBlank() && password.isNotBlank() &&
                        (!createAccount || (PasswordPolicy.isValid(password) && name.isNotBlank())),
                    loading = busy,
                ) { vm.submit(createAccount, email, password, name, markNew) }

                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = cs.outlineVariant)
                    Text(stringResource(R.string.auth_or), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg))
                    HorizontalDivider(Modifier.weight(1f), color = cs.outlineVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    // Sign in shows provider names; Sign up shows the logos only, like the reference.
                    SocialButton(if (createAccount) null else "Google", R.drawable.ic_google, "Google", !busy, Modifier.weight(1f)) {
                        vm.social("google", "Google", openTab)
                    }
                    SocialButton(if (createAccount) null else "Facebook", R.drawable.ic_facebook, "Facebook", !busy, Modifier.weight(1f)) {
                        vm.social("facebook", "Facebook", openTab)
                    }
                }
            }

            // Bottom: Join / Forgot Password on Sign in, the terms line on Sign up.
            Column(Modifier.padding(top = Spacing.xl, bottom = Spacing.md)) {
                if (!createAccount) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.new_to_forensim), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                        Spacer(Modifier.width(Spacing.sm))
                        UnderlinedLink(stringResource(R.string.auth_sign_up)) { switchMode(true) }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                }
                LegalConsent(createAccount, onOpen = { legal = it })
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(R.string.training_disclaimer), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (resetOpen) {
        var resetEmail by rememberSaveable { mutableStateOf(email) }
        AlertDialog(
            onDismissRequest = { resetOpen = false },
            title = { Text(stringResource(R.string.reset_your_password)) },
            text = {
                Column {
                    Text(stringResource(R.string.reset_body))
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(value = resetEmail, onValueChange = { resetEmail = it.trim() }, label = { Text(stringResource(R.string.email_label)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.resetPassword(resetEmail) { resetOpen = false } }, enabled = !vm.resetBusy) {
                    Text(if (vm.resetBusy) stringResource(R.string.send_link) + "…" else stringResource(R.string.send_link))
                }
            },
            dismissButton = { TextButton(onClick = { resetOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * The main auth action: a solid, high-contrast block (white on the dark theme, ink on the light
 * theme) with modest corners, like the reference sign-in.
 */
@Composable
private fun AuthButton(text: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    com.forensim.app.core.designsystem.Button(
        onClick = onClick, enabled = enabled && !loading,
        shape = RoundedCornerShape(10.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = cs.onBackground, contentColor = cs.background,
            disabledContainerColor = cs.onBackground.copy(alpha = 0.12f), disabledContentColor = cs.onBackground.copy(alpha = 0.38f),
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        if (loading && !LocalReducedMotion.current) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = cs.onBackground)
        } else {
            Text(text, style = MaterialTheme.typography.buttonLarge)
        }
    }
}

/** Outlined provider button; [text] null shows the logo alone (with [label] for TalkBack). */
@Composable
private fun SocialButton(text: String?, icon: Int, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(10.dp), color = Color.Transparent, contentColor = cs.onBackground,
        border = BorderStroke(1.dp, cs.outline), modifier = modifier.heightIn(min = 56.dp)
            .semantics { if (text == null) contentDescription = "Continue with $label" },
    ) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
            if (text != null) {
                Spacer(Modifier.width(12.dp))
                Text(text, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Underlined text link with a full-height touch target. */
@Composable
private fun UnderlinedLink(text: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = Spacing.touchTarget).clip(RoundedCornerShape(6.dp))
        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
            color = MaterialTheme.colorScheme.onBackground)
    }
}

/** Password rules as a quiet list: a dash while unmet, a green check once met. */
@Composable
internal fun PasswordChecklist(password: String) {
    val fx = ForensimTheme.colors
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(start = Spacing.xs)) {
        PasswordPolicy.rules.forEach { rule ->
            val ok = rule.test(password)
            Row(Modifier.padding(vertical = 2.dp).semantics(mergeDescendants = true) {
                contentDescription = "${rule.label}: ${if (ok) "met" else "not met"}"
            }, verticalAlignment = Alignment.CenterVertically) {
                if (ok) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = fx.success, modifier = Modifier.size(18.dp))
                } else {
                    Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(12.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(cs.onSurfaceVariant))
                    }
                }
                Spacer(Modifier.width(Spacing.md))
                Text(rule.label, style = MaterialTheme.typography.bodyMedium, color = if (ok) cs.onSurface else cs.onSurfaceVariant)
            }
        }
    }
}

/**
 * "By signing in / creating an account you agree to the Terms of Use and Privacy Policy", with
 * both names as links (Play Store requirement). Links are underlined, not maroon.
 */
@Composable
private fun LegalConsent(createAccount: Boolean, onOpen: (com.forensim.app.feature.legal.LegalDocument) -> Unit) {
    val terms = stringResource(R.string.terms_of_use)
    val privacy = stringResource(R.string.privacy_policy)
    val sentence = stringResource(if (createAccount) R.string.consent_create else R.string.consent_sign_in, terms, privacy)
    val linkStyle = androidx.compose.ui.text.TextLinkStyles(
        androidx.compose.ui.text.SpanStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
        ),
    )
    val text = androidx.compose.ui.text.buildAnnotatedString {
        append(sentence)
        listOf(terms to com.forensim.app.feature.legal.LegalDocument.TERMS, privacy to com.forensim.app.feature.legal.LegalDocument.PRIVACY)
            .forEach { (label, doc) ->
                val start = sentence.indexOf(label)
                if (start >= 0) addLink(
                    androidx.compose.ui.text.LinkAnnotation.Clickable(label, linkStyle) { onOpen(doc) },
                    start, start + label.length,
                )
            }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
}
