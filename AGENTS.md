# Repository Instructions

ZeroBit is a long-term FOSS Android automation project: transparent automation
for Android, built for humans and AI.

The project is licensed under `GPL-3.0-or-later`. New project-owned source
files should use the SPDX identifier `GPL-3.0-or-later` where practical.

## Product Direction

- ZeroBit is an Android app/runtime; OpenMacro is the planned open automation
  file format and OpenMacro Workspace is the user's versioned workspace.
- Macros must be human-readable, AI-editable, versionable, portable,
  explainable, and locally executable.
- The trust model is: **AI proposes. Schema validates. App explains. User
  approves. Engine runs. Logs prove.**
- Runtime behavior must remain deterministic. Do not place an AI black box in
  the execution path.
- GitHub may be a canonical collaboration/history workspace, but never the
  real-time runtime database. The phone must keep a validated offline copy.
- Keep secrets and sensitive runtime data local by default. Versioned macro
  files should reference local secret identifiers, never secret values.
- External macro changes are proposals until validated, explained, and approved
  by the user. Preserve an audit trail and rollback path.
- Prefer a small set of reliable capabilities with excellent validation and
  diagnostics over a broad set of fragile features.

## Architecture Principles

- Treat macro files as user-owned source code, not opaque app preferences.
- Keep schemas and public formats stable, documented, diff-friendly, and
  backwards-compatible. Add explicit migrations when formats evolve.
- Separate workspace data, encrypted local secrets, runtime state, and
  diagnostic logs.
- Permissions must be discoverable and explainable before a macro is enabled.
- Every execution should be traceable: why it ran, why it did not run, what it
  attempted, and what failed.
- Build local-first. Network, GitHub, AI, MCP, and future relay integrations
  must remain optional and must not make local automations unreliable.
- Design extension points deliberately; do not add premature plugin machinery
  or abstractions without a concrete first use.

## Performance and Technical-Debt Policy

- Avoid known technical debt from the first implementation. Do not knowingly
  merge a temporary architecture without an explicit, near-term removal plan.
- Use event-driven Android APIs and the narrowest available signal. Do not poll
  when a callback, observer, broadcast, scheduler, or system API can provide the
  same information.
- Minimize wakeups, background work, allocations, database reads, IPC, network
  calls, and retained state. Battery, CPU, RAM, storage, and data use are product
  requirements.
- Do not gather or retain more information than a feature needs. Prefer
  targeted queries and bounded logs over broad scans, continuous captures, or
  unbounded histories.
- Background work must have a clear owner, lifecycle, cancellation path, and
  measurable reason to exist.
- Benchmark or profile code when performance cost is uncertain; do not rely on
  intuition for hot paths.
- Choose maintained, well-understood dependencies. Avoid adding a library for
  behavior that is small, stable, and safer to own directly.
- Add tests with foundational formats, migrations, validators, schedulers, and
  execution behavior. Fix flaky tests rather than normalizing retries.

## Branches and Git Hygiene

- `main` is the stable integration branch and should remain buildable.
- Develop on `feature/*`, `fix/*`, `wip/*`, or another purpose-named branch,
  then merge through a focused pull request.
- Keep commits narrow and understandable. Do not mix formatting, refactors, and
  behavior changes unless they are inseparable.
- Do not commit APKs, keystores, signing secrets, local SDK paths, generated
  build outputs, raw device captures, or temporary debug logs.
- Use the `agents-md-maintainer` skill for changes to this file.
- Clear a dummy `GITHUB_TOKEN` before GitHub CLI commands so `gh` uses keyring
  credentials: `$env:GITHUB_TOKEN=$null`.

## Environment

- Work from the repository root in PowerShell on Windows.
- Target Java 21 unless the checked-in Gradle/Android toolchain requires a
  documented change.
- The usual local Android SDK path is
  `C:\Users\Vibhor\AppData\Local\Android\Sdk`.
- The repository is currently a greenfield project, not a fork. Do not add
  upstream-sync, patch-stack, or inherited-branding assumptions.

## Build and Verification

- Local Android compilation and APK builds are prohibited to avoid unnecessary
  RAM pressure. Use GitHub Actions for compilation and APK production.
- `.github/workflows/verify.yml` is the hosted verification lane for pull
  requests and pushes to `main`.
- `.github/workflows/debug-apk.yml` is the manual hosted debug APK lane. Run:
  `gh workflow run debug-apk.yml --ref <branch>`.
- Gradle commands in automation must use `--no-daemon`.
- Keep CI cancellation enabled so newer commits replace obsolete runs.
- When the Android project is introduced, keep the root Gradle wrapper as the
  only supported build entry point and commit its wrapper files.
- Do not weaken validation merely to make CI green. Correct the code, test, or
  workflow assumption.

## Debug APK Signing

- Debug APKs use a stable repository-held signing secret so repeated
  `adb install -r` upgrades work.
- Required GitHub Actions secrets are `DEBUG_KEYSTORE_BASE64`,
  `DEBUG_KEYSTORE_PASSWORD`, `DEBUG_KEY_ALIAS`, and `DEBUG_KEY_PASSWORD`.
- Never generate a new debug key on each workflow run and never commit the
  keystore.
- Keep the debug application ID separate from any future production package so
  development cannot overwrite real user automations.
- Production application ID: `com.vibhor1102.zerobit`.
- Debug application ID: `com.vibhor1102.zerobit.debug`.

## Device Debugging

- The normal test device runs Android 14 and uses wireless ADB.
- First run `adb devices`; already-paired devices may reconnect automatically.
- If reconnection is needed, ask the user for the current wireless-debugging
  port. The usual phone IP is `192.168.1.170`, but the port changes.
- Only install GitHub Actions debug artifacts for development.
- Set up ADB, package state, and focused logcat capture yourself. Ask the human
  only for UI interaction or judgement that cannot be automated.
- Keep requested manual testing narrow and proportional to the risky behavior.
- Do not collect broad or continuous device logs when a package/tag/process
  filter can answer the question.
