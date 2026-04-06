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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntFunction;

import io.helidon.build.archetype.engine.v2.Context.Scope;

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

        Symbol(int id, String name, Spec domain, boolean guardable, boolean tainted) {
            this.id = id;
            this.name = requireNonNull(name, "name is null");
            this.domain = requireNonNull(domain, "domain is null");
            this.guardable = guardable;
            this.tainted = tainted;
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

            Fact(Guard definedUnder, Value value) {
                this.definedUnder = requireNonNull(definedUnder, "definedUnder is null");
                this.value = requireNonNull(value, "value is null");
            }

            Guard definedUnder() {
                return definedUnder;
            }

            Value value() {
                return value;
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof Fact)) {
                    return false;
                }
                Fact other = (Fact) o;
                return definedUnder.equals(other.definedUnder) && value.equals(other.value);
            }

            @Override
            public int hashCode() {
                return Objects.hash(definedUnder, value);
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
            if (!(o instanceof Guard)) {
                return false;
            }
            Guard other = (Guard) o;
            return id == other.id && residual.equals(other.residual);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, residual);
        }
    }

    static final class Guards {
        private static final int FALSE_ID = 0;
        private static final int TRUE_ID = 1;

        private final Symbol.Table symbols;
        private final List<Decision> decisions = new ArrayList<>();
        private final Map<Decision, Integer> ids = new HashMap<>();
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
            Expression residual = left.residual().and(right.residual()).reduce();
            return guard(decision, residual);
        }

        Guard or(Guard left, Guard right) {
            if (isFalse(left)) {
                return right;
            }
            if (isFalse(right)) {
                return left;
            }
            if (isPure(left) && isPure(right)) {
                return guard(decision(left).or(decision(right), symbols), Expression.TRUE);
            }
            return residualGuard(rawExpression(left).or(rawExpression(right)).reduce());
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
            if (left.equals(right) || isFalse(left) || right.equals(trueGuard())) {
                return true;
            }
            if (isPure(left) && isPure(right)) {
                return decision(left).subtract(decision(right), symbols).isFalse();
            }
            return rawExpression(left).equals(rawExpression(right));
        }

        boolean equivalent(Guard left, Guard right) {
            if (left.equals(right)) {
                return true;
            }
            if (isPure(left) && isPure(right)) {
                return decision(left).equals(decision(right));
            }
            return rawExpression(left).equals(rawExpression(right));
        }

        Guard eq(int symbolId, String value) {
            Symbol symbol = guardableScalar(symbolId);
            Set<String> allowed = new TreeSet<>();
            allowed.add(requireNonNull(value, "value is null"));
            allowed.retainAll(scalarValues(symbol.domain()));
            if (allowed.isEmpty()) {
                return falseGuard();
            }
            ConstraintSet set = new ConstraintSet(Map.of(symbolId, new ScalarConstraint(allowed)), Map.of());
            return guard(Decision.of(List.of(set), symbols), Expression.TRUE);
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
            MembershipConstraint membership = MembershipConstraint.required(item);
            ConstraintSet set = new ConstraintSet(Map.of(), Map.of(symbolId, membership));
            return guard(Decision.of(List.of(set), symbols), Expression.TRUE);
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
                    return guard(Decision.of(List.of(new ConstraintSet(
                            Map.of(symbolId, new ScalarConstraint(scalarValues(symbol.domain()))),
                            Map.of())), symbols), Expression.TRUE);
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
            return supported.and(residual).reduce();
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
            Expression normalizedResidual = residual.reduce();
            if (decision.isFalse() || normalizedResidual == Expression.FALSE) {
                return falseGuard();
            }
            if (decision.equals(Decision.TRUE) && normalizedResidual == Expression.TRUE) {
                return trueGuard();
            }
            int id = register(decision);
            return new Guard(id, normalizedResidual);
        }

        private Guard residualGuard(Expression expression) {
            return guard(Decision.TRUE, expression.reduce());
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
            return supported.and(guard.residual()).reduce();
        }

        private static Expression negate(Expression expression) {
            if (expression == Expression.TRUE) {
                return Expression.FALSE;
            }
            if (expression == Expression.FALSE) {
                return Expression.TRUE;
            }
            return Expression.create("!(" + expression.literal() + ")").reduce();
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
        private static final Decision TRUE = new Decision(List.of(new ConstraintSet()));

        private final List<ConstraintSet> sets;

        private Decision(List<ConstraintSet> sets) {
            this.sets = List.copyOf(sets);
        }

        static Decision of(List<ConstraintSet> sets, Symbol.Table symbols) {
            if (sets.isEmpty()) {
                return FALSE;
            }
            List<ConstraintSet> normalized = new ArrayList<>();
            for (ConstraintSet set : sets) {
                ConstraintSet next = set.normalized(symbols);
                if (next.isTrue()) {
                    return TRUE;
                }
                if (!normalized.contains(next)) {
                    normalized.add(next);
                }
            }
            boolean changed;
            do {
                changed = false;
                for (int i = 0; i < normalized.size() && !changed; i++) {
                    for (int j = i + 1; j < normalized.size(); j++) {
                        ConstraintSet left = normalized.get(i);
                        ConstraintSet right = normalized.get(j);
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
                        ConstraintSet merged = left.tryMerge(right, symbols);
                        if (merged != null) {
                            normalized.set(i, merged);
                            normalized.remove(j);
                            changed = true;
                            break;
                        }
                    }
                }
            } while (changed);
            if (normalized.isEmpty()) {
                return FALSE;
            }
            normalized.sort(Comparator.comparing(ConstraintSet::literal));
            return new Decision(normalized);
        }

        boolean isFalse() {
            return sets.isEmpty();
        }

        Decision and(Decision other, Symbol.Table symbols) {
            if (isFalse() || other.isFalse()) {
                return FALSE;
            }
            if (equals(TRUE)) {
                return other;
            }
            if (other.equals(TRUE)) {
                return this;
            }
            List<ConstraintSet> result = new ArrayList<>();
            for (ConstraintSet left : sets) {
                for (ConstraintSet right : other.sets) {
                    ConstraintSet intersection = left.intersect(right, symbols);
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
            List<ConstraintSet> result = new ArrayList<>(sets);
            result.addAll(other.sets);
            return of(result, symbols);
        }

        Decision subtract(Decision other, Symbol.Table symbols) {
            if (isFalse() || other.isFalse()) {
                return this;
            }
            if (other.equals(TRUE)) {
                return FALSE;
            }
            List<ConstraintSet> remaining = new ArrayList<>(sets);
            for (ConstraintSet right : other.sets) {
                List<ConstraintSet> next = new ArrayList<>();
                for (ConstraintSet left : remaining) {
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
            for (ConstraintSet set : sets) {
                expression = expression.or(set.toExpression(keyResolver, symbols));
            }
            return expression.reduce();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Decision && sets.equals(((Decision) o).sets);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sets);
        }
    }

    private static final class ConstraintSet {
        private final Map<Integer, ScalarConstraint> scalars;
        private final Map<Integer, MembershipConstraint> memberships;

        private ConstraintSet() {
            this(Map.of(), Map.of());
        }

        private ConstraintSet(Map<Integer, ScalarConstraint> scalars, Map<Integer, MembershipConstraint> memberships) {
            this.scalars = new TreeMap<>(scalars);
            this.memberships = new TreeMap<>(memberships);
        }

        boolean isTrue() {
            return scalars.isEmpty() && memberships.isEmpty();
        }

        ConstraintSet normalized(Symbol.Table symbols) {
            Map<Integer, ScalarConstraint> nextScalars = new TreeMap<>();
            for (Map.Entry<Integer, ScalarConstraint> entry : scalars.entrySet()) {
                Set<String> domainValues = scalarValues(symbols.symbol(entry.getKey()).domain());
                if (!entry.getValue().allowed().equals(domainValues)) {
                    nextScalars.put(entry.getKey(), entry.getValue());
                }
            }
            Map<Integer, MembershipConstraint> nextMemberships = new TreeMap<>();
            for (Map.Entry<Integer, MembershipConstraint> entry : memberships.entrySet()) {
                MembershipConstraint constraint = entry.getValue();
                if (!constraint.isEmpty()) {
                    nextMemberships.put(entry.getKey(), constraint);
                }
            }
            return new ConstraintSet(nextScalars, nextMemberships);
        }

        ConstraintSet intersect(ConstraintSet other, Symbol.Table symbols) {
            Map<Integer, ScalarConstraint> nextScalars = new TreeMap<>(scalars);
            for (Map.Entry<Integer, ScalarConstraint> entry : other.scalars.entrySet()) {
                ScalarConstraint current = nextScalars.get(entry.getKey());
                if (current == null) {
                    nextScalars.put(entry.getKey(), entry.getValue());
                } else {
                    ScalarConstraint intersection = current.intersect(entry.getValue());
                    if (intersection == null) {
                        return null;
                    }
                    nextScalars.put(entry.getKey(), intersection);
                }
            }
            Map<Integer, MembershipConstraint> nextMemberships = new TreeMap<>(memberships);
            for (Map.Entry<Integer, MembershipConstraint> entry : other.memberships.entrySet()) {
                MembershipConstraint current = nextMemberships.get(entry.getKey());
                if (current == null) {
                    nextMemberships.put(entry.getKey(), entry.getValue());
                } else {
                    MembershipConstraint intersection = current.intersect(entry.getValue());
                    if (intersection == null) {
                        return null;
                    }
                    nextMemberships.put(entry.getKey(), intersection);
                }
            }
            return new ConstraintSet(nextScalars, nextMemberships).normalized(symbols);
        }

        boolean subsetOf(ConstraintSet other, Symbol.Table symbols) {
            Set<Integer> scalarKeys = new HashSet<>(scalars.keySet());
            scalarKeys.addAll(other.scalars.keySet());
            for (int symbolId : scalarKeys) {
                Set<String> leftAllowed = allowed(symbolId, symbols);
                Set<String> rightAllowed = other.allowed(symbolId, symbols);
                if (!rightAllowed.containsAll(leftAllowed)) {
                    return false;
                }
            }
            Set<Integer> membershipKeys = new HashSet<>(memberships.keySet());
            membershipKeys.addAll(other.memberships.keySet());
            for (int symbolId : membershipKeys) {
                MembershipConstraint left = memberships.getOrDefault(symbolId, MembershipConstraint.EMPTY);
                MembershipConstraint right = other.memberships.getOrDefault(symbolId, MembershipConstraint.EMPTY);
                if (!left.required().containsAll(right.required()) || !left.forbidden().containsAll(right.forbidden())) {
                    return false;
                }
            }
            return true;
        }

        ConstraintSet tryMerge(ConstraintSet other, Symbol.Table symbols) {
            if (!memberships.equals(other.memberships)) {
                return null;
            }
            Integer diffSymbolId = null;
            Map<Integer, ScalarConstraint> nextScalars = new TreeMap<>();
            Set<Integer> keys = new TreeSet<>(scalars.keySet());
            keys.addAll(other.scalars.keySet());
            for (int symbolId : keys) {
                Set<String> domainValues = scalarValues(symbols.symbol(symbolId).domain());
                Set<String> leftAllowed = allowed(symbolId, symbols);
                Set<String> rightAllowed = other.allowed(symbolId, symbols);
                if (!leftAllowed.equals(rightAllowed)) {
                    if (diffSymbolId != null) {
                        return null;
                    }
                    diffSymbolId = symbolId;
                    Set<String> union = new TreeSet<>(leftAllowed);
                    union.addAll(rightAllowed);
                    if (!union.equals(domainValues)) {
                        nextScalars.put(symbolId, new ScalarConstraint(union));
                    }
                } else if (!leftAllowed.equals(domainValues)) {
                    nextScalars.put(symbolId, new ScalarConstraint(leftAllowed));
                }
            }
            return diffSymbolId == null ? null : new ConstraintSet(nextScalars, memberships).normalized(symbols);
        }

        List<ConstraintSet> subtract(ConstraintSet other, Symbol.Table symbols) {
            ConstraintSet intersection = intersect(other, symbols);
            if (intersection == null) {
                return List.of(this);
            }
            if (subsetOf(other, symbols)) {
                return List.of();
            }
            Split split = splitAgainst(other, symbols);
            if (split == null) {
                return List.of(this);
            }
            List<ConstraintSet> result = new ArrayList<>();
            if (split.outside() != null) {
                result.add(split.outside());
            }
            if (split.overlap() == null) {
                return result;
            }
            result.addAll(split.overlap().subtract(other, symbols));
            return result;
        }

        Expression toExpression(IntFunction<String> keyResolver, Symbol.Table symbols) {
            Expression expression = Expression.TRUE;
            for (Map.Entry<Integer, ScalarConstraint> entry : scalars.entrySet()) {
                Symbol symbol = symbols.symbol(entry.getKey());
                expression = expression.and(entry.getValue().toExpression(keyResolver.apply(entry.getKey()), symbol.domain()));
            }
            for (Map.Entry<Integer, MembershipConstraint> entry : memberships.entrySet()) {
                expression = expression.and(entry.getValue().toExpression(keyResolver.apply(entry.getKey())));
            }
            return expression.reduce();
        }

        String literal() {
            return scalars + "|" + memberships;
        }

        private Split splitAgainst(ConstraintSet other, Symbol.Table symbols) {
            for (Map.Entry<Integer, ScalarConstraint> entry : other.scalars.entrySet()) {
                int symbolId = entry.getKey();
                Set<String> leftAllowed = allowed(symbolId, symbols);
                Set<String> rightAllowed = other.allowed(symbolId, symbols);
                if (!rightAllowed.containsAll(leftAllowed)) {
                    Set<String> outside = new TreeSet<>(leftAllowed);
                    outside.removeAll(rightAllowed);
                    Set<String> overlap = new TreeSet<>(leftAllowed);
                    overlap.retainAll(rightAllowed);
                    return new Split(withScalar(symbolId, outside, symbols), withScalar(symbolId, overlap, symbols));
                }
            }
            for (Map.Entry<Integer, MembershipConstraint> entry : other.memberships.entrySet()) {
                int symbolId = entry.getKey();
                MembershipConstraint left = memberships.getOrDefault(symbolId, MembershipConstraint.EMPTY);
                for (String required : entry.getValue().required()) {
                    if (!left.required().contains(required) && !left.forbidden().contains(required)) {
                        return new Split(withMembershipForbidden(symbolId, required, symbols),
                                withMembershipRequired(symbolId, required, symbols));
                    }
                }
                for (String forbidden : entry.getValue().forbidden()) {
                    if (!left.required().contains(forbidden) && !left.forbidden().contains(forbidden)) {
                        return new Split(withMembershipRequired(symbolId, forbidden, symbols),
                                withMembershipForbidden(symbolId, forbidden, symbols));
                    }
                }
            }
            return null;
        }

        private ConstraintSet withScalar(int symbolId, Set<String> allowed, Symbol.Table symbols) {
            Map<Integer, ScalarConstraint> nextScalars = new TreeMap<>(scalars);
            Set<String> domainValues = scalarValues(symbols.symbol(symbolId).domain());
            if (allowed.equals(domainValues)) {
                nextScalars.remove(symbolId);
            } else {
                nextScalars.put(symbolId, new ScalarConstraint(allowed));
            }
            return new ConstraintSet(nextScalars, memberships).normalized(symbols);
        }

        private ConstraintSet withMembershipRequired(int symbolId, String item, Symbol.Table symbols) {
            Map<Integer, MembershipConstraint> nextMemberships = new TreeMap<>(memberships);
            MembershipConstraint current = nextMemberships.getOrDefault(symbolId, MembershipConstraint.EMPTY);
            nextMemberships.put(symbolId, current.withRequired(item));
            return new ConstraintSet(scalars, nextMemberships).normalized(symbols);
        }

        private ConstraintSet withMembershipForbidden(int symbolId, String item, Symbol.Table symbols) {
            Map<Integer, MembershipConstraint> nextMemberships = new TreeMap<>(memberships);
            MembershipConstraint current = nextMemberships.getOrDefault(symbolId, MembershipConstraint.EMPTY);
            nextMemberships.put(symbolId, current.withForbidden(item));
            return new ConstraintSet(scalars, nextMemberships).normalized(symbols);
        }

        private Set<String> allowed(int symbolId, Symbol.Table symbols) {
            ScalarConstraint constraint = scalars.get(symbolId);
            return constraint == null ? scalarValues(symbols.symbol(symbolId).domain()) : constraint.allowed();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ConstraintSet)) {
                return false;
            }
            ConstraintSet other = (ConstraintSet) o;
            return scalars.equals(other.scalars) && memberships.equals(other.memberships);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scalars, memberships);
        }
    }

    private static final class ScalarConstraint {
        private final Set<String> allowed;

        private ScalarConstraint(Set<String> allowed) {
            if (allowed.isEmpty()) {
                throw new IllegalArgumentException("Scalar constraint must not be empty");
            }
            this.allowed = canonicalStrings(allowed);
        }

        Set<String> allowed() {
            return allowed;
        }

        ScalarConstraint intersect(ScalarConstraint other) {
            Set<String> intersection = new TreeSet<>(allowed);
            intersection.retainAll(other.allowed);
            return intersection.isEmpty() ? null : new ScalarConstraint(intersection);
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
            Set<String> excluded = new TreeSet<>(domainValues);
            excluded.removeAll(allowed);
            if (excluded.size() == 1) {
                return Expression.create("${" + key + "} != '" + excluded.iterator().next() + "'");
            }
            Expression expression = Expression.FALSE;
            for (String value : allowed) {
                expression = expression.or(Expression.create("${" + key + "} == '" + value + "'"));
            }
            return expression.reduce();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ScalarConstraint && allowed.equals(((ScalarConstraint) o).allowed);
        }

        @Override
        public int hashCode() {
            return Objects.hash(allowed);
        }
    }

    private static final class MembershipConstraint {
        private static final MembershipConstraint EMPTY = new MembershipConstraint(Set.of(), Set.of());

        private final Set<String> required;
        private final Set<String> forbidden;

        private MembershipConstraint(Set<String> required, Set<String> forbidden) {
            Set<String> nextRequired = new TreeSet<>(required);
            Set<String> nextForbidden = new TreeSet<>(forbidden);
            if (!disjoint(nextRequired, nextForbidden)) {
                throw new IllegalArgumentException("Membership required and forbidden items must be disjoint");
            }
            this.required = Set.copyOf(nextRequired);
            this.forbidden = Set.copyOf(nextForbidden);
        }

        static MembershipConstraint required(String item) {
            return new MembershipConstraint(Set.of(item), Set.of());
        }

        Set<String> required() {
            return required;
        }

        Set<String> forbidden() {
            return forbidden;
        }

        boolean isEmpty() {
            return required.isEmpty() && forbidden.isEmpty();
        }

        MembershipConstraint intersect(MembershipConstraint other) {
            Set<String> nextRequired = new TreeSet<>(required);
            nextRequired.addAll(other.required);
            Set<String> nextForbidden = new TreeSet<>(forbidden);
            nextForbidden.addAll(other.forbidden);
            if (!disjoint(nextRequired, nextForbidden)) {
                return null;
            }
            return new MembershipConstraint(nextRequired, nextForbidden);
        }

        MembershipConstraint withRequired(String item) {
            Set<String> nextRequired = new TreeSet<>(required);
            nextRequired.add(item);
            return new MembershipConstraint(nextRequired, forbidden);
        }

        MembershipConstraint withForbidden(String item) {
            Set<String> nextForbidden = new TreeSet<>(forbidden);
            nextForbidden.add(item);
            return new MembershipConstraint(required, nextForbidden);
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
            return expression.reduce();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MembershipConstraint)) {
                return false;
            }
            MembershipConstraint other = (MembershipConstraint) o;
            return required.equals(other.required) && forbidden.equals(other.forbidden);
        }

        @Override
        public int hashCode() {
            return Objects.hash(required, forbidden);
        }
    }

    private static final class Split {
        private final ConstraintSet outside;
        private final ConstraintSet overlap;

        private Split(ConstraintSet outside, ConstraintSet overlap) {
            this.outside = outside;
            this.overlap = overlap;
        }

        ConstraintSet outside() {
            return outside;
        }

        ConstraintSet overlap() {
            return overlap;
        }
    }

    private static boolean disjoint(Set<String> left, Set<String> right) {
        for (String value : left) {
            if (right.contains(value)) {
                return false;
            }
        }
        return true;
    }
}
