# Publishing checklist (Play Store + GitHub Releases)

1. **Legal pages** are published by GitHub Pages from this repo's `docs/` folder:
   - Privacy policy: `https://layfate.github.io/Forensim/legal/privacy-policy.html`
   - Terms: `https://layfate.github.io/Forensim/legal/terms-of-use.html`
   - Account deletion: `https://layfate.github.io/Forensim/legal/delete-account.html`
   After editing `LegalScreens.kt`, run `python docs/legal/build_legal_html.py` to regenerate.
2. **Upload key**: already in `C:\Users\corti\Documents\Forensimkeys` (never in the repo); the
   release build signs with it. Enrol in Play App Signing. To let GitHub-APK users update from Play
   (and the reverse), choose to upload this key as the *app signing key* during setup instead of
   letting Google generate one; this choice is permanent.
3. **Version**: raise `versionCode` (and `versionName`) in `app/build.gradle.kts` for every upload.
4. **Build**: `./gradlew :app:bundleRelease` (AAB for Play) and `:app:assembleRelease` (APK for
   GitHub Releases).
5. **Play Console > App content**: fill in from [`data-safety.md`](data-safety.md).
6. **Store listing**: text and graphics from [`store-listing.md`](store-listing.md).
7. **App access**: give reviewers a student test account and password.
8. Start with an **internal testing** track, then closed testing (new personal developer
   accounts need 12+ testers for 14 days before production).
