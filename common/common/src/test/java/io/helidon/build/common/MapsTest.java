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
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link Maps}.
 */
class MapsTest {

    @Test
    void testEqualsWithPredicate() {
        Map<String, Integer> left = new LinkedHashMap<>();
        left.put("a", 1);
        left.put("b", 2);
        Map<String, Integer> right = new LinkedHashMap<>();
        right.put("b", 2);
        right.put("a", 1);

        assertThat(Maps.equals(left, right, Integer::equals), is(true));
    }

    @Test
    void testHashCodeIgnoresEntryOrder() {
        Map<String, Integer> left = new LinkedHashMap<>();
        left.put("a", 1);
        left.put("b", 2);
        Map<String, Integer> right = new LinkedHashMap<>();
        right.put("b", 2);
        right.put("a", 1);

        assertThat(Maps.hashCode(left, String::hashCode, value -> value.hashCode()),
                is(Maps.hashCode(right, String::hashCode, value -> value.hashCode())));
    }

    @Test
    void testHashCodeSupportsNullKeysAndValues() {
        Map<String, Integer> left = new LinkedHashMap<>();
        left.put(null, 1);
        left.put("a", null);
        Map<String, Integer> right = new LinkedHashMap<>();
        right.put("a", null);
        right.put(null, 1);

        assertThat(Maps.hashCode(left, String::hashCode, value -> value.hashCode()),
                is(Maps.hashCode(right, String::hashCode, value -> value.hashCode())));
    }
}
