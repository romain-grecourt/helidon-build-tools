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
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.build.common.VirtualFileSystem;
import io.helidon.build.common.xml.XMLElement;

import org.junit.jupiter.api.Test;

import static io.helidon.build.common.test.utils.TestFiles.targetDir;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link Variations}.
 */
class VariationsTest {

    @Test
    void testVariationsList1() {
        Variations expected = loadVariations("variations/expected/list1.xml");
        Variations actual = variationEntries("variations", "list1.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsList2() {
        Variations expected = loadVariations("variations/expected/list2.xml");
        Variations actual = variationEntries("variations", "list2.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsList3() {
        Variations expected = loadVariations("variations/expected/list3.xml");
        Variations actual = variationEntries("variations", "list3.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsEnum1() {
        Variations expected = loadVariations("variations/expected/enum1.xml");
        Variations actual = variationEntries("variations", "enum1.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsEnum2() {
        Variations expected = loadVariations("variations/expected/enum2.xml");
        Variations actual = variationEntries("variations", "enum2.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsEnum3() {
        Variations expected = loadVariations("variations/expected/enum3.xml");
        Variations actual = variationEntries("variations", "enum3.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsBoolean1() {
        Variations expected = loadVariations("variations/expected/boolean1.xml");
        Variations actual = variationEntries("variations", "boolean1.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsBoolean2() {
        Variations expected = loadVariations("variations/expected/boolean2.xml");
        Variations actual = variationEntries("variations", "boolean2.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsBoolean3() {
        Variations expected = loadVariations("variations/expected/boolean3.xml");
        Variations actual = variationEntries("variations", "boolean3.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsBoolean4() {
        Variations expected = loadVariations("variations/expected/boolean4.xml");
        Variations actual = variationEntries("variations", "boolean4.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsText1() {
        Variations expected = Variations.of(Map.of("name", "Foo"), Set.of("name"));
        Variations actual = variationEntries("variations", "text1.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationEntriesText1() {
        Variations expected = Variations.of(Map.of("name", "Foo"), Set.of("name"));
        Variations actual = variationEntries("variations", "text1.xml", List.of());
        assertThat(actual.toString(), is(expected.toString()));
        assertThat(actual.exhaustive(), is(false));
        assertThat(actual.unboundedInputs(), contains("name"));
    }

    @Test
    void testVariationEntriesText1WithExternalValueAreExhaustive() {
        Variations expected = Variations.of(Map.of("name", "Bar"));
        Variations actual = variationEntries("variations", "text1.xml", List.of(), Map.of("name", "Bar"));
        assertThat(actual.toString(), is(expected.toString()));
        assertThat(actual.exhaustive(), is(true));
        assertThat(actual.unboundedInputs(), is(Set.of()));
    }

    @Test
    void testVariationsText2() {
        Variations expected = Variations.of(Map.of("name", "<?>"), Set.of("name"));
        Variations actual = variationEntries("variations", "text2.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationEntriesText2() {
        Variations expected = Variations.of(Map.of("name", "<?>"), Set.of("name"));
        Variations actual = variationEntries("variations", "text2.xml", List.of());
        assertThat(actual.toString(), is(expected.toString()));
    }

    @Test
    void testVariationEntriesBoolean1AreExhaustive() {
        Variations actual = variationEntries("variations", "boolean1.xml", List.of());
        assertThat(actual.exhaustive(), is(true));
        assertThat(actual.unboundedInputs(), is(Set.of()));
        assertThat(actual.stream().allMatch(Variations.Entry::exhaustive), is(true));
    }

    @Test
    void testVariationsOfCreatesExhaustiveEntry() {
        Variations actual = Variations.of(Map.of("name", "Foo"));
        assertThat(actual.iterator().next().exhaustive(), is(true));
        assertThat(actual.toString(), is(Variations.of(Map.of("name", "Foo"), Set.of()).toString()));
    }

    @Test
    void testVariationEntryToStringWithSeparator() {
        Variations.Entry actual = Variations.of(Map.of("name", "Foo"), Set.of("name")).iterator().next();
        assertThat(actual.toString(" "), is("name=Foo unbounded=[name]"));
        assertThat(actual.toString(false, " "), is("name=Foo"));
    }

    @Test
    void testVariationsToStringWithSeparator() {
        Variations actual = variationEntries("variations", "boolean1.xml", List.of());
        assertThat(actual.toString(" | "), is("{colors=false} | {colors=true}"));
    }

    @Test
    void testVariationsSubstitutions() {
        Variations expected = loadVariations("variations/expected/substitutions.xml");
        Variations actual = variationEntries("variations", "substitutions.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsConditionals() {
        Variations expected = loadVariations("variations/expected/conditionals.xml");
        Variations actual = variationEntries("variations", "conditionals.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsNestedConditionalOptionPruning() {
        Variations expected = loadVariations("variations/expected/nested-conditional-option.xml");
        Variations actual = variationEntries("variations", "nested-conditional-option.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsJoinOrder1() {
        Variations expected = loadVariations("variations/expected/join-order1.xml");
        Variations actual = variationEntries("variations", "join-order1.xml", List.of());
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    @Test
    void testVariationsE2e() {
        Variations actual = variationEntries("e2e", "main.xml", List.of());
        assertThat(actual.size(), is(65604));
    }

    @Test
    void testVariationsFilters() {
        Variations expected = loadVariations("variations/expected/filtered.xml");
        List<Expression> filters = filters("variations/filters.xml");
        Variations actual = variationEntries("e2e", "main.xml", filters);
        assertThat(actual.toString(false), is(expected.toString(false)));
    }

    static Variations variationEntries(String path, String entrypoint, List<Expression> filters) {
        return variationEntries(path, entrypoint, filters, Map.of());
    }

    static Variations variationEntries(String path,
                                       String entrypoint,
                                       List<Expression> filters,
                                       Map<String, String> externalValues) {
        Path targetDir = targetDir(VariationsTest.class);
        try (FileSystem fs = VirtualFileSystem.create(targetDir.resolve("test-classes"))) {
            Path cwd = fs.getPath(path);
            Path source = cwd.resolve(entrypoint).toAbsolutePath().normalize();
            ScriptCompiler compiler = new ScriptCompiler(() -> source, cwd);
            return Variations.compute(compiler, filters, externalValues);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("SameParameterValue")
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
        Set<Variations.Entry> result = new java.util.TreeSet<>();
        for (XMLElement e : loadXml(path).children("variation")) {
            Map<String, String> map = new java.util.LinkedHashMap<>();
            for (XMLElement entry : e.children()) {
                map.put(entry.name(), entry.value());
            }
            result.addAll(Variations.of(map));
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
}
