package com.forensim.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.forensim.app.R

/**
 * Fonts the user can pick in Settings > Font. Poppins is the default: the closest openly licensed
 * match for Fiverr's Macan (Macan is a commercial typeface and is not bundled).
 */
enum class AppFont(val label: String, val googleName: String?) {
    POPPINS("Poppins", null), INTER("Inter", null), MANROPE("Manrope", "Manrope"), DM_SANS("DM Sans", "DM Sans");
    companion object {
        val DEFAULT = POPPINS
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

private val GoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

/**
 * Family for [font]. Poppins and Inter are bundled. The others are fetched once from Google Fonts
 * through Play services and cached by the system; until they arrive (or without Play services /
 * offline) the bundled Inter of the same weight is used, so text never shows in an unexpected face.
 */
fun fontFamilyFor(font: AppFont): FontFamily {
    if (font == AppFont.POPPINS) return Poppins
    val name = font.googleName ?: return Inter
    val google = GoogleFont(name)
    return FontFamily(
        Font(googleFont = google, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
        Font(googleFont = google, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
        Font(googleFont = google, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
        Font(googleFont = google, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold),
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )
}

/**
 * Poppins (SIL OFL 1.1, see assets/licenses/poppins.txt), the default face, bundled in the five
 * weights the type system uses:
 *  - Regular 400: body text, descriptions, secondary information
 *  - Medium 500: navigation labels, input fields, buttons
 *  - SemiBold 600: emphasised labels and hero numbers
 *  - Bold 700 / ExtraBold 800: available for rare emphasis; headings deliberately stay Medium
 */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold),
)

/**
 * Inter (SIL OFL 1.1, see assets/licenses/inter.txt), bundled in two weights. Medium falls back to
 * Regular and Bold/ExtraBold to SemiBold.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/**
 * Text is vertically centred in its line box and font padding is off, so text lines up with
 * icons and the 4 dp grid regardless of the line height.
 */
private val Centred = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun FontFamily.style(size: Int, line: Int, weight: FontWeight, tracking: TextUnit) = TextStyle(
    fontFamily = this,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking,
    lineHeightStyle = Centred,
    platformStyle = NoFontPadding,
)

/** Font weights of the type system. Use these (or the typography roles) instead of raw weights. */
object Weights {
    val Regular = FontWeight.Normal
    val Medium = FontWeight.Medium
    val SemiBold = FontWeight.SemiBold
    val Bold = FontWeight.Bold
    val ExtraBold = FontWeight.ExtraBold
}

/**
 * One type scale for the whole app: 12, 14, 16, 18, 20, 24 and 28 sp only. Every screen reads
 * these roles from MaterialTheme.typography, never a hard-coded size or family.
 *
 * Like the reference marketplace UI, hierarchy comes from SIZE, not heavy weights: Poppins runs
 * dark, so headings use Medium and only hero numbers go bolder.
 *
 *  display*   28 sp  SemiBold/Medium  hero numbers and the rare hero heading
 *  headline*  24/20/18 sp  Medium    page titles ("Welcome back"), section headings
 *  title*     18/16/14 sp  Medium    card titles, list titles
 *  body*      16/14/12 sp  Regular   reading text, descriptions, secondary info
 *  label*     14/12/12 sp  Medium    buttons, inputs, navigation, chips
 *
 * Line heights sit around 1.3x for headings and 1.5x for body text. Poppins is a wide geometric
 * face, so large sizes get slight negative tracking and small labels a touch of positive tracking.
 * All sizes are in sp so they follow the system font-scale setting.
 */
fun forensimTypography(family: FontFamily = Poppins): Typography = with(family) {
    Typography(
        displayLarge = style(28, 36, Weights.SemiBold, (-0.02).em),
        displayMedium = style(28, 36, Weights.Medium, (-0.02).em),
        displaySmall = style(28, 36, Weights.Medium, (-0.02).em),
        headlineLarge = style(24, 32, Weights.Medium, (-0.015).em),
        headlineMedium = style(20, 28, Weights.Medium, (-0.01).em),
        headlineSmall = style(18, 26, Weights.Medium, (-0.005).em),
        titleLarge = style(18, 26, Weights.Medium, (-0.005).em),
        titleMedium = style(16, 24, Weights.Medium, 0.em),
        titleSmall = style(14, 20, Weights.Medium, 0.em),
        bodyLarge = style(16, 24, Weights.Regular, 0.em),
        bodyMedium = style(14, 21, Weights.Regular, 0.em),
        bodySmall = style(12, 18, Weights.Regular, 0.005.em),
        labelLarge = style(14, 20, Weights.Medium, 0.005.em),
        labelMedium = style(12, 16, Weights.Medium, 0.01.em),
        labelSmall = style(12, 16, Weights.Medium, 0.01.em),
    )
}

val ForensimTypography = forensimTypography()

/** Tabular (fixed-width) digits, so numbers in stats, scores and codes don't jiggle or misalign. */
const val TabularNumbers = "tnum"

/** Large-button label: Medium 16 sp (labelLarge at body size), for full-width primary actions. */
val Typography.buttonLarge: TextStyle
    get() = labelLarge.copy(fontSize = 16.sp, lineHeight = 24.sp)
