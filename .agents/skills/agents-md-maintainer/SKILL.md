---
name: agents-md-maintainer
description: Maintain concise AGENTS.md repository instructions. Use when adding, editing, reviewing, or deciding whether to update AGENTS.md or agent-facing repo guidance after feature, CI, branch, release, build, test, or workflow changes.
---

# AGENTS.md Maintainer

Maintain `AGENTS.md` as a concise starter instruction set for future coding agents.

## Purpose

`AGENTS.md` should contain durable repo conventions, branch policy, build/release/debug workflows, verification expectations, and safety rules that future agents need before doing work.

Assume future agents are capable. Do not explain generic agent behavior, tool usage, or how to inspect the repo unless this repository has a specific non-obvious workflow.

## Editing Rules

- Keep additions terse.
- Prefer one precise bullet over a paragraph.
- Preserve existing meaning unless explicitly asked to revise it.
- Do not reorganize or rewrite broad sections for a small addition.
- Do not remove nuance, warnings, or conditional wording without explicit permission.
- Avoid duplicating information already present.
- Add guidance only when it is likely to matter for future work.
- Do not include temporary debug notes, one-off commands, stale branch names, or personal narration.

## When To Update

Update `AGENTS.md` when a change creates or changes durable expectations, such as:

- branch, rebase, or release policy
- hosted debug or release workflows
- required package names or artifact checks
- device-testing conventions
- build limitations or preferred CI paths
- recurring cleanup or generated-file hazards

Do not update `AGENTS.md` for ordinary implementation details, temporary experiments, or feature internals that are obvious from code.

## Git Hygiene

Before editing, inspect the current branch and dirty state.

## Output

After editing, summarize:

- what guidance changed
- why it belongs in `AGENTS.md`
- whether existing guidance was intentionally preserved
