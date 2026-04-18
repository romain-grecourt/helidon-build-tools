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
package io.helidon.build.maven.archetype;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.helidon.build.archetype.engine.v2.Expression;
import io.helidon.build.archetype.engine.v2.Variations;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegrationTestMojoTest {

    @Test
    void testAssertExpectedVariationCountAllowsDisabledAssertion() {
        Variations variations = Variations.of(
                Variations.entry(Map.of("shape", "circle")),
                Variations.entry(Map.of("shape", "square")));

        assertDoesNotThrow(() ->
                IntegrationTestMojo.assertExpectedVariationCount(-1, variations));
    }

    @Test
    void testAssertExpectedVariationCountMatchesComputedVariations() {
        Variations variations = Variations.of(
                Variations.entry(Map.of("shape", "circle")),
                Variations.entry(Map.of("shape", "square")));

        assertDoesNotThrow(() ->
                IntegrationTestMojo.assertExpectedVariationCount(2, variations));
    }

    @Test
    void testAssertExpectedVariationCountFailsOnMismatch() {
        Variations variations = Variations.of(
                Variations.entry(Map.of("shape", "circle")),
                Variations.entry(Map.of("shape", "square")));

        MojoFailureException ex = assertThrows(MojoFailureException.class,
                () -> IntegrationTestMojo.assertExpectedVariationCount(3, variations));
        assertThat(ex.getMessage(), is("Expected 3 variations but found 2"));
    }

    @Test
    void testDefaultFiltersReturnsEmptyWithoutRulesFile(@TempDir Path tempDir) {
        assertThat(IntegrationTestMojo.defaultFilters(tempDir, tempDir), empty());
    }

    @Test
    void testDefaultFiltersLoadProjectRulesFile(@TempDir Path tempDir) throws IOException {
        Path rulesDir = tempDir.resolve("src/test/archetype");
        Files.createDirectories(rulesDir);
        Files.writeString(rulesDir.resolve("rules.xml"), """
                <rules>
                    <rule if="${mode} == 'a'">
                        <exclude if="${flag}"/>
                    </rule>
                </rules>
                """);

        List<Expression> filters = IntegrationTestMojo.defaultFilters(tempDir, tempDir);
        assertThat(filters.size(), is(1));
        assertThat(filters.get(0).equivalent(Expression.create("${flag} && ${mode} == 'a'")), is(true));
    }

    @Test
    void testDefaultFiltersFallsBackToStagedRulesFile(@TempDir Path tempDir) throws IOException {
        Path projectDir = tempDir.resolve("project");
        Path rulesDir = tempDir.resolve("test/archetype");
        Files.createDirectories(projectDir);
        Files.createDirectories(rulesDir);
        Files.writeString(rulesDir.resolve("rules.xml"), """
                <rules>
                    <rule if="${mode} == 'b'">
                        <exclude if="${flag}"/>
                    </rule>
                </rules>
                """);

        List<Expression> filters = IntegrationTestMojo.defaultFilters(tempDir, projectDir);
        assertThat(filters.size(), is(1));
        assertThat(filters.get(0).equivalent(Expression.create("${flag} && ${mode} == 'b'")), is(true));
    }
}
