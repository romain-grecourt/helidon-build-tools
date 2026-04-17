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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntFunction;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.archetype.engine.v2.Expression.Operator;
import io.helidon.build.archetype.engine.v2.Expression.Token;
import io.helidon.build.common.Lists;

final class Domain {

    private Domain() {
    }

    static final class Spec {
        static final Spec OPEN_TEXT = new Spec(Kind.OPEN_TEXT);
        static final Spec BOOLEAN = new Spec(Kind.BOOLEAN, "false", "true");

        enum Kind {
            OPEN_TEXT,
            BOOLEAN,
            CHOICE,
            FINITE_TEXT,
            MEMBERSHIP;

            boolean isScalar() {
                switch (this) {
                    case BOOLEAN:
                    case CHOICE:
                    case FINITE_TEXT:
                        return true;
                    case OPEN_TEXT:
                    case MEMBERSHIP:
                    default:
                        return false;
                }
            }
        }

        private final Kind kind;
        private final String[] values;
        private final long mask;

        Spec(Kind kind, String... values) {
            if (values.length > Long.SIZE) {
                throw new IllegalArgumentException(String.format(
                        "Too many values: %d > %d",
                        values.length,
                        Long.SIZE));
            }
            this.kind = kind;
            this.values = values;
            if (kind == Kind.OPEN_TEXT) {
                this.mask = 0L;
            } else if (values.length == Long.SIZE) {
                this.mask = -1L;
            } else {
                this.mask = (1L << values.length) - 1L;
            }
        }

        Kind kind() {
            return kind;
        }

        String[] values() {
            return values;
        }

        long mask() {
            return mask;
        }

        boolean contains(String value) {
            return ordinal(value) >= 0;
        }

        boolean containsAll(Set<String> values) {
            for (String value : values) {
                if (!contains(value)) {
                    return false;
                }
            }
            return true;
        }

        long mask(String value) {
            int ordinal = ordinal(value);
            return 1L << ordinal;
        }

        int ordinal(String value) {
            return Arrays.binarySearch(values, value);
        }

        long mask(Set<String> values) {
            long mask = 0L;
            for (String value : values) {
                mask |= mask(value);
            }
            return mask;
        }

        long mask(Value<?> value) {
            switch (kind) {
                case BOOLEAN:
                case CHOICE:
                case FINITE_TEXT: {
                    String literal = Value.scalarLiteral(value);
                    if (literal == null) {
                        return 0L;
                    }
                    int ordinal = ordinal(literal);
                    return ordinal < 0 ? 0L : 1L << ordinal;
                }
                case MEMBERSHIP:
                    if (value == null || value.type() != Value.Type.LIST) {
                        return 0L;
                    }
                    long mask = 0L;
                    for (String item : new TreeSet<>(value.getList())) {
                        int ordinal = ordinal(item);
                        if (ordinal < 0) {
                            return 0L;
                        }
                        mask |= 1L << ordinal;
                    }
                    return mask;
                case OPEN_TEXT:
                default:
                    return 0L;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Spec)) {
                return false;
            }
            Spec other = (Spec) o;
            return kind == other.kind
                   && mask == other.mask
                   && Arrays.equals(values, other.values);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(kind, mask) + Arrays.hashCode(values);
        }

        @Override
        public String toString() {
            return kind + Arrays.toString(values);
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
            this.name = name;
            this.domain = domain;
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

        String value(int ordinal) {
            return domain.values[ordinal];
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
    }

    static final class Table {
        private final List<Symbol> symbols;
        private final Map<String, Integer> ids;

        private Table(List<Symbol> symbols, Map<String, Integer> ids) {
            this.symbols = symbols;
            this.ids = ids;
        }

        static Builder builder() {
            return new Builder();
        }

        Symbol symbol(int id) {
            if (id < 0 || id >= symbols.size()) {
                throw new IllegalArgumentException("Unknown symbol id: " + id);
            }
            return symbols.get(id);
        }

        int findId(String name) {
            return ids.getOrDefault(name, -1);
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

    static final class GuardedValue {
        private final Value<?> value;
        private final Guard guard;
        private final long mask;

        GuardedValue(Value<?> value, Guard guard, long mask) {
            this.value = value;
            this.guard = guard;
            this.mask = mask;
        }

        Value<?> value() {
            return value;
        }

        Guard guard() {
            return guard;
        }

        long mask() {
            return mask;
        }

        GuardedValue withGuard(Guard nextGuard) {
            return guard.equals(nextGuard) ? this : new GuardedValue(value, nextGuard, mask);
        }
    }

    static final class LatticeValue {
        static final LatticeValue TOP = new LatticeValue(Kind.TOP, null, 0L, 0L, 0L);

        enum Kind {
            BOTTOM,
            TOP,
            OPEN_TEXT,
            FINITE_SCALAR,
            MEMBERSHIP
        }

        private final Kind kind;
        private final String sample;
        private final long scalarMask;
        private final long requiredMask;
        private final long possibleMask;

        private LatticeValue(Kind kind, String sample, long scalarMask, long requiredMask, long possibleMask) {
            this.kind = kind;
            this.sample = sample;
            this.scalarMask = scalarMask;
            this.requiredMask = requiredMask;
            this.possibleMask = possibleMask;
        }

        static LatticeValue top(Spec spec) {
            switch (spec.kind()) {
                case OPEN_TEXT:
                    return openText(null);
                case BOOLEAN:
                case CHOICE:
                case FINITE_TEXT:
                    return finiteScalar(spec.mask());
                case MEMBERSHIP:
                    return membership(0L, spec.mask());
                default:
                    return TOP;
            }
        }

        static LatticeValue openText(String sample) {
            return new LatticeValue(Kind.OPEN_TEXT, sample, 0L, 0L, 0L);
        }

        static LatticeValue finiteScalar(long mask) {
            return new LatticeValue(Kind.FINITE_SCALAR, null, mask, 0L, 0L);
        }

        static LatticeValue membership(long required, long possible) {
            return new LatticeValue(Kind.MEMBERSHIP, null, 0L, required, possible);
        }

        static LatticeValue join(LatticeValue left, LatticeValue right) {
            if (left.equals(right)) {
                return left;
            }
            if (left.kind == Kind.BOTTOM) {
                return right;
            }
            if (right.kind == Kind.BOTTOM) {
                return left;
            }
            if (left.kind == Kind.TOP || right.kind == Kind.TOP) {
                return TOP;
            }
            if (left.kind == Kind.OPEN_TEXT && right.kind == Kind.OPEN_TEXT) {
                return openText(Objects.equals(left.sample, right.sample) ? left.sample : null);
            }
            if (left.kind == Kind.OPEN_TEXT || right.kind == Kind.OPEN_TEXT) {
                return openText(null);
            }
            if (left.kind != right.kind) {
                return TOP;
            }
            switch (left.kind) {
                case FINITE_SCALAR:
                    return finiteScalar(left.scalarMask | right.scalarMask);
                case MEMBERSHIP:
                    return membership(left.requiredMask & right.requiredMask, left.possibleMask | right.possibleMask);
                default:
                    return TOP;
            }
        }

        Kind kind() {
            return kind;
        }

        String sample() {
            return sample;
        }

        long scalarMask() {
            return scalarMask;
        }

        Set<String> scalarValues(Spec spec) {
            return values(spec, scalarMask);
        }

        String singletonScalar(Spec spec) {
            int ordinal = Long.numberOfTrailingZeros(scalarMask);
            return spec.values[ordinal];
        }

        Set<String> possibleValues(Spec spec) {
            return values(spec, possibleMask);
        }

        private static Set<String> values(Spec spec, long mask) {
            Set<String> values = new TreeSet<>();
            long remaining = mask;
            while (remaining != 0L) {
                int ordinal = Long.numberOfTrailingZeros(remaining);
                values.add(spec.values[ordinal]);
                remaining &= remaining - 1L;
            }
            return values;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LatticeValue)) {
                return false;
            }
            LatticeValue other = (LatticeValue) o;
            return kind == other.kind
                    && Objects.equals(sample, other.sample)
                    && scalarMask == other.scalarMask
                    && requiredMask == other.requiredMask
                    && possibleMask == other.possibleMask;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, sample, scalarMask, requiredMask, possibleMask);
        }
    }

    static final class Guard {
        static final int FALSE_ID = 0;
        static final int TRUE_ID = 1;
        static final Guard FALSE = new Guard(FALSE_ID, Residual.TRUE);
        static final Guard TRUE = new Guard(TRUE_ID, Residual.TRUE);

        private final int id;
        private final Residual residual;

        Guard(int id, Residual residual) {
            this.id = id;
            this.residual = residual;
        }

        int id() {
            return id;
        }

        Residual residual() {
            return residual;
        }

        boolean isPure() {
            return residual.isTrue();
        }

        boolean isFalse() {
            return id == FALSE_ID || residual.isFalse();
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
            return id == other.id && residual.equals(other.residual);
        }

        @Override
        public int hashCode() {
            return 31 * Integer.hashCode(id) + residual.hashCode();
        }
    }

    private static final class Residual {
        static final Residual TRUE = new Residual(Kind.TRUE, -1, 0L, null, null, List.of());
        static final Residual FALSE = new Residual(Kind.FALSE, -1, 0L, null, null, List.of());

        enum Kind {
            TRUE,
            FALSE,
            OPAQUE,
            DEFINED,
            SCALAR_EQ,
            SCALAR_IN,
            MEMBERSHIP_CONTAINS_ALL,
            NOT,
            AND,
            OR
        }

        private final Kind kind;
        private final int id;
        private final long mask;
        private final String value;
        private final Expression expr;
        private final List<Residual> children;

        Residual(Kind kind, int id, long mask, String value, Expression expr, List<Residual> children) {
            this.kind = kind;
            this.id = id;
            this.mask = mask;
            this.value = value;
            this.expr = expr;
            this.children = children;
        }

        static Residual opaque(Expression expr) {
            Expression folded = expr.fold();
            if (folded == Expression.TRUE) {
                return TRUE;
            }
            if (folded == Expression.FALSE) {
                return FALSE;
            }
            return new Residual(Kind.OPAQUE, -1, 0L, null, folded, List.of());
        }

        static Residual scalarEq(int id, String value) {
            return new Residual(Kind.SCALAR_EQ, id, 0L, value, null, List.of());
        }

        static Residual scalarIn(int id, long mask) {
            if (mask == 0L) {
                return FALSE;
            }
            return new Residual(Kind.SCALAR_IN, id, mask, null, null, List.of());
        }

        static Residual membershipContainsAll(int id, long mask) {
            if (mask == 0L) {
                return TRUE;
            }
            return new Residual(Kind.MEMBERSHIP_CONTAINS_ALL, id, mask, null, null, List.of());
        }

        static Residual not(Residual residual) {
            if (residual == TRUE) {
                return FALSE;
            }
            if (residual == FALSE) {
                return TRUE;
            }
            if (residual.kind == Kind.NOT) {
                return residual.children.get(0);
            }
            return new Residual(Kind.NOT, -1, 0L, null, null, List.of(residual));
        }

        static Residual and(List<Residual> children) {
            return combine(Kind.AND, TRUE, FALSE, children);
        }

        static Residual or(List<Residual> children) {
            return combine(Kind.OR, FALSE, TRUE, children);
        }

        private static Residual combine(Kind kind, Residual identity, Residual absorbing, List<Residual> children) {
            Set<Residual> normalized = new LinkedHashSet<>();
            for (Residual child : children) {
                if (child == null || child == identity) {
                    continue;
                }
                if (child == absorbing) {
                    return absorbing;
                }
                if (child.kind == kind) {
                    normalized.addAll(child.children);
                } else {
                    normalized.add(child);
                }
            }
            if (normalized.isEmpty()) {
                return identity;
            }
            if (normalized.size() == 1) {
                return normalized.iterator().next();
            }
            return new Residual(kind, -1, 0L, null, null, new ArrayList<>(normalized));
        }

        Residual fold() {
            switch (kind) {
                case OPAQUE:
                    return opaque(expr);
                case NOT:
                    return not(children.get(0).fold());
                case AND:
                    return and(Lists.map(children, Residual::fold));
                case OR:
                    return or(Lists.map(children, Residual::fold));
                default:
                    return this;
            }
        }

        boolean isTrue() {
            return this == TRUE || kind == Kind.TRUE;
        }

        boolean isFalse() {
            return this == FALSE || kind == Kind.FALSE;
        }

        Expression expression(IntFunction<String> resolver, Table table) {
            switch (kind) {
                case TRUE:
                    return Expression.TRUE;
                case OPAQUE:
                    return expr;
                case DEFINED:
                    return Expression.create("${" + resolver.apply(id) + "}");
                case SCALAR_EQ: {
                    Symbol symbol = table.symbol(id);
                    Spec domain = symbol.domain();
                    String key = resolver.apply(id);
                    if (domain.kind() == Spec.Kind.BOOLEAN) {
                        if ("true".equals(value)) {
                            return Expression.create("${" + key + "}");
                        }
                        if ("false".equals(value)) {
                            return Expression.create("!${" + key + "}");
                        }
                    }
                    return Expression.create("${" + key + "} == '" + value + "'");
                }
                case SCALAR_IN: {
                    Symbol symbol = table.symbol(id);
                    Spec domain = symbol.domain();
                    String key = resolver.apply(id);
                    long fullMask = domain.mask();
                    if (mask == fullMask) {
                        return Expression.TRUE;
                    }
                    if (domain.kind() == Spec.Kind.BOOLEAN && Long.bitCount(mask) == 1) {
                        int ordinal = Long.numberOfTrailingZeros(mask);
                        String value = symbol.value(ordinal);
                        if ("true".equals(value)) {
                            return Expression.create("${" + key + "}");
                        }
                        return Expression.create("!${" + key + "}");
                    }
                    long excludedMask = fullMask & ~mask;
                    if (Long.bitCount(excludedMask) == 1) {
                        int ordinal = Long.numberOfTrailingZeros(excludedMask);
                        String str = symbol.value(ordinal);
                        return Expression.create("${" + key + "} != '" + str + "'");
                    }
                    List<Expression> terms = new ArrayList<>();
                    long remaining = mask;
                    while (remaining != 0L) {
                        int ordinal = Long.numberOfTrailingZeros(remaining);
                        String str = symbol.value(ordinal);
                        terms.add(Expression.create("${" + key + "} == '" + str + "'"));
                        remaining &= remaining - 1L;
                    }
                    return Expression.FALSE.or(terms);
                }
                case MEMBERSHIP_CONTAINS_ALL: {
                    Symbol symbol = table.symbol(id);
                    String key = resolver.apply(id);
                    List<Expression> terms = new ArrayList<>();
                    long remaining = mask;
                    while (remaining != 0L) {
                        int ordinal = Long.numberOfTrailingZeros(remaining);
                        String str = symbol.value(ordinal);
                        terms.add(Expression.create("${" + key + "} contains '" + str + "'"));
                        remaining &= remaining - 1L;
                    }
                    return Expression.TRUE.and(terms);
                }
                case NOT:
                    return children.get(0).expression(resolver, table).negate();
                case AND:
                    return Expression.TRUE.and(Lists.map(children, child -> child.expression(resolver, table)));
                case OR:
                    return Expression.FALSE.or(Lists.map(children, child -> child.expression(resolver, table)));
                case FALSE:
                default:
                    return Expression.FALSE;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Residual)) {
                return false;
            }
            Residual other = (Residual) o;
            return kind == other.kind
                   && id == other.id
                   && mask == other.mask
                   && Objects.equals(value, other.value)
                   && Objects.equals(expr, other.expr)
                   && children.equals(other.children);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, id, mask, value, expr, children);
        }
    }

    static final class Guards {
        private static final int COMPACT_MAX_VARIABLES = 4;
        private static final int COMPACT_MAX_TOKENS = 16;
        private final Table table;
        private final List<Decision> decisions = new ArrayList<>();
        private final Map<Decision, Integer> ids = new HashMap<>();
        private final Map<Guard, Map<Guard, Guard>> orCache = new HashMap<>();
        private final Map<Long, Boolean> subsetCache = new HashMap<>();

        Guards(Table table) {
            this.table = table;
            register(Decision.FALSE);
            register(Decision.TRUE);
        }

        Guard and(Guard left, Guard right) {
            if (left.isFalse() || right.isFalse()) {
                return Guard.FALSE;
            }
            Decision decision = decision(left).and(decision(right), table);
            Residual leftResidual = left.residual();
            Residual rightResidual = right.residual();
            Residual residual = leftResidual.equals(rightResidual)
                    ? leftResidual
                    : Residual.and(List.of(leftResidual, rightResidual));
            return guard(decision, residual, true);
        }

        Guard or(Guard left, Guard right) {
            if (left.equals(right)) {
                return left;
            }
            if (left.isFalse()) {
                return right;
            }
            if (right.isFalse()) {
                return left;
            }
            boolean cacheable = cacheable(left) && cacheable(right);
            if (cacheable) {
                Guard cached = orCache.getOrDefault(left, Map.of()).get(right);
                if (cached == null) {
                    cached = orCache.getOrDefault(right, Map.of()).get(left);
                }
                if (cached != null) {
                    return cached;
                }
            }
            Guard result;
            Decision leftDecision = decision(left);
            Decision rightDecision = decision(right);
            if (left.residual().equals(right.residual())) {
                if (subsetOf(left.id(), right.id())) {
                    return right;
                }
                if (subsetOf(right.id(), left.id())) {
                    return left;
                }
                Decision decision = leftDecision.or(rightDecision, table);
                result = guard(decision, left.residual(), true);
            } else if (leftDecision.equals(rightDecision)) {
                result = guard(leftDecision, Residual.or(List.of(left.residual(), right.residual())), false);
            } else if (right.isPure() && subsetOf(left.id(), right.id())) {
                return right;
            } else if (left.isPure() && subsetOf(right.id(), left.id())) {
                return left;
            } else if (left.isPure() && right.isPure()) {
                Decision decision = leftDecision.or(rightDecision, table);
                result = guard(decision, Residual.TRUE, true);
            } else {
                result = residualGuard(expression(left).or(expression(right)));
            }
            if (cacheable && cacheable(result)) {
                orCache.computeIfAbsent(left, k -> new HashMap<>()).put(right, result);
                orCache.computeIfAbsent(right, k -> new HashMap<>()).put(left, result);
            }
            return result;
        }

        Guard not(Guard guard) {
            if (guard.isFalse()) {
                return Guard.TRUE;
            }
            if (guard.equals(Guard.TRUE)) {
                return Guard.FALSE;
            }
            if (guard.isPure()) {
                Decision decision = Decision.TRUE.subtract(decision(guard), table);
                return guard(decision, Residual.TRUE, true);
            }
            return residualGuard(expression(guard).negate());
        }

        Guard minus(Guard left, Guard right) {
            return and(left, not(right));
        }

        boolean implies(Guard left, Guard right) {
            if (implies0(left, right)) {
                return true;
            }
            Decision leftDecision = decision(left);
            Decision rightDecision = decision(right);
            if (left.isPure() && right.isPure()) {
                return leftDecision.subtract(rightDecision, table).isFalse();
            }
            return expression(left).equivalent(expression(right));
        }

        private boolean implies0(Guard left, Guard right) {
            if (left.equals(right) || left.isFalse() || right.equals(Guard.TRUE)) {
                return true;
            }
            return left.residual().equals(right.residual()) && subsetOf(left.id(), right.id())
                    || right.isPure() && subsetOf(left.id(), right.id());
        }

        boolean equivalent(Guard left, Guard right) {
            if (left.equals(right)) {
                return true;
            }
            if (left.isPure() && right.isPure()) {
                return decision(left).equals(decision(right));
            }
            return expression(left).equivalent(expression(right));
        }

        Guard eq(int id, String value) {
            Symbol symbol = table.symbol(id);
            Spec domain = symbol.domain();
            if (!domain.contains(value)) {
                return Guard.FALSE;
            }
            Clause clause = new Clause(new int[]{id}, new long[]{domain.mask(value), 0L});
            Decision decision = Decision.of(List.of(clause), table);
            return guard(decision, Residual.TRUE, true);
        }

        Guard contains(int id, String value) {
            return containsAll(id, Set.of(value));
        }

        Guard containsAll(int id, Set<String> values) {
            Symbol symbol = table.symbol(id);
            Spec domain = symbol.domain();
            if (!symbol.guardable || symbol.tainted || domain.kind != Spec.Kind.MEMBERSHIP) {
                return Guard.FALSE;
            }
            if (!domain.containsAll(values)) {
                return Guard.FALSE;
            }
            Clause clause = new Clause(new int[]{id}, new long[]{domain.mask(values), 0L});
            Decision decision = Decision.of(List.of(clause), table);
            return guard(decision, Residual.TRUE, true);
        }

        Expression expression(Guard guard, Scope scope) {
            Expression supported = decision(guard).expression(id -> scope.key(table.symbol(id).name()), table);
            Expression residual = guard.residual().expression(id -> scope.key(table.symbol(id).name()), table);
            return expression(supported, residual);
        }

        private Expression expression(Expression supported, Expression residual) {
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

        private Guard guard(Decision decision, Residual residual, boolean foldConstants) {
            Residual normalizedResidual = foldConstants ? residual.fold() : residual;
            if (decision.isFalse() || normalizedResidual.isFalse()) {
                return Guard.FALSE;
            }
            if (decision.equals(Decision.TRUE) && normalizedResidual.isTrue()) {
                return Guard.TRUE;
            }
            int id = register(decision);
            return new Guard(id, normalizedResidual);
        }

        Guard residualGuard(Expression expr) {
            return guard(Decision.TRUE, residual(expr), true);
        }

        private Residual residual(Expression expr) {
            Expression normalized = expr.fold();
            if (normalized == Expression.TRUE) {
                return Residual.TRUE;
            }
            if (normalized == Expression.FALSE) {
                return Residual.FALSE;
            }
            int capacity = normalized.tokens().size();
            Residual[] residualStack = new Residual[capacity];
            int[] idStack = new int[capacity];
            Value<?>[] literalStack = new Value<?>[capacity];
            Expression[] exprStack = new Expression[capacity];
            int size = 0;
            for (Token token : normalized.tokens()) {
                if (token.isVariable()) {
                    String variable = token.variable();
                    Expression termExpr = Expression.create("${" + variable + "}");
                    residualStack[size] = Residual.opaque(termExpr);
                    idStack[size] = table.findId(variable);
                    literalStack[size] = null;
                    exprStack[size] = termExpr;
                    size++;
                    continue;
                }
                if (token.isOperand()) {
                    Value<?> literal = token.operand();
                    Expression termExpr = new Expression(List.of(Token.of(literal)), true);
                    residualStack[size] = Residual.opaque(termExpr);
                    idStack[size] = -1;
                    literalStack[size] = literal;
                    exprStack[size] = termExpr;
                    size++;
                    continue;
                }
                Operator op = token.operator();
                switch (op) {
                    case NOT: {
                        int index = size - 1;
                        residualStack[index] = Residual.not(residualStack[index]);
                        idStack[index] = -1;
                        literalStack[index] = null;
                        exprStack[index] = exprStack[index].negate();
                        break;
                    }
                    case AND: {
                        int right = --size;
                        int left = --size;
                        residualStack[left] = Residual.and(List.of(residualStack[left], residualStack[right]));
                        idStack[left] = -1;
                        literalStack[left] = null;
                        exprStack[left] = exprStack[left].and(exprStack[right]);
                        size = left + 1;
                        break;
                    }
                    case OR: {
                        int right = --size;
                        int left = --size;
                        residualStack[left] = Residual.or(List.of(residualStack[left], residualStack[right]));
                        idStack[left] = -1;
                        literalStack[left] = null;
                        exprStack[left] = exprStack[left].or(exprStack[right]);
                        size = left + 1;
                        break;
                    }
                    case EQUAL:
                    case NOT_EQUAL: {
                        int right = --size;
                        int left = --size;
                        Residual direct = compareResidual(idStack[left], literalStack[right]);
                        if (direct == null) {
                            direct = compareResidual(idStack[right], literalStack[left]);
                        }
                        Expression combined = Expression.create(exprStack[left], op, exprStack[right]);
                        residualStack[left] = direct == null
                                ? Residual.opaque(combined)
                                : op == Operator.EQUAL ? direct : Residual.not(direct);
                        idStack[left] = -1;
                        literalStack[left] = null;
                        exprStack[left] = combined;
                        size = left + 1;
                        break;
                    }
                    case CONTAINS: {
                        int right = --size;
                        int left = --size;
                        Residual direct = containsResidual(idStack[left], literalStack[right]);
                        if (direct == null) {
                            direct = scalarInResidual(idStack[right], literalStack[left]);
                        }
                        Expression combined = Expression.create(exprStack[left], Operator.CONTAINS, exprStack[right]);
                        residualStack[left] = direct == null ? Residual.opaque(combined) : direct;
                        idStack[left] = -1;
                        literalStack[left] = null;
                        exprStack[left] = combined;
                        size = left + 1;
                        break;
                    }
                    default:
                        return Residual.opaque(normalized);
                }
            }
            return size == 1 ? residualStack[0] : Residual.opaque(normalized);
        }

        private Residual compareResidual(int id, Value<?> literal) {
            if (id < 0 || literal == null) {
                return null;
            }
            Symbol symbol = table.symbol(id);
            Spec domain = symbol.domain();
            String scalar = Value.scalarLiteral(literal);
            if (scalar == null) {
                return null;
            }
            switch (domain.kind()) {
                case BOOLEAN:
                case CHOICE:
                case FINITE_TEXT:
                    if (domain.contains(scalar)) {
                        return Residual.scalarEq(symbol.id(), scalar);
                    }
                    return Residual.FALSE;
                case OPEN_TEXT:
                    return Residual.scalarEq(symbol.id(), scalar);
                default:
                    return null;
            }
        }

        private Residual containsResidual(int id, Value<?> literal) {
            if (id < 0 || literal == null) {
                return null;
            }
            Symbol symbol = table.symbol(id);
            Spec domain = symbol.domain();
            if (domain.kind() != Spec.Kind.MEMBERSHIP) {
                return null;
            }
            if (literal.type() == Value.Type.STRING) {
                String item = literal.getString();
                return domain.contains(item)
                        ? Residual.membershipContainsAll(symbol.id(), domain.mask(item))
                        : Residual.FALSE;
            }
            if (literal.type() == Value.Type.LIST) {
                Set<String> required = new TreeSet<>(literal.getList());
                return domain.containsAll(required)
                        ? Residual.membershipContainsAll(symbol.id(), domain.mask(required))
                        : Residual.FALSE;
            }
            return null;
        }

        private Residual scalarInResidual(int id, Value<?> literal) {
            if (id < 0 || literal == null || literal.type() != Value.Type.LIST) {
                return null;
            }
            Symbol symbol = table.symbol(id);
            Spec domain = symbol.domain();
            if (!domain.kind().isScalar()) {
                return null;
            }
            Set<String> allowed = new TreeSet<>(literal.getList());
            allowed.removeIf(value -> !domain.contains(value));
            if (allowed.isEmpty()) {
                return Residual.FALSE;
            }
            if (allowed.size() == domain.values.length) {
                return Residual.TRUE;
            }
            return Residual.scalarIn(symbol.id(), domain.mask(allowed));
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
            return decision(guard.id());
        }

        private Decision decision(int guardId) {
            if (guardId < 0 || guardId >= decisions.size()) {
                throw new IllegalArgumentException("Unknown guard id: " + guardId);
            }
            return decisions.get(guardId);
        }

        private boolean subsetOf(int leftId, int rightId) {
            if (leftId == rightId || leftId == Guard.FALSE_ID || rightId == Guard.TRUE_ID) {
                return true;
            }
            if (rightId == Guard.FALSE_ID) {
                return false;
            }
            long key = ((long) leftId << 32) | Integer.toUnsignedLong(rightId);
            Boolean cached = subsetCache.get(key);
            if (cached != null) {
                return cached;
            }
            boolean result = decision(leftId).subsetOf(decision(rightId), table);
            subsetCache.put(key, result);
            return result;
        }

        private Expression expression(Guard guard) {
            Expression supported = decision(guard).expression(id -> table.symbol(id).name(), table);
            Expression residual = guard.residual().expression(id -> table.symbol(id).name(), table);
            return expression(supported, residual);
        }

        private boolean cacheable(Guard guard) {
            Residual residual = guard.residual();
            if (residual.isTrue() || residual.isFalse()) {
                return true;
            }
            Expression expr = residual.expression(id -> table.symbol(id).name(), table);
            return expr.tokens().size() <= COMPACT_MAX_TOKENS && expr.variableCountAtMost(COMPACT_MAX_VARIABLES);
        }
    }

    private static boolean exactValueEquals(Spec spec, Value<?> left, Value<?> right, long leftMask) {
        long rightMask = spec.mask(right);
        return leftMask != 0L || rightMask != 0L ? leftMask != 0L && leftMask == rightMask : Value.isEqual(left, right);
    }

    static boolean sameExactValue(Spec spec, Value<?> left, Value<?> right) {
        return exactValueEquals(spec, left, right, spec.mask(left));
    }

    static final class Fact {
        private final Guard guard;
        private final LatticeValue value;
        private final List<GuardedValue> guardedValues;

        Fact(Guard guard, LatticeValue value, List<GuardedValue> guardedValues) {
            this.guard = guard;
            this.value = value;
            this.guardedValues = guardedValues;
        }

        Guard guard() {
            return guard;
        }

        LatticeValue value() {
            return value;
        }

        List<GuardedValue> guardedValues() {
            return guardedValues;
        }

        Guard exactDefined(Guards guards) {
            Guard result = Guard.FALSE;
            for (GuardedValue guardedValue : guardedValues) {
                result = guards.or(result, guardedValue.guard);
            }
            return result;
        }

        Guard supportedExactDefined(Symbol symbol, Guards guards) {
            Guard result = Guard.FALSE;
            Spec domain = symbol.domain();
            for (GuardedValue guardedValue : guardedValues) {
                boolean supported;
                switch (domain.kind()) {
                    case BOOLEAN:
                    case CHOICE:
                    case FINITE_TEXT:
                        supported = guardedValue.mask != 0L;
                        break;
                    case MEMBERSHIP:
                        supported = guardedValue.value.type() == Value.Type.LIST
                                && (guardedValue.mask != 0L || guardedValue.value.getList().isEmpty());
                        break;
                    case OPEN_TEXT:
                    default:
                        supported = true;
                        break;
                }
                if (supported) {
                    result = guards.or(result, guardedValue.guard);
                }
            }
            return result;
        }

        Guard match(Symbol sym, Value<?> candidate, Guards guards) {
            Guard result = Guard.FALSE;
            Spec domain = sym.domain();
            for (GuardedValue guardedValue : guardedValues) {
                if (exactValueEquals(domain, guardedValue.value, candidate, guardedValue.mask)) {
                    result = guards.or(result, guardedValue.guard);
                }
            }
            return result;
        }

        Guard scalarAny(Symbol sym, Set<String> values, Guards guards) {
            if (!sym.domain.kind().isScalar()) {
                return Guard.FALSE;
            }
            long allowedMask = 0L;
            for (String value : values) {
                if (sym.domain.contains(value)) {
                    allowedMask |= sym.domain.mask(value);
                }
            }
            if (allowedMask == 0L) {
                return Guard.FALSE;
            }
            Guard result = Guard.FALSE;
            for (GuardedValue guardedValue : guardedValues) {
                if ((guardedValue.mask & allowedMask) != 0L) {
                    result = guards.or(result, guardedValue.guard);
                }
            }
            return result;
        }

        Guard listContains(Symbol sym, Set<String> values, Guards guards) {
            if (sym.domain.kind() != Spec.Kind.MEMBERSHIP) {
                return Guard.FALSE;
            }
            if (values.isEmpty()) {
                return exactDefined(guards);
            }
            Spec domain = sym.domain();
            if (!domain.containsAll(values)) {
                return Guard.FALSE;
            }
            long required = domain.mask(values);
            Guard result = Guard.FALSE;
            for (GuardedValue guardedValue : guardedValues) {
                if (required == 0L ? guardedValue.value.type() == Value.Type.LIST
                        : guardedValue.mask != 0L && (required & ~guardedValue.mask) == 0L) {
                    result = guards.or(result, guardedValue.guard);
                }
            }
            return result;
        }

        static Fact exact(Guard guard, Spec spec, LatticeValue value, Value<?> exactValue) {
            List<GuardedValue> guardedValues;
            if (exactValue != null && exactValue.isPresent()) {
                guardedValues = List.of(new GuardedValue(exactValue, guard, spec.mask(exactValue)));
            } else {
                guardedValues = List.of();
            }
            return new Fact(guard, value, guardedValues);
        }

        static Fact merge(Fact left, Fact right, Guards guards) {
            Guard guard = guards.or(left.guard, right.guard);
            LatticeValue value = LatticeValue.join(left.value, right.value);
            if (left.guardedValues.isEmpty()) {
                return new Fact(guard, value, right.guardedValues);
            }
            if (right.guardedValues.isEmpty()) {
                return new Fact(guard, value, left.guardedValues);
            }
            List<GuardedValue> merged = new ArrayList<>();
            Iterator<GuardedValue> leftIt = left.guardedValues.iterator();
            Iterator<GuardedValue> rightIt = right.guardedValues.iterator();
            while (leftIt.hasNext() || rightIt.hasNext()) {
                GuardedValue next = leftIt.hasNext() ? leftIt.next() : rightIt.next();
                boolean mergedValue = false;
                for (int i = 0; i < merged.size(); i++) {
                    GuardedValue current = merged.get(i);
                    if (current.mask != 0L || next.mask != 0L
                            ? current.mask != 0L && current.mask == next.mask
                            : Value.isEqual(current.value, next.value)) {
                        merged.set(i, current.withGuard(guards.or(current.guard, next.guard)));
                        mergedValue = true;
                        break;
                    }
                }
                if (!mergedValue) {
                    merged.add(next);
                }
            }
            return new Fact(guard, value, merged);
        }
    }

    private static final class Decision {
        private static final Decision FALSE = new Decision(List.of());
        private static final Decision TRUE = new Decision(List.of(Clause.EMPTY));
        private static final int TRY_MERGE_MAX_CLAUSES = 32;

        private final List<Clause> clauses;

        Decision(List<Clause> clauses) {
            this.clauses = clauses;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Decision && clauses.equals(((Decision) o).clauses);
        }

        @Override
        public int hashCode() {
            return clauses.hashCode();
        }

        boolean isFalse() {
            return clauses.isEmpty();
        }

        Decision and(Decision other, Table table) {
            if (isFalse() || other.isFalse()) {
                return FALSE;
            }
            if (equals(other)) {
                return this;
            }
            if (this == TRUE) {
                return other;
            }
            if (other == TRUE) {
                return this;
            }
            List<Clause> result = new ArrayList<>();
            for (Clause left : clauses) {
                for (Clause right : other.clauses) {
                    Clause intersection = left.intersect(right, table);
                    if (intersection != null) {
                        result.add(intersection);
                    }
                }
            }
            return of(result, table);
        }

        Decision or(Decision other, Table table) {
            List<Clause> result = new ArrayList<>(clauses);
            boolean changed = false;
            for (Clause clause : other.clauses) {
                Clause candidate = clause;
                boolean restart;
                do {
                    restart = false;
                    for (int i = 0; i < result.size(); i++) {
                        Clause existing = result.get(i);
                        if (candidate.subsetOf(existing, table)) {
                            candidate = null;
                            break;
                        }
                        if (existing.subsetOf(candidate, table)) {
                            result.remove(i);
                            changed = true;
                            restart = true;
                            break;
                        }
                        if (result.size() <= TRY_MERGE_MAX_CLAUSES) {
                            Clause merged = existing.tryMerge(candidate, table);
                            if (merged != null) {
                                if (merged.ids.length == 0) {
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
                    if (candidate.ids.length == 0) {
                        return TRUE;
                    }
                    result.add(candidate);
                    changed = true;
                }
            }
            if (!changed) {
                return this;
            }
            result.sort(Clause::compare);
            return new Decision(result);
        }

        Decision subtract(Decision other, Table table) {
            if (isFalse() || other.isFalse()) {
                return this;
            }
            if (other == TRUE) {
                return FALSE;
            }
            List<Clause> remaining = new ArrayList<>(clauses);
            for (Clause right : other.clauses) {
                List<Clause> next = new ArrayList<>();
                for (Clause left : remaining) {
                    next.addAll(left.subtract(right, table));
                }
                remaining = next;
                if (remaining.isEmpty()) {
                    return FALSE;
                }
            }
            return of(remaining, table);
        }

        Expression expression(IntFunction<String> resolver, Table table) {
            if (isFalse()) {
                return Expression.FALSE;
            }
            Expression expr = Expression.FALSE;
            for (Clause clause : clauses) {
                expr = expr.or(clause.expression(resolver, table));
            }
            return expr;
        }

        boolean subsetOf(Decision other, Table table) {
            if (equals(other) || isFalse() || other == TRUE) {
                return true;
            }
            if (other.isFalse()) {
                return false;
            }
            for (Clause left : clauses) {
                boolean covered = false;
                for (Clause right : other.clauses) {
                    if (left.subsetOf(right, table)) {
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

        static Decision of(List<Clause> clauses, Table table) {
            if (clauses.isEmpty()) {
                return FALSE;
            }
            Set<Clause> unique = new LinkedHashSet<>();
            for (Clause clause : clauses) {
                Clause next = clause.normalized(table);
                if (next.ids.length == 0) {
                    return TRUE;
                }
                unique.add(next);
            }
            List<Clause> normalized = new ArrayList<>(unique);
            boolean changed;
            do {
                changed = false;
                for (int i = 0; i < normalized.size() && !changed; i++) {
                    for (int j = i + 1; j < normalized.size(); j++) {
                        Clause left = normalized.get(i);
                        Clause right = normalized.get(j);
                        if (left.subsetOf(right, table)) {
                            normalized.remove(i);
                            changed = true;
                            break;
                        }
                        if (right.subsetOf(left, table)) {
                            normalized.remove(j);
                            changed = true;
                            break;
                        }
                        if (normalized.size() <= TRY_MERGE_MAX_CLAUSES) {
                            Clause merged = left.tryMerge(right, table);
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
            normalized.sort(Clause::compare);
            return new Decision(normalized);
        }
    }

    private static final class Clause {
        private static final int[] EMPTY_IDS = new int[0];
        private static final long[] EMPTY_MASKS = new long[0];
        private static final Clause EMPTY = new Clause(EMPTY_IDS, EMPTY_MASKS);

        private final int[] ids;
        private final long[] masks;

        Clause(int[] ids, long[] masks) {
            this.ids = ids;
            this.masks = masks;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Clause)) {
                return false;
            }
            Clause other = (Clause) o;
            return Arrays.equals(ids, other.ids) && Arrays.equals(masks, other.masks);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(ids) + Arrays.hashCode(masks);
        }

        Clause normalized(Table table) {
            int nextCount = ids.length;
            boolean changed = false;
            for (int i = 0; i < ids.length; i++) {
                Symbol sym = table.symbol(ids[i]);
                long fullMask = sym.domain.mask();
                int offset = i * 2;
                long mask0 = masks[offset];
                if (sym.domain.kind().isScalar() ? mask0 == fullMask : mask0 == 0L && masks[offset + 1] == 0L) {
                    changed = true;
                    nextCount--;
                }
            }
            if (!changed) {
                return this;
            }
            if (nextCount == 0) {
                return EMPTY;
            }
            int[] nextIds = new int[nextCount];
            long[] nextMasks = new long[nextCount * 2];
            int nextIndex = 0;
            for (int i = 0; i < ids.length; i++) {
                Symbol sym = table.symbol(ids[i]);
                long fullMask = sym.domain.mask();
                int offset = i * 2;
                long mask0 = masks[offset];
                if (sym.domain.kind().isScalar() ? mask0 != fullMask : mask0 != 0L || masks[offset + 1] != 0L) {
                    writeEntry(nextIds, nextMasks, nextIndex, ids[i], mask0, masks[offset + 1]);
                    nextIndex++;
                }
            }
            return new Clause(nextIds, nextMasks);
        }

        Clause intersect(Clause other, Table table) {
            if (this == other) {
                return this;
            }
            if (ids.length == 0) {
                return other;
            }
            if (other.ids.length == 0) {
                return this;
            }
            int[] nextIds = new int[ids.length + other.ids.length];
            long[] nextMasks = new long[(ids.length + other.ids.length) * 2];
            int left = 0;
            int right = 0;
            int nextIndex = 0;
            while (left < ids.length || right < other.ids.length) {
                if (right == other.ids.length || left < ids.length && ids[left] < other.ids[right]) {
                    int leftOffset = left * 2;
                    writeEntry(nextIds, nextMasks, nextIndex, ids[left], masks[leftOffset], masks[leftOffset + 1]);
                    left++;
                    nextIndex++;
                    continue;
                }
                if (left == ids.length || other.ids[right] < ids[left]) {
                    int rightOffset = right * 2;
                    writeEntry(nextIds, nextMasks, nextIndex, other.ids[right],
                            other.masks[rightOffset], other.masks[rightOffset + 1]);
                    right++;
                    nextIndex++;
                    continue;
                }
                int id = ids[left];
                Symbol sym = table.symbol(id);
                long nextMask0;
                long nextMask1;
                if (sym.domain.kind().isScalar()) {
                    nextMask0 = masks[left * 2] & other.masks[right * 2];
                    if (nextMask0 == 0L) {
                        return null;
                    }
                    nextMask1 = 0L;
                } else {
                    int leftOffset = left * 2;
                    int rightOffset = right * 2;
                    long leftRequired = masks[leftOffset];
                    long leftForbidden = masks[leftOffset + 1];
                    long rightRequired = other.masks[rightOffset];
                    long rightForbidden = other.masks[rightOffset + 1];
                    if ((leftRequired & rightForbidden) != 0L || (leftForbidden & rightRequired) != 0L) {
                        return null;
                    }
                    nextMask0 = leftRequired | rightRequired;
                    nextMask1 = leftForbidden | rightForbidden;
                }
                writeEntry(nextIds, nextMasks, nextIndex, id, nextMask0, nextMask1);
                left++;
                right++;
                nextIndex++;
            }
            return create(nextIds, nextMasks, nextIndex);
        }

        boolean subsetOf(Clause other, Table table) {
            if (this == other) {
                return true;
            }
            if (ids.length < other.ids.length) {
                return false;
            }
            int left = 0;
            for (int right = 0; right < other.ids.length; right++) {
                int id = other.ids[right];
                while (left < ids.length && ids[left] < id) {
                    left++;
                }
                if (left == ids.length || ids[left] != id) {
                    return false;
                }
                Symbol sym = table.symbol(id);
                if (sym.domain.kind().isScalar()) {
                    if ((masks[left * 2] & ~other.masks[right * 2]) != 0L) {
                        return false;
                    }
                } else if ((other.masks[right * 2] & ~masks[left * 2]) != 0L
                           || (other.masks[right * 2 + 1] & ~masks[left * 2 + 1]) != 0L) {
                    return false;
                }
            }
            return true;
        }

        Clause tryMerge(Clause other, Table table) {
            boolean diff = false;
            int[] nextIds = new int[ids.length + other.ids.length];
            long[] nextMasks = new long[(ids.length + other.ids.length) * 2];
            int left = 0;
            int right = 0;
            int nextIndex = 0;
            while (left < ids.length || right < other.ids.length) {
                int id;
                long leftMask;
                long rightMask;
                long nextMask0;
                long nextMask1;
                if (right == other.ids.length || left < ids.length && ids[left] < other.ids[right]) {
                    id = ids[left];
                    Symbol sym = table.symbol(id);
                    if (!sym.domain.kind().isScalar()) {
                        return null;
                    }
                    leftMask = masks[left * 2];
                    rightMask = sym.domain.mask();
                    left++;
                } else if (left == ids.length || other.ids[right] < ids[left]) {
                    id = other.ids[right];
                    Symbol sym = table.symbol(id);
                    if (!sym.domain.kind().isScalar()) {
                        return null;
                    }
                    leftMask = sym.domain.mask();
                    rightMask = other.masks[right * 2];
                    right++;
                } else {
                    id = ids[left];
                    Symbol sym = table.symbol(id);
                    if (!sym.domain.kind().isScalar()) {
                        int leftOffset = left * 2;
                        int rightOffset = right * 2;
                        if (masks[leftOffset] != other.masks[rightOffset]
                            || masks[leftOffset + 1] != other.masks[rightOffset + 1]) {
                            return null;
                        }
                        nextMask0 = masks[leftOffset];
                        nextMask1 = masks[leftOffset + 1];
                        left++;
                        right++;
                        writeEntry(nextIds, nextMasks, nextIndex, id, nextMask0, nextMask1);
                        nextIndex++;
                        continue;
                    }
                    leftMask = masks[left * 2];
                    rightMask = other.masks[right * 2];
                    left++;
                    right++;
                }
                long fullMask = table.symbol(id).domain.mask();
                if (leftMask != rightMask) {
                    if (diff) {
                        return null;
                    }
                    diff = true;
                    long unionMask = leftMask | rightMask;
                    if (unionMask != fullMask) {
                        writeEntry(nextIds, nextMasks, nextIndex, id, unionMask, 0L);
                        nextIndex++;
                    }
                } else if (leftMask != fullMask) {
                    writeEntry(nextIds, nextMasks, nextIndex, id, leftMask, 0L);
                    nextIndex++;
                }
            }
            return diff ? create(nextIds, nextMasks, nextIndex) : null;
        }

        List<Clause> subtract(Clause other, Table table) {
            Clause intersection = intersect(other, table);
            if (intersection == null) {
                return List.of(this);
            }
            if (subsetOf(other, table)) {
                return List.of();
            }
            for (int i = 0; i < other.ids.length; i++) {
                int id = other.ids[i];
                Symbol sym = table.symbol(id);
                if (!sym.domain.kind().isScalar()) {
                    continue;
                }
                int index = Arrays.binarySearch(ids, id);
                long leftMask = index >= 0 ? masks[index * 2] : sym.domain.mask();
                long rightMask = other.masks[i * 2];
                long outsideMask = leftMask & ~rightMask;
                if (outsideMask != 0L) {
                    long overlapMask = leftMask & rightMask;
                    Clause outside = withScalar(id, outsideMask, sym);
                    Clause overlap = withScalar(id, overlapMask, sym);
                    List<Clause> result = new ArrayList<>();
                    result.add(outside);
                    result.addAll(overlap.subtract(other, table));
                    return result;
                }
            }
            for (int i = 0; i < other.ids.length; i++) {
                int id = other.ids[i];
                Symbol sym = table.symbol(id);
                if (sym.domain.kind().isScalar()) {
                    continue;
                }
                int index = Arrays.binarySearch(ids, id);
                long leftRequired = index >= 0 ? masks[index * 2] : 0L;
                long leftForbidden = index >= 0 ? masks[index * 2 + 1] : 0L;
                long known = leftRequired | leftForbidden;
                for (int y = 0; y < 2; y++) {
                    boolean parity = y == 0;
                    long unresolved = other.masks[i * 2 + y] & ~known;
                    if (unresolved == 0L) {
                        continue;
                    }
                    List<Clause> result = new ArrayList<>();
                    int ordinal = Long.numberOfTrailingZeros(unresolved);
                    String value = sym.value(ordinal);
                    result.add(withMembership(id, value, sym, !parity));
                    result.addAll(withMembership(id, value, sym, parity).subtract(other, table));
                    return result;
                }
            }
            return List.of(this);
        }

        Expression expression(IntFunction<String> resolver, Table table) {
            Expression expr = Expression.TRUE;
            for (int i = 0; i < ids.length; i++) {
                Symbol sym = table.symbol(ids[i]);
                int offset = i * 2;
                expr = sym.domain.kind().isScalar()
                        ? expr.and(expression(resolver.apply(ids[i]), masks[offset], sym))
                        : expr.and(expression(resolver.apply(ids[i]), masks[offset], masks[offset + 1], sym));
            }
            return expr;
        }

        Clause withScalar(int id, long nextMask, Symbol sym) {
            if (nextMask == sym.domain.mask()) {
                int index = Arrays.binarySearch(ids, id);
                if (index < 0) {
                    return this;
                }
                return removeEntry(index);
            }
            int index = Arrays.binarySearch(ids, id);
            if (index >= 0) {
                int offset = index * 2;
                if (masks[offset] == nextMask && masks[offset + 1] == 0L) {
                    return this;
                }
                return updateEntry(index, nextMask, 0L);
            }
            return insertEntry(-index - 1, id, nextMask, 0L);
        }

        Clause withMembership(int id, String value, Symbol sym, boolean parity) {
            int index = Arrays.binarySearch(ids, id);
            long required = index < 0 ? 0L : masks[index * 2];
            long forbidden = index < 0 ? 0L : masks[index * 2 + 1];
            long mask = sym.domain.mask(value);
            if ((forbidden & mask) != 0L) {
                return this;
            }
            if (parity) {
                return withMembership(index, id, required | mask, forbidden);
            }
            return withMembership(index, id, required, forbidden | mask);
        }

        Clause withMembership(int index, int id, long nextRequired, long nextForbidden) {
            if (nextRequired == 0L && nextForbidden == 0L) {
                if (index < 0) {
                    return this;
                }
                return removeEntry(index);
            }
            if (index >= 0) {
                int offset = index * 2;
                if (masks[offset] == nextRequired && masks[offset + 1] == nextForbidden) {
                    return this;
                }
                return updateEntry(index, nextRequired, nextForbidden);
            }
            return insertEntry(-index - 1, id, nextRequired, nextForbidden);
        }

        Clause updateEntry(int index, long mask0, long mask1) {
            long[] masks = Arrays.copyOf(this.masks, this.masks.length);
            int offset = index * 2;
            masks[offset] = mask0;
            masks[offset + 1] = mask1;
            return new Clause(ids, masks);
        }

        Clause removeEntry(int index) {
            if (ids.length == 1) {
                return EMPTY;
            }
            int[] nextIds = new int[ids.length - 1];
            System.arraycopy(ids, 0, nextIds, 0, index);
            System.arraycopy(ids, index + 1, nextIds, index, ids.length - index - 1);
            int offset = index * 2;
            long[] nextMasks = new long[masks.length - 2];
            System.arraycopy(masks, 0, nextMasks, 0, offset);
            System.arraycopy(masks, offset + 2, nextMasks, offset, masks.length - offset - 2);
            return new Clause(nextIds, nextMasks);
        }

        Clause insertEntry(int insertion, int id, long mask0, long mask1) {
            int[] nextIds = new int[ids.length + 1];
            System.arraycopy(ids, 0, nextIds, 0, insertion);
            nextIds[insertion] = id;
            System.arraycopy(ids, insertion, nextIds, insertion + 1, ids.length - insertion);
            int offset = insertion * 2;
            long[] nextMasks = new long[masks.length + 2];
            System.arraycopy(masks, 0, nextMasks, 0, offset);
            nextMasks[offset] = mask0;
            nextMasks[offset + 1] = mask1;
            System.arraycopy(masks, offset, nextMasks, offset + 2, masks.length - offset);
            return new Clause(nextIds, nextMasks);
        }

        int compare(Clause other) {
            int compared = Arrays.compare(ids, other.ids);
            return compared != 0 ? compared : Arrays.compare(masks, other.masks);
        }

        static Clause create(int[] ids, long[] masks, int size) {
            if (size == 0) {
                return EMPTY;
            }
            return new Clause(size == ids.length ? ids : Arrays.copyOf(ids, size),
                    size * 2 == masks.length ? masks : Arrays.copyOf(masks, size * 2));
        }

        static void writeEntry(int[] ids, long[] masks, int index, int id, long mask0, long mask1) {
            int offset = index * 2;
            ids[index] = id;
            masks[offset] = mask0;
            masks[offset + 1] = mask1;
        }

        private static Expression expression(String key, long allowed, Symbol sym) {
            Spec domain = sym.domain();
            if (domain.kind() == Spec.Kind.BOOLEAN) {
                if (allowed == domain.mask("true")) {
                    return Expression.create("${" + key + "}");
                }
                if (allowed == domain.mask("false")) {
                    return Expression.create("!${" + key + "}");
                }
            }
            long fullMask = domain.mask();
            if (allowed == fullMask) {
                return Expression.TRUE;
            }
            long excluded = fullMask & ~allowed;
            if (Long.bitCount(excluded) == 1) {
                int ordinal = Long.numberOfTrailingZeros(excluded);
                String str = sym.value(ordinal);
                return Expression.create("${" + key + "} != '" + str + "'");
            }
            Expression expr = Expression.FALSE;
            long remaining = allowed;
            while (remaining != 0L) {
                int ordinal = Long.numberOfTrailingZeros(remaining);
                String str = sym.value(ordinal);
                expr = expr.or(Expression.create("${" + key + "} == '" + str + "'"));
                remaining &= remaining - 1L;
            }
            return expr;
        }

        static Expression expression(String key, long required, long forbidden, Symbol sym) {
            Expression expr = Expression.TRUE;
            long remaining = required;
            while (remaining != 0L) {
                int ordinal = Long.numberOfTrailingZeros(remaining);
                String str = sym.value(ordinal);
                List<Token> tokens = List.of(Token.of(key), Token.of(Value.of(str)), Token.of(Operator.CONTAINS));
                expr = expr.and(new Expression(tokens, true));
                remaining &= remaining - 1L;
            }
            remaining = forbidden;
            while (remaining != 0L) {
                int ordinal = Long.numberOfTrailingZeros(remaining);
                String str = sym.value(ordinal);
                List<Token> tokens = Token.negate(List.of(Token.of(key), Token.of(Value.of(str)), Token.of(Operator.CONTAINS)));
                expr = expr.and(new Expression(tokens, true));
                remaining &= remaining - 1L;
            }
            return expr;
        }
    }
}
