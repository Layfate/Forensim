# Publishing checklist (Play Store + GitHub Releases)

1. **Host the legal pages** (`docs/legal/*.html`) at a public URL, e.g. GitHub Pages from this
   repo's `docs/` folder. You'll get:
   - Privacy policy: `https://<user>.github.io/<repo>/legal/privacy-policy.html`
   - Terms: `.../legal/terms-of-use.html`
   - Account deletion: `.../legal/delete-account.html`
   After editing `LegalScreens.kt`, run `python docs/legal/build_legal_html.py` to regenerate.
2. **Upload key**: create it in `C:\Users\corti\Documents\Forensimkeys` (never in the repo), point
   the release `signingConfig` at it, and enrol in Play App Signing.
3. **Version**: raise `versionCode` (and `versionName`) in `app/build.gradle.kts` for every upload.
4. **Build**: `./gradlew :app:bundleRelease` (AAB for Play) and `:app:assembleRelease` (APK for
   GitHub Releases).
5. **Play Console > App content**: fill in from [`data-safety.md`](data-safety.md).
6. **Store listing**: text and graphics from [`store-listing.md`](store-listing.md).
7. **App access**: give reviewers a student test account and password.
8. Start with an **internal testing** track, then closed testing (new personal developer
   accounts need 12+ testers for 14 days before production).
