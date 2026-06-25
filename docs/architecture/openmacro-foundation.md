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

## Generated capability forms

Capability field metadata now produces editor-facing form models with labels,
help, required and advanced state, current values, and valid reference choices.
Text fields receive only text-compatible choices; dynamic notification outputs
appear only when that macro requested them. Unsupported capability blocks remain
preserved and return no editable form rather than being approximated.

Model-level block edits can update or remove configuration values across normal
lanes and nested condition trees. The remaining editor step is a targeted YAML
source patcher so visual edits preserve comments and surrounding formatting
instead of invoking the canonical formatter.

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

Workspace operations now sit behind a `MacroWorkspaceStore` port so a future
Storage Access Framework adapter can replace direct paths without changing
review and approval logic. The port has explicit list, read, write, and delete
outcomes. Rename is a higher-level mutation: it refuses overwrite, patches the
declared macro ID without reformatting comments, writes the new file, then
removes the old one with rollback if that final step fails. Approval history
remains app-private throughout.

Existing variable declarations now have focused visual controls for optional
text, number, and boolean initial values and for secret-key identifiers. These
controls patch only the declaration field in source and immediately run the
shared proposal validator. They never display or edit the secret value. Adding,
renaming, and deleting declarations remains separate work because those
operations must update or reject references atomically.
