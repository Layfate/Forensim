package com.forensim.app.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * ForenSim colour tokens: maroon & peach palette (2026-11 redesign, colours chosen by the owner).
 *
 * Light (2026-11, modelled on a marketplace app's light UI): #FAFAFA page, white cards and bars
 * with #E4E5E7 hairlines, near-black #222325 ink for text, deep maroon #74293B for primary
 * actions (buttons, switches, selection), and green #1DBF73 for presence, progress and success.
 * Dark (2026-11, modelled on the same marketplace app's dark UI): a near-black #0E0E0E page,
 * neutral charcoal #1C1D1F cards, rows, top bar and bottom bar with #292A2E hairlines, a lifted
 * #2A2B2F profile header, white text and grey #A3A4A8 secondary text. The owner's light maroon
 * #B76E7D stays the one accent (buttons, selection, links); green #1DBF73 marks success/presence.
 *
 * Green / amber / red are kept only for meaning (success, attention, error), tuned warm so they
 * sit with the maroon. Text pairs meet WCAG AA. Components read colours from
 * MaterialTheme.colorScheme or [ForensimColors], never raw hex.
 */
internal object Palette {
    // ---- Dark (marketplace-style neutral charcoal, light maroon accent, 2026-11) ----
    val Night = Color(0xFF0E0E0E)        // Page background behind cards and bars
    val NightSurface = Color(0xFF1C1D1F) // Cards, list rows, top bar, bottom bar
    val SoftMaroon = Color(0xFFB76E7D)   // Primary accent, the light maroon (4.5:1 on cards, 5.1:1 on page)
    val MaroonHoverDark = Color(0xFFC98B98) // Button hover / pressed
    val MutedPeach = Color(0xFFF0DCD9)   // Peach accent text
    val HighlightDark = Color(0xFF2F2A2C) // Selected rows/chips: charcoal with a hint of maroon
    // elevation steps
    val Night0 = Color(0xFF0A0A0A)
    val Night1 = Color(0xFF1C1D1F)       // same as cards: bars sit flush like the reference
    val Night3 = Color(0xFF2A2B2F)       // Raised header, quiet fills (chips, inputs)
    val Night4 = Color(0xFF38393D)       // Progress tracks, icon tiles, highest
    val NightLine = Color(0xFF74767B)    // Field borders, 3.7:1 on cards
    val NightHair = Color(0xFF292A2E)    // Dividers and card hairlines
    val TextHigh = Color(0xFFFFFFFF)     // Text (16.9:1 on cards)
    val TextMuted = Color(0xFFA3A4A8)    // Secondary text and icons (6.8:1 on cards)

    // ---- Light (marketplace-style neutrals, 2026-11) ----
    // Near-black ink on white cards over a #FAFAFA page, hairline greys and one green accent.
    val White = Color(0xFFFFFFFF)        // Cards, top bars, bottom bar
    val Page = Color(0xFFFAFAFA)         // Page background behind cards
    val Surface = Color(0xFFF5F5F5)      // Quiet fills (chips, inputs)
    val Ink = Color(0xFF222325)          // Primary text and primary buttons (15.7:1 on white)
    val Ink2 = Color(0xFF404145)         // Strong secondary text (10:1)
    val InkMuted = Color(0xFF62646A)     // Secondary text (5.9:1 on white, 5.6:1 on Page)
    val Grey400 = Color(0xFF95979D)      // Disabled text, de-emphasised numbers ("/ 5")
    val LightOutline = Color(0xFF8A8C92) // Field borders, 3.4:1 on white (UI parts)
    val LightHair = Color(0xFFE4E5E7)    // Dividers and card hairlines
    val LightSurfaceHigh = Color(0xFFEFEFF0)
    val LightSurfaceHighest = Color(0xFFE4E5E7)
    val Green = Color(0xFF1DBF73)        // Brand accent: presence, progress markers, success
    val GreenDark = Color(0xFF19A463)
    val GreenTint = Color(0xFFE8F8F0)    // Success rows/chips
    val Charcoal = Color(0xFF2A2B2F)     // Dark profile header
    // Light-theme accent: a deep maroon in place of the old near-black (white text on it 10:1).
    val DeepMaroon = Color(0xFF74293B)   // Buttons, switches, tabs, selection
    val DeeperMaroon = Color(0xFF561D2B) // Hero/header gradient end, snackbars
    // Kept for the dark theme preview
    val Maroon = Color(0xFF6A2636)

    // Aliases used by the theme picker preview
    val Paper = White
    val Night2 = NightSurface
}

/** Semantic colours not covered by Material's scheme. */
data class ForensimColors(
    /** Checkmarks, completion, confirmation. Always green. */
    val success: Color,
    /** Background for success rows/chips: peach (light) / highlight (dark), with neutral text. */
    val successContainer: Color,
    val onSuccessContainer: Color,
    val attention: Color,
    val attentionContainer: Color,
    val onAttentionContainer: Color,
    val hudScrim: Color,
    val onHud: Color,
    val onHudMuted: Color,
    val hudAttention: Color,
    /** Hero gradient (feature panels, sign-in header). Content on it is always [onHero]. */
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    /** Soft glow behind animations and icons. */
    val glow: Color,
    /** Informational accents (icons). */
    val info: Color,
)

internal val DarkForensimColors = ForensimColors(
    success = Palette.Green,
    successContainer = Color(0xFF15291F),
    onSuccessContainer = Palette.TextHigh,
    attention = Color(0xFFFBBF24),
    attentionContainer = Color(0xFF33291A),
    onAttentionContainer = Palette.TextHigh,
    hudScrim = Color(0xE6121212),
    onHud = Palette.TextHigh,
    onHudMuted = Color(0xFFD6CCCE),
    hudAttention = Color(0xFFFBBF24),
    // Flat charcoal like the reference profile header; maroon stays an accent, not a wash.
    heroStart = Color(0xFF303135),
    heroEnd = Palette.Night3,
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xFFC9CACE),
    glow = Color(0x14FFFFFF),
    info = Color(0xFF60A5FA),
)

internal val LightForensimColors = ForensimColors(
    success = Palette.Green,
    successContainer = Palette.GreenTint,
    onSuccessContainer = Palette.Ink,
    attention = Color(0xFFD97706),
    attentionContainer = Color(0xFFFFF6E5),
    onAttentionContainer = Palette.Ink,
    // The 3D HUD always sits on a dark scene, so it stays dark in light theme too.
    hudScrim = Color(0xE6121212),
    onHud = Palette.TextHigh,
    onHudMuted = Color(0xFFD6CCCE),
    hudAttention = Color(0xFFFBBF24),
    heroStart = Palette.DeepMaroon,
    heroEnd = Palette.DeeperMaroon,
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xFFDADBDD),
    glow = Color(0x0F222325),
    info = Color(0xFF446EE7),
)
