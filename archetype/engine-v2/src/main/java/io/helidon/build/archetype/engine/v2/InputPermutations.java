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

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Predicate;

import io.helidon.build.archetype.engine.v2.Context.ScopeValue;
import io.helidon.build.archetype.engine.v2.Context.ValueKind;
import io.helidon.build.archetype.engine.v2.ScriptInvoker.InvocationException;
import io.helidon.build.common.Lists;
import io.helidon.build.common.Maps;
import io.helidon.build.common.Permutations;
import io.helidon.build.common.logging.Log;

import static io.helidon.build.archetype.engine.v2.InputResolver.optionIndex;
import static io.helidon.build.archetype.engine.v2.InputResolver.optionNodes;
import static io.helidon.build.archetype.engine.v2.InputResolver.visitValue;

/**
 * A utility to compute input permutations.
 */
public class InputPermutations {

    private static final Random RANDOM = new Random();

    private final Node node;
    private final Path cwd;
    private final Map<String, String> externalValues = new HashMap<>();
    private final Map<String, String> externalDefaults = new HashMap<>();
    private Predicate<Map<String, String>> filter = m -> true;

    /**
     * Create a new instance.
     *
     * @param node script
     */
    public InputPermutations(Node node) {
        this.node = Objects.requireNonNull(node, "node is null!");
        this.cwd = node.script().path().getParent();
    }

    /**
     * Set the external values.
     *
     * @param externalValues external values
     * @return this instance
     */
    public InputPermutations externalValues(Map<String, String> externalValues) {
        if (externalValues != null) {
            this.externalValues.putAll(externalValues);
        }
        return this;
    }

    /**
     * Set the external defaults.
     *
     * @param externalDefaults external values
     * @return this instance
     */
    public InputPermutations externalDefaults(Map<String, String> externalDefaults) {
        if (externalDefaults != null) {
            this.externalDefaults.putAll(externalDefaults);
        }
        return this;
    }

    /**
     * Set the filter.
     *
     * @param excludes excludes
     * @return this instance
     */
    public InputPermutations filter(Collection<Collection<Expression>> excludes) {
        return filter(p -> !Lists.anyMatch(excludes, it -> {
            try {
                return Lists.allMatch(it, expr -> expr.eval(p));
            } catch (Expression.UnresolvedVariableException ignored) {
                return false;
            }
        }));
    }

    /**
     * Set the filter.
     *
     * @param predicate predicate
     * @return this instance
     */
    public InputPermutations filter(Predicate<Map<String, String>> predicate) {
        if (predicate != null) {
            this.filter = predicate;
        }
        return this;
    }

    /**
     * Compute the input permutations.
     *
     * @return list of permutations
     */
    public List<Map<String, String>> compute() {

        // Make a single pass to capture all permutations
        InvokerImpl invoker = new InvokerImpl(cwd);
        invoker.invoke(node);

        // for each permutation, perform a normal execution
        // and use the resulting context values as permutation
        List<Map<String, String>> permutations = Lists.flatMap(invoker.stack.pop());
        Map<String, List<TreeMap<String, ScopeValue<?>>>> result0 = new LinkedHashMap<>();
        for (Map<String, String> permutation : permutations) {
            if (!filter.test(permutation)) {
                continue;
            }
            Map<String, ScopeValue<?>> contextValues = execute(permutation);
            if (!contextValues.isEmpty()) {
                Map<String, ScopeValue<?>> filteredValues = Maps.mapValue(contextValues, (k, v) -> {
                    if (v.isEmpty()) {
                        return false;
                    }
                    switch (v.kind()) {
                        case USER:
                        case DEFAULT:
                            return true;
                        default:
                            return false;
                    }
                }, v -> v);
                if (!filteredValues.isEmpty()) {
                    Map<String, String> effectivePermutation = Maps.mapValue(filteredValues, v -> v.asString().orElse("null"));
                    if (filter.test(effectivePermutation)) {
                        result0.computeIfAbsent(effectivePermutation.toString(), k -> new ArrayList<>())
                                .add(new TreeMap<>(filteredValues));
                    }
                }
            }
        }

        // filter implicit duplicates (default values)
        List<Map<String, String>> result1 = Lists.map(result0.values(), l -> {
            List<Map<String, String>> duplicates = Lists.map(l, p -> Maps.mapValue(p,
                    (k, v) -> v.kind() == ValueKind.USER, Value::getString));
            Map<String, String> chosen = null;
            for (Map<String, String> p : duplicates) {
                if (chosen == null) {
                    chosen = p;
                    continue;
                }
                if (p.size() > chosen.size()) {
                    chosen = p;
                }
            }
            return chosen;
        });

        // sort the result
        List<Map<String, String>> list = new ArrayList<>(result1);
        list.sort(Comparator.comparing(m -> m.entrySet().iterator().next().toString()));
        return list;
    }

    private Map<String, ScopeValue<?>> execute(Map<String, String> permutation) {
        try {
            Context context = new Context().pushCwd(cwd);
            ScriptInvoker.invoke(node, context, new InputResolverImpl(context, permutation));
            return context.scope().values();
        } catch (InvocationException ex) {
            if (!(ex.getCause() instanceof InvalidOption)) {
                Log.warn(ex, "Permutation error: %s, permutation: %s",
                        ex.getCause().getMessage(),
                        permutation);
            }
        }
        return Map.of();
    }

    private final class InputResolverImpl extends InputResolver {

        private final Map<String, String> permutation;

        InputResolverImpl(Context context, Map<String, String> permutation) {
            super(context);
            this.permutation = permutation;
        }

        @Override
        protected boolean visitInput(Node node, Context.Scope scope) {
            String id = node.attribute("id").getString();
            int code = resolveInput(node, scope);
            if (code < 0) {
                String path = scope.key();
                String rawValue = permutation.get(path);
                if (rawValue == null) {
                    Value<?> defaultValue = defaultValue(node, scope);
                    if (defaultValue.isEmpty()) {
                        throw new InputUnresolvedException(path);
                    }
                    scope.getOrCreate(id).value(defaultValue, ValueKind.DEFAULT);
                    return visitValue(node, defaultValue);
                }
                Value<?> value = Value.dynamic(scope.interpolate(rawValue));
                List<Node> options;
                List<String> values;
                switch (node.kind()) {
                    case INPUT_LIST:
                        options = optionNodes(node, n -> n.expression().eval(s -> scope.get(s).value()));
                        values = Lists.map(options, o -> scope.interpolate(o.value().getString()));
                        validateValue(values, value);
                        code = 1;
                        break;
                    case INPUT_ENUM:
                        String opt = value.getString();
                        options = optionNodes(node, n -> n.expression().eval(s -> scope.get(s).value()));
                        values = Lists.map(options, o -> scope.interpolate(o.value().getString()));
                        if (!values.contains(opt)) {
                            throw new InvalidOption(opt, permutation);
                        }
                        code = 1;
                        break;
                    default:
                        code = visitValue(node, value) ? 1 : 0;
                }
                scope.getOrCreate(id).value(value, ValueKind.USER);
            }
            return code == 1;
        }

        @Override
        protected int resolveInput(Node node, Context.Scope scope) {
            String rawValue = externalValues.get(scope.key());
            if (rawValue != null) {
                String id = node.attribute("id").getString();
                scope.getOrCreate(id).value(Value.dynamic(rawValue), ValueKind.USER);
            }
            return super.resolveInput(node, scope);
        }

        void validateValue(List<String> optionValues, Value<?> value) {
            if (!"none".equals(value.getString())) {
                for (String opt : value.getList()) {
                    if (!optionValues.contains(opt)) {
                        throw new InvalidOption(opt, permutation);
                    }
                }
            }
        }
    }

    private final class InvokerImpl extends ScriptInvoker {

        private final Deque<List<List<Map<String, String>>>> stack = new ArrayDeque<>();

        InvokerImpl(Path cwd) {
            super(new Context().pushCwd(cwd));
            stack.push(Lists.of());
        }

        @Override
        public boolean visit(Node node) {
            switch (node.kind()) {
                case CONDITION:
                    return true;
                case SCRIPT:
                case STEP:
                case INPUTS:
                    stack.push(Lists.of());
                    break;
                case OUTPUT:
                    return false;
                case PRESET_BOOLEAN:
                case PRESET_TEXT:
                case PRESET_LIST:
                case PRESET_ENUM:
                    // use local var instead of preset to allow overrides
                    context().scope().getOrCreate(node.attribute("path").getString())
                            .value(node.value(), ValueKind.LOCAL_VAR);
                    return true;
                case INPUT_BOOLEAN:
                case INPUT_TEXT:
                case INPUT_LIST:
                case INPUT_ENUM:
                case INPUT_OPTION:
                    return visitInput(node);
                default:
            }
            return super.visit(node);
        }

        @Override
        public void postVisit(Node node) {
            switch (node.kind()) {
                case EXEC:
                    context().popCwd();
                    break;
                case SCRIPT:
                case STEP:
                case INPUTS:
                    // nested permutations
                    List<List<Map<String, String>>> perms = stack.pop();

                    int permSize = perms.stream().map(List::size).reduce(1, (a, b) -> a * b);
                    if (permSize > 150000) {
                        Log.warn("Too many permutations: %s, node: %s", permSize, node.location());
                    }

                    // compute permutations
                    List<List<Map<String, String>>> computed = compute(perms);

                    // reduce and merge to avoid computing again at the parent level
                    List<Map<String, String>> merged = Lists.map(computed, Maps::merge);

                    // add permutation
                    stack.getFirst().add(merged);
                    break;
                case INPUT_BOOLEAN:
                case INPUT_TEXT:
                case INPUT_LIST:
                case INPUT_ENUM:
                case INPUT_OPTION:
                    postVisitInput(node);
                    break;
                default:
            }
        }

        private boolean visitInput(Node node) {
            stack.push(Lists.of());
            switch (node.kind()) {
                case INPUT_BOOLEAN:
                case INPUT_TEXT:
                case INPUT_LIST:
                case INPUT_ENUM:
                    Context.Scope scope = context().pushScope(s -> s.getOrCreate(node));
                    if (node.kind() == Node.Kind.INPUT_ENUM) {
                        List<Node> options = optionNodes(node, n -> true);
                        String defaultValue = node.attribute("default").asString().orElse(null);
                        int defaultIndex = optionIndex(defaultValue, options);
                        // skip if there is only one option with a default value
                        if (options.size() == 1 && defaultIndex >= 0) {
                            return false;
                        }
                    }
                    Value<?> value = scope.value();
                    if (value.isPresent()) {
                        // we only maintain values for variables and presets
                        // to control the flow if there is a value
                        return visitValue(node, value);
                    }
                    break;
                case INPUT_OPTION:
                    // clear all the nested values from a previous pass
                    context().scope().clear();
                    break;
                default:
                    throw new IllegalStateException("Unexpected node kind: " + node.kind());
            }
            return true;
        }

        private void postVisitInput(Node node) {
            // compute permutations for the input
            List<List<Map<String, String>>> computed = Lists.filter(compute(node), l -> !l.isEmpty());

            // parent level computes permutations for the first dimension
            // we reduce to avoid computing again at the parent level,
            // E.g.,
            // computed: [[{colors=red}], [{colors=orange}]]
            // reduced: [[{colors=red}, {colors=orange}]]
            List<Map<String, String>> reduced = Lists.flatMap(computed);

            // add permutation
            stack.getFirst().add(reduced);
        }

        List<List<Map<String, String>>> compute(List<List<Map<String, String>>> perms) {
            List<List<Map<String, String>>> computed = new ArrayList<>();
            Iterator<List<Map<String, String>>> it = new Permutations.ListIterator<>(perms);
            while (it.hasNext()) {
                List<Map<String, String>> next = it.next();
                computed.add(next);
            }
            return computed;
        }

        private List<List<Map<String, String>>> compute(Node node) {
            // nested permutations
            // each element of the list represents the permutations for a child
            List<List<Map<String, String>>> perms = Lists.filter(stack.pop(), l -> !l.isEmpty());
            Context.Scope scope = context().scope();
            String path = scope.key();
            switch (node.kind()) {
                case INPUT_BOOLEAN:
                case INPUT_TEXT:
                case INPUT_LIST:
                case INPUT_ENUM:
                    Value<?> value = scope.value();
                    context().popScope();
                    if (value.isPresent() && !visitValue(node, value)) {
                        return perms;
                    }
                    switch (node.kind()) {
                        case INPUT_BOOLEAN:
                            // add true to all nested permutations and a new permutation for false
                            // E.g.,
                            // nested:   [[{colors=red}], [{colors=orange}]]
                            // computed: [[{colors=red, path=true}, [{colors=orange, path=true}], [{path=false}]]
                            putValue(perms, path, "true");
                            perms.add(Lists.of(Maps.of(path, "false")));
                            return perms;
                        case INPUT_LIST:
                            // the nested permutations represent the permutations for each option
                            // compute permutations for the options
                            // E.g.,
                            // options: [a, b]
                            // computed: [[], [a], [b], [a,b]]
                            return Lists.map(Permutations.of(perms), option -> {
                                switch (option.size()) {
                                    case 0:
                                        // an empty permutation needs an entry
                                        return Lists.of(Maps.of(path, "none"));
                                    case 1:
                                        // single permutation (single option)
                                        return option.get(0);
                                    default:
                                        // multiple options
                                        // e.g.
                                        //
                                        // options:  [[{colors=red,    red=burgundy},  {colors=red,    red=auburn}],
                                        //            [{colors=orange, orange=salmon}, {colors=orange, orange=peach}]]
                                        //
                                        // computed: [[{colors=red, red=burgundy}, {colors=orange, orange=salmon}],
                                        //            [{colors=red, red=auburn},   {colors=orange, orange=salmon}],
                                        //            [{colors=red, red=burgundy}, {colors=orange, orange=peach}],
                                        //            [{colors=red, red=auburn},   {colors=orange, orange=peach}]]
                                        //
                                        // merged:   [{colors='red,orange', red=burgundy, orange=salmon},
                                        //            {colors='red,orange', red=burgundy, orange=peach},
                                        //            {colors='red,orange', red=auburn,   orange=peach},
                                        //            {colors='red,orange', red=auburn,   orange=peach}]
                                        List<List<Map<String, String>>> comp = compute(option);
                                        return Lists.map(comp, l -> Maps.merge(l, (v1, v2) -> v1 + "," + v2));
                                }
                            });
                        case INPUT_TEXT:
                            String id = node.attribute("id").getString();
                            String defaultValue = node.attribute("default").asString().orElse(null);
                            String inputValue = Optional.ofNullable(externalValues.get(path))
                                    .or(() -> Optional.ofNullable(externalDefaults.get(path)))
                                    .or(() -> Optional.ofNullable(defaultValue))
                                    .orElse(id + "-" + RANDOM.nextInt());

                            // add the text value to all nested permutations
                            // E.g.,
                            // nested:   [[{colors=red}], [{colors=orange}]]
                            // computed: [[{colors=red, path=xxx}, [{colors=orange, path=xxx}]]
                            putValue(perms, path, inputValue);
                            return perms;
                        default:
                            // enum does not need computing (one-of)
                            return perms;
                    }
                case INPUT_OPTION:
                    // add the option value to all nested permutations
                    // nested: [[{colors=red}], [{colors=orange}]]
                    // computed: [[{colors=red, path=option}, [{colors=orange, path=option}]]
                    putValue(perms, path, node.value().getString());
                    return perms;
                default:
                    throw new IllegalArgumentException("Unexpected node kind: " + node.kind());
            }
        }

        private void putValue(List<List<Map<String, String>>> perms, String path, String value) {
            if (perms.isEmpty()) {
                perms.add(Lists.of(Maps.of(path, value)));
            } else {
                perms.forEach(l -> {
                    if (l.isEmpty()) {
                        l.add(Maps.of(path, value));
                    } else {
                        l.forEach(p -> p.put(path, value));
                    }
                });
            }
        }
    }

    private static final class InvalidOption extends RuntimeException {
        InvalidOption(String option, Map<String, String> permutation) {
            super(String.format("Invalid option value: %s, permutation: %s", option, permutation));
        }
    }
}
