/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.build.common.Lists;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;

import static io.helidon.build.common.test.utils.TestFiles.testResourcePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

/**
 * Unit test for class {@link InputPermutations}.
 */
@SuppressWarnings("unchecked")
class InputPermutationsTest {

    @Test
    void testList1() {
        List<Map<String, String>> permutations = permutations("permutations/list1.xml");
        assertThat(permutations, isEqual(
                Map.of("colors", "none"),
                Map.of("colors", "red"),
                Map.of("colors", "orange"),
                Map.of("colors", "red,orange")));
    }

    @Test
    void testList2() {
        List<Map<String, String>> permutations = permutations("permutations/list2.xml");
        assertThat(permutations, isEqual(
                Map.of("colors", "none"),
                Map.of("colors", "red", "red", "burgundy"),
                Map.of("colors", "red", "red", "auburn"),
                Map.of("colors", "orange", "orange", "salmon"),
                Map.of("colors", "orange", "orange", "peach"),
                Map.of("colors", "red,orange", "red", "burgundy", "orange", "salmon"),
                Map.of("colors", "red,orange", "red", "auburn", "orange", "salmon"),
                Map.of("colors", "red,orange", "red", "burgundy", "orange", "peach"),
                Map.of("colors", "red,orange", "red", "auburn", "orange", "peach")));
    }

    @Test
    void testEnum1() {
        List<Map<String, String>> permutations = permutations("permutations/enum1.xml");
        assertThat(permutations, isEqual(
                Map.of("colors", "red"),
                Map.of("colors", "orange")));
    }

    @Test
    void testEnum2() {
        List<Map<String, String>> permutations = permutations("permutations/enum2.xml");
        assertThat(permutations, isEqual(
                Map.of("colors", "green"),
                Map.of("colors", "red", "colors.red", "burgundy"),
                Map.of("colors", "red", "colors.red", "auburn"),
                Map.of("colors", "orange", "colors.orange", "salmon"),
                Map.of("colors", "orange", "colors.orange", "peach")));
    }

    @Test
    void testBoolean1() {
        List<Map<String, String>> permutations = permutations("permutations/boolean1.xml");
        assertThat(permutations, isEqual(
                Map.of("colors", "true"),
                Map.of("colors", "false")));
    }

    @Test
    void testBoolean2() {
        List<Map<String, String>> permutations = permutations("permutations/boolean2.xml");
        assertThat(permutations, isEqual(
                Map.of("colors", "true", "colors.tones", "none"),
                Map.of("colors", "true", "colors.tones", "dark"),
                Map.of("colors", "true", "colors.tones", "light"),
                Map.of("colors", "true", "colors.tones", "dark,light"),
                Map.of("colors", "false")));
    }

    @Test
    void testText1() {
        List<Map<String, String>> permutations = permutations("permutations/text1.xml");
        assertThat(permutations, isEqual(
                Map.of("name", "Foo")));
    }

    @Test
    void testText2() {
        List<Map<String, String>> permutations = permutations("permutations/text2.xml");
        assertThat(permutations.size(), is(1));
        assertThat(permutations.get(0).size(), is(1));
        assertThat(permutations.get(0).get("name"), startsWith("name-"));
    }

    @Test
    void testSubstitutions() {
        List<Map<String, String>> permutations = permutations("permutations/substitutions.xml");
        assertThat(permutations, isEqual(
                Map.of("list-things", "none", "text", "a-foo-a-bar"),
                Map.of("list-things", "a-bar", "text", "a-foo-a-bar")));
    }

    @Test
    void testConditionals() {
        List<Map<String, String>> permutations = permutations("permutations/conditionals.xml");
        List<Map<String, String>> expected = new ArrayList<>();
        expected.add(Map.of("heat", "none"));
        expected.add(Map.of("heat", "warm", "warm", "none"));
        expected.add(Map.of("heat", "warm", "warm", "red", "red", "burgundy"));
        expected.add(Map.of("heat", "warm", "warm", "red", "red", "auburn"));
        expected.add(Map.of("heat", "cold", "cold", "none"));
        expected.add(Map.of("heat", "cold", "cold", "green"));
        expected.add(Map.of("heat", "cold", "cold", "blue", "blue", "azure"));
        expected.add(Map.of("heat", "cold", "cold", "blue", "blue", "indigo"));
        expected.add(Map.of("heat", "cold", "cold", "blue,green", "blue", "indigo"));
        expected.add(Map.of("heat", "cold", "cold", "blue,green", "blue", "azure"));
        expected.add(Map.of("heat", "warm,cold", "warm", "none", "cold", "none"));
        expected.add(Map.of("heat", "warm,cold", "warm", "none", "cold", "green", "green", "tea"));
        expected.add(Map.of("heat", "warm,cold", "warm", "none", "cold", "green", "green", "lime"));
        expected.add(Map.of("heat", "warm,cold", "warm", "none", "cold", "blue", "blue", "azure"));
        expected.add(Map.of("heat", "warm,cold", "warm", "none", "cold", "blue", "blue", "indigo"));
        expected.add(Map.of("heat", "warm,cold", "warm", "none", "cold", "blue", "blue", "ultramarine"));
        expected.add(Map.of("heat", "warm,cold", "warm", "none", "cold", "blue,green", "green", "tea", "blue", "azure"));
        expected.add(Map.of("heat", "warm,cold", "warm", "none", "cold", "blue,green", "green", "tea", "blue", "indigo"));
        expected.add(Map.of("heat", "warm,cold", "warm", "none", "cold", "blue,green", "green", "tea", "blue", "ultramarine"));
        expected.add(Map.of("heat", "warm,cold", "warm", "none", "cold", "blue,green", "green", "lime", "blue", "azure"));
        expected.add(Map.of("heat", "warm,cold", "warm", "none", "cold", "blue,green", "green", "lime", "blue", "indigo"));
        expected.add(Map.of("heat", "warm,cold", "warm", "none", "cold", "blue,green", "green", "lime", "blue", "ultramarine"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "light", "cold", "none"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "light", "cold", "green", "green", "tea"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "light", "cold", "green", "green", "lime"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "light", "cold", "blue", "blue", "azure"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "light", "cold", "blue", "blue", "indigo"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "light", "cold", "blue", "blue", "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "burgundy", "cold", "none"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "burgundy", "cold", "green", "green", "tea"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "burgundy", "cold", "green", "green", "lime"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "burgundy", "cold", "blue", "blue", "azure"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "burgundy", "cold", "blue", "blue", "indigo"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "burgundy", "cold", "blue", "blue", "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "auburn", "cold", "none"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "auburn", "cold", "green", "green", "tea"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "auburn", "cold", "green", "green", "lime"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "auburn", "cold", "blue", "blue", "azure"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "auburn", "cold", "blue", "blue", "indigo"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red", "red", "auburn", "cold", "blue", "blue", "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat", "warm,cold", "warm", "orange", "orange", "salmon", "cold", "none"));
        expected.add(Map.of("heat", "warm,cold", "warm", "orange", "orange", "salmon", "cold", "green", "green", "tea"));
        expected.add(Map.of("heat", "warm,cold", "warm", "orange", "orange", "salmon", "cold", "green", "green", "lime"));
        expected.add(Map.of("heat", "warm,cold", "warm", "orange", "orange", "salmon", "cold", "blue", "blue", "azure"));
        expected.add(Map.of("heat", "warm,cold", "warm", "orange", "orange", "salmon", "cold", "blue", "blue", "indigo"));
        expected.add(Map.of("heat", "warm,cold", "warm", "orange", "orange", "salmon", "cold", "blue", "blue", "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "orange",
                "orange",
                "salmon",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "orange",
                "orange",
                "salmon",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "orange",
                "orange",
                "salmon",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "orange",
                "orange",
                "salmon",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "orange",
                "orange",
                "salmon",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "orange",
                "orange",
                "salmon",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat", "warm,cold", "warm", "orange", "orange", "peach", "cold", "none"));
        expected.add(Map.of("heat", "warm,cold", "warm", "orange", "orange", "peach", "cold", "green", "green", "tea"));
        expected.add(Map.of("heat", "warm,cold", "warm", "orange", "orange", "peach", "cold", "green", "green", "lime"));
        expected.add(Map.of("heat", "warm,cold", "warm", "orange", "orange", "peach", "cold", "blue", "blue", "azure"));
        expected.add(Map.of("heat", "warm,cold", "warm", "orange", "orange", "peach", "cold", "blue", "blue", "indigo"));
        expected.add(Map.of("heat", "warm,cold", "warm", "orange", "orange", "peach", "cold", "blue", "blue", "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "orange",
                "orange",
                "peach",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "orange",
                "orange",
                "peach",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "orange",
                "orange",
                "peach",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "orange",
                "orange",
                "peach",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "orange",
                "orange",
                "peach",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "orange",
                "orange",
                "peach",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red,orange", "orange", "salmon", "red", "light", "cold", "none"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "light",
                "cold",
                "green",
                "green",
                "tea"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "light",
                "cold",
                "green",
                "green",
                "lime"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "light",
                "cold",
                "blue",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "light",
                "cold",
                "blue",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "light",
                "cold",
                "blue",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red,orange", "orange", "peach", "red", "light", "cold", "none"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "light",
                "cold",
                "green",
                "green",
                "tea"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "light",
                "cold",
                "green",
                "green",
                "lime"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "light",
                "cold",
                "blue",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "light",
                "cold",
                "blue",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "light",
                "cold",
                "blue",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "light",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red,orange", "orange", "salmon", "red", "burgundy", "cold", "none"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "burgundy",
                "cold",
                "green",
                "green",
                "tea"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "burgundy",
                "cold",
                "green",
                "green",
                "lime"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "burgundy",
                "cold",
                "blue",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "burgundy",
                "cold",
                "blue",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "burgundy",
                "cold",
                "blue",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red,orange", "orange", "peach", "red", "burgundy", "cold", "none"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "burgundy",
                "cold",
                "green",
                "green",
                "tea"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "burgundy",
                "cold",
                "green",
                "green",
                "lime"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "burgundy",
                "cold",
                "blue",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "burgundy",
                "cold",
                "blue",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "burgundy",
                "cold",
                "blue",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "burgundy",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red,orange", "orange", "salmon", "red", "auburn", "cold", "none"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "auburn",
                "cold",
                "green",
                "green",
                "tea"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "auburn",
                "cold",
                "green",
                "green",
                "lime"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "auburn",
                "cold",
                "blue",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "auburn",
                "cold",
                "blue",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "auburn",
                "cold",
                "blue",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "salmon",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat", "warm,cold", "warm", "red,orange", "orange", "peach", "red", "auburn", "cold", "none"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "auburn",
                "cold",
                "green",
                "green",
                "tea"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "auburn",
                "cold",
                "green",
                "green",
                "lime"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "auburn",
                "cold",
                "blue",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "auburn",
                "cold",
                "blue",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "auburn",
                "cold",
                "blue",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "tea",
                "blue",
                "ultramarine"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "azure"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "indigo"));
        expected.add(Map.of("heat",
                "warm,cold",
                "warm",
                "red,orange",
                "orange",
                "peach",
                "red",
                "auburn",
                "cold",
                "blue,green",
                "green",
                "lime",
                "blue",
                "ultramarine"));

        assertThat(permutations, isEqual(expected));
    }

    @Test
    void testE2e() {
        List<Map<String, String>> permutations = permutations("e2e/main.xml");
        assertThat(permutations.size(), is(65604));
    }

    @Test
    void testFilters() {
        Collection<Collection<Expression>> excludes = List.of(
                exclude("${theme} == 'colors'",
                        "${theme.base.style} == 'modern'"),
                exclude("${theme} == 'colors'",
                        "${theme.base.colors} contains 'orange'"),
                exclude("${theme} == 'colors'",
                        "${theme.base.colors} contains 'yellow'"),
                exclude("${theme} == 'colors'",
                        "${theme.base.colors} contains 'indigo'"),
                exclude("${theme} == 'colors'",
                        "${theme.base.colors} contains 'violet'"),
                exclude("${theme} == 'colors'",
                        "${theme.base.colors} contains 'pink'"),
                exclude("${theme} == 'colors'",
                        "${theme.base.colors} contains 'light-pink'"),
                exclude("${theme} == 'colors'",
                        "${theme.base.colors} contains 'cyan'"),
                exclude("${theme} == 'colors'",
                        "${theme.base.colors} contains 'light-salmon'"),
                exclude("${theme} == 'colors'",
                        "${theme.base.colors} contains 'coral'"),
                exclude("${theme} == 'colors'",
                        "${theme.base.colors} contains 'tomato'"),
                exclude("${theme} == 'colors'",
                        "${theme.base.colors} contains 'lemon'"),
                exclude("${theme} == 'colors'",
                        "${theme.base.colors} contains 'khaki'"),
                exclude("${theme} == 'shapes'",
                        "${theme.base.style} == 'modern'"),
                exclude("${theme} == 'shapes'",
                        "${theme.base.shapes}  contains 'arrow'"));

        List<Map<String, String>> permutations =
                new InputPermutations(load("e2e/main.xml"))
                        .filter(excludes)
                        .compute();

        List<Map<String, String>> expected = new ArrayList<>();
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "colors",
                "theme.base", "custom",
                "theme.base.colors", "none",
                "theme.base.palette-name", "My Palette",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "colors",
                "theme.base", "custom",
                "theme.base.colors", "red",
                "theme.base.palette-name", "My Palette",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "colors",
                "theme.base", "custom",
                "theme.base.colors", "green",
                "theme.base.palette-name", "My Palette",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "colors",
                "theme.base", "custom",
                "theme.base.colors", "red,green",
                "theme.base.palette-name", "My Palette",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "colors",
                "theme.base", "custom",
                "theme.base.colors", "blue",
                "theme.base.palette-name", "My Palette",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "colors",
                "theme.base", "custom",
                "theme.base.colors", "red,blue",
                "theme.base.palette-name", "My Palette",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "colors",
                "theme.base", "custom",
                "theme.base.colors", "green,blue",
                "theme.base.palette-name", "My Palette",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "colors",
                "theme.base", "custom",
                "theme.base.colors", "red,green,blue",
                "theme.base.palette-name", "My Palette",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "colors",
                "theme.base", "rainbow",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "none",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "circle",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "triangle",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "circle,triangle",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "rectangle",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "circle,rectangle",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "triangle,rectangle",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "circle,triangle,rectangle",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "donut",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "circle,donut",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "triangle,donut",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "circle,triangle,donut",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "rectangle,donut",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "circle,rectangle,donut",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "triangle,rectangle,donut",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "custom",
                "theme.base.library-name", "My Shapes",
                "theme.base.shapes", "circle,triangle,rectangle,donut",
                "theme.base.style", "classic"));
        expected.add(Map.of(
                "artifactId", "my-project",
                "theme", "shapes",
                "theme.base", "2d",
                "theme.base.style", "classic"));

        assertThat(permutations, isEqual(expected));
    }

    @Test
    void testExternals() {
        List<Map<String, String>> perms = new InputPermutations(load("permutations/text2.xml"))
                .externalValues(Map.of("name", "foo"))
                .compute();
        assertThat(perms.size(), is(1));
        assertThat(perms.get(0).size(), is(1));
        assertThat(perms.get(0).get("name"), is("foo"));
    }

    @Test
    void testExternals2() {
        List<Map<String, String>> perms = new InputPermutations(load("permutations/enum2.xml"))
                .externalValues(Map.of("colors.orange", "peach"))
                .compute();
        assertThat(perms, isEqual(
                Map.of("colors", "green"),
                Map.of("colors", "orange", "colors.orange", "peach"),
                Map.of("colors", "red", "colors.red", "burgundy"),
                Map.of("colors", "red", "colors.red", "auburn")));
    }

    static List<Expression> exclude(String... exclude) {
        return Lists.map(List.of(exclude), Expression::create);
    }

    static Node load(String path) {
        return Script.load(testResourcePath(InputPermutationsTest.class, path));
    }

    static List<Map<String, String>> permutations(String path) {
        return new InputPermutations(load(path)).compute();
    }

    static PermutationMatcher isEqual(Map<String, String>... permutations) {
        return new PermutationMatcher(Arrays.asList(permutations));
    }

    static PermutationMatcher isEqual(List<Map<String, String>> permutations) {
        return new PermutationMatcher(permutations);
    }

    static final class PermutationMatcher extends TypeSafeMatcher<List<Map<String, String>>> {

        private final List<String> expected;

        PermutationMatcher(List<Map<String, String>> permutations) {
            expected = flatten(permutations);
        }

        @Override
        protected boolean matchesSafely(List<Map<String, String>> actual) {
            return expected.equals(flatten(actual));
        }

        @Override
        public void describeTo(Description description) {
            describe(expected, description);
        }

        @Override
        protected void describeMismatchSafely(List<Map<String, String>> item, Description description) {
            description.appendText("was ");
            describe(flatten(item), description);
        }

        static List<String> flatten(List<Map<String, String>> permutations) {
            return permutations.stream()
                    .flatMap(m -> m.entrySet().stream())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .sorted(Comparator.comparing(Object::toString))
                    .collect(Collectors.toList());
        }

        static void describe(List<String> permutations, Description description) {
            Iterator<String> it = permutations.iterator();
            while (it.hasNext()) {
                String next = it.next();
                description.appendText(next);
                if (it.hasNext()) {
                    // use newlines to make it easier to diff
                    description.appendValue(System.lineSeparator());
                }
            }
        }
    }
}
