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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntFunction;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.common.Lists;

import static java.util.Objects.requireNonNull;

final class Domain {

    private Domain() {
    }

    interface Spec {
        Kind kind();

        enum Kind {
            BOOLEAN,
            CHOICE,
            MEMBERSHIP,
            OPEN_TEXT,
            FINITE_TEXT
        }

        final class Boolean implements Spec {
            @Override
            public Kind kind() {
                return Kind.BOOLEAN;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof Boolean;
            }

            @Override
            public int hashCode() {
                return Kind.BOOLEAN.hashCode();
            }

            @Override
            public String toString() {
                return kind().name();
            }
        }

        final class Choice implements Spec {
            private final Set<String> values;

            Choice(Set<String> values) {
                if (values.isEmpty()) {
                    throw new IllegalArgumentException("Choice spec requires at least one value");
                }
                this.values = canonicalStrings(values);
            }

            @Override
            public Kind kind() {
                return Kind.CHOICE;
            }

            Set<String> values() {
                return values;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof Choice && values.equals(((Choice) o).values);
            }

            @Override
            public int hashCode() {
                return Objects.hash(kind(), values);
            }

            @Override
            public String toString() {
                return kind() + values.toString();
            }
        }

        final class Membership implements Spec {
            private final Set<String> items;

            Membership(Set<String> items) {
                if (items.isEmpty()) {
                    throw new IllegalArgumentException("Membership spec requires at least one item");
                }
                this.items = canonicalStrings(items);
            }

            @Override
            public Kind kind() {
                return Kind.MEMBERSHIP;
            }

            Set<String> items() {
                return items;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof Membership && items.equals(((Membership) o).items);
            }

            @Override
            public int hashCode() {
                return Objects.hash(kind(), items);
            }

            @Override
            public String toString() {
                return kind() + items.toString();
            }
        }

        final class OpenText implements Spec {
            @Override
            public Kind kind() {
                return Kind.OPEN_TEXT;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof OpenText;
            }

            @Override
            public int hashCode() {
                return Kind.OPEN_TEXT.hashCode();
            }

            @Override
            public String toString() {
                return kind().name();
            }
        }

        final class FiniteText implements Spec {
            private final Set<String> values;

            FiniteText(Set<String> values) {
                if (values.isEmpty()) {
                    throw new IllegalArgumentException("Finite text spec requires at least one value");
                }
                this.values = canonicalStrings(values);
            }

            @Override
            public Kind kind() {
                return Kind.FINITE_TEXT;
            }

            Set<String> values() {
                return values;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof FiniteText && values.equals(((FiniteText) o).values);
            }

            @Override
            public int hashCode() {
                return Objects.hash(kind(), values);
            }

            @Override
            public String toString() {
                return kind() + values.toString();
            }
        }
    }

    static final class Symbol {
        private final int id;
        private final String name;
        private final Spec domain;
        private final boolean guardable;
        private final boolean tainted;
        private final Map<String, Integer> scalarOrdinals;

        Symbol(int id, String name, Spec domain, boolean guardable, boolean tainted) {
            this.id = id;
            this.name = requireNonNull(name, "name is null");
            this.domain = requireNonNull(domain, "domain is null");
            this.guardable = guardable;
            this.tainted = tainted;
            this.scalarOrdinals = scalarOrdinals(domain);
        }

        int id() {
            return id;
        }

        String name() {
            return name;
        }

        Spec domain() {
            return domain;
        }

        boolean guardable() {
            return guardable;
        }

        boolean tainted() {
            return tainted;
        }

        boolean maskableScalar() {
            return scalarOrdinals != null;
        }

        long scalarMask(Set<String> values) {
            if (scalarOrdinals == null) {
                throw new IllegalStateException("Scalar mask requested for non-maskable symbol: " + name);
            }
            long mask = 0L;
            for (String value : values) {
                Integer ordinal = scalarOrdinals.get(value);
                if (ordinal == null) {
                    throw new IllegalArgumentException("Unknown scalar value for symbol " + name + ": " + value);
                }
                mask |= 1L << ordinal;
            }
            return mask;
        }

        private static Map<String, Integer> scalarOrdinals(Spec domain) {
            switch (domain.kind()) {
                case BOOLEAN:
                case CHOICE:
                case FINITE_TEXT:
                    Set<String> values = new TreeSet<>(scalarValues(domain));
                    if (values.size() > Long.SIZE) {
                        return null;
                    }
                    Map<String, Integer> ordinals = new LinkedHashMap<>();
                    int ordinal = 0;
                    for (String value : values) {
                        ordinals.put(value, ordinal++);
                    }
                    return Map.copyOf(ordinals);
                default:
                    return null;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Symbol)) {
                return false;
            }
            Symbol other = (Symbol) o;
            return id == other.id
                   && guardable == other.guardable
                   && tainted == other.tainted
                   && name.equals(other.name)
                   && domain.equals(other.domain);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name, domain, guardable, tainted);
        }

        @Override
        public String toString() {
            return name + "#" + id + ":" + domain;
        }

        static final class Fact {
            private final Guard definedUnder;
            private final Value value;
            private final List<ExactCase> exactCases;

            Fact(Guard definedUnder, Value value) {
                this(definedUnder, value, List.of());
            }

            Fact(Guard definedUnder, Value value, List<ExactCase> exactCases) {
                this.definedUnder = requireNonNull(definedUnder, "definedUnder is null");
                this.value = requireNonNull(value, "value is null");
                this.exactCases = List.copyOf(requireNonNull(exactCases, "exactCases is null"));
            }

            Guard definedUnder() {
                return definedUnder;
            }

            Value value() {
                return value;
            }

            List<ExactCase> exactCases() {
                return exactCases;
            }

            Guard exactDefined(Guards guards) {
                Guard result = guards.falseGuard();
                for (ExactCase exactCase : exactCases) {
                    result = guards.or(result, exactCase.guard);
                }
                return result;
            }

            Guard match(io.helidon.build.archetype.engine.v2.Value<?> candidate, Guards guards) {
                Guard result = guards.falseGuard();
                for (ExactCase exactCase : exactCases) {
                    if (io.helidon.build.archetype.engine.v2.Value.isEqual(exactCase.value, candidate)) {
                        result = guards.or(result, exactCase.guard);
                    }
                }
                return result;
            }

            Guard scalarAny(Set<String> values, Guards guards) {
                Guard result = guards.falseGuard();
                for (ExactCase exactCase : exactCases) {
                    String scalar = exactCase.scalarLiteral();
                    if (scalar != null && values.contains(scalar)) {
                        result = guards.or(result, exactCase.guard);
                    }
                }
                return result;
            }

            Guard listContains(Set<String> required, Guards guards) {
                Guard result = guards.falseGuard();
                for (ExactCase exactCase : exactCases) {
                    if (exactCase.containsAll(required)) {
                        result = guards.or(result, exactCase.guard);
                    }
                }
                return result;
            }

            static Fact exact(Guard definedUnder,
                              Value value,
                              io.helidon.build.archetype.engine.v2.Value<?> exactValue) {
                if (exactValue == null || !exactValue.isPresent()) {
                    return new Fact(definedUnder, value);
                }
                return new Fact(definedUnder, value, List.of(new ExactCase(exactValue, definedUnder)));
            }

            static Fact merge(Fact left, Fact right, Guards guards) {
                Guard definedUnder = guards.or(left.definedUnder, right.definedUnder);
                Value value = Value.join(left.value, right.value);
                if (left.exactCases.isEmpty()) {
                    return right.exactCases.isEmpty()
                            ? new Fact(definedUnder, value)
                            : new Fact(definedUnder, value, right.exactCases);
                }
                if (right.exactCases.isEmpty()) {
                    return new Fact(definedUnder, value, left.exactCases);
                }
                List<ExactCase> merged = new ArrayList<>();
                for (ExactCase exactCase : left.exactCases) {
                    mergeExactCase(merged, exactCase, guards);
                }
                for (ExactCase exactCase : right.exactCases) {
                    mergeExactCase(merged, exactCase, guards);
                }
                return new Fact(definedUnder, value, merged);
            }

            private static void mergeExactCase(List<ExactCase> merged, ExactCase next, Guards guards) {
                for (int i = 0; i < merged.size(); i++) {
                    ExactCase current = merged.get(i);
                    if (io.helidon.build.archetype.engine.v2.Value.isEqual(current.value, next.value)) {
                        merged.set(i, new ExactCase(current.value, guards.or(current.guard, next.guard)));
                        return;
                    }
                }
                merged.add(next);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (!(o instanceof Fact)) {
                    return false;
                }
                Fact other = (Fact) o;
                return (definedUnder == other.definedUnder || definedUnder.equals(other.definedUnder))
                       && value.equals(other.value)
                       && (exactCases == other.exactCases || exactCases.equals(other.exactCases));
            }

            @Override
            public int hashCode() {
                int result = definedUnder.hashCode();
                result = 31 * result + value.hashCode();
                return 31 * result + exactCases.hashCode();
            }

            static final class ExactCase {
                private final io.helidon.build.archetype.engine.v2.Value<?> value;
                private final Guard guard;

                ExactCase(io.helidon.build.archetype.engine.v2.Value<?> value, Guard guard) {
                    this.value = requireNonNull(value, "value is null");
                    this.guard = requireNonNull(guard, "guard is null");
                }

                io.helidon.build.archetype.engine.v2.Value<?> value() {
                    return value;
                }

                Guard guard() {
                    return guard;
                }

                boolean containsAll(Set<String> required) {
                    if (value.type() != io.helidon.build.archetype.engine.v2.Value.Type.LIST) {
                        return false;
                    }
                    return new HashSet<>(value.getList()).containsAll(required);
                }

                String scalarLiteral() {
                    return io.helidon.build.archetype.engine.v2.Value.scalarLiteral(value);
                }

                @Override
                public boolean equals(Object o) {
                    if (this == o) {
                        return true;
                    }
                    if (!(o instanceof ExactCase)) {
                        return false;
                    }
                    ExactCase other = (ExactCase) o;
                    return io.helidon.build.archetype.engine.v2.Value.isEqual(value, other.value)
                           && (guard == other.guard || guard.equals(other.guard));
                }

                @Override
                public int hashCode() {
                    return 31 * io.helidon.build.archetype.engine.v2.Value.hash(value) + guard.hashCode();
                }
            }
        }

        static final class Table {
            private final List<Symbol> symbols;
            private final Map<String, Integer> idsByName;

            private Table(List<Symbol> symbols, Map<String, Integer> idsByName) {
                this.symbols = List.copyOf(symbols);
                this.idsByName = Map.copyOf(idsByName);
            }

            static Builder builder() {
                return new Builder();
            }

            Symbol symbol(int symbolId) {
                return symbols.get(symbolId);
            }

            Symbol symbol(String name) {
                Integer id = idsByName.get(name);
                if (id == null) {
                    throw new IllegalArgumentException("Unknown symbol: " + name);
                }
                return symbols.get(id);
            }

            Integer findId(String name) {
                return idsByName.get(name);
            }

            int size() {
                return symbols.size();
            }

            List<Symbol> symbols() {
                return symbols;
            }

            static final class Builder {
                private final Map<String, Symbol> symbolsByName = new LinkedHashMap<>();

                int define(String name, Spec domain, boolean guardable, boolean tainted) {
                    Symbol existing = symbolsByName.get(name);
                    if (existing != null) {
                        Symbol expected = new Symbol(existing.id(), name, domain, guardable, tainted);
                        if (!existing.equals(expected)) {
                            throw new IllegalArgumentException("Conflicting symbol definition: " + name);
                        }
                        return existing.id();
                    }
                    int id = symbolsByName.size();
                    Symbol symbol = new Symbol(id, name, domain, guardable, tainted);
                    symbolsByName.put(name, symbol);
                    return id;
                }

                Table build() {
                    List<Symbol> symbols = new ArrayList<>(symbolsByName.values());
                    Map<String, Integer> idsByName = new LinkedHashMap<>();
                    for (Symbol symbol : symbols) {
                        idsByName.put(symbol.name(), symbol.id());
                    }
                    return new Table(symbols, idsByName);
                }
            }
        }
    }

    interface Value {
        Kind kind();

        enum Kind {
            BOTTOM,
            TOP,
            BOOLEAN_SET,
            CHOICE_SET,
            MEMBERSHIP_SET,
            OPEN_TEXT,
            FINITE_TEXT,
            LIST_SUMMARY
        }

        static Value bottom() {
            return Bottom.INSTANCE;
        }

        static Value top() {
            return Top.INSTANCE;
        }

        static Value top(Spec spec) {
            switch (spec.kind()) {
                case BOOLEAN:
                    return new BooleanSet(Set.of(false, true));
                case CHOICE:
                    return new ChoiceSet(((Spec.Choice) spec).values());
                case MEMBERSHIP:
                    return new ListSummary(Set.of(), ((Spec.Membership) spec).items());
                case OPEN_TEXT:
                    return new OpenText(null);
                case FINITE_TEXT:
                    return new FiniteText(((Spec.FiniteText) spec).values());
                default:
                    return top();
            }
        }

        static Value booleanSet(boolean... values) {
            Set<java.lang.Boolean> actual = new TreeSet<>();
            for (boolean value : values) {
                actual.add(value);
            }
            return new BooleanSet(actual);
        }

        static Value choiceSet(Set<String> values) {
            return new ChoiceSet(values);
        }

        static Value membershipSet(Set<String> required, Set<String> possible) {
            return new MembershipSet(required, possible);
        }

        static Value openText(String sample) {
            return new OpenText(sample);
        }

        static Value finiteText(Set<String> values) {
            return new FiniteText(values);
        }

        static Value listSummary(Set<String> required, Set<String> possible) {
            return new ListSummary(required, possible);
        }

        static Value join(Value left, Value right) {
            requireNonNull(left, "left is null");
            requireNonNull(right, "right is null");
            if (left.equals(right)) {
                return left;
            }
            if (left.kind() == Kind.BOTTOM) {
                return right;
            }
            if (right.kind() == Kind.BOTTOM) {
                return left;
            }
            if (left.kind() == Kind.TOP || right.kind() == Kind.TOP) {
                return top();
            }
            if (left instanceof BooleanSet && right instanceof BooleanSet) {
                Set<java.lang.Boolean> values = new TreeSet<>(((BooleanSet) left).values());
                values.addAll(((BooleanSet) right).values());
                return new BooleanSet(values);
            }
            if (left instanceof ChoiceSet && right instanceof ChoiceSet) {
                Set<String> values = new TreeSet<>(((ChoiceSet) left).values());
                values.addAll(((ChoiceSet) right).values());
                return new ChoiceSet(values);
            }
            if (left instanceof FiniteText && right instanceof FiniteText) {
                Set<String> values = new TreeSet<>(((FiniteText) left).values());
                values.addAll(((FiniteText) right).values());
                return new FiniteText(values);
            }
            if (left instanceof MembershipSet && right instanceof MembershipSet) {
                Set<String> required = new TreeSet<>(((MembershipSet) left).required());
                required.retainAll(((MembershipSet) right).required());
                Set<String> possible = new TreeSet<>(((MembershipSet) left).possible());
                possible.addAll(((MembershipSet) right).possible());
                return new MembershipSet(required, possible);
            }
            if (left instanceof ListSummary && right instanceof ListSummary) {
                Set<String> required = new TreeSet<>(((ListSummary) left).required());
                required.retainAll(((ListSummary) right).required());
                Set<String> possible = new TreeSet<>(((ListSummary) left).possible());
                possible.addAll(((ListSummary) right).possible());
                return new ListSummary(required, possible);
            }
            if (left instanceof OpenText && right instanceof OpenText) {
                String leftSample = ((OpenText) left).sample();
                String rightSample = ((OpenText) right).sample();
                return new OpenText(Objects.equals(leftSample, rightSample) ? leftSample : null);
            }
            if (left instanceof MembershipSet && right instanceof ListSummary) {
                return join(new ListSummary(((MembershipSet) left).required(), ((MembershipSet) left).possible()), right);
            }
            if (left instanceof ListSummary && right instanceof MembershipSet) {
                return join(left, new ListSummary(((MembershipSet) right).required(), ((MembershipSet) right).possible()));
            }
            if (left.kind() == Kind.OPEN_TEXT || right.kind() == Kind.OPEN_TEXT) {
                return new OpenText(null);
            }
            return top();
        }

        final class Bottom implements Value {
            private static final Bottom INSTANCE = new Bottom();

            private Bottom() {
            }

            @Override
            public Kind kind() {
                return Kind.BOTTOM;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof Bottom;
            }

            @Override
            public int hashCode() {
                return kind().hashCode();
            }
        }

        final class Top implements Value {
            private static final Top INSTANCE = new Top();

            private Top() {
            }

            @Override
            public Kind kind() {
                return Kind.TOP;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof Top;
            }

            @Override
            public int hashCode() {
                return kind().hashCode();
            }
        }

        final class BooleanSet implements Value {
            private final Set<java.lang.Boolean> values;

            BooleanSet(Set<java.lang.Boolean> values) {
                if (values.isEmpty()) {
                    throw new IllegalArgumentException("Boolean set must not be empty");
                }
                this.values = Set.copyOf(new TreeSet<>(values));
            }

            @Override
            public Kind kind() {
                return Kind.BOOLEAN_SET;
            }

            Set<java.lang.Boolean> values() {
                return values;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof BooleanSet && values.equals(((BooleanSet) o).values);
            }

            @Override
            public int hashCode() {
                return Objects.hash(kind(), values);
            }
        }

        final class ChoiceSet implements Value {
            private final Set<String> values;

            ChoiceSet(Set<String> values) {
                if (values.isEmpty()) {
                    throw new IllegalArgumentException("Choice set must not be empty");
                }
                this.values = canonicalStrings(values);
            }

            @Override
            public Kind kind() {
                return Kind.CHOICE_SET;
            }

            Set<String> values() {
                return values;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof ChoiceSet && values.equals(((ChoiceSet) o).values);
            }

            @Override
            public int hashCode() {
                return Objects.hash(kind(), values);
            }
        }

        final class MembershipSet implements Value {
            private final Set<String> required;
            private final Set<String> possible;

            MembershipSet(Set<String> required, Set<String> possible) {
                if (!possible.containsAll(required)) {
                    throw new IllegalArgumentException("Required membership must be included in possible membership");
                }
                this.required = canonicalStrings(required);
                this.possible = canonicalStrings(possible);
            }

            @Override
            public Kind kind() {
                return Kind.MEMBERSHIP_SET;
            }

            Set<String> required() {
                return required;
            }

            Set<String> possible() {
                return possible;
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof MembershipSet)) {
                    return false;
                }
                MembershipSet other = (MembershipSet) o;
                return required.equals(other.required) && possible.equals(other.possible);
            }

            @Override
            public int hashCode() {
                return Objects.hash(kind(), required, possible);
            }
        }

        final class OpenText implements Value {
            private final String sample;

            OpenText(String sample) {
                this.sample = sample;
            }

            @Override
            public Kind kind() {
                return Kind.OPEN_TEXT;
            }

            String sample() {
                return sample;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof OpenText && Objects.equals(sample, ((OpenText) o).sample);
            }

            @Override
            public int hashCode() {
                return Objects.hash(kind(), sample);
            }
        }

        final class FiniteText implements Value {
            private final Set<String> values;

            FiniteText(Set<String> values) {
                if (values.isEmpty()) {
                    throw new IllegalArgumentException("Finite text values must not be empty");
                }
                this.values = canonicalStrings(values);
            }

            @Override
            public Kind kind() {
                return Kind.FINITE_TEXT;
            }

            Set<String> values() {
                return values;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof FiniteText && values.equals(((FiniteText) o).values);
            }

            @Override
            public int hashCode() {
                return Objects.hash(kind(), values);
            }
        }

        final class ListSummary implements Value {
            private final Set<String> required;
            private final Set<String> possible;

            ListSummary(Set<String> required, Set<String> possible) {
                if (!possible.containsAll(required)) {
                    throw new IllegalArgumentException("Required list members must be included in possible list members");
                }
                this.required = canonicalStrings(required);
                this.possible = canonicalStrings(possible);
            }

            @Override
            public Kind kind() {
                return Kind.LIST_SUMMARY;
            }

            Set<String> required() {
                return required;
            }

            Set<String> possible() {
                return possible;
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof ListSummary)) {
                    return false;
                }
                ListSummary other = (ListSummary) o;
                return required.equals(other.required) && possible.equals(other.possible);
            }

            @Override
            public int hashCode() {
                return Objects.hash(kind(), required, possible);
            }
        }
    }

    static final class Guard {
        private final int id;
        private final Expression residual;

        Guard(int id, Expression residual) {
            this.id = id;
            this.residual = requireNonNull(residual, "residual is null");
        }

        int id() {
            return id;
        }

        Expression residual() {
            return residual;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Guard)) {
                return false;
            }
            Guard other = (Guard) o;
            return id == other.id && (residual == other.residual || residual.equals(other.residual));
        }

        @Override
        public int hashCode() {
            return 31 * Integer.hashCode(id) + residual.hashCode();
        }
    }

    static final class Guards {
        private static final int FALSE_ID = 0;
        private static final int TRUE_ID = 1;
        private static final int COMPACT_MAX_VARIABLES = 4;
        private static final int COMPACT_MAX_TOKENS = 16;
        private static final int EXACT_IMPLIES_MAX_SHAPES = 8;

        private final Symbol.Table symbols;
        private final List<Decision> decisions = new ArrayList<>();
        private final Map<Decision, Integer> ids = new HashMap<>();
        private final Map<Guard, Map<Guard, Guard>> orCache = new HashMap<>();
        private final Map<Long, Boolean> subsetCache = new HashMap<>();
        private final Guard falseGuard;
        private final Guard trueGuard;

        Guards(Symbol.Table symbols) {
            this.symbols = requireNonNull(symbols, "symbols is null");
            register(Decision.FALSE);
            register(Decision.TRUE);
            this.falseGuard = new Guard(FALSE_ID, Expression.TRUE);
            this.trueGuard = new Guard(TRUE_ID, Expression.TRUE);
        }

        Guard trueGuard() {
            return trueGuard;
        }

        Guard falseGuard() {
            return falseGuard;
        }

        Guard and(Guard left, Guard right) {
            if (isFalse(left) || isFalse(right)) {
                return falseGuard();
            }
            Decision decision = decision(left).and(decision(right), symbols);
            Expression residual = left.residual().equals(right.residual())
                    ? left.residual()
                    : left.residual().and(right.residual());
            return guard(decision, residual);
        }

        Guard or(Guard left, Guard right) {
            if (left.equals(right)) {
                return left;
            }
            if (isFalse(left)) {
                return right;
            }
            if (isFalse(right)) {
                return left;
            }
            boolean cacheable = cacheable(left) && cacheable(right);
            if (cacheable) {
                Guard cached = cachedOr(left, right);
                if (cached != null) {
                    return cached;
                }
            }
            Guard result;
            Decision leftDecision = decision(left);
            Decision rightDecision = decision(right);
            if (left.residual().equals(right.residual())) {
                if (shapewiseSubsetOf(left.id(), right.id())) {
                    return right;
                }
                if (shapewiseSubsetOf(right.id(), left.id())) {
                    return left;
                }
                result = guard(leftDecision.orWithoutSubsetChecks(rightDecision, symbols), left.residual());
            } else if (leftDecision.equals(rightDecision)) {
                result = guard(leftDecision, left.residual().or(right.residual()), false);
            } else if (isPure(right) && shapewiseSubsetOf(left.id(), right.id())) {
                return right;
            } else if (isPure(left) && shapewiseSubsetOf(right.id(), left.id())) {
                return left;
            } else if (isPure(left) && isPure(right)) {
                result = guard(leftDecision.orWithoutSubsetChecks(rightDecision, symbols), Expression.TRUE);
            } else {
                result = residualGuard(rawExpression(left).or(rawExpression(right)), false);
            }
            if (cacheable && cacheable(result)) {
                rememberOr(left, right, result);
            }
            return result;
        }

        Guard not(Guard guard) {
            if (isFalse(guard)) {
                return trueGuard();
            }
            if (guard.equals(trueGuard())) {
                return falseGuard();
            }
            if (isPure(guard)) {
                return guard(Decision.TRUE.subtract(decision(guard), symbols), Expression.TRUE);
            }
            return residualGuard(negate(rawExpression(guard)));
        }

        Guard minus(Guard left, Guard right) {
            return and(left, not(right));
        }

        boolean implies(Guard left, Guard right) {
            if (shapewiseImplies(left, right)) {
                return true;
            }
            Decision leftDecision = decision(left);
            Decision rightDecision = decision(right);
            if (isPure(left) && isPure(right)) {
                return leftDecision.subtract(rightDecision, symbols).isFalse();
            }
            return equivalentExpressions(rawExpression(left), rawExpression(right));
        }

        boolean shapewiseImplies(Guard left, Guard right) {
            if (left.equals(right) || isFalse(left) || right.equals(trueGuard())) {
                return true;
            }
            return left.residual().equals(right.residual()) && shapewiseSubsetOf(left.id(), right.id())
                    || isPure(right) && shapewiseSubsetOf(left.id(), right.id());
        }

        boolean exactImplicationCheap(Guard left, Guard right) {
            return decision(left).shapeCountAtMost(EXACT_IMPLIES_MAX_SHAPES)
                    && decision(right).shapeCountAtMost(EXACT_IMPLIES_MAX_SHAPES);
        }

        boolean equivalent(Guard left, Guard right) {
            if (left.equals(right)) {
                return true;
            }
            if (isPure(left) && isPure(right)) {
                return decision(left).equals(decision(right));
            }
            return equivalentExpressions(rawExpression(left), rawExpression(right));
        }

        Guard eq(int symbolId, String value) {
            Symbol symbol = guardableScalar(symbolId);
            Set<String> allowed = new TreeSet<>();
            allowed.add(requireNonNull(value, "value is null"));
            allowed.retainAll(scalarValues(symbol.domain()));
            if (allowed.isEmpty()) {
                return falseGuard();
            }
            DecisionShape shape = DecisionShape.scalar(symbolId, new ScalarShape(symbol, allowed));
            return guard(Decision.of(List.of(shape), symbols), Expression.TRUE);
        }

        Guard contains(int symbolId, String value) {
            Symbol symbol = symbols.symbol(symbolId);
            if (!symbol.guardable() || symbol.tainted() || symbol.domain().kind() != Spec.Kind.MEMBERSHIP) {
                return falseGuard();
            }
            Spec.Membership spec = (Spec.Membership) symbol.domain();
            String item = requireNonNull(value, "value is null");
            if (!spec.items().contains(item)) {
                return falseGuard();
            }
            MembershipShape membership = MembershipShape.required(item);
            DecisionShape shape = DecisionShape.membership(symbolId, membership);
            return guard(Decision.of(List.of(shape), symbols), Expression.TRUE);
        }

        Guard defined(int symbolId) {
            Symbol symbol = symbols.symbol(symbolId);
            if (!symbol.guardable() || symbol.tainted()) {
                return falseGuard();
            }
            switch (symbol.domain().kind()) {
                case BOOLEAN:
                case CHOICE:
                case FINITE_TEXT:
                    return guard(Decision.of(List.of(DecisionShape.scalar(
                            symbolId,
                            new ScalarShape(symbol, scalarValues(symbol.domain())))), symbols), Expression.TRUE);
                case MEMBERSHIP:
                    return trueGuard();
                default:
                    return falseGuard();
            }
        }

        Expression toExpression(Guard guard, Scope scope) {
            requireNonNull(scope, "scope is null");
            Expression supported = decision(guard).toExpression(symbolId -> scope.key(symbols.symbol(symbolId).name()), symbols);
            Expression residual = guard.residual();
            if (supported == Expression.FALSE || residual == Expression.FALSE) {
                return Expression.FALSE;
            }
            if (supported == Expression.TRUE) {
                return residual;
            }
            if (residual == Expression.TRUE) {
                return supported;
            }
            return supported.and(residual);
        }

        private Symbol guardableScalar(int symbolId) {
            Symbol symbol = symbols.symbol(symbolId);
            if (!symbol.guardable() || symbol.tainted()) {
                throw new IllegalArgumentException("Symbol is not guardable: " + symbol.name());
            }
            switch (symbol.domain().kind()) {
                case BOOLEAN:
                case CHOICE:
                case FINITE_TEXT:
                    return symbol;
                default:
                    throw new IllegalArgumentException("Symbol is not scalar guardable: " + symbol.name());
            }
        }

        private Guard guard(Decision decision, Expression residual) {
            return guard(decision, residual, true);
        }

        private Guard guard(Decision decision, Expression residual, boolean foldConstants) {
            Expression normalizedResidual = foldConstants ? residual.foldConstants() : residual;
            if (decision.isFalse() || normalizedResidual == Expression.FALSE) {
                return falseGuard();
            }
            if (decision.equals(Decision.TRUE) && normalizedResidual == Expression.TRUE) {
                return trueGuard();
            }
            int id = register(decision);
            return new Guard(id, normalizedResidual);
        }

        Guard residualGuard(Expression expression) {
            return guard(Decision.TRUE, expression);
        }

        Guard residualGuard(Expression expression, boolean foldConstants) {
            return guard(Decision.TRUE, expression, foldConstants);
        }

        private Guard cachedOr(Guard left, Guard right) {
            Map<Guard, Guard> cached = orCache.get(left);
            if (cached != null) {
                Guard result = cached.get(right);
                if (result != null) {
                    return result;
                }
            }
            cached = orCache.get(right);
            return cached == null ? null : cached.get(left);
        }

        private void rememberOr(Guard left, Guard right, Guard result) {
            orCache.computeIfAbsent(left, k -> new HashMap<>()).put(right, result);
            orCache.computeIfAbsent(right, k -> new HashMap<>()).put(left, result);
        }

        private int register(Decision decision) {
            Integer existing = ids.get(decision);
            if (existing != null) {
                return existing;
            }
            int id = decisions.size();
            decisions.add(decision);
            ids.put(decision, id);
            return id;
        }

        private Decision decision(Guard guard) {
            return decisions.get(guard.id());
        }

        private boolean shapewiseSubsetOf(int leftDecisionId, int rightDecisionId) {
            if (leftDecisionId == rightDecisionId || leftDecisionId == FALSE_ID || rightDecisionId == TRUE_ID) {
                return true;
            }
            if (rightDecisionId == FALSE_ID) {
                return false;
            }
            long key = ((long) leftDecisionId << 32) | Integer.toUnsignedLong(rightDecisionId);
            Boolean cached = subsetCache.get(key);
            if (cached != null) {
                return cached;
            }
            boolean result = decisions.get(leftDecisionId).shapewiseSubsetOf(decisions.get(rightDecisionId), symbols);
            subsetCache.put(key, result);
            return result;
        }

        private boolean isPure(Guard guard) {
            return guard.residual() == Expression.TRUE;
        }

        private boolean isFalse(Guard guard) {
            return guard.id() == FALSE_ID || guard.residual() == Expression.FALSE;
        }

        private Expression rawExpression(Guard guard) {
            Expression supported = decision(guard).toExpression(symbolId -> symbols.symbol(symbolId).name(), symbols);
            if (supported == Expression.FALSE || guard.residual() == Expression.FALSE) {
                return Expression.FALSE;
            }
            if (supported == Expression.TRUE) {
                return guard.residual();
            }
            if (guard.residual() == Expression.TRUE) {
                return supported;
            }
            return supported.and(guard.residual());
        }

        private static Expression negate(Expression expression) {
            if (expression == Expression.TRUE) {
                return Expression.FALSE;
            }
            if (expression == Expression.FALSE) {
                return Expression.TRUE;
            }
            return Expression.create("!(" + expression.literal() + ")");
        }

        private static boolean equivalentExpressions(Expression left, Expression right) {
            return left.equals(right) || left.reduce().equals(right.reduce());
        }

        private static Expression compact(Expression expression) {
            if (expression == Expression.TRUE || expression == Expression.FALSE) {
                return expression;
            }
            if (expression.tokens().size() > COMPACT_MAX_TOKENS
                    || !expression.variableCountAtMost(COMPACT_MAX_VARIABLES)) {
                return expression.foldConstants();
            }
            return expression.reduce();
        }

        private static boolean cacheable(Guard guard) {
            return guard.residual() == Expression.TRUE
                    || guard.residual() == Expression.FALSE
                    || guard.residual().tokens().size() <= COMPACT_MAX_TOKENS
                    && guard.residual().variableCountAtMost(COMPACT_MAX_VARIABLES);
        }
    }

    private static Set<String> scalarValues(Spec spec) {
        switch (spec.kind()) {
            case BOOLEAN:
                return Set.of("false", "true");
            case CHOICE:
                return ((Spec.Choice) spec).values();
            case FINITE_TEXT:
                return ((Spec.FiniteText) spec).values();
            default:
                throw new IllegalArgumentException("Spec is not scalar: " + spec.kind());
        }
    }

    private static Set<String> canonicalStrings(Set<String> values) {
        return Set.copyOf(new TreeSet<>(values));
    }

    private static final class Decision {
        private static final Decision FALSE = new Decision(List.of());
        private static final Decision TRUE = new Decision(List.of(new DecisionShape()));
        private static final int TRY_MERGE_MAX_SHAPES = 32;

        private final List<DecisionShape> shapes;

        private Decision(List<DecisionShape> shapes) {
            this.shapes = List.copyOf(shapes);
        }

        static Decision of(List<DecisionShape> shapes, Symbol.Table symbols) {
            if (shapes.isEmpty()) {
                return FALSE;
            }
            Set<DecisionShape> unique = new LinkedHashSet<>();
            for (DecisionShape shape : shapes) {
                DecisionShape next = shape.normalized(symbols);
                if (next.isTrue()) {
                    return TRUE;
                }
                unique.add(next);
            }
            List<DecisionShape> normalized = new ArrayList<>(unique);
            boolean changed;
            do {
                changed = false;
                for (int i = 0; i < normalized.size() && !changed; i++) {
                    for (int j = i + 1; j < normalized.size(); j++) {
                        DecisionShape left = normalized.get(i);
                        DecisionShape right = normalized.get(j);
                        if (left.subsetOf(right, symbols)) {
                            normalized.remove(i);
                            changed = true;
                            break;
                        }
                        if (right.subsetOf(left, symbols)) {
                            normalized.remove(j);
                            changed = true;
                            break;
                        }
                        if (normalized.size() <= TRY_MERGE_MAX_SHAPES) {
                            DecisionShape merged = left.tryMerge(right, symbols);
                            if (merged != null) {
                                normalized.set(i, merged);
                                normalized.remove(j);
                                changed = true;
                                break;
                            }
                        }
                    }
                }
            } while (changed);
            if (normalized.isEmpty()) {
                return FALSE;
            }
            normalized.sort(Comparator.comparing(DecisionShape::literal));
            return new Decision(normalized);
        }

        boolean isFalse() {
            return shapes.isEmpty();
        }

        Decision and(Decision other, Symbol.Table symbols) {
            if (isFalse() || other.isFalse()) {
                return FALSE;
            }
            if (equals(other)) {
                return this;
            }
            if (equals(TRUE)) {
                return other;
            }
            if (other.equals(TRUE)) {
                return this;
            }
            List<DecisionShape> result = new ArrayList<>();
            for (DecisionShape left : shapes) {
                for (DecisionShape right : other.shapes) {
                    DecisionShape intersection = left.intersect(right, symbols);
                    if (intersection != null) {
                        result.add(intersection);
                    }
                }
            }
            return of(result, symbols);
        }

        Decision or(Decision other, Symbol.Table symbols) {
            if (isFalse()) {
                return other;
            }
            if (other.isFalse()) {
                return this;
            }
            if (equals(other)) {
                return this;
            }
            if (shapewiseSubsetOf(other, symbols)) {
                return other;
            }
            if (other.shapewiseSubsetOf(this, symbols)) {
                return this;
            }
            return orWithoutSubsetChecks(other, symbols);
        }

        Decision orWithoutSubsetChecks(Decision other, Symbol.Table symbols) {
            List<DecisionShape> result = new ArrayList<>(shapes);
            boolean changed = false;
            for (DecisionShape shape : other.shapes) {
                DecisionShape candidate = shape;
                boolean restart;
                do {
                    restart = false;
                    for (int i = 0; i < result.size(); i++) {
                        DecisionShape existing = result.get(i);
                        if (candidate.subsetOf(existing, symbols)) {
                            candidate = null;
                            break;
                        }
                        if (existing.subsetOf(candidate, symbols)) {
                            result.remove(i);
                            changed = true;
                            restart = true;
                            break;
                        }
                        if (result.size() <= TRY_MERGE_MAX_SHAPES) {
                            DecisionShape merged = existing.tryMerge(candidate, symbols);
                            if (merged != null) {
                                if (merged.isTrue()) {
                                    return TRUE;
                                }
                                result.remove(i);
                                candidate = merged;
                                changed = true;
                                restart = true;
                                break;
                            }
                        }
                    }
                } while (restart);
                if (candidate != null) {
                    if (candidate.isTrue()) {
                        return TRUE;
                    }
                    result.add(candidate);
                    changed = true;
                }
            }
            if (!changed) {
                return this;
            }
            result.sort(Comparator.comparing(DecisionShape::literal));
            return new Decision(result);
        }

        Decision subtract(Decision other, Symbol.Table symbols) {
            if (isFalse() || other.isFalse()) {
                return this;
            }
            if (other.equals(TRUE)) {
                return FALSE;
            }
            List<DecisionShape> remaining = new ArrayList<>(shapes);
            for (DecisionShape right : other.shapes) {
                List<DecisionShape> next = new ArrayList<>();
                for (DecisionShape left : remaining) {
                    next.addAll(left.subtract(right, symbols));
                }
                remaining = next;
                if (remaining.isEmpty()) {
                    return FALSE;
                }
            }
            return of(remaining, symbols);
        }

        Expression toExpression(IntFunction<String> keyResolver, Symbol.Table symbols) {
            if (isFalse()) {
                return Expression.FALSE;
            }
            Expression expression = Expression.FALSE;
            for (DecisionShape shape : shapes) {
                expression = expression.or(shape.toExpression(keyResolver, symbols));
            }
            return expression;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Decision && shapes.equals(((Decision) o).shapes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(shapes);
        }

        boolean shapewiseSubsetOf(Decision other, Symbol.Table symbols) {
            if (equals(other) || isFalse() || other.equals(TRUE)) {
                return true;
            }
            if (other.isFalse()) {
                return false;
            }
            for (DecisionShape left : shapes) {
                boolean covered = false;
                for (DecisionShape right : other.shapes) {
                    if (left.subsetOf(right, symbols)) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    return false;
                }
            }
            return true;
        }

        boolean shapeCountAtMost(int maxShapes) {
            return shapes.size() <= maxShapes;
        }
    }

    private static final class DecisionShape {
        private static final int[] NO_SYMBOL_IDS = new int[0];
        private static final ScalarShape[] NO_SCALARS = new ScalarShape[0];
        private static final MembershipShape[] NO_MEMBERSHIPS = new MembershipShape[0];

        private final int[] scalarSymbolIds;
        private final ScalarShape[] scalarShapes;
        private final int[] membershipSymbolIds;
        private final MembershipShape[] membershipShapes;
        private final int membershipsHash;
        private final int hashCode;

        private DecisionShape() {
            this(NO_SYMBOL_IDS, NO_SCALARS, NO_SYMBOL_IDS, NO_MEMBERSHIPS);
        }

        private DecisionShape(int symbolId, ScalarShape scalarShape) {
            this(new int[] {symbolId}, new ScalarShape[] {scalarShape}, NO_SYMBOL_IDS, NO_MEMBERSHIPS);
        }

        private DecisionShape(int[] scalarSymbolIds,
                              ScalarShape[] scalarShapes,
                              int[] membershipSymbolIds,
                              MembershipShape[] membershipShapes) {
            this.scalarSymbolIds = scalarSymbolIds;
            this.scalarShapes = scalarShapes;
            this.membershipSymbolIds = membershipSymbolIds;
            this.membershipShapes = membershipShapes;
            this.membershipsHash = entriesHash(membershipSymbolIds, membershipShapes);
            this.hashCode = 31 * entriesHash(scalarSymbolIds, scalarShapes) + membershipsHash;
        }

        static DecisionShape scalar(int symbolId, ScalarShape scalarShape) {
            return new DecisionShape(symbolId, scalarShape);
        }

        static DecisionShape membership(int symbolId, MembershipShape membershipShape) {
            return new DecisionShape(
                    NO_SYMBOL_IDS,
                    NO_SCALARS,
                    new int[] {symbolId},
                    new MembershipShape[] {membershipShape});
        }

        boolean isTrue() {
            return scalarShapes.length == 0 && membershipShapes.length == 0;
        }

        DecisionShape normalized(Symbol.Table symbols) {
            int nextScalarCount = scalarShapes.length;
            int nextMembershipCount = membershipShapes.length;
            boolean changed = false;
            for (int i = 0; i < scalarShapes.length; i++) {
                Set<String> domainValues = scalarValues(symbols.symbol(scalarSymbolIds[i]).domain());
                if (scalarShapes[i].allowed.equals(domainValues)) {
                    changed = true;
                    nextScalarCount--;
                }
            }
            for (MembershipShape membershipShape : membershipShapes) {
                if (membershipShape.isEmpty()) {
                    changed = true;
                    nextMembershipCount--;
                }
            }
            if (!changed) {
                return this;
            }
            if (nextScalarCount == 0 && nextMembershipCount == 0) {
                return new DecisionShape();
            }
            int[] nextScalarIds = nextScalarCount == 0 ? NO_SYMBOL_IDS : new int[nextScalarCount];
            ScalarShape[] nextScalars = nextScalarCount == 0 ? NO_SCALARS : new ScalarShape[nextScalarCount];
            int scalarIndex = 0;
            for (int i = 0; i < scalarShapes.length; i++) {
                Set<String> domainValues = scalarValues(symbols.symbol(scalarSymbolIds[i]).domain());
                if (!scalarShapes[i].allowed.equals(domainValues)) {
                    nextScalarIds[scalarIndex] = scalarSymbolIds[i];
                    nextScalars[scalarIndex] = scalarShapes[i];
                    scalarIndex++;
                }
            }
            int[] nextMembershipIds = nextMembershipCount == 0 ? NO_SYMBOL_IDS : new int[nextMembershipCount];
            MembershipShape[] nextMemberships = nextMembershipCount == 0
                    ? NO_MEMBERSHIPS
                    : new MembershipShape[nextMembershipCount];
            int membershipIndex = 0;
            for (int i = 0; i < membershipShapes.length; i++) {
                if (!membershipShapes[i].isEmpty()) {
                    nextMembershipIds[membershipIndex] = membershipSymbolIds[i];
                    nextMemberships[membershipIndex] = membershipShapes[i];
                    membershipIndex++;
                }
            }
            return new DecisionShape(nextScalarIds, nextScalars, nextMembershipIds, nextMemberships);
        }

        DecisionShape intersect(DecisionShape other, Symbol.Table symbols) {
            if (this == other) {
                return this;
            }
            if (isTrue()) {
                return other;
            }
            if (other.isTrue()) {
                return this;
            }
            ScalarBuilder nextScalars = new ScalarBuilder(scalarShapes.length + other.scalarShapes.length);
            int leftScalar = 0;
            int rightScalar = 0;
            while (leftScalar < scalarShapes.length || rightScalar < other.scalarShapes.length) {
                if (rightScalar == other.scalarShapes.length
                        || leftScalar < scalarShapes.length
                        && scalarSymbolIds[leftScalar] < other.scalarSymbolIds[rightScalar]) {
                    nextScalars.add(scalarSymbolIds[leftScalar], scalarShapes[leftScalar]);
                    leftScalar++;
                } else if (leftScalar == scalarShapes.length
                        || other.scalarSymbolIds[rightScalar] < scalarSymbolIds[leftScalar]) {
                    nextScalars.add(other.scalarSymbolIds[rightScalar], other.scalarShapes[rightScalar]);
                    rightScalar++;
                } else {
                    int symbolId = scalarSymbolIds[leftScalar];
                    ScalarShape intersection = scalarShapes[leftScalar].intersect(other.scalarShapes[rightScalar],
                            symbols.symbol(symbolId));
                    if (intersection == null) {
                        return null;
                    }
                    nextScalars.add(symbolId, intersection);
                    leftScalar++;
                    rightScalar++;
                }
            }
            MembershipBuilder nextMemberships = new MembershipBuilder(membershipShapes.length + other.membershipShapes.length);
            int leftMembership = 0;
            int rightMembership = 0;
            while (leftMembership < membershipShapes.length || rightMembership < other.membershipShapes.length) {
                if (rightMembership == other.membershipShapes.length
                        || leftMembership < membershipShapes.length
                        && membershipSymbolIds[leftMembership] < other.membershipSymbolIds[rightMembership]) {
                    nextMemberships.add(membershipSymbolIds[leftMembership], membershipShapes[leftMembership]);
                    leftMembership++;
                } else if (leftMembership == membershipShapes.length
                        || other.membershipSymbolIds[rightMembership] < membershipSymbolIds[leftMembership]) {
                    nextMemberships.add(other.membershipSymbolIds[rightMembership], other.membershipShapes[rightMembership]);
                    rightMembership++;
                } else {
                    int symbolId = membershipSymbolIds[leftMembership];
                    MembershipShape intersection = membershipShapes[leftMembership]
                            .intersect(other.membershipShapes[rightMembership]);
                    if (intersection == null) {
                        return null;
                    }
                    nextMemberships.add(symbolId, intersection);
                    leftMembership++;
                    rightMembership++;
                }
            }
            return new DecisionShape(nextScalars.symbolIds(), nextScalars.shapes(),
                    nextMemberships.symbolIds(), nextMemberships.shapes());
        }

        boolean subsetOf(DecisionShape other, Symbol.Table symbols) {
            if (this == other) {
                return true;
            }
            if (scalarShapes.length < other.scalarShapes.length || membershipShapes.length < other.membershipShapes.length) {
                return false;
            }
            int leftScalar = 0;
            for (int rightScalar = 0; rightScalar < other.scalarShapes.length; rightScalar++) {
                int symbolId = other.scalarSymbolIds[rightScalar];
                while (leftScalar < scalarShapes.length && scalarSymbolIds[leftScalar] < symbolId) {
                    leftScalar++;
                }
                if (leftScalar == scalarShapes.length
                        || scalarSymbolIds[leftScalar] != symbolId
                        || !scalarShapes[leftScalar].subsetOf(other.scalarShapes[rightScalar])) {
                    return false;
                }
            }
            int leftMembership = 0;
            for (int rightMembership = 0; rightMembership < other.membershipShapes.length; rightMembership++) {
                int symbolId = other.membershipSymbolIds[rightMembership];
                while (leftMembership < membershipShapes.length && membershipSymbolIds[leftMembership] < symbolId) {
                    leftMembership++;
                }
                if (leftMembership == membershipShapes.length || membershipSymbolIds[leftMembership] != symbolId) {
                    return false;
                }
                MembershipShape left = membershipShapes[leftMembership];
                MembershipShape right = other.membershipShapes[rightMembership];
                if (!left.required.containsAll(right.required) || !left.forbidden.containsAll(right.forbidden)) {
                    return false;
                }
            }
            return true;
        }

        DecisionShape tryMerge(DecisionShape other, Symbol.Table symbols) {
            if (!membershipsEqual(other)) {
                return null;
            }
            if (Math.abs(scalarShapes.length - other.scalarShapes.length) > 1) {
                return null;
            }
            Integer diffSymbolId = null;
            ScalarBuilder nextScalars = new ScalarBuilder(scalarShapes.length + other.scalarShapes.length);
            int leftScalar = 0;
            int rightScalar = 0;
            while (leftScalar < scalarShapes.length || rightScalar < other.scalarShapes.length) {
                int symbolId;
                ScalarShape leftShape;
                ScalarShape rightShape;
                if (rightScalar == other.scalarShapes.length
                        || leftScalar < scalarShapes.length
                        && scalarSymbolIds[leftScalar] < other.scalarSymbolIds[rightScalar]) {
                    symbolId = scalarSymbolIds[leftScalar];
                    leftShape = scalarShapes[leftScalar];
                    rightShape = null;
                    leftScalar++;
                } else if (leftScalar == scalarShapes.length
                        || other.scalarSymbolIds[rightScalar] < scalarSymbolIds[leftScalar]) {
                    symbolId = other.scalarSymbolIds[rightScalar];
                    leftShape = null;
                    rightShape = other.scalarShapes[rightScalar];
                    rightScalar++;
                } else {
                    symbolId = scalarSymbolIds[leftScalar];
                    leftShape = scalarShapes[leftScalar];
                    rightShape = other.scalarShapes[rightScalar];
                    leftScalar++;
                    rightScalar++;
                }
                Symbol symbol = symbols.symbol(symbolId);
                Set<String> domainValues = scalarValues(symbol.domain());
                Set<String> leftAllowed = leftShape == null ? domainValues : leftShape.allowed;
                Set<String> rightAllowed = rightShape == null ? domainValues : rightShape.allowed;
                if (!leftAllowed.equals(rightAllowed)) {
                    if (diffSymbolId != null) {
                        return null;
                    }
                    diffSymbolId = symbolId;
                    Set<String> union = new TreeSet<>(leftAllowed);
                    union.addAll(rightAllowed);
                    if (!union.equals(domainValues)) {
                        nextScalars.add(symbolId, new ScalarShape(symbol, union));
                    }
                } else if (!leftAllowed.equals(domainValues)) {
                    nextScalars.add(symbolId, leftShape == null ? rightShape : leftShape);
                }
            }
            return diffSymbolId == null ? null : new DecisionShape(
                    nextScalars.symbolIds(),
                    nextScalars.shapes(),
                    membershipSymbolIds,
                    membershipShapes);
        }

        List<DecisionShape> subtract(DecisionShape other, Symbol.Table symbols) {
            DecisionShape intersection = intersect(other, symbols);
            if (intersection == null) {
                return List.of(this);
            }
            if (subsetOf(other, symbols)) {
                return List.of();
            }
            DecisionPartition partition = splitAgainst(other, symbols);
            if (partition == null) {
                return List.of(this);
            }
            List<DecisionShape> result = new ArrayList<>();
            if (partition.outside != null) {
                result.add(partition.outside);
            }
            if (partition.overlap == null) {
                return result;
            }
            result.addAll(partition.overlap.subtract(other, symbols));
            return result;
        }

        Expression toExpression(IntFunction<String> keyResolver, Symbol.Table symbols) {
            Expression expression = Expression.TRUE;
            for (int i = 0; i < scalarShapes.length; i++) {
                Symbol symbol = symbols.symbol(scalarSymbolIds[i]);
                expression = expression.and(scalarShapes[i].toExpression(keyResolver.apply(scalarSymbolIds[i]), symbol.domain()));
            }
            for (int i = 0; i < membershipShapes.length; i++) {
                expression = expression.and(membershipShapes[i].toExpression(keyResolver.apply(membershipSymbolIds[i])));
            }
            return expression;
        }

        String literal() {
            StringBuilder builder = new StringBuilder();
            builder.append('{');
            for (int i = 0; i < scalarShapes.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(scalarSymbolIds[i]).append(':').append(scalarShapes[i].allowed);
            }
            builder.append("}|{");
            for (int i = 0; i < membershipShapes.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(membershipSymbolIds[i])
                        .append(':')
                        .append(membershipShapes[i].required)
                        .append('/')
                        .append(membershipShapes[i].forbidden);
            }
            builder.append('}');
            return builder.toString();
        }

        private DecisionPartition splitAgainst(DecisionShape other, Symbol.Table symbols) {
            int leftScalar = 0;
            for (int rightScalar = 0; rightScalar < other.scalarShapes.length; rightScalar++) {
                int symbolId = other.scalarSymbolIds[rightScalar];
                while (leftScalar < scalarShapes.length && scalarSymbolIds[leftScalar] < symbolId) {
                    leftScalar++;
                }
                Set<String> leftAllowed = leftScalar < scalarShapes.length && scalarSymbolIds[leftScalar] == symbolId
                        ? scalarShapes[leftScalar].allowed
                        : scalarValues(symbols.symbol(symbolId).domain());
                Set<String> rightAllowed = other.scalarShapes[rightScalar].allowed;
                if (!rightAllowed.containsAll(leftAllowed)) {
                    Set<String> outside = new TreeSet<>(leftAllowed);
                    outside.removeAll(rightAllowed);
                    Set<String> overlap = new TreeSet<>(leftAllowed);
                    overlap.retainAll(rightAllowed);
                    return new DecisionPartition(withScalar(symbolId, outside, symbols), withScalar(symbolId, overlap, symbols));
                }
            }
            int leftMembership = 0;
            for (int rightMembership = 0; rightMembership < other.membershipShapes.length; rightMembership++) {
                int symbolId = other.membershipSymbolIds[rightMembership];
                while (leftMembership < membershipShapes.length && membershipSymbolIds[leftMembership] < symbolId) {
                    leftMembership++;
                }
                MembershipShape left = leftMembership < membershipShapes.length && membershipSymbolIds[leftMembership] == symbolId
                        ? membershipShapes[leftMembership]
                        : MembershipShape.EMPTY;
                MembershipShape right = other.membershipShapes[rightMembership];
                for (String required : right.required) {
                    if (!left.required.contains(required) && !left.forbidden.contains(required)) {
                        return new DecisionPartition(
                                withMembershipForbidden(symbolId, required),
                                withMembershipRequired(symbolId, required));
                    }
                }
                for (String forbidden : right.forbidden) {
                    if (!left.required.contains(forbidden) && !left.forbidden.contains(forbidden)) {
                        return new DecisionPartition(
                                withMembershipRequired(symbolId, forbidden),
                                withMembershipForbidden(symbolId, forbidden));
                    }
                }
            }
            return null;
        }

        private DecisionShape withScalar(int symbolId, Set<String> allowed, Symbol.Table symbols) {
            Symbol symbol = symbols.symbol(symbolId);
            Set<String> domainValues = scalarValues(symbol.domain());
            if (allowed.equals(domainValues)) {
                int index = Arrays.binarySearch(scalarSymbolIds, symbolId);
                if (index < 0) {
                    return this;
                }
                if (scalarShapes.length == 1) {
                    return new DecisionShape(NO_SYMBOL_IDS, NO_SCALARS, membershipSymbolIds, membershipShapes);
                }
                return new DecisionShape(
                        removeIndex(scalarSymbolIds, index),
                        removeIndex(scalarShapes, index),
                        membershipSymbolIds,
                        membershipShapes);
            }
            ScalarShape nextShape = new ScalarShape(symbol, allowed);
            int index = Arrays.binarySearch(scalarSymbolIds, symbolId);
            if (index >= 0) {
                if (scalarShapes[index].equals(nextShape)) {
                    return this;
                }
                ScalarShape[] nextScalars = Arrays.copyOf(scalarShapes, scalarShapes.length);
                nextScalars[index] = nextShape;
                return new DecisionShape(scalarSymbolIds, nextScalars, membershipSymbolIds, membershipShapes);
            }
            int insertion = -index - 1;
            int[] nextScalarIds = insert(scalarSymbolIds, insertion, symbolId);
            ScalarShape[] nextScalars = insert(scalarShapes, insertion, nextShape);
            return new DecisionShape(nextScalarIds, nextScalars, membershipSymbolIds, membershipShapes);
        }

        private DecisionShape withMembershipRequired(int symbolId, String item) {
            int index = Arrays.binarySearch(membershipSymbolIds, symbolId);
            MembershipShape current = index < 0 ? MembershipShape.EMPTY : membershipShapes[index];
            MembershipShape nextShape = current.withRequired(item);
            if (nextShape.equals(current)) {
                return this;
            }
            return withMembership(index, symbolId, nextShape);
        }

        private DecisionShape withMembershipForbidden(int symbolId, String item) {
            int index = Arrays.binarySearch(membershipSymbolIds, symbolId);
            MembershipShape current = index < 0 ? MembershipShape.EMPTY : membershipShapes[index];
            MembershipShape nextShape = current.withForbidden(item);
            if (nextShape.equals(current)) {
                return this;
            }
            return withMembership(index, symbolId, nextShape);
        }

        private DecisionShape withMembership(int index, int symbolId, MembershipShape nextShape) {
            if (index >= 0) {
                MembershipShape[] nextMemberships = Arrays.copyOf(membershipShapes, membershipShapes.length);
                nextMemberships[index] = nextShape;
                return new DecisionShape(scalarSymbolIds, scalarShapes, membershipSymbolIds, nextMemberships);
            }
            int insertion = -index - 1;
            return new DecisionShape(
                    scalarSymbolIds,
                    scalarShapes,
                    insert(membershipSymbolIds, insertion, symbolId),
                    insert(membershipShapes, insertion, nextShape));
        }

        private boolean membershipsEqual(DecisionShape other) {
            return membershipsHash == other.membershipsHash
                    && entriesEqual(membershipSymbolIds, membershipShapes, other.membershipSymbolIds, other.membershipShapes);
        }

        private static boolean entriesEqual(int[] leftIds, Object[] leftValues, int[] rightIds, Object[] rightValues) {
            return Arrays.equals(leftIds, rightIds) && Arrays.equals(leftValues, rightValues);
        }

        private static int entriesHash(int[] symbolIds, Object[] values) {
            int result = 1;
            for (int i = 0; i < symbolIds.length; i++) {
                result = 31 * result + Integer.hashCode(symbolIds[i]);
                result = 31 * result + values[i].hashCode();
            }
            return result;
        }

        private static int[] removeIndex(int[] values, int index) {
            int[] next = new int[values.length - 1];
            System.arraycopy(values, 0, next, 0, index);
            System.arraycopy(values, index + 1, next, index, values.length - index - 1);
            return next;
        }

        private static <T> T[] removeIndex(T[] values, int index) {
            T[] next = Arrays.copyOf(values, values.length - 1);
            System.arraycopy(values, index + 1, next, index, values.length - index - 1);
            return next;
        }

        private static int[] insert(int[] values, int insertion, int value) {
            int[] next = new int[values.length + 1];
            System.arraycopy(values, 0, next, 0, insertion);
            next[insertion] = value;
            System.arraycopy(values, insertion, next, insertion + 1, values.length - insertion);
            return next;
        }

        private static <T> T[] insert(T[] values, int insertion, T value) {
            T[] next = Arrays.copyOf(values, values.length + 1);
            System.arraycopy(next, insertion, next, insertion + 1, values.length - insertion);
            next[insertion] = value;
            return next;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DecisionShape)) {
                return false;
            }
            DecisionShape other = (DecisionShape) o;
            return hashCode == other.hashCode
                    && entriesEqual(scalarSymbolIds, scalarShapes, other.scalarSymbolIds, other.scalarShapes)
                    && entriesEqual(membershipSymbolIds, membershipShapes, other.membershipSymbolIds, other.membershipShapes);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private static final class ScalarBuilder {
            private final int[] symbolIds;
            private final ScalarShape[] shapes;
            private int size;

            private ScalarBuilder(int capacity) {
                this.symbolIds = capacity == 0 ? NO_SYMBOL_IDS : new int[capacity];
                this.shapes = capacity == 0 ? NO_SCALARS : new ScalarShape[capacity];
            }

            void add(int symbolId, ScalarShape shape) {
                symbolIds[size] = symbolId;
                shapes[size] = shape;
                size++;
            }

            int[] symbolIds() {
                return size == 0 ? NO_SYMBOL_IDS : size == symbolIds.length ? symbolIds : Arrays.copyOf(symbolIds, size);
            }

            ScalarShape[] shapes() {
                return size == 0 ? NO_SCALARS : size == shapes.length ? shapes : Arrays.copyOf(shapes, size);
            }
        }

        private static final class MembershipBuilder {
            private final int[] symbolIds;
            private final MembershipShape[] shapes;
            private int size;

            private MembershipBuilder(int capacity) {
                this.symbolIds = capacity == 0 ? NO_SYMBOL_IDS : new int[capacity];
                this.shapes = capacity == 0 ? NO_MEMBERSHIPS : new MembershipShape[capacity];
            }

            void add(int symbolId, MembershipShape shape) {
                symbolIds[size] = symbolId;
                shapes[size] = shape;
                size++;
            }

            int[] symbolIds() {
                return size == 0 ? NO_SYMBOL_IDS : size == symbolIds.length ? symbolIds : Arrays.copyOf(symbolIds, size);
            }

            MembershipShape[] shapes() {
                return size == 0 ? NO_MEMBERSHIPS : size == shapes.length ? shapes : Arrays.copyOf(shapes, size);
            }
        }
    }

    private static final class ScalarShape {
        private final Set<String> allowed;
        private final long allowedMask;
        private final boolean masked;

        private ScalarShape(Symbol symbol, Set<String> allowed) {
            if (allowed.isEmpty()) {
                throw new IllegalArgumentException("Scalar constraint must not be empty");
            }
            this.allowed = canonicalStrings(allowed);
            this.masked = symbol.maskableScalar();
            this.allowedMask = masked ? symbol.scalarMask(this.allowed) : 0L;
        }

        ScalarShape intersect(ScalarShape other, Symbol symbol) {
            if (allowed.equals(other.allowed)) {
                return this;
            }
            if (subsetOf(other)) {
                return other;
            }
            if (other.subsetOf(this)) {
                return this;
            }
            Set<String> intersection = new TreeSet<>(allowed);
            intersection.retainAll(other.allowed);
            return intersection.isEmpty() ? null : new ScalarShape(symbol, intersection);
        }

        boolean subsetOf(ScalarShape other) {
            if (this == other || masked && other.masked && allowedMask == other.allowedMask || allowed.equals(other.allowed)) {
                return true;
            }
            if (allowed.size() > other.allowed.size()) {
                return false;
            }
            if (masked && other.masked) {
                return (allowedMask & ~other.allowedMask) == 0L;
            }
            return other.allowed.containsAll(allowed);
        }

        Expression toExpression(String key, Spec spec) {
            if (spec.kind() == Spec.Kind.BOOLEAN) {
                if (allowed.equals(Set.of("true"))) {
                    return Expression.create("${" + key + "}");
                }
                if (allowed.equals(Set.of("false"))) {
                    return Expression.create("!${" + key + "}");
                }
            }
            Set<String> domainValues = scalarValues(spec);
            if (allowed.equals(domainValues)) {
                return Expression.TRUE;
            }
            Set<String> excluded = new TreeSet<>(domainValues);
            excluded.removeAll(allowed);
            if (excluded.size() == 1) {
                return Expression.create("${" + key + "} != '" + excluded.iterator().next() + "'");
            }
            Expression expression = Expression.FALSE;
            for (String value : allowed) {
                expression = expression.or(Expression.create("${" + key + "} == '" + value + "'"));
            }
            return expression;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ScalarShape && allowed.equals(((ScalarShape) o).allowed);
        }

        @Override
        public int hashCode() {
            return Objects.hash(allowed);
        }
    }

    private static final class MembershipShape {
        private static final MembershipShape EMPTY = new MembershipShape(Set.of(), Set.of());

        private final Set<String> required;
        private final Set<String> forbidden;

        private MembershipShape(Set<String> required, Set<String> forbidden) {
            Set<String> nextRequired = new TreeSet<>(required);
            Set<String> nextForbidden = new TreeSet<>(forbidden);
            if (Lists.intersects(nextRequired, nextForbidden)) {
                throw new IllegalArgumentException("Membership required and forbidden items must be disjoint");
            }
            this.required = Set.copyOf(nextRequired);
            this.forbidden = Set.copyOf(nextForbidden);
        }

        static MembershipShape required(String item) {
            return new MembershipShape(Set.of(item), Set.of());
        }

        boolean isEmpty() {
            return required.isEmpty() && forbidden.isEmpty();
        }

        MembershipShape intersect(MembershipShape other) {
            if (isEmpty()) {
                return other;
            }
            if (other.isEmpty()) {
                return this;
            }
            if (equals(other)) {
                return this;
            }
            if (Lists.intersects(required, other.forbidden) || Lists.intersects(forbidden, other.required)) {
                return null;
            }
            if (required.containsAll(other.required) && forbidden.containsAll(other.forbidden)) {
                return this;
            }
            if (other.required.containsAll(required) && other.forbidden.containsAll(forbidden)) {
                return other;
            }
            Set<String> nextRequired = new TreeSet<>(required);
            nextRequired.addAll(other.required);
            Set<String> nextForbidden = new TreeSet<>(forbidden);
            nextForbidden.addAll(other.forbidden);
            return new MembershipShape(nextRequired, nextForbidden);
        }

        MembershipShape withRequired(String item) {
            Set<String> nextRequired = new TreeSet<>(required);
            nextRequired.add(item);
            return new MembershipShape(nextRequired, forbidden);
        }

        MembershipShape withForbidden(String item) {
            Set<String> nextForbidden = new TreeSet<>(forbidden);
            nextForbidden.add(item);
            return new MembershipShape(required, nextForbidden);
        }

        Expression toExpression(String key) {
            Expression expression = Expression.TRUE;
            for (String item : required) {
                expression = expression.and(Expression.create("${" + key + "} contains '" + item + "'"));
            }
            for (String item : forbidden) {
                expression = expression.and(Expression.create("!(${"
                        + key
                        + "} contains '"
                        + item
                        + "')"));
            }
            return expression;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MembershipShape)) {
                return false;
            }
            MembershipShape other = (MembershipShape) o;
            return required.equals(other.required) && forbidden.equals(other.forbidden);
        }

        @Override
        public int hashCode() {
            return Objects.hash(required, forbidden);
        }
    }

    private static final class DecisionPartition {
        private final DecisionShape outside;
        private final DecisionShape overlap;

        private DecisionPartition(DecisionShape outside, DecisionShape overlap) {
            this.outside = outside;
            this.overlap = overlap;
        }
    }
}
