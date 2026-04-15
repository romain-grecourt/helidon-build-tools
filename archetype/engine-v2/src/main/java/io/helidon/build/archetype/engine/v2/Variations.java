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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.helidon.build.common.Lists;
import io.helidon.build.common.Maps;

import static java.util.Objects.requireNonNull;

/**
 * Computed variations represented as an immutable set of entries.
 * Compute these immutable result sets via {@link VariationEngine}.
 * <p>
 * A variation is exhaustive when every active user input is enumerated by the computed entries.
 * Some inputs, such as free-form text inputs, cannot be exhaustively enumerated.
 * <p>
 * An input is active for a given entry when it participates in the reachable configuration represented by that
 * entry.
 * <p>
 * Inputs excluded by conditions, selected options, or other branch-specific pruning are not active for that
 * entry and therefore do not affect exhaustiveness.
 * <p>
 * Those inputs are tracked as unbounded on the affected entries and surfaced across the whole set through
 * {@link #unboundedInputs()}.
 * <p>
 * An unbounded entry still carries one representative value for the input, but that value stands in for an
 * open-ended range of possible user values rather than a complete enumeration.
 */
public final class Variations extends AbstractSet<Variations.Entry> {
    private final Set<Entry> entries;

    private Variations(Collection<Entry> entries) {
        this.entries = Collections.unmodifiableSet(new TreeSet<>(requireNonNull(entries)));
    }

    /**
     * Create a variation entry.
     *
     * @param map variation values
     * @return exhaustive variation entry
     */
    public static Entry entry(Map<String, String> map) {
        return entry(map, Set.of());
    }

    /**
     * Create a variation entry.
     *
     * @param map       variation values
     * @param unbounded unbounded input ids
     * @return variation entry
     */
    public static Entry entry(Map<String, String> map, Set<String> unbounded) {
        return entry(map, unbounded, null);
    }

    static Entry entry(Map<String, String> map, Set<String> unbounded, String signature) {
        return new Entry(map, unbounded, signature);
    }

    /**
     * Create a singleton variation set.
     *
     * @param map       variation values
     * @param unbounded unbounded input ids
     * @return singleton variation set
     */
    public static Variations of(Map<String, String> map, Set<String> unbounded) {
        return new Variations(Set.of(entry(map, unbounded)));
    }

    /**
     * Create a variation set from existing entries.
     *
     * @param entries variation entries
     * @return variation set
     */
    public static Variations of(Collection<Entry> entries) {
        return new Variations(entries);
    }

    /**
     * Create a variation set from existing entries.
     *
     * @param entries variation entries
     * @return variation set
     */
    public static Variations of(Entry... entries) {
        return new Variations(Set.of(entries));
    }

    /**
     * Merge multiple computed variation sets into one.
     * <p>
     * Entries with the same resolved values are merged so the result preserves a single representative entry
     * and the union of their unbounded inputs.
     *
     * @param variations computed variations to merge
     * @return merged variations
     */
    public static Variations union(Collection<Variations> variations) {
        requireNonNull(variations);
        Map<String, Entry> merged = new HashMap<>();
        for (Variations variation : variations) {
            requireNonNull(variation);
            for (Entry entry : variation) {
                merged.merge(entry.identity(), entry, Entry::merge);
            }
        }
        return new Variations(merged.values());
    }

    @Override
    public Iterator<Entry> iterator() {
        return entries.iterator();
    }

    @Override
    public int size() {
        return entries.size();
    }

    /**
     * Get the input ids that remain unbounded across the computed variations.
     *
     * @return unbounded input ids
     */
    public Set<String> unboundedInputs() {
        Set<String> unboundedInputs = new TreeSet<>();
        for (Entry entry : entries) {
            unboundedInputs.addAll(entry.unbounded());
        }
        return Collections.unmodifiableSet(unboundedInputs);
    }

    /**
     * Returns whether the computed variations are exhaustive.
     *
     * @return {@code true} if no input remains unbounded
     */
    public boolean exhaustive() {
        return entries.stream().allMatch(Entry::exhaustive);
    }

    @Override
    public String toString() {
        return toString(true);
    }

    /**
     * Render the computed variations with a custom separator between entries.
     *
     * @param separator separator between rendered entries
     * @return rendered variations
     */
    public String toString(String separator) {
        return toString(true, separator);
    }

    /**
     * Render the computed variations.
     *
     * @param markUnbounded whether non-exhaustive entries should include their unbounded input ids
     * @return rendered variations
     */
    public String toString(boolean markUnbounded) {
        return toString(markUnbounded, System.lineSeparator());
    }

    /**
     * Render the computed variations with a custom separator between entries.
     *
     * @param markUnbounded whether non-exhaustive entries should include their unbounded input ids
     * @param separator     separator between rendered entries
     * @return rendered variations
     */
    public String toString(boolean markUnbounded, String separator) {
        requireNonNull(separator);
        return entries.stream()
                .map(entry -> entry.toString(markUnbounded))
                .collect(Collectors.joining(separator));
    }

    /**
     * Computed variation.
     */
    public static final class Entry extends AbstractMap<String, String> implements Comparable<Variations.Entry> {
        private final Map<String, String> map;
        private final String signature;
        private final Set<String> unbounded;

        private Entry(Map<String, String> values, Set<String> unbounded, String signature) {
            requireNonNull(values);
            requireNonNull(unbounded);
            this.map = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            this.signature = signature;
            this.unbounded = Collections.unmodifiableSet(new TreeSet<>(unbounded));
        }

        @Override
        public Set<Map.Entry<String, String>> entrySet() {
            return map.entrySet();
        }

        /**
         * Get the active input ids that remain unbounded.
         *
         * @return unbounded input ids
         */
        public Set<String> unbounded() {
            return unbounded;
        }

        /**
         * Returns whether this variation is exhaustive.
         *
         * @return {@code true} if the variation has no unbounded inputs
         */
        public boolean exhaustive() {
            return unbounded.isEmpty();
        }

        @Override
        public int compareTo(Variations.Entry other) {
            int result = Maps.compare(this, other);
            if (result != 0) {
                return result;
            }
            return Lists.compare(unbounded, other.unbounded);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Variations.Entry)) {
                return false;
            }
            Variations.Entry other = (Variations.Entry) o;
            return Objects.equals(map, other.map) && Objects.equals(unbounded, other.unbounded);
        }

        @Override
        public int hashCode() {
            return Objects.hash(map, unbounded);
        }

        String signature() {
            if (signature != null) {
                return signature;
            }
            return Lists.join(new TreeMap<>(map).entrySet(), " ");
        }

        String identity() {
            return Lists.join(new TreeMap<>(map).entrySet(), " ");
        }

        @Override
        public String toString() {
            return toString(true);
        }

        /**
         * Render the variation entry with a custom separator between values.
         *
         * @param separator separator between rendered values
         * @return rendered variation entry
         */
        public String toString(String separator) {
            return toString(true, separator);
        }

        /**
         * Render the variation entry.
         *
         * @param markUnbounded whether to include unbounded input ids for non-exhaustive entries
         * @return rendered variation entry
         */
        public String toString(boolean markUnbounded) {
            return toString(markUnbounded, ", ");
        }

        /**
         * Render the variation entry with a custom separator between values.
         *
         * @param markUnbounded whether to include unbounded input ids for non-exhaustive entries
         * @param separator     separator between rendered values
         * @return rendered variation entry
         */
        public String toString(boolean markUnbounded, String separator) {
            requireNonNull(separator);
            String values = entrySet().stream()
                    .map(Map.Entry::toString)
                    .collect(Collectors.joining(separator));
            if (!markUnbounded || exhaustive()) {
                return values;
            }
            return values + " unbounded=" + unbounded;
        }

        Variations.Entry merge(Variations.Entry other) {
            requireNonNull(other);
            Variations.Entry selected = compare(other) >= 0 ? this : other;
            Set<String> merged = new TreeSet<>(unbounded);
            merged.addAll(other.unbounded);
            if (selected.unbounded.equals(merged)) {
                return selected;
            }
            return new Variations.Entry(selected, merged, selected.signature);
        }

        private int compare(Variations.Entry other) {
            int result = Integer.compare(size(), other.size());
            return result != 0 ? result : Maps.compare(map, other.map);
        }
    }
}
