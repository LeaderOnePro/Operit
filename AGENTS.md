# Repository Guidelines

## Project Structure & Module Organization
- `app/` — Android app (Kotlin, Jetpack Compose, ObjectBox, Shizuku). Sources in `app/src/main`, instrumented tests in `app/src/androidTest`.
- `dragonbones/` — Android library with CMake/NDK code and Compose UI helpers.
- `bridge/` — Node.js TCP bridge for MCP (TypeScript). See `bridge/README.md`.
- `examples/` — Example automation/scripts (TS/JS).
- `tools/` — Helper scripts for development and JS execution.
- Root Gradle files: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`.

## Build, Test, and Development Commands
- Build debug APK: `./gradlew :app:assembleDebug`
- Install debug on device: `./gradlew :app:installDebug`
- Run instrumented tests (device/emulator required): `./gradlew :app:connectedAndroidTest`
- Clean: `./gradlew clean`
- Build library: `./gradlew :dragonbones:assembleRelease`
- Bridge (Node): `cd bridge && npm install && npm run build && npm start -- 8752 node ../your-mcp-server.js`

## Coding Style & Naming Conventions
- Kotlin: 4-space indent, `PascalCase` classes, `camelCase` methods/vars, package names lowercase.
- Compose: keep composables small and previewable; hoist state; avoid blocking I/O on main.
- Resources: XML names `lower_snake_case` (e.g., `ic_logo.xml`, `activity_main.xml`).
- KDoc for public APIs; prefer coroutines over threads; use `Slf4j`/`kotlin-logging` for logs.

## Testing Guidelines
- Frameworks: AndroidX JUnit4 + Espresso in `app/src/androidTest`.
- Naming: place tests mirroring package, files ending with `Test.kt`.
- Run: `./gradlew connectedAndroidTest` with an emulator/device booted.
- Aim for tests around UI flows and critical utilities; keep tests hermetic where possible.

## Commit & Pull Request Guidelines
- Commits: concise, imperative (e.g., "Fix crash on rotate"), group related changes, reference issues (`fix #123`) when applicable.
- PRs: clear description, linked issues, screenshots/GIFs for UI, steps to verify, and notes on risks/rollout. Keep PRs focused.
- Ensure: builds pass, no unrelated reformatting, and no secrets/keys committed.

## Security & Configuration Tips
- Use JDK 17; Android SDK configured via `local.properties` (`sdk.dir=`). Target/compileSdk 34.
- App permits cleartext for local tooling; avoid shipping secrets. Review manifests and `network_security_config` before release.
- For debugging, use the `debug` variant; release builds may disable debugging and minification.

