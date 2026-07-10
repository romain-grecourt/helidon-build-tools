/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import io.helidon.build.common.CurrentThreadExecutorService;
import io.helidon.build.maven.stager.VariableValue.ListValue;
import io.helidon.build.maven.stager.VariableValue.MapValue;
import io.helidon.build.maven.stager.VariableValue.SimpleValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link TemplateTask}.
 */
class TemplateTaskTest {

    @TempDir
    private Path tempDir;

    @Test
    void testTemplateEmptyVariable() throws Exception {
        Files.writeString(tempDir.resolve("template.mustache"), "{{version}}");
        Path outputDir = tempDir.resolve("stage");
        TemplateTask task = new TemplateTask(null,
                Map.of("source", "template.mustache", "target", "index.txt"),
                List.of(new Variable("version", new VariableValue.EmptyValue("version"))));

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> task.execute(context(), outputDir, Map.of()).toCompletableFuture().get());

        assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));
        assertThat(ex.getCause().getMessage(), containsString("version"));

        Path target = outputDir.resolve("index.txt");
        assertThat(Files.exists(target), is(true));
        assertThat(Files.readString(target), is(""));
    }

    @Test
    void testTemplateReplaceHelper() throws Exception {
        List<Variable> variables = List.of(
                new Variable("version", new SimpleValue("4.0.0-SNAPSHOT")),
                new Variable("suffix", new SimpleValue("-SNAPSHOT")),
                new Variable("qualifier", new SimpleValue("-RC1")),
                new Variable("release", new MapValue(List.of(
                        new Variable("version", new SimpleValue("5.0.0-SNAPSHOT"))))));

        String output = execute("""
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
                List.of(new Variable("version", new SimpleValue("4.0.0-SNAPSHOT"))));

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> task.execute(context(), outputDir, Map.of()).toCompletableFuture().get());

        assertThat(ex.getCause().getCause().getMessage(), containsString("replace requires exactly 3 arguments"));
    }

    @Test
    void testTemplateReplaceHelperRejectsExtraArguments() {
        List<Variable> variables = List.of(new Variable("version", new SimpleValue("4.0.0-SNAPSHOT")));

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> execute("{{replace version \"-SNAPSHOT\" \"\" extra}}", variables));

        assertThat(ex.getCause().getCause().getMessage(), containsString("replace requires exactly 3 arguments"));
    }

    @Test
    void testTemplateRepeatedListsConcatenateInOrder() throws Exception {
        List<Variable> variables = List.of(
                new Variable("versions", new ListValue("2.0.0", "3.0.0")),
                new Variable("empty", new ListValue(List.of())),
                new Variable("versions", new ListValue(List.of())),
                new Variable("versions", new ListValue("4.0.0")),
                new Variable("empty", new ListValue(List.of())));

        String output = execute("{{#versions}}{{value}},{{/versions}}{{#empty}}unexpected{{/empty}}", variables);

        assertThat(output, is("2.0.0,3.0.0,4.0.0,"));
    }

    @Test
    void testTemplateListItemsExposeValues() throws Exception {
        List<Variable> variables = List.of(new Variable("versions", new ListValue("2.0.0", "3.0.0", "4.0.0")));
        String output = execute("{{#versions}}{{value}},{{/versions}}", variables);

        assertThat(output, is("2.0.0,3.0.0,4.0.0,"));
    }

    @Test
    void testTemplateListItemsRenderDirectly() throws Exception {
        List<Variable> variables = List.of(new Variable("versions", new ListValue("2.0.0", "3.0.0", "4.0.0")));
        String output = execute("{{#versions}}{{.}},{{/versions}}", variables);

        assertThat(output, is("2.0.0,3.0.0,4.0.0,"));
    }

    @Test
    void testTemplateListItemsExposePosition() throws Exception {
        List<Variable> variables = List.of(new Variable("versions", new ListValue("2.0.0", "3.0.0", "4.0.0")));
        String output = execute("{{#versions}}{{index}}:{{first}}:{{last}}:{{value}}\n{{/versions}}", variables);

        assertThat(output, is("""
                0:true:false:2.0.0
                1:false:false:3.0.0
                2:false:true:4.0.0
                """));
    }

    @Test
    void testTemplateRepeatedEqualScalarsCoalesce() throws Exception {
        String output = execute("{{version}}", List.of(
                new Variable("version", new SimpleValue("4.0.0")),
                new Variable("version", new SimpleValue("4.0.0"))));

        assertThat(output, is("4.0.0"));
    }

    @Test
    void testTemplateRepeatedEmptyValuesRemainInvalid() {
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> execute("{{versions}}", List.of(
                        new Variable("versions", new VariableValue.EmptyValue("versions")),
                        new Variable("versions", new VariableValue.EmptyValue("versions")))));

        assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));
        assertThat(ex.getCause().getMessage(), containsString("versions"));
    }

    @Test
    void testTemplateRepeatedMapsMergeRecursively() throws Exception {
        List<Variable> variables = List.of(
                new Variable("release", new MapValue(List.of(
                        new Variable("version", new SimpleValue("4.0.0")),
                        new Variable("versions", new ListValue("2.0.0", "3.0.0")),
                        new Variable("coordinates", new MapValue(List.of(
                                new Variable("groupId", new SimpleValue("io.helidon")))))))),
                new Variable("release", new MapValue(List.of(
                        new Variable("version", new SimpleValue("4.0.0")),
                        new Variable("channel", new SimpleValue("stable")),
                        new Variable("versions", new ListValue("4.0.0")),
                        new Variable("coordinates", new MapValue(List.of(
                                new Variable("artifactId", new SimpleValue("helidon-bom")))))))));

        String output = execute(
                "{{release.version}}:{{release.channel}}:{{release.coordinates.groupId}}:"
                + "{{release.coordinates.artifactId}}:"
                + "{{#release.versions}}{{.}},{{/release.versions}}",
                variables);

        assertThat(output, is("4.0.0:stable:io.helidon:helidon-bom:2.0.0,3.0.0,4.0.0,"));
    }

    @Test
    void testTemplateEntriesPropertyIteratesLiteralDottedKeys() throws Exception {
        List<Variable> variables = List.of(
                new Variable("properties", new MapValue(List.of(
                        new Variable("cli.version", new SimpleValue("4.2.0")),
                        new Variable("schemas.version", new SimpleValue("2026.07"))))));

        String expected = """
                cli.version=4.2.0
                schemas.version=2026.07
                """;

        assertThat(execute(
                "{{#properties.entries}}{{key}}={{value}}\n{{/properties.entries}}",
                variables), is(expected));
    }

    @Test
    void testTemplateEntriesPropertyDecoratesEntries() throws Exception {
        List<Variable> variables = List.of(
                new Variable("properties", new MapValue(List.of(
                        new Variable("cli.version", new SimpleValue("4.2.0")),
                        new Variable("schemas.version", new SimpleValue("2026.07"))))));

        String output = execute(
                "{{#properties.entries}}{{index}}:{{first}}:{{last}}:{{key}}\n{{/properties.entries}}",
                variables);

        assertThat(output, is("""
                0:true:false:cli.version
                1:false:true:schemas.version
                """));
    }

    @Test
    void testTemplateEntriesPropertyRendersValueDirectly() throws Exception {
        List<Variable> variables = List.of(
                new Variable("properties", new MapValue(List.of(
                        new Variable("cli.version", new SimpleValue("4.2.0")),
                        new Variable("schemas.version", new SimpleValue("2026.07"))))));

        String output = execute("{{#properties.entries}}{{value}},{{/properties.entries}}", variables);

        assertThat(output, is("4.2.0,2026.07,"));
    }

    @Test
    void testTemplateEntriesPropertyResolvesDottedValue() throws Exception {
        MapValue cli = new MapValue(List.of(
                new Variable("name", new SimpleValue("helidon-cli")),
                new Variable("version", new SimpleValue("4.2.0"))));
        MapValue schemas = new MapValue(List.of(
                new Variable("name", new SimpleValue("schemas")),
                new Variable("version", new SimpleValue("2026.07"))));
        List<Variable> variables = List.of(new Variable("components", new MapValue(List.of(
                new Variable("cli", cli),
                new Variable("schemas", schemas)))));

        String output = execute(
                "{{#components.entries}}{{key}}={{value.name}}:{{value.version}}\n{{/components.entries}}",
                variables);

        assertThat(output, is("""
                cli=helidon-cli:4.2.0
                schemas=schemas:2026.07
                """));
    }

    @Test
    void testTemplateEntriesPropertySkipsMissingAndNonMapValues() throws Exception {
        List<Variable> variables = List.of(new Variable("version", new SimpleValue("4.2.0")));

        String output = execute(
                "{{#missing.entries}}missing{{/missing.entries}}{{#version.entries}}version{{/version.entries}}",
                variables);

        assertThat(output, is(""));
    }

    @Test
    void testTemplateMapStillLooksUpLiteralDottedKeys() throws Exception {
        List<Variable> variables = List.of(
                new Variable("properties", new MapValue(List.of(
                        new Variable("cli.version", new SimpleValue("4.2.0")),
                        new Variable("schemas.version", new SimpleValue("2026.07"))))));

        String output = execute("{{#properties}}{{cli.version}}{{/properties}}", variables);

        assertThat(output, is("4.2.0"));
    }

    @Test
    void testTemplateConflictingScalarsFail() {
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> execute("unused", List.of(
                        new Variable("version", new SimpleValue("1.0.0")),
                        new Variable("version", new SimpleValue("2.0.0")))));

        assertThat(ex.getCause(), is(instanceOf(IllegalArgumentException.class)));
        assertThat(ex.getCause().getMessage(), containsString("Conflicting variable 'version'"));
    }

    @Test
    void testTemplateNestedMapConflictsUseQualifiedName() {
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> execute("unused", List.of(
                        new Variable("release", new MapValue(List.of(
                                new Variable("version", new SimpleValue("1.0.0"))))),
                        new Variable("release", new MapValue(List.of(
                                new Variable("version", new SimpleValue("2.0.0"))))))));

        assertThat(ex.getCause(), is(instanceOf(IllegalArgumentException.class)));
        assertThat(ex.getCause().getMessage(), containsString("Conflicting variable 'release.version'"));
    }

    @Test
    void testTemplateMixedTypeConflictsUseQualifiedName() {
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> execute("unused", List.of(
                        new Variable("release", new MapValue(List.of(
                                new Variable("version", new SimpleValue("1.0.0"))))),
                        new Variable("release", new MapValue(List.of(
                                new Variable("version", new ListValue("2.0.0"))))))));

        assertThat(ex.getCause(), is(instanceOf(IllegalArgumentException.class)));
        assertThat(ex.getCause().getMessage(), containsString("Conflicting variable 'release.version'"));
    }

    String execute(String template, List<Variable> variables) throws Exception {
        Files.writeString(tempDir.resolve("template.mustache"), template);
        Path outputDir = tempDir.resolve("stage");
        TemplateTask task = new TemplateTask(null,
                Map.of("source", "template.mustache", "target", "index.txt"),
                variables);

        task.execute(context(), outputDir, Map.of()).toCompletableFuture().get();
        return Files.readString(outputDir.resolve("index.txt"));
    }

    StagingContext context() {
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
}
