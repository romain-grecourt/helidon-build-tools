# Archetype Regression Workflow

## Wrapper Entry Point

Run the workflow through:

```text
.agents/skills/helidon-archetype-regression/scripts/run-regression.sh
```

That wrapper keeps the regression state under the skill directory and gives
the user one stable approval prefix instead of a long sequence of ad hoc
commands.

## Required Inputs

- Run the build-tools install steps from the `helidon-build-tools` repo.
- Require the Helidon checkout location explicitly through
  `--helidon-dir <path>`.
- When the skill is triggered and the Helidon location is unknown, ask the
  user for it before starting the regression workflow.
- Verify that `<helidon-dir>/archetypes/archetypes/pom.xml` exists before
  running generation commands.

Do not default `--helidon-dir` to `../helidon`. The wrapper should fail
early with a usage error when it is missing.

## Script Interface

```text
Usage:
  run-regression.sh <diff_variations|diff_projects|compile_gate|all> \
      [options]

Options:
  --baseline-ref <rev>        Baseline git ref to install first
                              Default: HEAD
  --helidon-dir <path>        Required Helidon checkout location
  --state-dir <path>          Override the default .state root directory
  --threshold-seconds <n>     Compile gate threshold, default 5
  --variations-threshold-seconds <n>
                              Variation timing gate threshold, default 15
  --verbose                   Echo commands as they run
  --no-restore-current        Skip reinstalling current workspace at end
```

## Default State Layout

The wrapper keeps its state root under:

```text
.agents/skills/helidon-archetype-regression/.state/
```

Each invocation creates one unique run directory under that root, for
example:

```text
.agents/skills/helidon-archetype-regression/.state/20260325-123456-12345/
```

The important subdirectories inside each run directory are:

- `logs/` for install, generation, compare, and timing logs
- `worktrees/baseline/` for the detached baseline checkout
- `snapshots/diff_variations/{baseline,actual}/`
- `snapshots/diff_projects/{baseline,actual}/`

The wrapper hard-caps the state root at 10 run directories. When a new run
would exceed that cap, it prunes the oldest run directories first.

The nested `.gitignore` should ignore `.state/`.

## Baseline Source

Use one dedicated detached worktree under:

```text
.state/<run-id>/worktrees/baseline
```

That worktree exists only to install the clean baseline build-tools into
`~/.m2`. Do not use the modified workspace as the baseline install source.

The wrapper should:

1. Resolve `--baseline-ref` to a commit.
2. Create the baseline worktree inside the current run directory.
3. Reuse that worktree within the same invocation when both diff modes run,
   such as in `all`.

## Build-Tools Install Command

Use the narrow install that rebuilds the archetype plugin and its
dependencies, including `helidon-archetype-engine-v2`:

```sh
mvn -pl maven-plugins/helidon-archetype-maven-plugin \
    -am \
    install \
    -DskipTests
```

Run that once from the baseline worktree before baseline generation and
again from the current workspace before the actual generation.

## Mode: `compile_gate`

Use this mode to check compiler complexity.

The wrapper should:

1. Install the current workspace into `~/.m2`.
2. Run:

```sh
mvn -f archetypes/archetypes/pom.xml \
    compile \
    -e \
    -Dhelidon.build.archetype.engine.v2.debugReduction=true
```

3. Capture the wall-clock time from `/usr/bin/time -p`.
4. Fail when the measured time exceeds `--threshold-seconds`.
5. Report the measured wall-clock time and threshold in the summary.

## Mode: `diff_variations`

Use this mode when the change affects variation computation but not
generated project contents. This is also the required gate before
interpreting `diff_projects`.

The wrapper should:

1. Prepare the baseline worktree at `--baseline-ref`.
2. Install the baseline build-tools into `~/.m2`.
3. Run:

```sh
mvn -f archetypes/archetypes/pom.xml \
    clean \
    install \
    -Darchetype.test.variationsOnly=true \
    -Darchetype.test.maxVariations=-1
```

4. Copy `target/tests/projects.csv` into the baseline snapshot directory.
5. Install the current workspace into `~/.m2`.
6. Re-run the same generation command.
7. Copy `target/tests/projects.csv` into the actual snapshot directory.
8. Compare the copied snapshots with:

```sh
./archetypes/archetypes/etc/projects-diff.sh \
    --orig=/path/to/baseline \
    --actual=/path/to/actual \
    diff_csv
```

Do not diff against the live Helidon `target/` tree after another run has
started.
9. Capture the current-workspace wall-clock time from `/usr/bin/time -p`
   and fail when it exceeds `--variations-threshold-seconds`
   (default `15`).

## Mode: `diff_projects`

Use this mode when the change can affect generated files, including
`ScriptCompiler`, engine-v2 runtime behavior, or normalization logic.

Always run the `diff_variations` gate first. If `projects.csv` changes or
the variation timing gate fails, stop there and do not inspect project
tree diffs yet.

The wrapper should:

1. Run the `diff_variations` gate first in the same invocation when it
   has not already passed.
2. Prepare the baseline worktree at `--baseline-ref`.
3. Install the baseline build-tools into `~/.m2`.
4. Run:

```sh
mvn -f archetypes/archetypes/pom.xml \
    clean \
    install \
    -Darchetype.test.generateOnly=true \
    -Darchetype.test.parallelGeneration=true \
    -Darchetype.test.maxVariations=-1
```

5. Copy `target/tests/` into the baseline snapshot directory.
6. Install the current workspace into `~/.m2`.
7. Re-run the same generation command.
8. Copy `target/tests/` into the actual snapshot directory.
9. Compare the copied snapshots with `projects-diff.sh diff_csv` and
   `projects-diff.sh diff_projects`.
If the helper fails, treat that as a regression workflow failure and report
the helper output from the compare log.

The baseline side disables `archetype.test.maxVariations` explicitly so
cross-version comparisons still work when the Helidon POM already uses
the newer exact-count semantics and the baseline build-tools do not.

## Mode: `all`

`all` must run these checks in order and fail fast:

1. `compile_gate`
2. `diff_variations`
3. `diff_projects`

## Reporting

Always report:

- the pass/fail status for each mode that ran
- the measured wall-clock time and threshold for `compile_gate`
- whether outputs changed for the diff modes
- that `~/.m2` contains the current workspace install at the end, unless
  `--no-restore-current` was used
