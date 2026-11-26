# Backend setup and maintenance

ForenSim runs on one Supabase project. The app ships only the project URL and its **publishable**
key (`app/build.gradle.kts`); the service-role key and AI provider keys live only in Supabase
(Edge Function secrets). This page is for maintainers.

## Applying changes

- **Database:** migrations are in `supabase/migrations/` and are applied in order
  (`supabase db push`, or paste a file into the SQL editor). Each is safe to run again.
- **Edge Functions:** `supabase functions deploy <name>`. `ai-tutor` and `ai-quiz` share
  `supabase/functions/_shared/openrouter.ts`, so deploy them together:
  `supabase functions deploy ai-tutor ai-quiz`.

## Settings and secrets

| What | Where | Notes |
|---|---|---|
| `OPENROUTER_API_KEY` | Edge Functions → Secrets | AI tutor and AI quiz drafts. Only free OpenRouter models are used; a busy or failing model falls back to the next. Optional `OPENROUTER_MODELS` (comma-separated `:free` ids) puts preferred models first. Without a key the tutor answers with lesson excerpts. |
| Password policy | Authentication → Providers → Email | Minimum length **10**; lowercase, uppercase, digits and symbols, matching the app's sign-up checklist (`core/auth/PasswordPolicy.kt`). |
| Leaked-password protection | Authentication → Password security | Enable. |
| Redirect URL | Authentication → URL Configuration | Add `com.forensim.app://login-callback` for Google / Facebook sign-in. |
| Google sign-in | Google Cloud Console + Authentication → Providers → Google | OAuth client (Web application) with redirect URI `https://<project>.supabase.co/auth/v1/callback`. |
| Facebook sign-in | Meta for Developers + Authentication → Providers → Facebook | Same callback URL. Until a provider is enabled, its button says it isn't switched on yet. |
| E-mail confirmation | Authentication | New sign-ups are students; with confirmation on (default) they confirm by e-mail first. |

## Development accounts

`student@`, `professor@` and `admin@forensim.test` exist for testing. Their passwords are in the
git-ignored `supabase/seed/DEV_ACCOUNTS.local.md` (template: `supabase/seed/dev_users.sql`).
Delete or rotate them before a public launch.

## Migration notes

Some migrations depend on earlier ones being applied first:

1. **`professor_create_case` ambiguity** ("Could not choose the best candidate function"): run
   `20261126000001_case_resume_and_victim.sql`, then `20261128000001_five_red_herrings.sql`, in that order.
2. **Deleting quizzes** needs `20261129000001_delete_quizzes.sql`; until then the app explains that
   deleting needs a server update.
3. **Up to 5 red herrings** needs `20261128000001_five_red_herrings.sql` (before it, the limit is 3).
4. **More evidence types and red herrings** need `20261127000001_more_evidence_and_red_herrings.sql`
   *and* a redeploy of `submit-simulation`.
5. **Resumable cases and victims** need `20261126000001_case_resume_and_victim.sql`.
6. **Professor publishing and admin accounts** need `20261122000006_professor_publishing.sql` and the
   `admin-users` Edge Function (JWT verification on).
7. **Class code on student cards** needs `20261203000001_student_class_code.sql`.

## Security model

- Row-level security on every table, default deny. Roles live in `user_roles` and change only through
  the audited `admin_set_role` function.
- Answer keys (`private.answer_keys`) and simulation rubrics (`private.simulation_rubrics`) are in a
  schema the API doesn't expose. Graded questions are invisible until the student holds an attempt.
- Starting quiz attempts, saving answers, grading, replays and review release are `SECURITY DEFINER`
  functions; the client never submits a score.
- Simulation scoring runs in the `submit-simulation` Edge Function against the private rubric.
- The AI tutor retrieves only lessons the caller can already read, refuses while a graded attempt is
  open (enforced server-side), and logs usage for daily quotas.

## Tests

```bash
./gradlew :app:testDebugUnitTest                                   # app and engine unit tests
deno test --allow-env supabase/functions/_shared/openrouter.test.ts # AI model fallback
node --test supabase/functions/submit-simulation/scoring.test.ts    # rubric scoring
python -I supabase/tests/rls_smoke_test.py                          # live RLS / RPC checks
```

## Release signing

Release builds are signed with the upload key kept **outside** the repository. The build reads
`keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) from
`~/Documents/Forensimkeys/`, or from the path in `FORENSIM_KEYSTORE_PROPERTIES`. Without it, release
builds fall back to the debug key for local testing.

```bash
./gradlew :app:assembleRelease   # signed APK  (GitHub Releases)
./gradlew :app:bundleRelease     # signed AAB  (Google Play)
```
