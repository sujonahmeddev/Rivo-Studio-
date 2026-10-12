# Rivo Studio

Rivo Studio is a native Android coding workspace built with Kotlin and Jetpack Compose. Projects and editor data are local by default. The initial supported on-device runtime is CPython 3.13 embedded with Chaquopy; it is not a general Linux shell or an always-on hosting service.

> **Implementation status:** the project and automation are in active development. The GitHub Actions workflows build the actual `:app` Gradle module, but do not claim a successful Android build until a workflow run completes successfully. Check the run summary and artifacts for verified results.

## Get the APK from a phone

1. Push a change to a branch (the default `main` branch is always covered), or open a pull request targeting `main`.
2. In the repository, open **Actions → Android APK** and choose the run for your commit.
3. When the run is green, download **RivoStudio-debug-APK** from the run's **Artifacts** section. The artifact contains the debug APK and `BUILD-INFO.txt` with the commit and APK SHA-256.
4. Install the APK on an Android device that supports the app's `minSdk` (API 26). Android will warn that an APK downloaded outside the Play Store is from an unknown source; install only builds you trust.

The debug APK is signed with Android's debug key and is for testing. It is **not** a Play Store or production release. A failed or cancelled run does not upload a debug APK. Build details and failure diagnostics appear in the run summary and, on failure, in the `RivoStudio-build-diagnostics` artifact.

No PC, Android Studio, local Gradle invocation, or paid GitHub plan is required for the debug workflow. GitHub-hosted Actions availability and usage limits still apply to the repository.

## Build automation

- [`.github/workflows/android-apk.yml`](.github/workflows/android-apk.yml) — runs on every branch push, pull requests into `main`, and manual dispatch. It validates Gradle configuration, tests the bundled Python bridge, runs Android unit tests and lint, builds `:app:assembleDebug`, discovers the generated APK under the app module's debug output folder, and uploads it for 30 days.
- [`.github/workflows/release-apk.yml`](.github/workflows/release-apk.yml) — a separate manual workflow for signed release APKs. It intentionally skips release packaging until signing secrets are configured and verifies any resulting APK with `apksigner` before upload.
- Both workflows use JDK 17, the committed Gradle wrapper (Gradle 8.11.1), Android SDK platform/build tools 35, and Python 3.13 for Chaquopy. Gradle dependencies are cached by `gradle/actions/setup-gradle` when a cache is available.
- The Compose BOM is pinned to `2024.06.00` (Compose Foundation 1.6.8) to retain the editor's `TextFieldValue`, syntax-highlighting, and non-wrapping input API. Upgrading Compose requires a deliberate `TextFieldState` editor migration first.
- Workflows have read-only repository permissions, pinned third-party Actions, bounded job timeouts, and per-ref concurrency. A failed build creates a diagnostic report; there is no automatic source rewrite, commit, push, or retry loop. Fix the reported issue and push a new change to run verification again.
- For signing-secret names, setup, artifact details, and failure handling, see [`docs/CI_AND_RELEASES.md`](docs/CI_AND_RELEASES.md).

## Current app scope

The source contains these native app areas; each remains subject to verification on a real GitHub Actions build and Android device:

- **Projects and files:** app-private project folders, file/folder create/import/export/copy/move/rename/delete, ZIP import/export, SAF-based user-selected imports, path validation, and recent files.
- **Editor:** multiline text editing, tabs, bounded undo/redo, auto-indent, syntax-coloring helpers, search/replace, go-to-line, encoding controls, local recovery drafts, and pre-change snapshots.
- **AI:** opt-in provider configuration for OpenAI-compatible APIs, Gemini, and Anthropic; HTTPS requests, encrypted API-key storage, local chat history, selected-context confirmation and heuristic secret redaction.
- **Agent:** local project inspection, explicit context authorization, bounded one-file change proposals, local diff review, explicit apply/restore confirmation, and static Python checks. It does not execute generated code or arbitrary model commands.
- **Execution:** an embedded Python runner with output capture, stop requests, syntax/import/requirements diagnostics, and a bounded local log. Only Python scripts are runnable in this initial scope.

### Runtime and package limits

Chaquopy packages Python libraries into the APK **at build time**. This repository currently configures `python-telegram-bot` and `requests`; a project's `requirements.txt` is diagnostic only and does not install packages on the device. Packages with unavailable Android wheels/native dependencies may not be supported. The import/dependency checks report what the current embedded runtime can resolve; they do not prove that an external service, credential, or bot works.

Scripts execute inside the Rivo app process with the app's Android permissions, not in a security sandbox. Run only code you trust. Android may stop or suspend the app in the background, so uninterrupted bot hosting is not guaranteed.

## Privacy and security

- Project contents, chat history, editor preferences, recovery data, and execution metadata stay in app-private device storage by default. Project ZIP export is user initiated; imported SAF content is copied into the app's project folder.
- Provider API keys are encrypted at rest with AES-GCM and a key held by Android Keystore. Project source, chat history, and recovery text are app-private but are not separately encrypted by Rivo. Android automatic app backup is disabled. Uninstalling or clearing app data can permanently remove local data.
- AI requests require internet and send the prompt and recent conversation to the selected configured provider. Source is excluded unless the user selects and confirms context sharing. Common token patterns are heuristically redacted; this is not a guarantee, so inspect files before sharing.
- Runtime network access is available to a user-selected Python script. The AI agent does not run shell commands or apply proposed changes without the user's review and authorization.
- Release signing keys and passwords must never be committed. Store them only in the GitHub Actions Secrets described in the release guide.

## Architecture

The app is one Gradle application module so the first build stays reproducible and straightforward to download. Kotlin packages keep concerns separated: `core/project`, `core/editor`, `core/runtime`, `core/ai`, `core/agent`, `core/security`, `core/settings`, `core/data`, and `ui`. Room stores local metadata and conversations; the filesystem stores project files, recovery drafts, snapshots, and execution logs. Provider and runtime interfaces are intended to support future implementations.

## Roadmap

1. Run the new CI workflow on GitHub and resolve any real build or lint diagnostics before calling the APK verified.
2. Expand automated tests and exercise SAF, encrypted provider storage, editing/recovery, AI provider failures, and Python execution on supported Android versions/devices.
3. Improve structured diffs, project navigation and diagnostics; add more local runtimes only when their Android compatibility and package constraints can be verified.
4. Add migration/privacy review and performance testing before a production release.
