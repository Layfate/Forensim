package com.forensim.app.feature.legal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.WorkspaceList

/**
 * Single place for the facts the legal pages depend on. Update these (and the text below) together
 * with the Play Console listing whenever data practices change.
 */
object LegalInfo {
    const val APP = "Forensim"
    const val CONTACT_EMAIL = "elorde.princeart6881@gmail.com"
    const val EFFECTIVE_DATE = "November 24, 2026"
}

enum class LegalDocument { TERMS, PRIVACY }

private class LegalSection(val heading: String, val body: List<String>)

private val e = LegalInfo.CONTACT_EMAIL

private val TERMS = listOf(
    LegalSection("1. Agreement", listOf(
        "These Terms of Use govern your use of the ${LegalInfo.APP} mobile application and its online services (together, \"${LegalInfo.APP}\"). By creating an account, signing in or using ${LegalInfo.APP}, you agree to these Terms and to our Privacy Policy. If you do not agree, do not use ${LegalInfo.APP}.",
    )),
    LegalSection("2. Who may use Forensim", listOf(
        "${LegalInfo.APP} is an educational tool for forensic science students, professors and school administrators. You must be at least 13 years old, and old enough under the law where you live to agree to these Terms (or have a parent or guardian agree on your behalf).",
        "You must give accurate account information, keep your password confidential and not share your account. You are responsible for activity under your account. Tell us right away at $e if you believe your account has been used without permission.",
    )),
    LegalSection("3. Training simulation only", listOf(
        "${LegalInfo.APP} is a training simulation, not a validated investigative instrument. Lessons, 3D crime scenes, AR models, quizzes and AI tutor answers are for learning only. They are not legal, medical or professional forensic advice, and must not be used to process real evidence or make real-world decisions.",
    )),
    LegalSection("4. Academic integrity", listOf(
        "Graded quizzes and assessment cases are scored on our servers. Your professor can see your attempts, answers, scores, timing and whether work was submitted late. While a graded attempt is open, the AI tutor and hints are locked.",
        "You agree not to share or publish quiz or case answers, use another person's account, take an assessment for someone else, or try to get around time limits, attempt limits or locks.",
    )),
    LegalSection("5. Acceptable use", listOf(
        "Do not: break the law using ${LegalInfo.APP}; upload unlawful, offensive or infringing content (including profile photos); harass other users; try to access data or features you are not authorised to use; probe, scan or attack our systems; reverse engineer the app except where the law allows; or use automated tools to access ${LegalInfo.APP}.",
    )),
    LegalSection("6. AI tutor", listOf(
        "The AI tutor uses a third-party AI model to answer questions about your lessons. AI answers can be incomplete or wrong; check important points against your lessons and your professor. Daily usage limits apply, and requests that are unsafe or unrelated to your studies may be refused.",
    )),
    LegalSection("7. Content and intellectual property", listOf(
        "${LegalInfo.APP}, its design, software, lessons, simulations and quizzes are protected by intellectual property laws. We give you a personal, non-transferable, revocable licence to use them for your own studies while you have an account. Third-party images, videos and fonts remain the property of their owners and are used under their licences, credited in the app where required.",
        "Content you submit (such as a profile photo or, for professors, uploaded lessons and quizzes) stays yours. You give us permission to store, display and process it only to run ${LegalInfo.APP} for you and your classes.",
    )),
    LegalSection("8. Third-party services", listOf(
        "Some features rely on third parties, such as Google or Facebook sign-in and YouTube videos inside lessons. Your use of those features is also subject to those providers' terms and privacy policies.",
    )),
    LegalSection("9. Changes, availability and termination", listOf(
        "We may update, suspend or discontinue features at any time. Some content is saved on your device for offline use, but graded work requires a connection.",
        "School administrators may suspend or remove accounts that break these Terms or school policy. You may stop using ${LegalInfo.APP} at any time and delete your account in the app (Profile > Settings > Account > Delete account) or by request (see the Privacy Policy).",
    )),
    LegalSection("10. Disclaimers and limitation of liability", listOf(
        "${LegalInfo.APP} is provided \"as is\" and \"as available\", without warranties of any kind to the extent permitted by law. We do not guarantee that it will be uninterrupted, error-free or that content is complete or current.",
        "To the extent permitted by law, we are not liable for indirect, incidental or consequential damages, or for loss of data or grades, arising from your use of ${LegalInfo.APP}. Nothing in these Terms limits rights you have under applicable consumer protection law.",
    )),
    LegalSection("11. Governing law", listOf(
        "These Terms are governed by the laws of the Republic of the Philippines.",
    )),
    LegalSection("12. Changes to these Terms", listOf(
        "We may update these Terms. We will show the new effective date here and, for significant changes, notify you in the app. Continuing to use ${LegalInfo.APP} after a change means you accept the updated Terms.",
    )),
    LegalSection("13. Contact", listOf("Questions about these Terms: $e")),
)

private val PRIVACY = listOf(
    LegalSection("1. Overview", listOf(
        "This Privacy Policy explains what information ${LegalInfo.APP} collects, how we use and share it, and the choices and rights you have. We process personal information in line with the Data Privacy Act of 2012 (Republic Act No. 10173) of the Philippines and other applicable laws.",
    )),
    LegalSection("2. Information we collect", listOf(
        "Account information: your name, email address, password (stored only as a secure hash by our authentication provider) and your role (student, professor or administrator).",
        "Profile information you choose to add: gender, student ID number and a profile photo.",
        "Learning activity: classes you join or create, lesson progress, quiz answers, scores and timing, actions and scores in 3D case simulations, and your answers to the daily quiz and evidence challenges on the home screen (used to show your daily streak).",
        "AI tutor: the questions you type are sent for processing to answer you (see section 4). We keep a usage record for each request (time, type, whether it was blocked and the amount of text processed) to enforce daily limits. We do not store the text of your questions or the tutor's answers on our servers.",
        "Sign-in with Google or Facebook: if you choose it, we receive your name and email address from that provider.",
        "On your device only: your app settings (theme, language, controls, sound, notifications), your onboarding answers, saved lessons for offline use and a copy of your profile photo. If you allow notifications, a daily study reminder is created on your device; no notification service receives your data. The app also checks your connection status and whether your device has a gyroscope or camera to enable features; these checks stay on your device.",
    )),
    LegalSection("3. What we do not collect", listOf(
        "${LegalInfo.APP} does not access your location, contacts, microphone or camera, does not show ads, does not use third-party analytics or advertising trackers, and does not sell your personal information.",
    )),
    LegalSection("4. How we use and share information", listOf(
        "We use your information to create and secure your account, deliver lessons, quizzes and simulations, grade work, show progress to you and your professors, run the AI tutor, keep the service working and prevent misuse.",
        "Professors can see the progress, attempts, answers and scores of students in the classes they teach. School administrators can manage accounts and see audit records of administrative actions.",
        "Service providers who process data on our behalf: Supabase (database, sign-in and file storage hosting); Anthropic (the AI model behind the tutor), which receives your question, the recent conversation and relevant lesson excerpts (and, when you ask about your scores or progress, a short summary of your recent results); Google and Meta when you use their sign-in; and Google/YouTube when you play a lesson video (videos load only after you tap play).",
        "We may also disclose information if required by law, or to protect the rights, safety and security of our users and service.",
    )),
    LegalSection("5. Storage and security", listOf(
        "Data is sent over encrypted connections (HTTPS) and stored with access controls, so users can only reach data their role allows. Profile photos are kept in private storage. No method of storage or transmission is completely secure, but we work to protect your information.",
    )),
    LegalSection("6. Retention", listOf(
        "We keep your information while your account is active and as long as needed for your school's academic records.",
        "When an account is deleted, its profile, photo, class memberships, lesson progress, quiz and case attempts and scores, chat messages, daily quiz answers and AI tutor usage records are deleted immediately. Classes, lessons and cases the account created for other students are kept without the author's identity, so those students keep their coursework. Audit records of administrative actions are kept for accountability with the account's identity removed, except where an administrator removed the account, in which case the record keeps the removed account's email address. Backups held by our hosting provider are overwritten on their normal schedule.",
    )),
    LegalSection("7. Your rights and choices", listOf(
        "Under the Data Privacy Act you may ask to access, correct or delete your personal information, object to its processing, or receive a copy of it. You can edit your name, gender, student ID and photo in the app at any time. You may also file a complaint with the National Privacy Commission of the Philippines.",
        "You can delete your account yourself at any time in the app: Profile > Settings > Account > Delete account. If you can't use the app, use the account deletion page on our website or email $e from the address on your account; we will confirm your identity and delete the account within 30 days.",
        "To exercise your other rights, email $e from the address on your account. We will confirm your identity and respond within a reasonable time.",
    )),
    LegalSection("8. Children", listOf(
        "${LegalInfo.APP} is intended for students at least 13 years old, typically in college-level forensic science programmes. We do not knowingly collect information from children under 13. If you believe a child has given us information, contact us and we will delete it.",
    )),
    LegalSection("9. Changes to this policy", listOf(
        "We may update this Privacy Policy. We will show the new effective date here and, for significant changes, notify you in the app.",
    )),
    LegalSection("10. Contact", listOf("Privacy questions and requests: $e")),
)

/** Full-screen Terms of Use / Privacy Policy. Available signed out (from sign-in) and in Settings. */
@Composable
fun LegalScreen(document: LegalDocument, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val title = if (document == LegalDocument.TERMS) "Terms of Use" else "Privacy Policy"
    val sections = if (document == LegalDocument.TERMS) TERMS else PRIVACY
    ScreenScaffold(title = title, onBack = onBack) { padding ->
        WorkspaceList(Modifier.padding(padding)) {
            item("effective") {
                Text("Effective ${LegalInfo.EFFECTIVE_DATE}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(sections, key = { it.heading }) { s ->
                Spacer(Modifier.height(Spacing.sm))
                Text(s.heading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                s.body.forEach { p ->
                    Spacer(Modifier.height(Spacing.xs + 2.dp))
                    Text(p, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item("end") { Spacer(Modifier.height(Spacing.lg)) }
        }
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
