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
