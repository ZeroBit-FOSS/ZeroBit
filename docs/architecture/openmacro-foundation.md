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

To preserve user-owned comments and formatting, the future YAML adapter should
retain a syntax tree and source ranges. Visual changes should patch the
affected field where possible. A canonical formatter is the explicit fallback,
not an automatic side effect of merely opening a file.

## Safe edit flow

1. Keep the last approved document and its content hash.
2. Parse a proposed source or visual edit.
3. Validate the document structure and each registered capability.
4. Explain the behavioral and permission difference from the approved version.
5. Ask for approval when runnable behavior changes.
6. Compile the approved model into an immutable runtime plan.
7. Execute only that plan and record bounded diagnostic events.

The runtime never interprets YAML directly and never asks AI what a block means.

## First implementation boundary

The initial implementation contains:

- the format-neutral document model and validator;
- a capability registry divided into the three visual lanes;
- three small built-in capabilities covering a trigger, condition, and action;
- field descriptions that a visual form can render;
- capability validation, explanations, and permission discovery; and
- compilation into immutable runtime instructions.

This proves that one capability definition can drive the code shape, future
form, validation, explanation, permissions, and runtime plan without premature
plugin machinery. The next slice should add YAML parsing and canonical writing,
including duplicate-key rejection and source-preservation tests, before either
editor becomes responsible for real user files.
