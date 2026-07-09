/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.build.maven.stager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.helidon.build.common.CurrentThreadExecutorService;
import io.helidon.build.common.Unchecked;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link StagingTask}.
 */
class StagingTaskTest {

    @TempDir
    private Path tempDir;

    @Test
    void testIterator() {
        Variables variables = new Variables();
        variables.add(new Variable("foo", new VariableValue.ListValue("foo1", "foo2")));
        ActionIterators taskIterators = new ActionIterators(List.of(new ActionIterator(variables)), null);
        List<String> renderedTargets = new LinkedList<>();
        StagingTask task = new StagingTask(null, null, taskIterators, Map.of("target", "{foo}")) {
            @Override
            protected void doExecute(StagingContext ctx, Path dir, Map<String, String> vars) {
                renderedTargets.add(resolveVar(target(), vars));
            }
        };
        StagingContext context = new TestContextImpl(new CurrentThreadExecutorService());
        task.execute(context, null, Map.of());
        assertThat(renderedTargets, hasItems("foo1", "foo2"));
    }

    @Test
    void testIteratorsWithManyVariables() {
        Variables variables = new Variables();
        variables.add(new Variable("foo", new VariableValue.ListValue("foo1", "foo2", "foo3")));
        variables.add(new Variable("bar", new VariableValue.ListValue("bar1", "bar2")));
        variables.add(new Variable("bob", new VariableValue.ListValue("bob1", "bob2", "bob3", "bob4")));
        ActionIterators taskIterators = new ActionIterators(List.of(new ActionIterator(variables)), null);
        List<String> renderedTargets = new LinkedList<>();
        StagingTask task = new StagingTask(null, null, taskIterators, Map.of("target", "{foo}-{bar}-{bob}")) {
            @Override
            protected void doExecute(StagingContext ctx, Path dir, Map<String, String> vars) {
                renderedTargets.add(resolveVar(target(), vars));
            }
        };
        StagingContext context = new TestContextImpl(new CurrentThreadExecutorService());
        task.execute(context, null, Map.of());
        assertThat(renderedTargets, hasItems(
                "foo1-bar1-bob1", "foo1-bar1-bob2", "foo1-bar1-bob3", "foo1-bar1-bob4",
                "foo1-bar2-bob1", "foo1-bar2-bob2", "foo1-bar2-bob3", "foo1-bar2-bob4",
                "foo2-bar1-bob1", "foo2-bar1-bob2", "foo2-bar1-bob3", "foo2-bar1-bob4",
                "foo2-bar2-bob1", "foo2-bar2-bob2", "foo2-bar2-bob3", "foo2-bar2-bob4",
                "foo3-bar1-bob1", "foo3-bar1-bob2", "foo3-bar1-bob3", "foo3-bar1-bob4",
                "foo3-bar2-bob1", "foo3-bar2-bob2", "foo3-bar2-bob3", "foo3-bar2-bob4"));
    }

    @Test
    void testTemplateEmptyVariable() throws Exception {
        Files.writeString(tempDir.resolve("template.mustache"), "{{version}}");
        Path outputDir = tempDir.resolve("stage");
        TemplateTask task = new TemplateTask(null,
                Map.of("source", "template.mustache", "target", "index.txt"),
                List.of(new Variable("version", new VariableValue.EmptyValue("version"))));
        StagingContext context = new StagingContext() {

            @Override
            public Path resolve(String path) {
                return tempDir.resolve(path);
            }

            @Override
            public void ensureDirectory(Path directory) {
                try {
                    Files.createDirectories(directory);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public Executor executor() {
                return new CurrentThreadExecutorService();
            }
        };

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> task.execute(context, outputDir, Map.of()).toCompletableFuture().get());

        assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));
        assertThat(ex.getCause().getMessage(), containsString("version"));
        Path target = outputDir.resolve("index.txt");
        if (Files.exists(target)) {
            assertThat(Files.readString(target), is(""));
        }
    }

    @Test
    void testTemplateReplaceHelper() throws Exception {
        List<Variable> variables = List.of(
                new Variable("version", new VariableValue.SimpleValue("4.0.0-SNAPSHOT")),
                new Variable("suffix", new VariableValue.SimpleValue("-SNAPSHOT")),
                new Variable("qualifier", new VariableValue.SimpleValue("-RC1")),
                new Variable("release", new VariableValue.MapValue(List.of(
                        new Variable("version", new VariableValue.SimpleValue("5.0.0-SNAPSHOT"))))));
        String output = executeTemplate("""
                version={{replace version "-SNAPSHOT" "-dev"}}
                release={{replace version "-SNAPSHOT" ""}}
                dynamic={{replace version suffix qualifier}}
                literal={{replace "4.0.0-SNAPSHOT" "-SNAPSHOT" ""}}
                dotted={{replace release.version "-SNAPSHOT" ""}}
                missing={{replace missing "-SNAPSHOT" ""}}
                escaped={{replace "4.0.0-\\"SNAPSHOT\\"" "\\\"SNAPSHOT\\"" "dev"}}
                """, variables);

        assertThat(output, is("""
                version=4.0.0-dev
                release=4.0.0
                dynamic=4.0.0-RC1
                literal=4.0.0
                dotted=5.0.0
                missing=
                escaped=4.0.0-dev
                """));
    }

    @Test
    void testTemplateReplaceHelperRequiresThreeArguments() throws Exception {
        Files.writeString(tempDir.resolve("template.mustache"), "{{replace version \"-SNAPSHOT\"}}");
        Path outputDir = tempDir.resolve("stage");
        TemplateTask task = new TemplateTask(null,
                Map.of("source", "template.mustache", "target", "index.txt"),
                List.of(new Variable("version", new VariableValue.SimpleValue("4.0.0-SNAPSHOT"))));

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> task.execute(templateContext(), outputDir, Map.of()).toCompletableFuture().get());

        assertThat(ex.getCause().getCause().getMessage(), containsString("replace requires exactly 3 arguments"));
    }

    @Test
    void testTemplateReplaceHelperRejectsExtraArguments() {
        List<Variable> variables = List.of(new Variable("version", new VariableValue.SimpleValue("4.0.0-SNAPSHOT")));

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> executeTemplate("{{replace version \"-SNAPSHOT\" \"\" extra}}", variables));

        assertThat(ex.getCause().getCause().getMessage(), containsString("replace requires exactly 3 arguments"));
    }

    @Test
    void testTemplateRepeatedListsConcatenateInOrder() throws Exception {
        List<Variable> variables = List.of(
                new Variable("versions", new VariableValue.ListValue("2.0.0", "3.0.0")),
                new Variable("empty", new VariableValue.ListValue(List.of())),
                new Variable("versions", new VariableValue.ListValue(List.of())),
                new Variable("versions", new VariableValue.ListValue("4.0.0")),
                new Variable("empty", new VariableValue.ListValue(List.of())));

        String output = executeTemplate("{{#versions}}{{value}},{{/versions}}{{#empty}}unexpected{{/empty}}", variables);

        assertThat(output, is("2.0.0,3.0.0,4.0.0,"));
    }

    @Test
    void testTemplateRepeatedEqualScalarsCoalesce() throws Exception {
        String output = executeTemplate("{{version}}", List.of(
                new Variable("version", new VariableValue.SimpleValue("4.0.0")),
                new Variable("version", new VariableValue.SimpleValue("4.0.0"))));

        assertThat(output, is("4.0.0"));
    }

    @Test
    void testTemplateRepeatedEmptyValuesRemainInvalid() {
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> executeTemplate("{{versions}}", List.of(
                        new Variable("versions", new VariableValue.EmptyValue("versions")),
                        new Variable("versions", new VariableValue.EmptyValue("versions")))));

        assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));
        assertThat(ex.getCause().getMessage(), containsString("versions"));
    }

    @Test
    void testTemplateRepeatedMapsMergeRecursively() throws Exception {
        List<Variable> variables = List.of(
                new Variable("release", new VariableValue.MapValue(List.of(
                        new Variable("version", new VariableValue.SimpleValue("4.0.0")),
                        new Variable("versions", new VariableValue.ListValue("2.0.0", "3.0.0")),
                        new Variable("coordinates", new VariableValue.MapValue(List.of(
                                new Variable("groupId", new VariableValue.SimpleValue("io.helidon")))))))),
                new Variable("release", new VariableValue.MapValue(List.of(
                        new Variable("version", new VariableValue.SimpleValue("4.0.0")),
                        new Variable("channel", new VariableValue.SimpleValue("stable")),
                        new Variable("versions", new VariableValue.ListValue("4.0.0")),
                        new Variable("coordinates", new VariableValue.MapValue(List.of(
                                new Variable("artifactId", new VariableValue.SimpleValue("helidon-bom")))))))));

        String output = executeTemplate(
                "{{release.version}}:{{release.channel}}:{{release.coordinates.groupId}}:"
                        + "{{release.coordinates.artifactId}}:"
                        + "{{#release.versions}}{{.}},{{/release.versions}}",
                variables);

        assertThat(output, is("4.0.0:stable:io.helidon:helidon-bom:2.0.0,3.0.0,4.0.0,"));
    }

    @Test
    void testTemplateConflictingScalarsFail() {
        assertRepeatedTemplateVariablesConflict("version",
                new VariableValue.SimpleValue("1.0.0"),
                new VariableValue.SimpleValue("2.0.0"));
    }

    @Test
    void testTemplateNestedMapConflictsUseQualifiedName() {
        assertRepeatedTemplateVariablesConflict("release.version",
                new VariableValue.MapValue(List.of(
                        new Variable("version", new VariableValue.SimpleValue("1.0.0")))),
                new VariableValue.MapValue(List.of(
                        new Variable("version", new VariableValue.SimpleValue("2.0.0")))));
    }

    @Test
    void testTemplateMixedTypeConflictsUseQualifiedName() {
        assertRepeatedTemplateVariablesConflict("release.version",
                new VariableValue.MapValue(List.of(
                        new Variable("version", new VariableValue.SimpleValue("1.0.0")))),
                new VariableValue.MapValue(List.of(
                        new Variable("version", new VariableValue.ListValue("2.0.0")))));
    }

    @Test
    void testHandleRetry() throws InterruptedException {
        StagingTask task = new StagingTask() {

            @Override
            protected CompletableFuture<Void> execBody(StagingContext ctx, Path dir, Map<String, String> vars) {
                return handleRetry(() -> doExecBody(ctx, dir, vars), ctx, 1, 3);
            }

            @Override
            protected void doExecute(StagingContext ctx, Path dir, Map<String, String> vars) {
                throw new UnsupportedOperationException("boo");
            }
        };
        StagingContext context = new TestContextImpl(new CurrentThreadExecutorService());
        try {
            task.execute(context, null, Map.of()).toCompletableFuture().get();
            Assertions.fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(UnsupportedOperationException.class)));
        }
    }

    @Test
    void testHandleTimeout() throws InterruptedException {
        StagingTask task = new StagingTask() {

            @Override
            protected CompletableFuture<Void> execBody(StagingContext ctx, Path dir, Map<String, String> vars) {
                return handleTimeout(() -> doExecBody(ctx, dir, vars), ctx, 500, 0);
            }

            @Override
            protected void doExecute(StagingContext ctx, Path dir, Map<String, String> vars) {
                sleep(60);
            }
        };
        StagingContext context = new TestContextImpl(Executors.newSingleThreadExecutor());
        try {
            task.execute(context, null, Map.of()).toCompletableFuture().get();
            Assertions.fail("task should timeout");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(TimeoutException.class)));
        }
    }

    @Test
    void testNoFailFast() throws InterruptedException {
        AtomicInteger count = new AtomicInteger();
        Function<Throwable, Void> exceptionally = ex -> {
            count.incrementAndGet();
            throw Unchecked.wrap(ex);
        };
        StagingTask subTask1 = new StagingTask() {

            @Override
            public CompletionStage<Void> execute(StagingContext ctx, Path dir, Map<String, String> vars) {
                return super.execute(ctx, dir, vars).exceptionally(exceptionally);
            }

            @Override
            protected void doExecute(StagingContext ctx, Path dir, Map<String, String> vars) {
                sleep(5); // sleep to ensure the 2nd sub-task fails first
                throw new IllegalStateException();
            }
        };
        StagingTask task = withFailedSubTask(exceptionally, subTask1);
        StagingContext context = new TestContextImpl(Executors.newCachedThreadPool());
        try {
            task.execute(context, null, Map.of()).toCompletableFuture().get();
            Assertions.fail("task should fail");
        } catch (ExecutionException e) {
            assertThat(count.get(), is(2));
            assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
        }
    }

    @Test
    void testFailedSiblingNoJoin() throws InterruptedException {
        StagingTask task = new StagingTask(null, List.of(
                new StagingTask() {

                    @Override
                    protected void doExecute(StagingContext ctx, Path dir, Map<String, String> vars) {
                        throw new IllegalStateException();
                    }
                }, new StagingTask()),
                null, null);
        StagingContext context = new TestContextImpl(Executors.newCachedThreadPool());
        try {
            task.execute(context, null, Map.of()).toCompletableFuture().get();
            Assertions.fail("task should fail");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
        }
    }

    @Test
    void testFailedJoin() throws InterruptedException {
        List<Integer> list = Collections.synchronizedList(new LinkedList<>());
        StagingTask task = new StagingTask(null, List.of(
                new StagingTask() {

                    @Override
                    protected void doExecute(StagingContext ctx, Path dir, Map<String, String> vars) {
                        list.add(1);
                        throw new IllegalStateException();
                    }
                }, new StagingTask(null, null, null, Map.of("join", "true")) {
                    @Override
                    protected void doExecute(StagingContext ctx, Path dir, Map<String, String> vars) {
                        list.add(2);
                    }
                }),
                null, null);
        StagingContext context = new TestContextImpl(Executors.newCachedThreadPool());
        try {
            task.execute(context, null, Map.of()).toCompletableFuture().get();
            Assertions.fail("task should fail");
        } catch (ExecutionException e) {
            assertThat(list, is(List.of(1)));
            assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
        }
    }

    @Test
    void testJoin() throws InterruptedException, ExecutionException {
        List<Integer> list = Collections.synchronizedList(new LinkedList<>());
        StagingTask task = new StagingTask(null, List.of(
                new StagingTask() {

                    @Override
                    protected void doExecute(StagingContext ctx, Path dir, Map<String, String> vars) {
                        sleep(5);
                        list.add(1);
                    }
                }, new StagingTask(null, null, null, Map.of("join", "true")) {
                    @Override
                    protected void doExecute(StagingContext ctx, Path dir, Map<String, String> vars) {
                        list.add(2);
                    }
                }),
                null, null);
        StagingContext context = new TestContextImpl(Executors.newCachedThreadPool());
        task.execute(context, null, Map.of()).toCompletableFuture().get();
        assertThat(list, is(List.of(1, 2)));
    }

    static StagingTask withFailedSubTask(Function<Throwable, Void> exceptionally, StagingTask subTask1) {
        return new StagingTask(null, List.of(subTask1, new StagingTask() {

            @Override
            public CompletionStage<Void> execute(StagingContext ctx, Path dir, Map<String, String> vars) {
                return super.execute(ctx, dir, vars).exceptionally(exceptionally);
            }

            @Override
            protected void doExecute(StagingContext ctx, Path dir, Map<String, String> vars) {
                throw new IllegalStateException();
            }
        }), null, null);
    }

    @SuppressWarnings("SameParameterValue")
    String executeTemplate(String template, List<Variable> variables) throws Exception {
        Files.writeString(tempDir.resolve("template.mustache"), template);
        Path outputDir = tempDir.resolve("stage");
        TemplateTask task = new TemplateTask(null,
                Map.of("source", "template.mustache", "target", "index.txt"),
                variables);

        task.execute(templateContext(), outputDir, Map.of()).toCompletableFuture().get();
        return Files.readString(outputDir.resolve("index.txt"));
    }

    void assertRepeatedTemplateVariablesConflict(String path, VariableValue first, VariableValue second) {
        int separator = path.indexOf('.');
        String name = separator < 0 ? path : path.substring(0, separator);
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> executeTemplate("unused", List.of(new Variable(name, first), new Variable(name, second))));

        assertThat(ex.getCause(), is(instanceOf(IllegalArgumentException.class)));
        assertThat(ex.getCause().getMessage(), containsString("Conflicting template variable '" + path + "'"));
    }

    StagingContext templateContext() {
        return new StagingContext() {

            @Override
            public Path resolve(String path) {
                return tempDir.resolve(path);
            }

            @Override
            public void ensureDirectory(Path directory) {
                try {
                    Files.createDirectories(directory);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public Executor executor() {
                return new CurrentThreadExecutorService();
            }
        };
    }

    static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    record TestContextImpl(Executor executor) implements StagingContext {
    }
}
