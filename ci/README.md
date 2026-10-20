# Enabling the APK build (one-time, 30 seconds)

Arena's GitHub token can't create Actions workflows, so this file is committed
outside `.github/workflows/` on purpose.

1. On GitHub (repo `Maturity-Ledger`, branch `arena/53d0e5b3-maturity-ledger`):
   **Add file → Create new file**
2. Name it exactly: `.github/workflows/build-apk.yml`
3. Paste the contents of `ci/build-apk.yml` (in this folder).
4. Commit to `arena/53d0e5b3-maturity-ledger`. Then run:
   **Actions → Build APK → Run workflow** (on that branch).

It runs the unit tests, builds `app-debug.apk`, and uploads it as the
`AFEC-debug-apk` artifact. Tell Arena when it's enabled and it will trigger
the run, fix any compile errors until green, download the APK, and commit
it to Git under `apk/`.
