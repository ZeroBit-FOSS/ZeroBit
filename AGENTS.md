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
- Keep the primary editor MacroDroid-simple: Triggers, Conditions, and Actions,
  with an always-available code view backed by the same validated model.
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

- `main` is the stable integration branch. Working and committing directly on `main` is allowed and expected.
- In autonomous continuation mode, act with full developer authority and
  responsibility: make and commit completed code changes without requesting
  explicit permission.
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

## Future Agent Backlog & Architecture Targets

To reach MacroDroid-level power while keeping a user-transparent design, future agents should build new architecture and capabilities iteratively.
- **Constraints**: No local compilation/app builds or live on-device testing. Only write code and tests. Run continuously without pausing unless blocked.
- **Backlog Management**: Update this backlog as items are resolved or discovered.

### Active Targets

- **Target 1: Calendar Event Draft Action**: Add explicit start, end, and
  timezone values plus bounded title, location, and description sources through
  a fixed calendar insert intent that never writes directly or requests access.

### Completed Foundations

- **Local Variables & Secrets Store**: OpenMacro has typed declarations,
  schema and validator coverage, durable non-secret values, Android
  Keystore-backed encrypted secrets, and macro-scoped runtime access.
- **Trigger Context Data Path**: Trigger callbacks carry typed values through
  conditions and actions; battery percentage and screen state are the first
  concrete fields.
- **Variable Mutation Actions**: Set, increment, and toggle actions validate
  declarations and types before compilation and fail closed again at runtime.
- **Runtime Value References**: Variable-setting, notification, logging, and
  SMS fields can use validated literal, local-variable, secret, or trigger-field
  sources; missing runtime values fail with explicit diagnostics.
- **Advanced Condition Trees**: OpenMacro supports backwards-compatible nested
  AND/OR/NOT condition trees across schema, YAML, validation, explanations,
  approval comparison, compiled plans, and deterministic runtime evaluation.
- **Notification Trigger Context**: A notification-listener broker supports
  optional package filtering and exposes only explicitly requested package,
  title, or text fields to each macro; payloads are not broadly logged.
- **Durable Desired Enable State**: App-private state records requested enabled
  macros, manual disable removes them, runtime shutdown preserves them, and
  restoration reports successes and failures without discarding failed intent.
- **Value Comparison Conditions**: Typed equality, numeric ordering, text
  matching, presence, and missing-value checks work over literals, variables,
  secrets, and trigger fields without a general expression language.
- **Logic-Group Diagnostics**: Bounded diagnostics now record whether explicit
  AND/OR/NOT groups passed or blocked and identify the deciding tree path.
- **Generated Form Models**: Capability metadata now produces typed field
  models, current values, and only the variable, secret, or trigger references
  actually available to that macro; model edits also support nested conditions.
- **Comment-Preserving Scalar Form Edits**: Existing scalar config values can
  be patched by source location without reformatting comments or surrounding
  YAML, then immediately pass through the shared proposal validator.
- **Focused Forms and General Config Patches**: The visual editor renders
  generated capability fields and available references. Existing values,
  collection/reference values, new keys, optional-key removal, and missing
  config objects use local source patches before shared validation.
- **Bounded Choice Controls**: Capability metadata can publish allowed values;
  forms now provide direct single- and multi-choice controls for schedule days,
  delivery mode, notification capture fields, and battery direction.
- **Action Flow Control**: Delay, stop, and conditional stop actions compile to
  bounded deterministic steps; waits are cancellation-aware and do not poll.
- **Schedule Semantics and Recurrence**: Time schedules validate explicit
  timezone, days, delivery mode, and alarm window; shared recurrence handles DST
  gaps/overlaps, emits planned-time context, and rearms through a one-shot port.
- **Runtime Process Ownership**: One idempotent process controller restores
  desired macros once and owns cancellation and resource shutdown.
- **Application Runtime Bootstrap**: One `Application` graph owns approvals,
  durable variables, encrypted secrets, desired enable state, Android ports,
  diagnostics, dispatch, and the process controller.
- **Durable Android Schedule Delivery**: AlarmManager uses occurrence-specific
  immutable intents, survives ordinary process death through validated external
  trigger delivery, and restores desired schedules after reboot or app update.
- **Recovery and Runtime Controls**: Restore outcomes become plain-English
  running, approval-required, access-required, or failed states. The editor can
  retry desired macros and enable or disable only persisted approved revisions.
- **Persistent Editor Approval**: The editor compares against app-private
  approval history, writes approval revisions before updating visual state, and
  safely keeps an older active revision if a replacement cannot start.
- **Permission Recovery Flows**: Recovery states offer focused runtime
  permission requests or Android settings routes for notification listener,
  exact alarms, SMS, notifications, and app details, followed by explicit retry.
- **Runtime Status Details**: The editor shows the active approval revision,
  subscription count, last runtime result, and a bounded recent-event drill-down
  whose diagnostics omit trigger payload and secret values.
- **App Launch Action**: A validated exact package action launches only normal
  Android launcher activities and reports missing or unlaunchable apps cleanly.
- **Bounded Web Action**: Web opening accepts only literal HTTP/HTTPS addresses
  with a host and no embedded credentials; arbitrary URI schemes and intent
  payloads remain unsupported.
- **Bounded Intent Actions**: Text sharing, app details, and app notification
  settings use typed runtime steps, exact package validation, and fixed Android
  intent shapes instead of arbitrary action strings or extras.
- **Source-Level Action Groups**: OpenMacro source can define bounded nested
  action groups with explicit stop/continue failure policy, recursive
  validation, permission discovery, and deterministic runtime execution.
- **Existing Action Group Forms**: The visual editor shows existing grouped
  child actions, edits their generated scalar fields through the shared model,
  and keeps grouped permission/explanation review accurate.
- **Action Group Structure Controls**: Existing groups can add bounded
  default-backed child actions, remove non-final children, and move children up
  or down while staying inside the shared proposal validator.
- **Workspace Mutation Boundary**: Workspace storage now has a replaceable port
  plus explicit list/read/write/delete operations. Rename updates the declared
  macro ID with a comment-preserving patch, refuses overwrite, and rolls back
  the new file if removing the old file fails.
- **Android Workspace Picker**: The editor can persist a Storage Access
  Framework folder, save valid macros under `macros/`, list workspace macro
  files, and open one into the same visual/code editor. Approval history,
  secrets, runtime state, and logs remain app-private.
- **Workspace Macro Management**: The Android editor can create collision-safe
  starter macros, rename through the transactional workspace mutation, and
  delete source after explicit confirmation without altering approvals,
  secrets, runtime state, or logs.
- **Workspace Draft Safety**: The editor tracks its exact last workspace
  read/write baseline, shows saved versus unsaved state, and confirms before an
  open, create, or active-file rename replaces a draft. Folder changes retain
  the draft and clear the old baseline.
- **Workspace External Refresh**: Manual refresh distinguishes unchanged,
  modified, missing, and invalid active files. Reload protects local drafts,
  and detected external edits require confirmation before overwrite.
- **Workspace Conflict-Safe Save**: Normal saves compare the target against the
  exact editor baseline immediately before writing. Modified, missing, invalid,
  and occupied targets require an explicit overwrite or recreate confirmation.
- **Workspace Save-As Semantics**: A changed declared macro ID requires Save as
  new or a guarded transactional file rename. Neither choice renames approval,
  runtime, secret, or diagnostic state.
- **Top-Level Block Structure Controls**: The visual editor can add bounded
  default-backed triggers, conditions, and actions, remove non-final required
  blocks, and reorder each lane through the shared proposal validator.
- **Comment-Preserving Top-Level Structure Patches**: Top-level add/remove/move
  edits touch only local block-list ranges, preserve surrounding source, reject
  unsupported flow-style lists, and immediately revalidate patched text.
- **Comment-Preserving Grouped Action Patches**: Grouped add/remove/move uses
  recursive local sequence edits, protects final children, preserves surrounding
  source, and immediately revalidates patched text.
- **Comment-Preserving Variable Add Patch**: Variable creation locally inserts,
  expands, or appends the declaration sequence, preserves surrounding source,
  rejects unsupported flow lists, and immediately revalidates patched text.
- **Local Visual Source Editing**: Visual form and structure changes no longer
  retain a whole-document writer fallback; unsupported source shapes fail
  explicitly instead of being normalized silently.
- **Capability-Backed Add Palette**: Top-level visual add choices come from
  opt-in capability metadata with safe ID bases and validated starter configs,
  removing the editor's hard-coded capability enum.
- **Document-Aware Capability Starters**: Capability factories can inspect the
  active document; variable actions appear only for compatible declarations,
  and all visible starters remain validator-clean.
- **Searchable Capability Add Picker**: Compact dialogs replace expanding add
  lists and filter safe starters by capability name, description, or type.
- **Registry-Backed Nested Add Pickers**: Action groups and condition trees use
  the same document-aware capability starters as top-level lanes, validate every
  visible option, and reject wrong-lane insertion before source mutation.
- **Context-Aware Capability Creation**: One creation contract receives the
  insertion location; Action Group starts with a unique Stop Actions child and
  is hidden when another nested group would exceed the four-level bound.
- **Validated Pre-Insert Capability Setup**: Open Web and exact-package actions
  remain searchable, collect required text before insertion, and pass the real
  capability validator before top-level or nested source mutation is allowed.
- **Typed Pre-Insert Capability Setup**: Share Text and SMS reuse generated
  controls for literals and document-valid value references, validate before
  insertion, and can switch selected references back to literal text.
- **Searchable Value Reference Picker**: Normal forms and pre-insert setup show
  every document- and type-filtered reference through one compact searchable
  dialog instead of capping the choices as inline buttons.
- **Bounded Installed App Picker**: Exact-package setup and existing forms can
  choose from a sorted, capped launcher-app snapshot loaded off the UI thread;
  manual entry remains available and broad package access is never requested.
- **Notification Trigger Pre-Insert Setup**: Notification Received starts with
  minimal capture, optional exact-package filtering, bounded capture choices,
  and shared package validation before source mutation.
- **Schedule Pre-Insert Setup**: Time Schedule starts with explicit portable
  time, weekday, UTC, windowed-delivery, and alarm-window values and validates
  every generated setup field before source mutation.
- **Searchable Timezone Picker**: Schedule setup and existing forms can choose
  from a sorted, capped IANA timezone snapshot while retaining manual entry and
  validator authority; large picker result sets render lazily.
- **Pre-Insert Access Preview**: Validator-clean setup drafts show capability-
  derived Android access such as SMS or exact alarms without requesting access
  or bypassing the existing approval and recovery flows.
- **Bounded Vibrate Action**: A validator-clean 250 ms starter compiles to one
  Android vibration capped at five seconds, reports unavailable hardware, and
  uses only the normal manifest vibration permission.
- **Clipboard Text Action**: Bounded literal or referenced text copies through a
  safe starter and Android clipboard step whose diagnostics never include the
  copied payload.
- **Charging State Condition**: Charging and not-charging checks use one sticky
  Android battery snapshot per evaluation, fail on unknown status, and never
  poll or retain battery history.
- **Battery Level Condition**: Current battery percentage comparisons use one
  sticky Android battery snapshot and the trigger's exact whole-number threshold
  semantics; fractional thresholds now fail validation instead of truncating.
- **Power Connection State Condition**: Plugged-in and unplugged checks inspect
  one sticky battery snapshot, distinguish known Android power sources in
  diagnostics, and fail closed on unknown source values.
- **Screen Interactive State Condition**: Screen-on and screen-off checks read
  Android's current interactive state once, need no permission, and install no
  observer or polling work.
- **Device Lock State Expansion**: The existing config-free unlocked condition
  remains compatible while a bounded field now supports explicit locked and
  unlocked checks through the same keyguard-backed runtime step.
- **Power Disconnected Trigger**: Android's disconnection broadcast now has a
  validated starter and typed runtime subscription alongside Power Connected,
  without polling or retained battery state.
- **Wi-Fi Connectivity Triggers**: Connected and disconnected transitions use
  owned Android default-network callbacks, suppress duplicate callback updates,
  expose only bounded state context, and never poll or capture SSIDs.
- **Airplane Mode State and Trigger**: Enabled and disabled checks read one
  strict global value, while transitions use Android's change broadcast and
  expose only bounded state context without polling or extra access.
- **Ringer Mode State and Trigger**: Normal, vibrate, and silent share one typed
  runtime vocabulary; checks read once and transitions use Android's broadcast
  with bounded state context and no polling or extra access.
- **Battery Saver State and Trigger**: Checks read Android's power service once;
  transitions use an owned change-broadcast subscription, reread authoritative
  state, and expose only bounded context without polling or extra access.
- **Power Source Condition Expansion**: Existing plugged/unplugged files remain
  valid while plugged checks can optionally require typed AC, USB, wireless, or
  dock power from one sticky battery snapshot.
- **Power Trigger Context**: Config-free power events expose bounded connection
  state, while connected events add a known canonical source from one optional
  sticky battery read without retaining history.
- **Time Window Condition**: Explicit-timezone daytime and overnight windows use
  shared strict schedule parsing, half-open boundaries, deterministic DST-safe
  instant evaluation, one clock read, and no polling.
- **Dial Number Action**: Setup accepts bounded literal or referenced phone text,
  runtime revalidates it, and fixed `ACTION_DIAL` opens the dialer without placing
  calls or requesting call permission.
- **Compose Email Action**: Setup accepts validated recipient, subject, and body
  text sources; runtime rechecks resolved values and fixed `ACTION_SENDTO` opens
  a draft without sending mail or requesting account permission.
- **Open Map Location Action**: Setup accepts bounded literal or referenced
  search text, runtime revalidates and encodes it into a fixed `geo` query, and
  opens a map app without reading location or accepting arbitrary URIs.
- **Set Alarm Action**: Whole-number time, optional bounded label, and skip-UI
  choice compile to fixed `ACTION_SET_ALARM` extras; the normal intent permission
  grants no access to existing alarms or clock data.
- **Set Timer Action**: A bounded whole-second duration, optional label, and
  skip-UI choice compile to fixed `ACTION_SET_TIMER` extras with no clock-state
  access or arbitrary intent fields.
- **Show Alarms Action**: A config-free typed step opens Android's alarm list
  through fixed `ACTION_SHOW_ALARMS` without reading alarm IDs or state into
  ZeroBit.
- **Existing Variable Forms**: The visual editor can update or remove initial
  text, number, and boolean values and can update secret-key identifiers through
  local source patches; secret values themselves never enter macro source.
- **Visual Variable Declaration Operations**: The visual editor can create text,
  number, boolean, and secret variable declarations from bounded valid templates
  with collision-safe names, rename declarations and their references with local
  source patches, and delete only unreferenced declarations.
- **Condition Tree Structure Forms**: Existing condition trees render their
  AND/OR/NOT structure in the visual editor. AND/OR groups can switch logic and
  append, remove, wrap, or unwrap condition children with local source patches
  that preserve existing child conditions and comments.
- **Standard Capability Expansion**: Screen On/Off and Battery Level triggers,
  Wi-Fi Connected condition, and Write Log and Send SMS actions are registered
  and compiled into deterministic runtime steps.

## Agent Handover & Long-Term Vision

- Continue making useful code or test progress without pausing between targets
  unless a major blockage requires the user's immediate input.
- Do not stop work merely to report progress. A progress update is not a
  completion point; continue into the next useful target in the same run.
- When the user requests continuous work mode, periodically update this file's
  completed foundations and active targets so future agents can resume from the
  real architecture state.
- During this phase, do not run local or hosted app builds and do not perform
  live device testing. Work only on source code, tests, schemas, documentation,
  and architecture that can be reviewed without building the app.
- Always keep one concrete architecture or capability target moving. When a
  target is completed, choose the next smallest durable step toward
  MacroDroid-level power.
- Treat this backlog as a running handover to future agents. Regularly mark
  completed targets, refine unclear ones, and add newly discovered follow-up
  work.
- Preserve ZeroBit's offline, local-first, deterministic, schema-validated,
  explainable, and user-transparent design while expanding its power.
