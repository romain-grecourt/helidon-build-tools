# Repository Guidelines

## Project Structure & Module Organization

The root `pom.xml` aggregates the main modules: `common/` shared
utilities, `maven-enforcer-rules/`, `maven-plugins/`, `cli/`,
`archetype/`, `linker/`, `dev-loop/`, and `licensing/`. Java code
follows the standard Maven layout:
`src/main/java` for production code and `src/test/java` for unit tests.
Maven plugin integration testing lives in `src/it`. Fixture projects
also live under paths such as `cli/tests/functional/src/it/projects/`.
Generated outputs under `target/` and `target/it/` should not be edited
or committed.

## Build, Test, and Development Commands

Use JDK 17 and Maven 3.8.2+ for local work.

- `mvn install` builds the full multi-module repository.
- `mvn -pl <module> -am test` runs unit tests for one module and its
  dependencies.
- `mvn -pl <module> -am verify` is the right pre-PR check when you
  touch Maven plugin `src/it`, Maven plugins, or linker/dev-loop
  integration code.
- `mvn validate -Pcheckstyle` runs the repository Checkstyle rules.
- `mvn validate -Pcopyright` checks license headers against `etc/copyright.txt`.
- `mvn verify -Pspotbugs` runs static analysis.

## Coding Style & Naming Conventions

Java style is enforced by `etc/checkstyle.xml`: 4-space indentation, LF
line endings, 130-character lines, and import groups ordered as `java`,
`javax`, then `io.helidon`.

Keep packages under `io.helidon.build...`, use `UpperCamelCase` for
types and `lowerCamelCase` for members, and prefer one top-level type
per file.

Public and protected APIs are expected to have Javadoc. Inline comments
are not capitalized and do not end with a period. Javadoc comments are
capitalized and end with a period. Test classes use `*Test`;
reserve `src/it` for Maven plugin integration tests.

For Markdown and other prose docs, wrap lines at 80 characters.
Prefer fenced code blocks for commands, and leave an empty line after
headings such as `#` and `##`. When a shell command is split across
lines, use trailing `\` continuations on argument lines.

## Testing Guidelines

JUnit 5 is the default test framework, with Hamcrest used heavily for
assertions; some IDE support tests also use Mockito. There is no
repository-wide coverage percentage gate in the build, so add or update
tests for each behavior change instead of aiming at a numeric target.
If you change linker behavior, expect CI to exercise that area on JDK
25 as well.

## Commit & Pull Request Guidelines

Recent history favors short, imperative subjects, sometimes with a
scope prefix, for example `4.x: Fix archetype integration test` or
`Linker code cleanup`. Before opening a PR, create or link the tracking
issue, use a branch name that includes the issue number. PR
descriptions should explain the change, list the validation commands
you ran, and link the issue.
