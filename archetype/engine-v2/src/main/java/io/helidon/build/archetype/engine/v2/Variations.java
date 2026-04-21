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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.helidon.build.common.Lists;
import io.helidon.build.common.Maps;
import io.helidon.build.common.xml.XMLElement;

import static java.util.Collections.unmodifiableMap;
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
        return unboundedInputs;
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
            this.map = unmodifiableMap(new LinkedHashMap<>(values));
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

    /**
     * Variation computation request.
     */
    public static final class Request {
        private final List<Expression> filters;
        private final Map<String, String> externalValues;
        private final Map<String, String> externalDefaults;
        private final List<Plan> plans;
        private final long maxIntermediate;

        /**
         * Empty variation request.
         */
        public static final Request EMPTY = builder().build();

        /**
         * Create a new builder.
         *
         * @return builder
         */
        public static Builder builder() {
            return new Builder();
        }

        private Request(Builder builder) {
            this.filters = List.copyOf(builder.filters);
            this.externalValues = unmodifiableMap(new LinkedHashMap<>(builder.externalValues));
            this.externalDefaults = unmodifiableMap(new LinkedHashMap<>(builder.externalDefaults));
            this.plans = List.copyOf(builder.plans);
            if (builder.maxIntermediate < 0) {
                throw new IllegalArgumentException("maxIntermediate must be >= 0");
            }
            this.maxIntermediate = builder.maxIntermediate;
        }

        /**
         * Global exclusion filters.
         *
         * @return exclusion filters
         */
        public List<Expression> filters() {
            return filters;
        }

        /**
         * Fixed external values.
         *
         * @return fixed external values
         */
        public Map<String, String> externalValues() {
            return externalValues;
        }

        /**
         * External defaults.
         *
         * @return external defaults
         */
        public Map<String, String> externalDefaults() {
            return externalDefaults;
        }

        /**
         * Named plans. An empty list requests plain variation computation.
         *
         * @return plans
         */
        public List<Plan> plans() {
            return plans;
        }

        /**
         * Maximum actual pre-normalization intermediate variation rows to allow.
         *
         * @return maximum intermediate variation row count
         */
        public long maxIntermediate() {
            return maxIntermediate;
        }

        /**
         * Request builder.
         */
        @SuppressWarnings("UnusedReturnValue")
        public static final class Builder {
            private final List<Expression> filters = new ArrayList<>();
            private final Map<String, String> externalValues = new LinkedHashMap<>();
            private final Map<String, String> externalDefaults = new LinkedHashMap<>();
            private final List<Plan> plans = new ArrayList<>();
            private long maxIntermediate = Long.MAX_VALUE;

            private Builder() {
            }

            /**
             * Add a global exclusion filter.
             *
             * @param filter exclusion filter
             * @return this builder
             */
            public Builder filter(Expression filter) {
                this.filters.add(requireNonNull(filter, "filter is null"));
                return this;
            }

            /**
             * Add a global exclusion filter from its expression string.
             *
             * @param expression exclusion filter expression
             * @return this builder
             */
            public Builder expression(String expression) {
                return filter(Expression.create(expression));
            }

            /**
             * Add global exclusion filters.
             *
             * @param filters exclusion filters
             * @return this builder
             */
            public Builder filters(Iterable<? extends Expression> filters) {
                requireNonNull(filters, "filters is null");
                for (Expression filter : filters) {
                    filter(filter);
                }
                return this;
            }

            /**
             * Add a fixed external value.
             *
             * @param key   external value key
             * @param value external value
             * @return this builder
             */
            public Builder externalValue(String key, String value) {
                requireNonNull(key, "key is null");
                requireNonNull(value, "value is null");
                this.externalValues.put(key, value);
                return this;
            }

            /**
             * Add fixed external values.
             *
             * @param externalValues fixed external values
             * @return this builder
             */
            public Builder externalValues(Map<String, String> externalValues) {
                requireNonNull(externalValues, "externalValues is null");
                externalValues.forEach(this::externalValue);
                return this;
            }

            /**
             * Add an external default.
             *
             * @param key   external default key
             * @param value external default value
             * @return this builder
             */
            public Builder externalDefault(String key, String value) {
                requireNonNull(key, "key is null");
                requireNonNull(value, "value is null");
                this.externalDefaults.put(key, value);
                return this;
            }

            /**
             * Add external defaults.
             *
             * @param externalDefaults external defaults
             * @return this builder
             */
            public Builder externalDefaults(Map<String, String> externalDefaults) {
                requireNonNull(externalDefaults, "externalDefaults is null");
                externalDefaults.forEach(this::externalDefault);
                return this;
            }

            /**
             * Add a plan.
             *
             * @param plan plan
             * @return this builder
             */
            public Builder plan(Plan plan) {
                requireNonNull(plan, "plan is null");
                this.plans.add(plan);
                return this;
            }

            /**
             * Add plans.
             *
             * @param plans plans
             * @return this builder
             */
            public Builder plans(Iterable<? extends Plan> plans) {
                requireNonNull(plans, "plans is null");
                for (Plan plan : plans) {
                    plan(plan);
                }
                return this;
            }

            /**
             * Set the maximum actual pre-normalization intermediate variation rows to allow.
             *
             * @param maxIntermediate maximum intermediate variation rows
             * @return this builder
             */
            public Builder maxIntermediate(long maxIntermediate) {
                this.maxIntermediate = maxIntermediate;
                return this;
            }

            /**
             * Build the request.
             *
             * @return request
             */
            public Request build() {
                return new Request(this);
            }
        }
    }

    /**
     * Loaded variation plan definition.
     */
    public static final class Plan {
        private final String id;
        private final Map<String, String> externalValues;
        private final Map<String, String> externalDefaults;
        private final List<Expression> filters;

        /**
         * Create a new builder.
         *
         * @return builder
         */
        public static Builder builder() {
            return new Builder();
        }

        private Plan(Builder builder) {
            this.id = requireNonNull(builder.id, "id is null");
            this.externalValues = unmodifiableMap(new LinkedHashMap<>(builder.externalValues));
            this.externalDefaults = unmodifiableMap(new LinkedHashMap<>(builder.externalDefaults));
            this.filters = List.copyOf(builder.filters);
        }

        /**
         * Load variation plans from a file.
         *
         * @param file plans file
         * @return loaded plans
         */
        public static List<Plan> load(Path file) {
            requireNonNull(file, "file is null");
            if (!Files.exists(file)) {
                throw new IllegalStateException("Variation plans file does not exist: " + file);
            }
            try (InputStream is = Files.newInputStream(file)) {
                XMLElement root = XMLElement.parse(is);
                if (!root.name().equals("plans")) {
                    throw new IllegalStateException("Unexpected variation plans root element: " + root.name());
                }
                List<Plan> plans = load(root);
                if (plans.isEmpty()) {
                    throw new IllegalStateException("Variation plans file does not contain any <plan> elements: " + file);
                }
                return plans;
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        /**
         * Load variation plans from a parsed XML root.
         *
         * @param root plans XML root
         * @return loaded plans
         */
        public static List<Plan> load(XMLElement root) {
            requireNonNull(root, "root is null");
            Map<String, Fragment> fragments = new LinkedHashMap<>();
            int fragmentIndex = 1;
            for (XMLElement elt : root.children("fragment")) {
                String id = elt.attribute("id", null);
                if (id == null || id.isBlank()) {
                    throw new IllegalStateException("Fragment is missing required id attribute");
                }
                Fragment fragment = fragment(elt, "fragment-" + fragmentIndex++);
                if (fragments.putIfAbsent(fragment.id, fragment) != null) {
                    throw new IllegalStateException("Duplicate fragment id: " + fragment.id);
                }
            }

            List<Plan> plans = new ArrayList<>();
            int index = 1;
            for (XMLElement plan : root.children("plan")) {
                Fragment fragment = fragment(plan, "plan-" + index++);
                resolve(fragment, fragments);
                Builder builder = builder().id(fragment.id)
                        .externalValues(fragment.externalValues)
                        .externalDefaults(fragment.externalDefaults)
                        .filters(fragment.filters);
                plans.add(builder.build());
            }
            return plans;
        }

        /**
         * Load exclusion filters from a rules-style XML file.
         *
         * @param file rules file
         * @return loaded exclusion filters
         */
        public static List<Expression> loadFilters(Path file) {
            requireNonNull(file, "file is null");
            try (InputStream is = Files.newInputStream(file)) {
                return loadFilters(XMLElement.parse(is));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        /**
         * Load exclusion filters from a rules-style XML root.
         *
         * @param root rules XML root
         * @return loaded exclusion filters
         */
        public static List<Expression> loadFilters(XMLElement root) {
            requireNonNull(root, "root is null");
            List<Expression> excludes = new ArrayList<>();
            for (XMLElement elt : root.traverse(it -> it.name().equals("exclude"))) {
                Expression exclude = Expression.create(elt.attribute("if"));
                for (XMLElement n = elt.parent(); n != null; n = n.parent()) {
                    if (n.name().equals("rule")) {
                        exclude = exclude.and(Expression.create(n.attribute("if")));
                    }
                    if (n == root) {
                        break;
                    }
                }
                excludes.add(exclude);
            }
            return excludes;
        }

        /**
         * Plan id.
         *
         * @return plan id
         */
        public String id() {
            return id;
        }

        /**
         * Fixed external values.
         *
         * @return fixed external values
         */
        public Map<String, String> externalValues() {
            return externalValues;
        }

        /**
         * External defaults.
         *
         * @return external defaults
         */
        public Map<String, String> externalDefaults() {
            return externalDefaults;
        }

        /**
         * Exclusion filters.
         *
         * @return exclusion filters
         */
        public List<Expression> filters() {
            return filters;
        }

        /**
         * Plan builder.
         */
        public static final class Builder {
            private String id;
            private final Map<String, String> externalValues = new LinkedHashMap<>();
            private final Map<String, String> externalDefaults = new LinkedHashMap<>();
            private final List<Expression> filters = new ArrayList<>();

            private Builder() {
            }

            /**
             * Set the plan id.
             *
             * @param id plan id
             * @return this builder
             */
            public Builder id(String id) {
                this.id = requireNonNull(id, "id is null");
                return this;
            }

            /**
             * Add a fixed external value.
             *
             * @param key   external value key
             * @param value external value
             * @return this builder
             */
            public Builder externalValue(String key, String value) {
                requireNonNull(key, "key is null");
                requireNonNull(value, "value is null");
                this.externalValues.put(key, value);
                return this;
            }

            /**
             * Add fixed external values.
             *
             * @param externalValues fixed external values
             * @return this builder
             */
            public Builder externalValues(Map<String, String> externalValues) {
                requireNonNull(externalValues, "externalValues is null");
                externalValues.forEach(this::externalValue);
                return this;
            }

            /**
             * Add an external default.
             *
             * @param key   external default key
             * @param value external default value
             * @return this builder
             */
            public Builder externalDefault(String key, String value) {
                requireNonNull(key, "key is null");
                requireNonNull(value, "value is null");
                this.externalDefaults.put(key, value);
                return this;
            }

            /**
             * Add external defaults.
             *
             * @param externalDefaults external defaults
             * @return this builder
             */
            public Builder externalDefaults(Map<String, String> externalDefaults) {
                requireNonNull(externalDefaults, "externalDefaults is null");
                externalDefaults.forEach(this::externalDefault);
                return this;
            }

            /**
             * Add an exclusion filter.
             *
             * @param filter exclusion filter
             * @return this builder
             */
            public Builder filter(Expression filter) {
                requireNonNull(filter, "filter is null");
                this.filters.add(filter);
                return this;
            }

            /**
             * Add exclusion filters.
             *
             * @param filters exclusion filters
             * @return this builder
             */
            public Builder filters(Iterable<? extends Expression> filters) {
                requireNonNull(filters, "filters is null");
                for (Expression filter : filters) {
                    filter(filter);
                }
                return this;
            }

            /**
             * Build the plan.
             *
             * @return plan
             */
            public Plan build() {
                return new Plan(this);
            }
        }

        private static Fragment fragment(XMLElement element, String defaultId) {
            String id = element.attribute("id", null);
            if (id == null || id.isBlank()) {
                id = defaultId;
            } else {
                id = id.trim();
            }
            String extendsValue = element.attribute("extends", "").trim();
            List<String> extendsIds = new ArrayList<>();
            if (!extendsValue.isEmpty()) {
                for (String token : extendsValue.split(",")) {
                    String extendsId = token.trim();
                    if (!extendsId.isEmpty()) {
                        extendsIds.add(extendsId);
                    }
                }
            }
            Map<String, String> externalValues = new LinkedHashMap<>();
            for (XMLElement elt : element.child("values").map(XMLElement::children).orElse(List.of())) {
                externalValues.put(elt.name(), elt.value());
            }
            Map<String, String> externalDefault = new LinkedHashMap<>();
            for (XMLElement elt : element.child("defaults").map(XMLElement::children).orElse(List.of())) {
                externalDefault.put(elt.name(), elt.value());
            }
            List<Expression> filters = element.child("rules").map(Plan::loadFilters).orElseGet(ArrayList::new);
            return new Fragment(id, extendsIds, externalValues, externalDefault, filters);
        }

        private static void resolve(Fragment fragment, Map<String, Fragment> fragments) {
            Deque<Fragment> stack = new ArrayDeque<>();
            Set<Fragment> resolving = new LinkedHashSet<>();
            stack.push(fragment);
            while (!stack.isEmpty()) {
                Fragment current = stack.peek();
                if (current.resolved) {
                    stack.pop();
                    continue;
                }
                if (resolving.contains(current)) {
                    Map<String, String> localValues = new LinkedHashMap<>(current.externalValues);
                    Map<String, String> localDefaults = new LinkedHashMap<>(current.externalDefaults);
                    List<Expression> localFilters = new ArrayList<>(current.filters);
                    current.externalValues.clear();
                    current.externalDefaults.clear();
                    current.filters.clear();
                    for (String parentId : current.extendsIds) {
                        Fragment parent = fragments.get(parentId);
                        current.externalValues.putAll(parent.externalValues);
                        current.externalDefaults.putAll(parent.externalDefaults);
                        current.filters.addAll(parent.filters);
                    }
                    current.externalValues.putAll(localValues);
                    current.externalDefaults.putAll(localDefaults);
                    current.filters.addAll(localFilters);
                    current.resolved = true;
                    resolving.remove(current);
                    stack.pop();
                    continue;
                }

                resolving.add(current);
                for (int i = current.extendsIds.size() - 1; i >= 0; i--) {
                    String parentId = current.extendsIds.get(i);
                    Fragment parent = fragments.get(parentId);
                    if (parent == null) {
                        throw new IllegalStateException(String.format(
                                "Unknown fragment '%s' referenced by '%s'",
                                parentId, current.id));
                    }
                    if (resolving.contains(parent)) {
                        List<String> cycle = new ArrayList<>();
                        for (Iterator<Fragment> it = stack.descendingIterator(); it.hasNext(); ) {
                            Fragment candidate = it.next();
                            if (candidate == parent || !cycle.isEmpty()) {
                                cycle.add(candidate.id);
                            }
                        }
                        cycle.add(parentId);
                        throw new IllegalStateException("Circular fragment inheritance: " + String.join(" -> ", cycle));
                    }
                    if (!parent.resolved) {
                        stack.push(parent);
                    }
                }
            }
        }

        private static final class Fragment {
            private final String id;
            private final List<String> extendsIds;
            private final Map<String, String> externalValues;
            private final Map<String, String> externalDefaults;
            private final List<Expression> filters;
            private boolean resolved;

            Fragment(String id,
                     List<String> extendsIds,
                     Map<String, String> externalValues,
                     Map<String, String> externalDefaults,
                     List<Expression> filters) {
                this.id = id;
                this.extendsIds = extendsIds;
                this.externalValues = externalValues;
                this.externalDefaults = externalDefaults;
                this.filters = filters;
            }
        }
    }

    /**
     * Semantic diagnostics for a variation request.
     */
    public static final class Diagnostics {
        private static final Diagnostics EMPTY = new Diagnostics(List.of());

        private final List<Finding> findings;

        Diagnostics(List<Finding> findings) {
            this.findings = List.copyOf(requireNonNull(findings, "findings is null"));
        }

        static Diagnostics empty() {
            return EMPTY;
        }

        /**
         * Semantic findings.
         *
         * @return findings
         */
        public List<Finding> findings() {
            return findings;
        }

        /**
         * Returns whether any warning-level finding exists.
         *
         * @return {@code true} if warnings exist
         */
        public boolean hasWarnings() {
            return findings.stream().anyMatch(it -> it.severity() == Finding.Severity.WARNING);
        }

        /**
         * Returns whether any error-level finding exists.
         *
         * @return {@code true} if errors exist
         */
        public boolean hasErrors() {
            return findings.stream().anyMatch(it -> it.severity() == Finding.Severity.ERROR);
        }
    }

    /**
     * Semantic diagnostic finding.
     */
    public static final class Finding {

        /**
         * Finding kind.
         */
        public enum Kind {
            UNSATISFIABLE_PLAN,
            PLAN_OVERLAP,
            REDUNDANT_PLAN,
            REDUNDANT_PIN,
            COVERAGE_GAP
        }

        /**
         * Finding severity.
         */
        public enum Severity {
            ERROR,
            WARNING
        }

        private final Kind kind;
        private final Severity severity;
        private final String planId;
        private final String relatedPlanId;
        private final String key;
        private final String value;
        private final String expression;

        private Finding(Kind kind,
                        Severity severity,
                        String planId,
                        String relatedPlanId,
                        String key,
                        String value,
                        String expression) {
            this.kind = requireNonNull(kind, "kind is null");
            this.severity = requireNonNull(severity, "severity is null");
            this.planId = planId;
            this.relatedPlanId = relatedPlanId;
            this.key = key;
            this.value = value;
            this.expression = expression;
        }

        static Finding unsatisfiablePlan(String planId, String expression) {
            return new Finding(
                    Kind.UNSATISFIABLE_PLAN,
                    Severity.ERROR,
                    requireNonNull(planId, "planId is null"),
                    null,
                    null,
                    null,
                    requireNonNull(expression, "expression is null"));
        }

        static Finding planOverlap(String planId, String relatedPlanId, String expression) {
            return new Finding(
                    Kind.PLAN_OVERLAP,
                    Severity.WARNING,
                    requireNonNull(planId, "planId is null"),
                    requireNonNull(relatedPlanId, "relatedPlanId is null"),
                    null,
                    null,
                    requireNonNull(expression, "expression is null"));
        }

        static Finding redundantPlan(String planId, String relatedPlanId) {
            return new Finding(
                    Kind.REDUNDANT_PLAN,
                    Severity.WARNING,
                    requireNonNull(planId, "planId is null"),
                    requireNonNull(relatedPlanId, "relatedPlanId is null"),
                    null,
                    null,
                    null);
        }

        static Finding redundantPin(String planId, String key, String value) {
            return new Finding(
                    Kind.REDUNDANT_PIN,
                    Severity.WARNING,
                    requireNonNull(planId, "planId is null"),
                    null,
                    requireNonNull(key, "key is null"),
                    requireNonNull(value, "value is null"),
                    null);
        }

        static Finding coverageGap(String expression) {
            return new Finding(
                    Kind.COVERAGE_GAP,
                    Severity.WARNING,
                    null,
                    null,
                    null,
                    null,
                    requireNonNull(expression, "expression is null"));
        }

        /**
         * Finding kind.
         *
         * @return finding kind
         */
        public Kind kind() {
            return kind;
        }

        /**
         * Finding severity.
         *
         * @return finding severity
         */
        public Severity severity() {
            return severity;
        }

        /**
         * Primary plan id.
         *
         * @return primary plan id when present
         */
        public Optional<String> planId() {
            return Optional.ofNullable(planId);
        }

        /**
         * Related plan id.
         *
         * @return related plan id when present
         */
        public Optional<String> relatedPlanId() {
            return Optional.ofNullable(relatedPlanId);
        }

        /**
         * Input key.
         *
         * @return input key when present
         */
        public Optional<String> key() {
            return Optional.ofNullable(key);
        }

        /**
         * Input value.
         *
         * @return input value when present
         */
        public Optional<String> value() {
            return Optional.ofNullable(value);
        }

        /**
         * Rendered expression.
         *
         * @return expression when present
         */
        public Optional<String> expression() {
            return Optional.ofNullable(expression);
        }
    }
}
