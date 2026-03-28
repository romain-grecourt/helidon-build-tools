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

If a task might need this workflow and the Helidon archetype checkout
location is not already known, ask the user for that location before
starting the regression work. Do not assume a default sibling checkout.

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
- Always run `diff_variations` before `diff_projects`. If `projects.csv`
  changes or the variation timing gate fails, stop there and do not
  interpret project-tree diffs yet.
- Run the workflow through
  `.agents/skills/helidon-archetype-regression/scripts/run-regression.sh`
  instead of retyping the Maven commands by hand.
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

1. Obtain the Helidon archetype checkout location from the user if it is
   not already known, then verify `archetypes/archetypes/pom.xml` exists
   there.
2. Choose the wrapper mode:
   - `compile_gate` for the archetype compiler timing check
   - `diff_variations` for `projects.csv` coverage-only checks and the
     `< 15s` variation timing gate
   - `diff_projects` for generated-project snapshot checks, but only
     after `diff_variations` passes
   - `all` when the scope is uncertain or the task needs every check
3. Run
   `.agents/skills/helidon-archetype-regression/scripts/run-regression.sh`
   with `--helidon-dir <path>` and, when needed, `--baseline-ref <rev>`.
4. Review the wrapper summary and any artifacts under
   `.agents/skills/helidon-archetype-regression/.state/<run-id>/`.
5. Report whether outputs changed and whether `~/.m2` was restored to the
   current workspace install, unless `--no-restore-current` was used.

## Reference

Read `references/workflow.md` for the exact commands,
wrapper usage, required `--helidon-dir` usage, state layout, and
helper-compatibility details.
