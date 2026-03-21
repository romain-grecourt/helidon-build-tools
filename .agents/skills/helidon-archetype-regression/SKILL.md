---
name: helidon-archetype-regression
description: >-
  Verify Helidon archetype outputs after changes to archetype/engine-v2,
  ScriptCompiler, normalization logic, or archetype variation computation. Use
  when a task must prove that generated archetype projects or projects.csv stay
  unchanged, including baseline creation from clean sources, reinstalling
  baseline and modified build-tools into the default ~/.m2 repository,
  regenerating outputs from the sibling helidon repo, and comparing results
  with the projects-diff.sh helper.
---

# Helidon Archetype Regression

## Overview

Use this skill when engine-v2 or other archetype-facing build-tools changes
might alter generated Helidon archetype outputs. Prefer the repo's own
generation and diff workflow over ad hoc spot checks.

## Decide Scope

- Compare only `projects.csv` when the change affects variation
  computation but not runtime project generation.
- Compare full `target/tests` when the change touches `ScriptCompiler`,
  engine-v2 runtime behavior, normalization, or anything else that can change
  generated files.
- Prefer the full-project workflow when the scope is uncertain.

## Guardrails

- Use the default `~/.m2` repository unless the user explicitly asks
  for a different local repository strategy.
- Reinstall the intended build-tools version before every generation. Do not
  assume `~/.m2` still contains the right engine-v2 build.
- Snapshot `target/tests` or `projects.csv` before the next
  generation. Re-runs overwrite the previous result.
- Restore the modified build-tools install at the end so the
  workspace is left in the expected state.
- If the diff helper reports unexpected changes, inspect
  representative files manually before concluding that the change is
  real.

## Workflow

1. Locate the sibling `helidon` repo and verify
   `archetypes/archetypes/pom.xml` exists.
2. Create a clean baseline source for `helidon-build-tools` at `HEAD` without
   local changes. Prefer a detached worktree or a local clone.
3. Install baseline build-tools into `~/.m2`.
4. In the `helidon` repo, generate the baseline output for the chosen scope.
5. Save the generated `projects.csv` or `target/tests` outside the repo.
6. Reinstall the modified build-tools from the current worktree into `~/.m2`.
7. Regenerate the same output in the `helidon` repo.
8. Diff the baseline and actual results with the helper script.
9. Report whether outputs changed and state that `~/.m2`
   currently contains the modified build-tools install.

## Reference

Read `references/workflow.md` for the exact commands,
comparison modes, and helper-script usage.
