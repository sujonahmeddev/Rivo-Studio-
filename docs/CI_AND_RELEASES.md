# GitHub Actions: APK builds and release signing

## Debug APK (no secrets or local build setup)

The workflow is [`.github/workflows/android-apk.yml`](../.github/workflows/android-apk.yml). It runs on every branch push, pull requests targeting `main`, and manual dispatch. In particular, every push to the repository's primary `main` branch builds automatically. It uses the committed Gradle wrapper and the Android application module actually declared by `settings.gradle.kts` (`:app`).

The job uses a pinned `ubuntu-24.04` runner, JDK 17, Gradle 8.11.1 through `gradlew`, Android SDK platform/build tools 35, and Python 3.13 for Chaquopy. `gradle/actions/setup-gradle` restores Gradle dependency caches when available (pull-request cache use is read-only). The job validates Gradle configuration, tests the Python bridge, runs `:app:testDebugUnitTest` and `:app:lintDebug`, then runs `:app:assembleDebug`.

After all checks succeed, the workflow discovers `.apk` files under `app/build/outputs/apk/debug`, copies them together with `BUILD-INFO.txt`, and uploads the **RivoStudio-debug-APK** artifact for 30 days. The metadata contains the commit SHA and APK SHA-256. To download from a phone, open the repository's **Actions** tab, open the successful **Android APK** run for your commit, and download the artifact in the run's **Artifacts** section.

The debug APK is a test build using the Android debug signing key. It is not a production/Play Store release. The workflow does not need signing secrets and uploads no APK for failed or cancelled builds.

## Failure reports, retries, and safe permissions

A failing build, test, lint, SDK setup, or APK collection step leaves the Actions run failed. The workflow summary shows the commit, ref, result, failed step context, and how to find diagnostics. When Gradle emits recognized compiler, dependency, lint, or test errors, up to 25 matching lines are also exposed as check annotations. Gradle output is retained in the run logs and bundled with the extracted failure report in the **RivoStudio-build-diagnostics** artifact for 14 days.

The pipeline does **not** change project files, change dependency versions, commit, or push in response to a failure. Automatic source repair is intentionally disabled: this repository has no configured code-repair service or narrowly scoped write credential, and allowing generated edits to self-commit would be unsafe. There is no automatic retry loop. Inspect the real error, make a reviewed correction, and push it; the next run performs a clean, bounded build. Debug builds cancel superseded runs on the same ref; release runs are serialized without cancellation. Workflows have read-only repository permissions and third-party Actions are pinned to commit SHAs.

If a failure happens before Gradle starts, the report may not include a Gradle error section; the Actions step that failed and its platform log remain the source of truth. No failed run is reported as successful, and debug/release artifacts are uploaded only after their respective build and verification steps succeed.

## Signed release APK (optional)

Signed releases use the separate manual workflow [`.github/workflows/release-apk.yml`](../.github/workflows/release-apk.yml). It never uses the debug key. It checks for all required secrets, runs release tests/lint and `:app:assembleRelease`, verifies every discovered release APK with Android `apksigner`, and only then uploads **RivoStudio-signed-release-APK** for 90 days. It does not publish a GitHub Release or create a falsely labelled unsigned release APK.

If signing secrets are missing, the release workflow intentionally skips the release build and writes that status into the run summary; debug builds continue to work. A signed release requires one-time preparation of a private Android signing keystore and four repository or protected-environment secrets:

| GitHub Actions secret | Value |
| --- | --- |
| `RIVO_RELEASE_KEYSTORE_BASE64` | Base64-encoded contents of the private `.jks`/`.keystore` file |
| `RIVO_RELEASE_STORE_PASSWORD` | Keystore password |
| `RIVO_RELEASE_KEY_ALIAS` | Alias of the signing key inside the keystore |
| `RIVO_RELEASE_KEY_PASSWORD` | Password for that key |

Add these under **Repository Settings → Secrets and variables → Actions** (or a protected GitHub Environment). Do not put the keystore, base64 value, passwords, API keys, or signing properties in Git, workflow YAML, build logs, or an APK. The workflow decodes the keystore only into the hosted runner's temporary directory, passes the other values to Gradle through environment variables, verifies the APK signature, and removes the temporary keystore when the job ends.

If you already have a production keystore, keep a secure backup before configuring it. If you do not have one, creating a durable signing identity is a one-time security-sensitive setup step; use a trusted keystore tool and store its backup securely. A release key cannot be recovered from GitHub after it is lost, and replacing it may prevent upgrades for users of an app signed with the original key. The debug workflow requires none of this setup.

The Android Gradle module reads release signing data only from `RIVO_RELEASE_STORE_FILE`, `RIVO_RELEASE_STORE_PASSWORD`, `RIVO_RELEASE_KEY_ALIAS`, and `RIVO_RELEASE_KEY_PASSWORD` environment variables. Gradle release artifact tasks fail if a complete signing configuration is absent. The application ID and debug variant are unchanged.

## One-time repository requirements

- GitHub Actions must be enabled for the repository. The workflows require no personal token, repository write permission, external AI service, or paid Actions feature.
- The workflow files must be present on the repository's default branch for manual dispatch to be available. Normal builds begin on a push or pull request once the workflow is on the relevant ref.
- No one-time setup is needed for debug APK builds. Release signing secrets are optional until a signed release is wanted.
