package com.forensim.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation

/*
 * Brand rule: maroon is a FILL colour (buttons, selected states, hero backgrounds), never a text
 * colour. Material's text/outlined buttons and focused field labels draw their text in `primary`,
 * so the app uses these drop-in wrappers instead, which keep text neutral. The signatures mirror
 * the Material 3 ones for the parameters the app uses.
 */

/*
 * Every button in the app goes through these wrappers, so they all share one press feel
 * (pressScale) on top of the Material ripple. Use them instead of the androidx.compose.material3
 * buttons of the same name.
 */

@Composable
private fun rememberInteraction(given: MutableInteractionSource?) = given ?: remember { MutableInteractionSource() }

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val i = rememberInteraction(interactionSource)
    androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier.pressScale(i), enabled = enabled, shape = shape,
        colors = colors, contentPadding = contentPadding, interactionSource = i, content = content)
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val i = rememberInteraction(interactionSource)
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier.pressScale(i), enabled = enabled, shape = shape,
        colors = colors, border = border, contentPadding = contentPadding, interactionSource = i, content = content)
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val i = rememberInteraction(interactionSource)
    androidx.compose.material3.Button(onClick = onClick, modifier = modifier.pressScale(i), enabled = enabled, shape = shape,
        colors = colors, border = border, contentPadding = contentPadding, interactionSource = i, content = content)
}

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val i = rememberInteraction(interactionSource)
    androidx.compose.material3.FilledTonalButton(onClick = onClick, modifier = modifier.pressScale(i), enabled = enabled, shape = shape,
        colors = colors, border = border, contentPadding = contentPadding, interactionSource = i, content = content)
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val i = rememberInteraction(interactionSource)
    androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier.pressScale(i, PressScale.ICON), enabled = enabled,
        colors = colors, interactionSource = i, content = content)
}

@Composable
fun FilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val i = rememberInteraction(interactionSource)
    androidx.compose.material3.FilledIconButton(onClick = onClick, modifier = modifier.pressScale(i, PressScale.ICON), enabled = enabled,
        shape = shape, colors = colors, interactionSource = i, content = content)
}

/** Field colours: maroon focus outline and cursor (accents), neutral label and icons. */
@Composable
fun forensimFieldColors(): TextFieldColors {
    val cs = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedLabelColor = cs.onSurface,
        focusedLeadingIconColor = cs.onSurface,
        focusedTrailingIconColor = cs.onSurface,
        focusedSupportingTextColor = cs.onSurfaceVariant,
        focusedPrefixColor = cs.onSurfaceVariant,
        focusedSuffixColor = cs.onSurfaceVariant,
    )
}

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: TextFieldColors = forensimFieldColors(),
) = androidx.compose.material3.OutlinedTextField(
    value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly, textStyle = textStyle,
    label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
    supportingText = supportingText, isError = isError, visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
    interactionSource = interactionSource, shape = shape, colors = colors,
)
