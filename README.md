# Forensim

AI-assisted forensic learning for criminology students: an LMS, a playable first-person 3D crime
scene, a 3D evidence viewer (AR fallback) and a policy-gated AI tutor. Android (Kotlin, Jetpack
Compose, Material 3) on Supabase. Blueprint: [`plan/FORENSIM_IMPLEMENTATION_PLAN.md`](plan/FORENSIM_IMPLEMENTATION_PLAN.md).

## What is implemented

| Plan area | Status |
|---|---|
| Phase 1 foundation: auth, server-resolved roles, role navigation, RLS, settings | Done |
| Phase 2 LMS: 7 modules / 14 lessons, progress, join-by-code, offline lesson cache, publishing | Done |
| Phase 3 secure assessment: server-held keys, timed attempts, server grading, review policies, audit log | Done |
| Phase 4 playable 3D case: joystick, touch look, optional gyro, collisions, reticle, tools, evidence states, chain of custody, tutorial, event log | Done (`.glb` rooms, furniture, evidence and victim bodies; see `art/README.md`) |
| Offline practice sync (plan section 9) | Done: training parts, practice case reports and case progress made offline are kept per user on the phone (`core/data/PracticeSync.kt`) and sent when the server is reachable again (app start / sign-in, Simulate tab, WorkManager job on network). Practice cases opened online once can be replayed offline. Graded cases still need a connection |
| Phase 5 procedural scenes: seeded generator, spawn validation, reachability, server manifest, rubric scoring | Done (3 templates) |
| Phase 6 hybrid AR | 3D viewer fallback only; marker AR and ARCore not started |
| Phase 7 AI tutor | Edge Function done (OpenRouter free models with automatic fallback, retrieval-grounded, assessment lock, quota); needs `OPENROUTER_API_KEY` secret |
| Professor tools | Tabs: Home (AI assistant, upload PDF modules, quiz builder, module/lesson/quiz visibility, insights, CSV export), Classes (class code, roster, case feedback release), Lab (3D scenes + AR viewer), Profile. Professors publish directly; no admin approval |
| Admin tools | Create/delete accounts (`admin-users` Edge Function), roles/suspension (audited), catalog, platform stats, audit log, AI limit, system notice |

All lesson content is a **draft marked `pending_review`**. A qualified instructor must review it.

## Project layout

```
app/src/main/java/com/forensim/app/
  core/designsystem   theme tokens + shared components (docs/design-system.md)
  core/network        minimal Supabase client (GoTrue, PostgREST, Edge Functions) on OkHttp
  core/data           repositories, DataStore settings/session, per-user offline cache
  core/model          wire models mirroring the schema
  engine/             renderer-independent gameplay (pure Kotlin, unit tested)
    scene/            ScenarioTemplate + seeded SceneGenerator
    physics/          CollisionWorld + PlayerController
    interaction/      tools
    controls/         GyroController (rotation-vector, no drift)
    renderer/         OpenGL ES 3 renderer (swap point for Filament/SceneView + .glb)
    SimulationGame.kt evidence state machine, targeting, coaching, event recorder
  feature/            auth, student, lms, quiz, tutor, simulation, ar, settings, professor, admin
supabase/
  migrations/         schema, RLS, trusted RPCs, seed curriculum, grants (applied to the project)
  functions/          submit-simulation (rubric scoring), ai-tutor and ai-quiz (OpenRouter free models, shared code in `_shared/openrouter.ts`), admin-users (create/delete accounts)
  seed/dev_users.sql  dev accounts template (no secrets)
  tests/              live RLS / RPC smoke test
```

## Backend (Supabase project `dadmvplgluqewozshtdz`)

Migrations and both Edge Functions are **already applied/deployed**. The app ships only the
publishable key (`app/build.gradle.kts`). Security model:

- RLS on every table, default deny. Roles live in `user_roles` and are changed only by the audited
  `admin_set_role` function.
- Answer keys (`private.answer_keys`) and simulation rubrics (`private.simulation_rubrics`) are in a
  schema the API does not expose. Graded questions are invisible until the student holds an attempt.
- Quiz attempts, saving answers, grading, replays and review release are `SECURITY DEFINER`
  functions; the client never submits a score.
- Simulation scoring happens in the `submit-simulation` Edge Function against the private rubric.
- The AI tutor retrieves only lessons the caller can already read, refuses while any graded
  attempt is open (enforced server-side), and logs usage for quotas.

### Remaining manual steps

00000000. **Fix "Could not choose the best candidate function … professor_create_case"** (not yet applied):
   the database has both the old 9-argument and the new 11-argument `professor_create_case`, because
   20261127 ran without 20261126. SQL editor: run these two files **in this order**:
   1. `supabase/migrations/20261126000001_case_resume_and_victim.sql` (drops the old version, adds the victim
      columns the new version writes, plus resumable cases);
   2. `supabase/migrations/20261128000001_five_red_herrings.sql` (puts back the current function body, which
      step 1 replaced with an older one, and raises the red-herring limit from 3 to 5).
   Both are safe to run again.

0000000. **Delete quizzes** (not yet applied): SQL editor: run
   `supabase/migrations/20261129000001_delete_quizzes.sql` (`professor_delete_quiz`, `professor_set_quiz_hidden`,
   table `class_hidden_quizzes`). Until it runs, press-and-hold → Delete on a quiz puts the quiz back and shows
   "Deleting quizzes needs a server update first."

000000. **Up to 5 red herrings** (not yet applied): SQL editor: run
   `supabase/migrations/20261128000001_five_red_herrings.sql` (`professor_create_case` allows 5 red herrings
   instead of 3; the app offers 0-5). Until it runs, creating a case with 4 or 5 red herrings fails with "A case
   can have at most 3 red herrings"; cases with 0-3 still work.

00000. **More evidence + red herrings** (applied):
   - SQL editor: run `supabase/migrations/20261127000001_more_evidence_and_red_herrings.sql` (20 new evidence types
     in the case pools, `private.red_herring_types()`, up to 7 evidence + 3 red herrings per case).
   - Edge Functions: redeploy `submit-simulation` (rubric for the new types; red herrings are never scored for
     procedure, and citing one as proof costs a point).
   Until both are done, creating a case with red herrings or new evidence types fails with "Evidence does not
   match the case type", and the new types would score without a rubric.

0000. **Resumable cases + victims** (not yet applied): SQL editor: run
   `supabase/migrations/20261126000001_case_resume_and_victim.sql` (victim columns on `case_sessions`,
   saved progress on `simulation_attempts`, `my_cases`, `save_case_progress`, updated `start_case` and
   `professor_create_case`). Until it runs, Simulate falls back to the old history list, practice cases
   start fresh each time, and creating a case *with a victim* fails (cases without one still work).

000. **Professor publishing + admin accounts** (not yet applied):
   - SQL editor: run `supabase/migrations/20261122000006_professor_publishing.sql` (PDF bucket `lesson-pdfs`, storage
     policies, `professor_*` RPCs, `service_audit`).
   - Edge Functions: deploy `supabase/functions/admin-users/index.ts` as `admin-users` (JWT verification on).

0. **Google / Facebook sign-in** (the app side is done: Custom Tabs + PKCE, redirect
   `com.forensim.app://login-callback`):
   - Supabase → Authentication → URL Configuration → *Redirect URLs*: add `com.forensim.app://login-callback`.
   - Google Cloud Console → create an OAuth client (Web application) with the authorised redirect URI
     `https://dadmvplgluqewozshtdz.supabase.co/auth/v1/callback`; paste its client ID/secret into
     Supabase → Authentication → Providers → Google, and enable it.
   - Meta for Developers → create an app with Facebook Login, same callback URL; paste the app ID/secret into
     Providers → Facebook, and enable it.
   Until a provider is enabled, its button explains that it is not switched on yet.
00. **Password policy on the server**: Authentication → Providers → Email → minimum length **10** and
   required characters **lowercase, uppercase, digits and symbols**, matching the app's sign-up checklist
   (`core/auth/PasswordPolicy.kt`).

1. **AI key** (tutor + quiz drafts): Supabase dashboard → Edge Functions → Secrets → add `OPENROUTER_API_KEY`.
   Only free OpenRouter models are used; when one is busy, rate-limited or down the next free one answers.
   Optional `OPENROUTER_MODELS` (comma-separated `:free` ids) puts your favorite models first. Without a key,
   or when every free model fails, the tutor answers with lesson excerpts.
   Deploy both functions together, since they share `_shared/openrouter.ts`:
   `supabase functions deploy ai-tutor ai-quiz`.
2. **Auth hardening**: enable leaked-password protection (Auth → Password security).
3. **Dev accounts**: `student@`, `professor@`, `admin@forensim.test` exist for testing; passwords
   are in the git-ignored `supabase/seed/DEV_ACCOUNTS.local.md`. Delete or rotate them before release.
4. New sign-ups are students. If e-mail confirmation is on (Supabase default), users confirm by
   e-mail before signing in.

## Build and test

```bash
./gradlew :app:assembleDebug          # APK
./gradlew :app:testDebugUnitTest      # engine tests (generator determinism, collisions, procedure)
node --test supabase/functions/submit-simulation/scoring.test.ts   # rubric scoring tests
python -I supabase/tests/rls_smoke_test.py   # live RLS/RPC checks (uses one graded attempt)
```

Student join code for the seed class: `FSCI-3A26`.

## Known gaps / next steps

- Camera marker AR (CameraX + OpenCV ArUco) and ARCore path (Phase 6).
- Hilt (manual `AppContainer` today), CI, release signing, Play Data Safety materials.
- On-device performance profiling on low/mid-range hardware.
