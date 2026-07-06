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
package io.helidon.build.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Tests {@link Maps}.
 */
class MapsTest {

    @Test
    void testComputeIfAbsentDoesNotMutateInput() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("one", "1");
        Map<String, Function<String, String>> mappings = Map.of("two", key -> "2");

        Map<String, String> result = Maps.computeIfAbsent(input, mappings);

        assertThat(result, is(Map.of("one", "1", "two", "2")));
        assertThat(input, is(Map.of("one", "1")));
        assertThat(result, is(not(sameInstance(input))));
    }

    @Test
    void testComputeIfAbsentPreservesExistingValues() {
        AtomicBoolean used = new AtomicBoolean();
        Map<String, String> input = Map.of("one", "1");
        Map<String, Function<String, String>> mappings = Map.of("one", key -> {
            used.set(true);
            return "2";
        });

        Map<String, String> result = Maps.computeIfAbsent(input, mappings);

        assertThat(result, is(Map.of("one", "1")));
        assertThat(used.get(), is(false));
    }

    @Test
    void testComputeIfAbsentSupportsImmutableInput() {
        Map<String, String> result = Maps.computeIfAbsent(Map.of("one", "1"), Map.of("two", key -> "2"));

        assertThat(result, is(Map.of("one", "1", "two", "2")));
    }

    @Test
    void testComputeIfAbsentPreservesOrder() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("one", "1");
        input.put("two", "2");
        Map<String, Function<String, String>> mappings = new LinkedHashMap<>();
        mappings.put("three", key -> "3");
        mappings.put("four", key -> "4");

        Map<String, String> result = Maps.computeIfAbsent(input, mappings);

        assertThat(List.copyOf(result.keySet()), contains("one", "two", "three", "four"));
    }
}
