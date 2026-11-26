<p align="center">
  <img src="docs/images/forensim-logo.png" width="160" alt="ForenSim logo">
</p>

<h1 align="center">ForenSim</h1>

<p align="center">
  <b>Forensic science learning for criminology students.</b><br>
  Lessons, quizzes, 3D crime-scene simulations and an AR evidence lab, in one Android app.
</p>

<p align="center">
  <a href="https://github.com/Layfate/Forensim/releases/latest"><img src="https://img.shields.io/github/v/release/Layfate/Forensim?label=release&color=7a2335" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin and Jetpack Compose">
  <img src="https://img.shields.io/badge/backend-Supabase-3FCF8E?logo=supabase&logoColor=white" alt="Supabase">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue" alt="MIT License"></a>
</p>

<p align="center">
  <a href="https://github.com/Layfate/Forensim/releases/latest"><b>Download the latest APK</b></a>
  &nbsp;·&nbsp;
  <a href="#features">Features</a>
  &nbsp;·&nbsp;
  <a href="#build-from-source">Build from source</a>
  &nbsp;·&nbsp;
  <a href="docs/backend-setup.md">Backend setup</a>
  &nbsp;·&nbsp;
  <a href="https://layfate.github.io/Forensim/legal/privacy-policy.html">Privacy</a>
</p>

---

## About

ForenSim turns forensic science coursework into hands-on practice. Professors run their subject
classes, publish lessons and quizzes, and generate crime-scene cases; students study, test
themselves, and process those scenes in 3D before deciding what happened. An evidence lab lets
everyone inspect evidence up close, including in augmented reality through the phone camera.

<!-- Screenshots: add docs/images/screenshots/*.png here. -->

## Features

### For students
- **Subject classes:** join with a class code from your professor; lessons, PDF modules and key terms unlock as your professor opens them.
- **Quizzes:** graded quizzes (timed, server-graded) and practice quizzes with instant feedback.
- **Every day:** a term, a quiz and a piece of evidence of the day, with a streak that follows your account.
- **3D crime scenes:** walk the scene, sign the entry log, document, collect and package each item with the right tool, then report your conclusion. Unfinished cases resume where you left off.
- **AI tutor:** answers grounded in your own lessons; paused during graded work.
- **Report and block:** report a chat message, a classmate or an AI answer, and block someone so their messages are hidden.

### For professors
- **Case generator:** choose the case type, room, 2–7 evidence items, red herrings and an optional victim; ForenSim builds a solvable scene and gives a code and QR to share. Case briefings are written to match the scene without giving the answer away.
- **Class management:** class codes, join approval, roster, class chat, visibility of each lesson and quiz.
- **Assessment:** graded or practice cases and quizzes, feedback release, insights and CSV export.
- **AI quiz drafts** from a topic or an uploaded PDF, reviewed before publishing.
- **Moderation:** remove messages in the class chat and review reports from your classes.

### For administrators
- **Accounts:** add, suspend, reactivate or delete accounts and change roles; every change is written to an audit log.
- **Reported content:** review reports from class chats and the AI tools, remove messages and close reports.
- **Platform:** usage statistics, course and class overview, AI tutor daily limit and a notice shown to every student.

### Evidence lab and AR
- **46 specimens in 7 categories:** ballistics, prints and impressions, biological and trace, weapons, tools and restraints, drugs, documents and belongings.
- **3D viewer** with identifying features and the correct collection and packaging for each item.
- **Augmented reality**, chosen automatically for each phone:

  | Phone | Experience |
  |---|---|
  | ARCore-supported | Full AR: place a specimen life-size on a real table and walk around it |
  | Camera, no ARCore | **Marker AR** (OpenCV): print the marker from the app and the specimen stands on it at true size; otherwise a gyroscope camera view |
  | No camera or sensors | 3D viewer |

- Swipe between specimens, and tap the yellow **INSPECT** tag for the evidence file.

## Download

Get **`Forensim-x.y.z.apk`** from the [latest release](https://github.com/Layfate/Forensim/releases/latest)
and open it on an Android phone (Android 8.0 or newer). GitHub shows the APK's SHA-256 digest next
to the download so you can check the file.

## Tech stack

| Area | Technology |
|---|---|
| App | Kotlin, Jetpack Compose, Material 3, Navigation, WorkManager, DataStore |
| 3D | Custom OpenGL ES 3 renderer with `.glb` models (rooms, furniture, evidence, victims) |
| AR | Google ARCore · CameraX · OpenCV (ArUco markers) · rotation-vector sensors |
| Backend | Supabase: Postgres with row-level security, Auth, Storage, Edge Functions (Deno) |
| AI | OpenRouter free models with automatic fallback (tutor and quiz drafts) |

## Build from source

Requirements: Android Studio (recent), JDK 17+, Android SDK 37.

```bash
git clone https://github.com/Layfate/Forensim.git
cd Forensim
./gradlew :app:assembleDebug        # debug APK: app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # unit tests
```

The app points at the project's Supabase instance with its public (publishable) key. To run your
own backend, apply `supabase/migrations/`, deploy `supabase/functions/`, and change the two
`buildConfigField` values in `app/build.gradle.kts`; see **[docs/backend-setup.md](docs/backend-setup.md)**.

Release builds are signed with an upload key kept outside the repository; without it they fall
back to the debug key. Details are in the backend guide.

Backend tests (scoring, AI model fallback and live row-level-security checks) are listed in the
[backend guide](docs/backend-setup.md#tests).

## Project structure

```
app/src/main/java/com/forensim/app/
  core/        design system, Supabase client, repositories, models
  engine/      3D scenes, scene generator, physics, renderer (pure Kotlin, unit tested)
  feature/     auth, student, professor, admin, lms, quiz, simulation, ar, tutor, settings
supabase/
  migrations/  schema, row-level security, trusted RPCs, seed curriculum
  functions/   submit-simulation (scoring), ai-tutor, ai-quiz, admin-users, delete-account
docs/          backend setup, design system, Play Store and legal pages, AR marker
art/           Blender sources and scripts for the 3D models
```

## Security and privacy

- Row-level security on every table; roles are server-side and audited.
- Answer keys and scoring rubrics live in a schema the API never exposes; the server does all grading.
- The camera is used only on the phone for AR; nothing is recorded or uploaded.
- Only the Supabase publishable key ships in the app; secrets stay in Supabase.
- Users can delete their account in the app (Profile > Settings > Account) or [on the web](https://layfate.github.io/Forensim/legal/delete-account.html).
- [Privacy Policy](https://layfate.github.io/Forensim/legal/privacy-policy.html) · [Terms of Use](https://layfate.github.io/Forensim/legal/terms-of-use.html)

## Project status

ForenSim is in active development. Lesson content is a draft under instructor review. Issues and
suggestions are welcome via [GitHub Issues](https://github.com/Layfate/Forensim/issues).

## Reporting a vulnerability

Please don't open a public issue for security problems. Use
[private vulnerability reporting](https://github.com/Layfate/Forensim/security/advisories/new)
instead; see [SECURITY.md](SECURITY.md).

## License

ForenSim is released under the [MIT License](LICENSE). Copyright (c) 2026 Layfate.
