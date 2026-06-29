# OpenMacro foundation

OpenMacro is a declarative automation format, not a general-purpose programming
language. Its first authoring syntax is a strict subset of YAML.

## Core rule

The visual editor and source editor are two views of the same macro:

```text
YAML source <-> parser/formatter <-> OpenMacro model <-> visual editor
                                      |
                                      v
                             validator and explainer
                                      |
                                      v
                              approved runtime plan
```

Neither editor owns a second representation. A change made in either view must
pass through the same model and validator before it can become runnable.

## Why YAML, rather than a new language

YAML is readable, produces useful Git diffs, supports comments, and is familiar
to current AI coding tools. A new language would require ZeroBit to build and
maintain a parser, formatter, syntax highlighter, error recovery, editor
integration, and AI conventions before it adds any automation value.

OpenMacro uses only YAML 1.2 data values: objects, lists, strings, numbers,
booleans, and null. The parser must reject duplicate keys, aliases, anchors,
merge keys, custom tags, and implicit YAML 1.1 values such as `yes` and `no`.
These restrictions keep files unsurprising and allow the same data to be
checked by JSON Schema.

The normal extension is a new validated capability type, not new language
syntax. General scripting can be evaluated later as an explicit, sandboxed
capability; it must not become an invisible escape hatch in the normal runtime.

## Version 0.1 semantics

- One file contains one macro.
- `metadata.id` and every block `id` are stable identifiers. Display names may
  change without breaking logs, references, or Git history.
- Multiple triggers mean “any trigger may start the macro.”
- All conditions must pass.
- Actions run in listed order.
- Runtime enabled/disabled state, secrets, approval state, and logs do not live
  in the macro file.
- Capability types use dotted names, for example
  `android.notification.show`.
- Each capability owns the schema, UI form, explanation, permission
  requirements, and runtime implementation for its `config`.

## UI and code equivalence

A macro's normal visual outline has only three lanes: Triggers, Conditions, and
Actions. Keep that outline clean even when individual capabilities are
powerful. Complexity belongs inside a block's focused setup screen, where
advanced options can be progressively revealed without turning the macro
overview into a programming canvas.

A capability is eligible for the normal visual editor only when its registry
entry provides:

1. a configuration schema;
2. a visual editor;
3. a human-readable explanation;
4. validation and permission discovery; and
5. a deterministic runtime implementation.

The source editor may eventually represent capabilities that the installed app
cannot visually edit. The app must preserve their source, label them as
unsupported, and refuse to enable the macro. It must never silently delete,
approximate, or execute an unknown block.

The YAML adapter retains the exact original source alongside the decoded model,
so merely reading a file never changes user-owned comments or formatting.
Visual changes should eventually patch the affected source range where
possible. The canonical writer is the explicit fallback for new files or a
user-requested format operation, not an automatic side effect of opening a
file.

## Safe edit flow

1. Keep the last approved document and its content hash.
2. Parse a proposed source or visual edit.
3. Validate the document structure and each registered capability.
4. Explain the behavioral and permission difference from the approved version.
5. Ask for approval when runnable behavior changes.
6. Compile the approved model into an immutable runtime plan.
7. Execute only that plan and record bounded diagnostic events.

The runtime never interprets YAML directly and never asks AI what a block means.

The proposal pipeline is the shared trust boundary for source and visual edits.
It returns one of three outcomes:

- source rejected, with YAML locations where available;
- validation rejected, while retaining the proposed source for correction; or
- approval-ready, with plain-English block explanations, permission impact,
  behavior changes, and an immutable runtime plan.

Approval is tied to runnable behavior rather than file churn. Comments,
formatting, macro display names, and descriptions may change without behavioral
re-approval when the compiled plan is unchanged. Trigger, condition, action,
ordering, configuration, macro identity, or permission changes require
approval.

## First implementation boundary

The initial implementation contains:

- the format-neutral document model and validator;
- a capability registry divided into the three visual lanes;
- three small built-in capabilities covering a trigger, condition, and action;
- field descriptions that a visual form can render;
- capability validation, explanations, and permission discovery; and
- compilation into immutable runtime instructions;
- strict YAML 1.2 reading with source locations and bounded input; and
- stable canonical writing without silently reformatting source on read; and
- a proposal pipeline joining parsing, validation, explanation, permission
  impact, behavioral comparison, and runtime-plan compilation.

This proves that one capability definition can drive the code shape, future
form, validation, explanation, permissions, and runtime plan without premature
plugin machinery. The source adapter rejects aliases, anchors, merge keys,
custom tags, directives, duplicate keys, multiple documents, ambiguous YAML
1.1 booleans, excessive nesting, and oversized files.

The next slice should add durable workspace and approval storage around this
pipeline. It must keep source files, approved snapshots, encrypted secrets,
runtime state, and diagnostic logs separate as required by the repository
architecture.

## Local storage boundary

The first storage implementation keeps two independent roots:

- OpenMacro Workspace stores user-owned files at
  `macros/<macro-id>.openmacro.yaml`. These files are suitable for Git and may
  change externally.
- App-private approval storage keeps immutable source snapshots and an atomic
  pointer to the currently approved revision. The runtime must use this
  approved snapshot, never whichever workspace contents happen to be newest.

Each approval or rollback creates a new revision linked to the previous
revision. Snapshot fingerprints are verified again when loaded. A malformed,
unsupported, missing, or modified approved snapshot is refused rather than
returned as runnable.

Secrets, runtime enabled state, and diagnostic logs have no directory or record
in either of these stores. They remain separate future components.

The next slice should define runtime ownership and lifecycle around approved
plans: enabling, disabling, event subscription, cancellation, and bounded
diagnostics. It should use event-driven Android signals and begin with the power
trigger proof capability.

## Runtime lifecycle

The first runtime coordinator accepts plans only through the approved-plan
provider. Enabling checks permissions before subscribing. Each enabled macro
owns its trigger subscriptions, and disabling or closing the runtime owner
cancels them.

Trigger callbacks enqueue work with a generation token. Work queued by an older
enable session becomes harmless after disable or re-enable. One macro executes
at most one run at a time: overlapping trigger events are recorded and ignored.
Conditions run in order and fail closed; actions run in order and stop on the
first failure.

Runtime diagnostics use a bounded in-memory ring. Events carry a macro id,
optional run id and block id, timestamp, outcome, and a capped message. This
answers why a macro ran or did not run without creating an unbounded history.

The Android proof adapters use the `ACTION_POWER_CONNECTED` broadcast,
`KeyguardManager` lock state, and local `NotificationManager`. The power
trigger is callback-driven and exists only while a macro owns the subscription;
there is no polling loop or permanent background service.

The next slice should provide the first app UI around these foundations:
the three-lane macro overview, source view, proposal review, and permission
discovery. Runtime enable state should then gain a separate durable store and
Android process-restoration owner.

## First editor surface

The app now opens into one editor session with two always-available views:

- Visual keeps the macro overview limited to Triggers, Conditions, and Actions,
  showing each block's plain-English explanation and permission needs.
- Code edits the exact OpenMacro YAML source. Parsing and validation run locally
  after a short cancellable background debounce, rather than blocking typing.

Both views share the proposal pipeline and one source string. Invalid source
does not erase the last valid visual explanation; the visual view is marked
stale until code becomes valid again. Behavior-changing edits show that review
is required, while comments, formatting, names, and descriptions remain
non-behavioral.

This is the editor architecture proof, not yet the complete MacroDroid-level
form builder. The next UI slices should generate focused block configuration
forms from capability fields, patch source without discarding comments, and
connect approval and enable actions to the app-private stores and runtime.

## Local values and trigger context

OpenMacro variables are declared in source, but their values remain app-private
runtime state. Text, number, and boolean values use a typed durable store.
Secret declarations contain only a local key identifier; secret values are
encrypted with a non-exportable Android Keystore key before ciphertext is
written to app preferences.

Capabilities receive a macro-scoped value boundary rather than the underlying
stores. They may resolve only names declared by the approved macro, cannot
discover secret storage keys, cannot write secret values, and cannot write a
value with the wrong declared type. Initial values fill missing variables but
do not overwrite values when a macro is re-enabled or the process restarts.

Trigger callbacks now carry a typed event map into the condition and action
runtime context. Battery triggers provide `battery.percentage`, while screen
triggers provide `screen.state`. This creates the deterministic data path needed
for future notification fields and other trigger-specific context without
coupling the runtime coordinator to Android payload classes.

The next value-system slice should add explicit, validated references from
capability configuration to declared variables and trigger fields.

Focused `openmacro.variable.set`, `openmacro.variable.increment`, and
`openmacro.variable.toggle` actions provide deterministic state changes without
introducing a general expression language. Their capability definitions
validate the referenced declaration and type against the whole document before
the approved plan is compiled. Runtime writes still pass through the same
macro-scoped type checks so malformed or stale plans fail closed.

Capability values may now be literals or explicit one-key references such as
`{variable: account_name}` and `{trigger: battery.percentage}`. Capability
definitions publish their trigger output names and types, so references are
checked against the macro's actual triggers before approval. Secrets resolve as
text through their declared variable names; their local storage keys remain
hidden from capabilities. Missing values or fields fail the action with a clear
diagnostic rather than silently becoming empty text.

## Advanced condition trees

The original `conditions` list remains a simple implicit AND for compatibility.
Macros that need richer logic may instead use `condition_tree` with nested
`all`, `any`, `not`, and `condition` nodes. A file cannot use both forms.

Tree depth, empty groups, nested block IDs, capability lanes, permissions, and
capability configuration are validated before compilation. The runtime executes
an immutable condition tree with short-circuit behavior: AND stops on the first
non-passing child, OR stops on the first passing child, and NOT inverts a
blocked or passing child while preserving failures. Approval comparison treats
logical tree changes as behavior changes even when the leaf blocks are the
same.

## Notification context and runtime restoration

The notification trigger uses Android's event-driven notification-listener
service. Each macro declares an optional exact package filter and a bounded
`capture` list containing only `package`, `title`, or `text`. The shared broker
builds a separate event map for each subscription and includes only those
requested fields. It does not poll, retain notification history, or write
payloads to diagnostics.

Desired enabled state is separate from active process subscriptions. A durable
app-private store records macro IDs after successful enablement. Manual disable
removes that intent, while runtime-owner shutdown only cancels active resources
and preserves the desired state. Process restoration retries each stored macro
through the normal approval and permission checks and reports failures without
forgetting them, allowing recovery after approval or special access returns.

## Typed value comparisons

`openmacro.value.compare` provides a deliberately small condition vocabulary:
typed equality, numeric ordering, text contains/starts-with/ends-with, and
presence or missing checks. Both sides use the same explicit literal, variable,
secret, or trigger-field sources as actions. Validation proves source
availability and compatible types before compilation; runtime resolution still
fails closed if approved assumptions no longer hold.

Explicit condition trees also emit bounded group-level diagnostics. Leaf events
retain their capability block IDs, while AND/OR/NOT outcomes identify the
logical tree path that decided the run. This keeps short-circuit behavior
explainable without logging sensitive compared values.

The visual editor now renders the existing tree shape, can switch AND/OR groups
with a local key patch, and can append, remove, wrap, or unwrap condition
children while preserving existing child conditions, comments, and nearby
formatting.

## Generated capability forms

Capability field metadata now produces editor-facing form models with labels,
help, required and advanced state, current values, and valid reference choices.
Text fields receive only text-compatible choices; dynamic notification outputs
appear only when that macro requested them. Unsupported capability blocks remain
preserved and return no editable form rather than being approximated.

Model-level block edits can update or remove configuration values across normal
lanes and nested condition trees. The targeted YAML source patcher preserves
comments and surrounding formatting for focused visual edits instead of invoking
the canonical formatter.

The targeted patcher replaces existing values by their parsed source offsets,
writes collection and reference values in compact flow-style YAML, inserts
missing config objects or keys, and removes individual entries. It preserves
file headers, unrelated comments, key order, and surrounding formatting, then
routes the patched source through the normal proposal pipeline. Focused visual
controls use this path so visual and code edits remain one source.

Capabilities may also publish a bounded set of allowed values. The generated
forms use these as direct single- or multi-choice controls while the validator
remains authoritative. This avoids asking users to memorize tokens such as
schedule weekdays, notification capture fields, or battery directions.

## Flow control and schedules

Action flow now includes bounded delay, stop, and conditional stop steps.
Delays belong to the runtime coordinator, wait without polling, and wake
immediately when a macro is disabled or replaced. Conditional stop reuses the
typed comparison vocabulary, keeping branching understandable without adding
an embedded scripting language.

`android.time.schedule` describes a local wall-clock time, selected weekdays,
an explicit IANA timezone, and either battery-friendly windowed delivery or
exact delivery. The shared schedule model deterministically advances through
DST gaps and chooses one occurrence during overlaps. Repeating behavior is
implemented above a one-shot alarm port, so platform adapters cannot quietly
invent different recurrence rules.

Schedule events expose only the planned instant and planned zoned local time.
Exact delivery declares Android's special exact-alarm access before enablement.
The Android adapter uses occurrence-specific immutable `PendingIntent` identity
and `AlarmManager` exact or windowed delivery. If the process is gone, the
receiver starts the application runtime, restores approved desired macros, and
routes the stored occurrence through the same coordinator path. Boot and
package-replacement broadcasts recreate alarms Android has cleared.

## Process ownership

The runtime has one idempotent process controller. It restores durable desired
enable state once, returns the same restoration summary to duplicate starters,
and owns final cancellation through `RuntimeOwner`. Activities, receivers, and
services should eventually request this application-owned controller rather
than constructing independent coordinators.

`ZeroBitApplication` now constructs that graph once with app-private approval
history, durable variables, encrypted secrets, desired enable state, Android
ports, bounded diagnostics, and one serial runtime dispatcher. Activities do
not own or close the runtime. The next app-facing slice should expose recovery
status and explicit special-access repair actions without leaking this graph
into Compose screens.

Restore results are now translated into plain-English running,
approval-required, access-required, or failed states. The visual editor can
retry desired macros after access is repaired and can enable or disable the
persisted approved revision. Approval writes the immutable app-private revision
before the editor treats it as approved. If an enabled macro cannot switch to a
new revision, the previous active revision remains visible as active rather
than being silently cancelled or misreported.

Access-required states produce focused repair actions rather than one generic
settings link. Normal Android permissions use the runtime permission contract;
notification-listener and exact-alarm access open their dedicated Android
screens. Returning to ZeroBit requires an explicit retry, so a settings visit
never silently changes which macros are active.

Runtime status exposes the active approval revision, source fingerprint,
subscription count, executing state, and a bounded recent diagnostic view.
These events explain lifecycle and block outcomes but do not include resolved
secret values or captured notification payload text.

The first app-control expansion is `android.app.launch`. It accepts one
validated exact package name and asks Android only for that package's normal
launcher activity. It does not accept components, raw intent flags, URIs, or
shell commands, leaving those more powerful surfaces for separately reviewed
capabilities with narrower schemas.

`android.web.open` is intentionally separate from a future general intent
action. It accepts only a literal HTTP or HTTPS URI with a host, rejects
embedded credentials and non-web schemes, and launches it through a browsable
Android intent. Dynamic URLs and arbitrary URI schemes remain unsupported until
their data-flow and approval risks have dedicated schemas.

Bounded intent actions follow the same pattern. `android.intent.share-text`,
`android.app.details`, and `android.app.notification-settings` each compile to
a typed runtime step with a fixed Android intent shape. Macro source can provide
the exact target package and, for sharing, a text value source; it cannot provide
arbitrary action strings, components, raw extras, shell commands, or hidden URI
schemes.

Action groups are source-level runtime composition, not a scripting escape
hatch. `openmacro.action.group` accepts a bounded nested action list plus an
explicit `stop` or `continue` failure policy. Nested actions still pass through
the same capability validators, permission discovery, and runtime step compiler;
group children cannot be triggers or conditions, cannot reuse top-level block
IDs, and cannot nest deeper than the documented limit. Visual editing for group
children can build on this source shape without changing the approved runtime
plan contract.

The first structure controls stay deliberately narrow: the visual editor can
append only bounded action types with valid defaults, currently write-log,
delay, and stop actions. It can also move grouped children within their parent
group and remove a child only when another child remains, keeping structure
changes inside the shared proposal validator instead of creating broken
placeholder blocks.

Workspace operations now sit behind a `MacroWorkspaceStore` port so a future
Storage Access Framework adapter can replace direct paths without changing
review and approval logic. The port has explicit list, read, write, and delete
outcomes. Rename is a higher-level mutation: it refuses overwrite, patches the
declared macro ID without reformatting comments, writes the new file, then
removes the old one with rollback if that final step fails. Approval history
remains app-private throughout.

Android now has that first Storage Access Framework adapter. The selected tree
URI is persisted, macros are stored only under `macros/*.openmacro.yaml`, and
the editor can save the current valid macro, list workspace macros, and open one
back into the same visual/code editing session. Approval history, secrets,
runtime state, and diagnostics still stay in app-private storage.

Workspace management builds on the same port. The editor can create a valid
starter macro without overwriting an existing ID, rename source with the
transactional comment-preserving mutation, and delete source only after an
explicit warning. These operations never rename or remove an approved snapshot,
enabled-runtime intent, local secret, or diagnostic record.

The editor also keeps the exact ID and source text from its last workspace read
or write. It marks later visual or code edits as unsaved and asks before opening
or creating another macro, or renaming the active workspace file, would replace
that draft. Changing workspace folders does not replace the editor contents; it
clears the old baseline so the current source is correctly treated as unsaved in
the new folder.

Manual workspace refresh lists the folder again and compares the active source
with that baseline. Unchanged, externally modified, missing, and invalid files
have separate states. Reload reads the file again and uses the existing draft
replacement confirmation; saving over a detected modified or invalid file has
its own overwrite confirmation. None of these checks alter the approved runtime
snapshot.

Normal saves repeat the comparison immediately before writing. The guarded
workspace mutation accepts a matching baseline or an unused new ID, but reports
modified, missing, invalid, and already-existing targets without writing.
Unconditional write remains a separate callback used only after the editor's
overwrite or recreate confirmation.

Changing a tracked macro's declared ID no longer implies an accidental second
file. The editor requires either Save as new, which keeps the old source and
guards the new target, or Rename file, which verifies the old baseline, refuses
an occupied target, writes the complete current editor source, and removes the
old file with rollback. Approval history and runtime state keep their original
identity in both cases.

The visual editor can now add bounded default-backed top-level triggers,
conditions, and actions, then remove or reorder them. Required trigger and
action lanes cannot become empty, condition-list controls stay disabled when a
condition tree owns that lane, IDs are collision-safe, and every result passes
through the shared proposal validator before becoming visible editor state.

Those top-level structure edits now use source-location patches rather than the
whole-document writer. Add touches only the chosen sequence, remove touches only
the selected item (or converts the final optional condition to `[]`), and move
swaps only adjacent item ranges. Surrounding comments and scalar formatting are
preserved; compact flow-style block lists are rejected as unsupported instead
of being rewritten speculatively, and patched text is validated immediately.

Grouped-action structure edits use the same local approach. Recursive YAML-node
lookup finds groups inside groups, add derives indentation from the existing
child list, remove protects the final child, and move swaps adjacent child
ranges. Flow-style child lists fail closed, nested generated-form lookup now
finds child actions recursively, and the exact patched source returns through
the shared proposal validator.

Variable creation is local as well. It inserts a new `variables` section before
triggers when absent, expands an empty list, or appends to an existing block
list while preserving nearby comments. Secret declarations include only their
local key identifier. Unsupported flow-style lists fail closed. With recursive
config lookup in place, the editor session no longer retains a whole-document
writer fallback for visual form edits.

Top-level add choices now come from capability metadata rather than an editor
enum. A capability opts in with a collision-safe ID base and a complete starter
config; the registry-backed palette currently exposes thirteen context-free trigger,
condition, and action starters. Tests add every published starter to a document
and run the full validator, while capabilities without a safe context-free
starter remain hidden instead of producing an invalid placeholder.

Creation metadata can now derive a nullable starter config from the active
document. Set Variable chooses the first writable primitive declaration,
Increment requires a number, and Toggle requires a boolean; each option vanishes
when no compatible declaration exists. Literal value comparison and conditional
stop starters are also published. Palette tests validate every visible option
and verify that secret-only documents do not expose variable mutation actions.

Visual add controls now open one bounded searchable picker instead of expanding
every capability inline. Search covers the capability name, description, and
stable type. The same registry-backed, document-aware starters drive top-level
lanes, nested action groups, and condition-tree groups; tests insert and validate
every nested option, while wrong-lane templates fail before source mutation.

Capability creation now receives an explicit top-level, action-group, or
condition-group insertion location through one factory contract. Action Group is
the first location-aware starter: it begins with a collision-safe Stop Actions
child, remains validator-clean through four group levels, and is omitted from a
level-four parent's picker so the editor never proposes a forbidden fifth level.

Capabilities may also opt into pre-insert setup instead of publishing an invalid
placeholder. Open Web, Launch App, App Details, Notification Settings, Share
Text, and SMS remain searchable and run the capability's real validator before
the source callback is allowed. Setup reuses generated field controls, so text
can remain literal or use only document-valid variable, secret, or trigger
references; users can switch a reference back to literal text. Unconfigured
templates are rejected again at the editor model boundary, and valid setup works
identically in top-level and nested action pickers.

Value-source fields no longer show only the first three references as inline
buttons. Normal forms and pre-insert setup share one bounded searchable dialog
over every reference already filtered by the document and expected field type;
an empty search result is explicit, and selecting a value returns through the
same config callback used by literal edits.

Exact-package fields can use a searchable installed-app chooser during setup or
later editing while retaining manual package entry. Android queries only normal
launcher activities declared through a launcher-intent visibility query, loads
off the UI thread, deduplicates and sorts packages deterministically, caps the
snapshot at 250 entries, and requests no broad package-list permission. Query
failure simply leaves manual entry available and does not affect macro runtime.

Notification Received is now discoverable through the same typed setup flow. It
starts with only the title field captured, keeps the exact-package filter absent
unless the user adds or selects one, exposes bounded package/title/text capture
choices, and rejects an empty capture set or malformed package before source
mutation. The package validator is shared with other exact-package capabilities.

Time Schedule also uses validated pre-insert setup. Its initial proposal is
fully explicit and portable: 08:00 on weekdays in UTC, windowed delivery, and a
15-minute window. Time, day set, IANA timezone, delivery mode, and window remain
editable through generated controls, and malformed zones or other schedule
values cannot reach the source patch callback.

Schedule setup and existing schedule forms can fill the same explicit timezone
text field from a searchable, sorted, capped snapshot of Java's installed IANA
zone database. Manual entry remains available and the schedule validator stays
authoritative. Timezone, launcher-app, and value-reference result lists render
lazily so large bounded catalogs do not eagerly compose every row.

Once a setup draft is validator-clean, the dialog previews Android access from
the capability's own permission discovery before insertion. SMS shows messaging
access, exact schedule delivery shows exact-alarm access, and windowed delivery
does not. This preview is informational only; permission requests remain in the
existing approval and recovery flows.

Vibrate is a deterministic one-shot action with a generated numeric form and a
250-millisecond starter. Validation requires a whole duration from 1 through
5000 milliseconds before compilation. Android uses `VibrationEffect`, reports
missing hardware or service failure explicitly, and relies only on the normal
manifest vibration permission rather than a runtime access prompt.

Copy Text to Clipboard accepts a bounded literal or validated text value source
and starts with a harmless `Copied by ZeroBit` value. Runtime resolution fails
closed when referenced data is unavailable or not text. Android writes a neutral
clipboard label, while action diagnostics report only generic success or failure
and never include the copied payload.

Battery Charging is a bounded-choice condition for charging or not charging. It
compiles to a boolean expectation and evaluates one current sticky Android
battery snapshot per condition check, treating charging/full and discharging/not-
charging explicitly. Missing or unknown platform status fails with diagnostics;
the evaluator does not poll or retain battery history.

Battery Level reuses one exact whole-percentage threshold model for both triggers
and conditions. Conditions compare against one sticky Android battery snapshot;
triggers react only when a later battery event crosses the approved threshold.
Fractional and out-of-range thresholds fail validation instead of being rounded,
and neither path polls or retains broad battery history.

Power Connection is a separate bounded condition because a plugged-in device is
not always actively charging. It checks one sticky battery snapshot for unplugged,
AC, USB, wireless, or dock power and reports the known source when it blocks an
unplugged-only macro. Unknown platform values fail rather than being guessed.

Screen State checks Android's current interactive state once when conditions run.
It supports explicit screen-on and screen-off choices, needs no permission, and
does not install an observer, poll, or retain screen history.

The original config-free Device Unlocked condition remains valid and still means
unlocked. New source can choose locked or unlocked through one bounded field;
both compile to the same keyguard-backed runtime check with opposite expectations.

Power Disconnected complements the existing Power Connected trigger through
Android's matching system broadcast. Both subscriptions are event-driven,
permission-free, and carry no unnecessary battery snapshot or retained state.

Wi-Fi Connected and Disconnected triggers use one Android default-network
callback per enabled trigger. A small transition tracker suppresses repeated
capability updates and ignores the initial state; cancellation unregisters the
owned callback exactly once. Events expose only `wifi.state`, avoiding SSID and
location-sensitive data, and use the existing network-state permission.

Airplane Mode supports both a current-state condition and a transition trigger.
The condition reads Android's global setting once and accepts only documented
binary values. The trigger consumes Android's change broadcast, filters the
approved target state, and exposes only `airplane_mode.state`; neither path polls,
retains history, or requests additional access.

Ringer Mode uses one typed normal, vibrate, or silent vocabulary across its
condition and transition trigger. The condition reads the audio service once;
the trigger filters Android's ringer-mode broadcast and exposes `ringer.state`.
Unknown platform values fail closed, and no listener, polling, or extra access is
introduced.

Battery Saver also has a current-state condition and target-state trigger. Both
use Android's power service; the trigger owns one dynamic system-broadcast
subscription and rereads the authoritative state when notified because the
broadcast carries no state payload. It exposes only `battery_saver.state`, and
subscription cancellation unregisters its receiver exactly once.

Power Connection keeps its original plugged-in and unplugged source files valid.
An optional bounded source can now narrow plugged-in checks to AC, USB, wireless,
or dock power. Compilation uses a typed source, Android decodes only known
platform constants from one sticky battery snapshot, and conflicting unplugged
plus exact-source files fail validation.

Power Connected and Disconnected retain their config-free source format while
publishing bounded `power.state` context. Connected delivery also reads one
sticky battery snapshot and publishes canonical `power.source` when Android
reports a known AC, USB, wireless, or dock source. If that optional lookup is
unavailable, the connection event still runs and source-dependent resolution
fails through the existing explicit missing-value path.

Time Window shares strict local-time, unique-weekday, and IANA-timezone parsing
with Time Schedule. Its immutable runtime model evaluates one `Instant` against
a half-open local interval: start is included, end is excluded, and overnight
windows belong to their starting weekday. Instant-to-zone conversion makes DST
gaps and overlaps deterministic without scheduling, polling, or retained state.

Dial Number accepts a literal or validated text value source during required
setup. A shared bounded predicate allows common international and service-code
symbols while rejecting URI/control injection, and runtime resolution repeats
that check. Android receives only fixed `ACTION_DIAL` with a `tel` URI assembled
from parts; ZeroBit never uses `ACTION_CALL` and requests no call permission.

Compose Email requires recipient, subject, and body text sources during setup.
Literal recipients pass a conservative single-address validator before
compilation, and referenced recipients are checked after runtime resolution;
subject and body lengths are bounded at both points. Android receives only
fixed `ACTION_SENDTO` with a `mailto` URI assembled from the validated address
plus standard subject and body extras. ZeroBit never sends the message and
requests no email-account permission.

Open Map Location stores only a bounded literal or referenced location query.
Runtime resolution rejects blank, oversized, or control-bearing text, then
percent-encodes the query into ZeroBit's fixed `geo:0,0?q=` shape and opens it
with `ACTION_VIEW`. Macro source cannot supply an arbitrary URI, and the action
does not read device location or request location permission.

Set Alarm accepts whole-number hour and minute fields, an optional bounded
label, and an explicit skip-UI boolean. The compiled runtime step maps only
those values to Android's fixed `ACTION_SET_ALARM` extras. The app declares the
normal `SET_ALARM` intent permission but receives no access to existing alarms
or clock data, and macro source cannot add ringtone, repeat, package, or other
intent fields.

Set Timer accepts a whole-number duration from 1 to 86400 seconds, an optional
bounded label, and an explicit skip-UI boolean. It compiles only to
`ACTION_SET_TIMER` with length, message, and skip-UI extras, reusing the normal
clock intent permission without reading timer or alarm state.

Existing variable declarations now have focused visual controls for optional
text, number, and boolean initial values and for secret-key identifiers. These
controls patch only the declaration field in source and immediately run the
shared proposal validator. They never display or edit the secret value. New
declarations can also be created from bounded text, number, boolean, and secret
templates with collision-safe names and valid defaults. Renaming a declaration
uses local source patches to update the declaration, variable action targets,
and `{variable: ...}` value references together, including nested action-group
children. Deleting a declaration is allowed only when no block targets it and no
value source references it.
