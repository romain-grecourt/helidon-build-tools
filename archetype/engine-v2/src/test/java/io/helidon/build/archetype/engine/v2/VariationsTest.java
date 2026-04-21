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
package io.helidon.build.archetype.engine.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.build.archetype.engine.v2.ScriptCompiler.ValidationException;
import io.helidon.build.archetype.engine.v2.Variations.Request;
import io.helidon.build.common.VirtualFileSystem;
import io.helidon.build.common.xml.XMLElement;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static io.helidon.build.archetype.engine.v2.ScriptCompiler.EXPR_TEXT_INPUT_CONTROL_FLOW;
import static io.helidon.build.archetype.engine.v2.Variations.entry;
import static io.helidon.build.common.test.utils.TestFiles.targetDir;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link Variations}.
 */
class VariationsTest {

    @Test
    void testVariationsList1() {
        Variations expected = loadVariations("variations/expected/list1.xml");
        Variations actual = variations("variations", "list1.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsList2() {
        Variations expected = loadVariations("variations/expected/list2.xml");
        Variations actual = variations("variations", "list2.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsList3() {
        Variations expected = loadVariations("variations/expected/list3.xml");
        Variations actual = variations("variations", "list3.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsEnum1() {
        Variations expected = loadVariations("variations/expected/enum1.xml");
        Variations actual = variations("variations", "enum1.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsEnum2() {
        Variations expected = loadVariations("variations/expected/enum2.xml");
        Variations actual = variations("variations", "enum2.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsEnum3() {
        Variations expected = loadVariations("variations/expected/enum3.xml");
        Variations actual = variations("variations", "enum3.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsBoolean1() {
        Variations expected = loadVariations("variations/expected/boolean1.xml");
        Variations actual = variations("variations", "boolean1.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsBoolean2() {
        Variations expected = loadVariations("variations/expected/boolean2.xml");
        Variations actual = variations("variations", "boolean2.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsBoolean3() {
        Variations expected = loadVariations("variations/expected/boolean3.xml");
        Variations actual = variations("variations", "boolean3.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsBoolean4() {
        Variations expected = loadVariations("variations/expected/boolean4.xml");
        Variations actual = variations("variations", "boolean4.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsBoolean5() {
        Variations expected = loadVariations("variations/expected/boolean5.xml");
        Variations actual = variations("variations", "boolean5.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsBoolean6() {
        Variations expected = loadVariations("variations/expected/boolean6.xml");
        Variations actual = variations("variations", "boolean6.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsBoolean7() {
        Variations expected = loadVariations("variations/expected/boolean7.xml");
        Variations actual = variations("variations", "boolean7.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsBoolean8() {
        Variations expected = loadVariations("variations/expected/boolean8.xml");
        Variations actual = variations("variations", "boolean8.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsBoolean9() {
        Variations expected = loadVariations("variations/expected/boolean9.xml");
        Variations actual = variations("variations", "boolean9.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsBoolean10() {
        Variations expected = loadVariations("variations/expected/boolean10.xml");
        Variations actual = variations("variations", "boolean10.xml", Request.builder()
                .externalValues(Map.of("variant", "advanced", "mode", "tailored"))
                .build());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsText1() {
        Variations expected = Variations.of(Map.of("name", "Foo"), Set.of("name"));
        Variations actual = variations("variations", "text1.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationEntriesText1() {
        Variations expected = Variations.of(Map.of("name", "Foo"), Set.of("name"));
        Variations actual = variations("variations", "text1.xml", Request.EMPTY);
        assertThat(actual.toString(), is(expected.toString()));
        assertThat(actual.exhaustive(), is(false));
        assertThat(actual.unboundedInputs(), contains("name"));
    }

    @Test
    void testVariationEntriesText1WithExternalValueAreExhaustive() {
        Variations expected = Variations.of(Map.of("name", "Bar"), Set.of());
        Variations actual = variations("variations", "text1.xml", Request.builder()
                .externalValue("name", "Bar")
                .build());
        assertThat(actual.toString(), is(expected.toString()));
        assertThat(actual.exhaustive(), is(true));
        assertThat(actual.unboundedInputs(), is(Set.of()));
    }

    @Test
    void testVariationsFixtureWithTextInputControlFlowFailsCompilerValidation() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validate("variations", "text-external-default.xml"));
        assertThat(ex.errors(), contains(List.of(
                containsString(EXPR_TEXT_INPUT_CONTROL_FLOW))));
    }

    @Test
    void testVariationsText2() {
        Variations expected = Variations.of(Map.of("name", "<?>"), Set.of("name"));
        Variations actual = variations("variations", "text2.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationEntriesText2() {
        Variations expected = Variations.of(Map.of("name", "<?>"), Set.of("name"));
        Variations actual = variations("variations", "text2.xml", Request.EMPTY);
        assertThat(actual.toString(), is(expected.toString()));
    }

    @Test
    void testVariationEntriesBoolean1AreExhaustive() {
        Variations actual = variations("variations", "boolean1.xml", Request.EMPTY);
        assertThat(actual.exhaustive(), is(true));
        assertThat(actual.unboundedInputs(), is(Set.of()));
        assertThat(actual.stream().allMatch(Variations.Entry::exhaustive), is(true));
    }

    @Test
    void testVariationsOfCreatesExhaustiveEntry() {
        Variations actual = Variations.of(Map.of("name", "Foo"), Set.of());
        assertThat(actual, contains(hasProperty("exhaustive", Variations.Entry::exhaustive, is(true))));
        assertThat(actual, is(Variations.of(Map.of("name", "Foo"), Set.of())));
    }

    @Test
    void testVariationEntryFactoryCreatesExhaustiveEntry() {
        Variations.Entry actual = entry(Map.of("name", "Foo"));
        assertThat(actual.exhaustive(), is(true));
        assertThat(actual, is(Variations.entry(Map.of("name", "Foo"))));
    }

    @Test
    void testVariationEntryToStringWithSeparator() {
        Variations.Entry actual = Variations.entry(Map.of("name", "Foo"), Set.of("name"));
        assertThat(actual.toString(" "), is("name=Foo unbounded=[name]"));
        assertThat(actual.toString(false, " "), is("name=Foo"));
    }

    @Test
    void testVariationsToStringWithSeparator() {
        Variations actual = variations("variations", "boolean1.xml", Request.EMPTY);
        assertThat(actual.toString(" | "), is("colors=false | colors=true"));
    }

    @Test
    void testVariationsUnionMergesEquivalentEntries() {
        Variations actual = Variations.union(List.of(
                Variations.of(
                        entry(Map.of("name", "Foo"), Set.of("name")),
                        entry(Map.of("name", "Bar"), Set.of())),
                Variations.of(
                        entry(Map.of("name", "Foo"), Set.of()),
                        entry(Map.of("name", "Baz"), Set.of()))));

        Variations expected = Variations.of(
                entry(Map.of("name", "Foo"), Set.of("name")),
                entry(Map.of("name", "Bar"), Set.of()),
                entry(Map.of("name", "Baz"), Set.of()));

        assertThat(actual, is(expected));
        assertThat(actual.unboundedInputs(), contains("name"));
    }

    @Test
    void testVariationsUnionRetainsDistinctResolvedValues() {
        Variations actual = Variations.union(List.of(
                Variations.of(entry(Map.of("name", "Foo", "preset", "red"))),
                Variations.of(entry(Map.of("name", "Foo", "preset", "blue")))));

        Variations expected = Variations.of(
                entry(Map.of("name", "Foo", "preset", "red")),
                entry(Map.of("name", "Foo", "preset", "blue")));

        assertThat(actual, is(expected));
    }

    @Test
    void testVariationsSubstitutions() {
        Variations expected = loadVariations("variations/expected/substitutions.xml");
        Variations actual = variations("variations", "substitutions.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsConditionals() {
        Variations expected = loadVariations("variations/expected/conditionals.xml");
        Variations actual = variations("variations", "conditionals.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsNestedConditionalOptionPruning() {
        Variations expected = loadVariations("variations/expected/nested-conditional-option.xml");
        Variations actual = variations("variations", "nested-conditional-option.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsPruneInactiveBranchBeforePresetValidation() {
        Variations expected = loadVariations("variations/expected/branch-pruning.xml");
        Variations actual = variations("variations", "branch-pruning.xml", Request.builder()
                .externalValues(Map.of("variant", "advanced", "mode", "hosted"))
                .build());
        assertThat(actual, is(expected));
    }

    @Test
    void testVariationsJoinOrder1() {
        Variations expected = loadVariations("variations/expected/join-order1.xml");
        Variations actual = variations("variations", "join-order1.xml", Request.EMPTY);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsFailWhenIntermediateCountExceedsMax() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                variations("variations", "boolean1.xml", Request.builder()
                        .maxIntermediate(1)
                        .build()));
        assertThat(ex.getMessage(),
                is("Intermediate variation row count 2 exceeds the configured limit of 1 while joining input 'colors'"));
    }

    @Test
    void testVariationsIgnoreProjectionOverestimate() {
        Variations expected = Variations.of(
                entry(Map.of("advanced", "false", "mode", "basic")),
                entry(Map.of("advanced", "true", "mode", "expert")));
        Variations actual = variations("variations", "projection-overestimate.xml", Request.builder()
                .maxIntermediate(2)
                .build());
        assertThat(actual, is(expected));
    }

    @Test
    void testVariationsE2e() {
        Variations actual = variations("e2e", "main.xml", Request.EMPTY);
        assertThat(actual.size(), is(65604));
    }

    @Test
    void testVariationsFilters() {
        Variations expected = loadVariations("variations/expected/filtered.xml");
        List<Expression> filters = filters("variations/filters.xml");
        Variations actual = variations("e2e", "main.xml", Request.builder()
                .filters(filters)
                .build());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsFiltersCanReferenceNestedInputKeys() {
        Variations expected = Variations.of(
                entry(Map.of("flag", "false", "format", "structured", "format.format-lib", "binder")),
                entry(Map.of("flag", "true", "format", "structured", "format.format-lib", "mapper")));
        Variations actual = variations("variations", "derived-local-filter.xml", Request.builder()
                .expression("${flag} != (${format.format-lib} == 'mapper')")
                .build());
        assertThat(actual, is(expected));
    }

    @Test
    void testVariationsPreferFilterGatesBeforeLowerCostFanOut() {
        List<Expression> filters = filters("variations/filter-aware-join-order-filters.xml");
        Variations actual = variations("variations", "filter-aware-join-order.xml", Request.builder()
                .maxIntermediate(20)
                .filters(filters)
                .build());
        assertThat(actual.size(), is(16));
        assertThat(actual.exhaustive(), is(true));
        assertThat(actual, hasItem(allOf(List.of(
                hasProperty("w", (Variations.Entry e) -> e.get("w"), is("base")),
                hasProperty("x", (Variations.Entry e) -> e.get("x"), is("false")),
                hasProperty("y", (Variations.Entry e) -> e.get("y"), is("basic")),
                hasProperty("z", (Variations.Entry e) -> e.get("z"), is("false"))
        ))));
        Set<String> expectedFanouts = new TreeSet<>(Arrays.asList(
                "a", "a,b", "a,b,c", "a,b,c,d", "a,b,d", "a,c", "a,c,d", "a,d",
                "b", "b,c", "b,c,d", "b,d",
                "c", "c,d",
                "d",
                "none"));
        Set<String> actualFanouts = actual.stream()
                .map(entry -> entry.get("fanout"))
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(actualFanouts, is(expectedFanouts));
    }

    @Test
    void testVariationsKeepNestedEnumWhenParentBooleanIsExternallyFixed() {
        Variations expected = loadVariations("variations/expected/nested-enum-preserved.xml");
        Variations actual = variations("variations", "nested-enum-under-fixed-boolean.xml", Request.builder()
                .externalValues(Map.of("variant", "advanced", "feature-a", "true", "feature-b", "true"))
                .build());
        assertThat(actual, is(expected));
    }

    @Test
    void testVariationsKeepNestedEnumAcrossSourceAndExecGraph() {
        Variations expected = loadVariations("variations/expected/nested-enum-preserved.xml");
        Variations actual = variations("variations/source-graph-repro", "main.xml", Request.builder()
                        .externalValues(Map.of("variant", "advanced", "feature-a", "true", "feature-b", "true"))
                        .build());
        assertThat(actual, is(expected));
    }

    static Variations variations(String path, String entrypoint, Request request) {
        Path targetDir = targetDir(VariationsTest.class);
        try (FileSystem fs = VirtualFileSystem.create(targetDir.resolve("test-classes"))) {
            Path cwd = fs.getPath(path);
            Path source = cwd.resolve(entrypoint).toAbsolutePath().normalize();
            return new VariationEngine(() -> source, cwd).compute(request);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("SameParameterValue")
    static void validate(String path, String entrypoint) {
        Path targetDir = targetDir(VariationsTest.class);
        try (FileSystem fs = VirtualFileSystem.create(targetDir.resolve("test-classes"))) {
            Path cwd = fs.getPath(path);
            Path source = cwd.resolve(entrypoint).toAbsolutePath().normalize();
            ScriptCompiler compiler = new ScriptCompiler(() -> source, cwd);
            compiler.validateOnly();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex.getMessage(), ex);
        }
    }

    static List<Expression> filters(String path) {
        List<Expression> excludes = new ArrayList<>();
        XMLElement root = loadXml(path);
        for (XMLElement elt : root.traverse(it -> it.name().equals("exclude"))) {
            Expression exclude = Expression.TRUE;
            for (XMLElement n = elt; n.parent() != null; n = n.parent()) {
                exclude = exclude.and(Expression.create(n.attribute("if")));
            }
            excludes.add(exclude);
        }
        return excludes;
    }

    static Variations loadVariations(String path) {
        List<Variations.Entry> result = new ArrayList<>();
        for (XMLElement e : loadXml(path).children("variation")) {
            Map<String, String> map = new java.util.LinkedHashMap<>();
            for (XMLElement entry : e.children()) {
                map.put(entry.name(), entry.value());
            }
            result.add(entry(map));
        }
        return Variations.of(result);
    }

    static XMLElement loadXml(String path) {
        Path targetDir = targetDir(VariationsTest.class);
        Path testClasses = targetDir.resolve("test-classes");
        try (InputStream is = Files.newInputStream(testClasses.resolve(path))) {
            return XMLElement.parse(is);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex.getMessage(), ex);
        }
    }

    static <T, U> Matcher<T> hasProperty(String name, Function<T, U> extractor, Matcher<U> subMatcher) {
        return new FeatureMatcher<>(subMatcher, "has property " + name, name) {
            @Override
            protected U featureValueOf(T target) {
                return extractor.apply(target);
            }
        };
    }
}
