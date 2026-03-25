# Archetype Regression Workflow

## Repo Assumptions

- Run the build-tools install commands from the `helidon-build-tools` repo.
- Use the sibling `helidon` checkout for archetype generation. The expected
  path is usually `../helidon` from the build-tools repo root.
- Verify that `archetypes/archetypes/pom.xml` exists before running
  generation commands.

## Baseline Source

Create a clean baseline source from `HEAD` without the local
modifications that are under test.

Preferred approach:

```sh
git worktree add --detach /tmp/helidon-build-tools-baseline HEAD
```

Acceptable fallback:

```sh
git clone /path/to/helidon-build-tools /tmp/helidon-build-tools-baseline
```

The baseline source exists only to install the clean build-tools artifacts into
`~/.m2`. Do not reuse the modified worktree for the baseline install.

## Install Build-Tools Into ~/.m2

Prefer the narrow install that rebuilds the archetype plugin and its
dependencies, including `helidon-archetype-engine-v2`:

```sh
mvn -pl maven-plugins/helidon-archetype-maven-plugin \
    -am \
    install \
    -DskipTests
```

Run that command once from the baseline source before baseline generation, then
run the same command again from the modified worktree before actual generation.

If that targeted installation ever stops being sufficient because the dependency
graph changes, fall back to a wider repository install and keep the same
baseline-then-modified order.

## Compare Variation-Only Changes

Use this path when the change affects variation computation but not
generated project contents.

Generate the baseline:

```sh
mvn -f archetypes/archetypes/pom.xml \
    clean \
    install \
    -Darchetype.test.variationsOnly=true
```

Save the baseline `projects.csv`, reinstall the modified build-tools into
`~/.m2`, rerun the same command, and compare the two files with:

```sh
./archetypes/archetypes/etc/projects-diff.sh \
    --orig=/path/to/baseline \
    --actual=/path/to/actual \
    diff_csv
```

Use `variationsOnly` here because the CSV diff compares computed variations,
not generated project directories.

## Compare Runtime Or Normalization Changes

Use this path when the change can affect generated files, including
`ScriptCompiler` and normalization work.

Generate the baseline:

```sh
mvn -f archetypes/archetypes/pom.xml \
    clean \
    install \
    -Darchetype.test.generateOnly=true \
    -Darchetype.test.parallelGeneration=true
```

Save `archetypes/archetypes/target/tests` outside the repo, reinstall the
modified build-tools into `~/.m2`, rerun the same command, and compare with:

```sh
./archetypes/archetypes/etc/projects-diff.sh \
    --orig=/path/to/baseline-tests \
    --actual=/path/to/actual-tests \
    diff_csv
./archetypes/archetypes/etc/projects-diff.sh \
    --orig=/path/to/baseline-tests \
    --actual=/path/to/actual-tests \
    diff_projects
```

The helper now defaults to the current generated project prefix, `test-project`.
Only pass `--project-dir=...` when comparing a non-standard directory prefix.
Use `generateOnly` here because the regression compares generated files in
`target/tests`; invoking Maven inside each generated project does not change the
diff input.

## Snapshot Guidance

- Copy `target/tests` or `projects.csv` to a stable directory before
  the next generation run.
- Do not diff directly against the live `target/tests` tree after
  another generation has started.

## Reporting

Always report:

- whether `projects.csv` changed
- whether generated projects changed
- any filtered noise that was ignored after inspection
- that `~/.m2` was restored to the modified build-tools install at the end
