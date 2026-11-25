# Security policy

## Supported versions

Only the [latest release](https://github.com/Layfate/Forensim/releases/latest) receives security fixes.

## Reporting a vulnerability

Please **do not** open a public issue, pull request or discussion for a security problem.

Report it privately through
[GitHub private vulnerability reporting](https://github.com/Layfate/Forensim/security/advisories/new).
Include the app version, the steps to reproduce and what an attacker could do. You should get a
reply within 7 days.

Examples of what to report:

- Reading or changing another user's data (row-level security bypass)
- Getting quiz answer keys or simulation rubrics before grading
- Raising your own role (student to professor or admin)
- Secrets or keys exposed in the app or this repository

The Supabase **publishable** key in `app/build.gradle.kts` is public by design and is not a
vulnerability on its own.

## Verifying a download

Install Forensim only from this repository's Releases page. Before installing, compare the APK's
SHA-256 with the digest GitHub shows next to the file, and check that the signing certificate
matches the one in the release notes:

```bash
sha256sum Forensim-x.y.z.apk
apksigner verify --print-certs Forensim-x.y.z.apk
```
