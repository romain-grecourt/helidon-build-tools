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
import java.util.Collections;
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
final class Domain {

    private Domain() {
    }

    private static Expression finiteScalarExpression(String key, long allowed, Symbol sym) {
        long mask = sym.domain.mask();
        if (allowed == mask) {
            return Expression.TRUE;
        }
        long excluded = mask & ~allowed;
        if (Long.bitCount(excluded) == 1) {
            int ordinal = Long.numberOfTrailingZeros(excluded);
            String str = sym.value(ordinal);
            List<Token> tokens = List.of(Token.of(key), Token.of(Value.of(str)), Token.of(Operator.NOT_EQUAL));
            return new Expression(tokens, true);
        }
        List<Expression> terms = new ArrayList<>();
        long remaining = allowed;
        while (remaining != 0L) {
            int ordinal = Long.numberOfTrailingZeros(remaining);
            String str = sym.value(ordinal);
            List<Token> tokens = List.of(Token.of(key), Token.of(Value.of(str)), Token.of(Operator.EQUAL));
            terms.add(new Expression(tokens, true));
            remaining &= remaining - 1L;
        }
        return Expression.or(terms);
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
                throw new IllegalArgumentException("Too many values: " + values.length + " > " + Long.SIZE);
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
                Symbol sym = symbolsByName.get(name);
                if (sym != null) {
                    if (!sym.name.equals(name)
                            || sym.guardable != guardable
                            || sym.tainted != tainted
                            || sym.domain.kind != domain.kind
                            || sym.domain.mask != domain.mask
                            || !Arrays.equals(sym.domain.values, domain.values())) {
                        throw new IllegalArgumentException("Conflicting symbol definition: " + name);
                    }
                    return sym.id();
                }
                int id = symbolsByName.size();
                sym = new Symbol(id, name, domain, guardable, tainted);
                symbolsByName.put(name, sym);
                return id;
            }

            Table build() {
                List<Symbol> symbols = new ArrayList<>(symbolsByName.values());
                Map<String, Integer> idsByName = new LinkedHashMap<>();
                for (Symbol sym : symbols) {
                    idsByName.put(sym.name(), sym.id());
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
            Set<String> values = new TreeSet<>();
            long remaining = scalarMask;
            while (remaining != 0L) {
                int ordinal = Long.numberOfTrailingZeros(remaining);
                values.add(spec.values[ordinal]);
                remaining &= remaining - 1L;
            }
            return values;
        }

        String singletonScalar(Spec spec) {
            int ordinal = Long.numberOfTrailingZeros(scalarMask);
            return spec.values[ordinal];
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
        static final Residual TRUE = new Residual(Kind.TRUE, -1, 0L, null, null, null, null);
        static final Residual FALSE = new Residual(Kind.FALSE, -1, 0L, null, null, null, null);

        enum Kind {
            TRUE,
            FALSE,
            OPAQUE,
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
        private final Residual child;
        private final Residual[] children;

        Residual(Kind kind, int id, long mask, String value, Expression expr, Residual child, Residual[] children) {
            this.kind = kind;
            this.id = id;
            this.mask = mask;
            this.value = value;
            this.expr = expr;
            this.child = child;
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
            return new Residual(Kind.OPAQUE, -1, 0L, null, folded, null, null);
        }

        static Residual scalarEq(int id, String value) {
            return new Residual(Kind.SCALAR_EQ, id, 0L, value, null, null, null);
        }

        static Residual scalarIn(int id, long mask) {
            return mask == 0L ? FALSE : new Residual(Kind.SCALAR_IN, id, mask, null, null, null, null);
        }

        static Residual membershipContainsAll(int id, long mask) {
            return mask == 0L ? TRUE : new Residual(Kind.MEMBERSHIP_CONTAINS_ALL, id, mask, null, null, null, null);
        }

        static Residual not(Residual residual) {
            if (residual == TRUE) {
                return FALSE;
            }
            if (residual == FALSE) {
                return TRUE;
            }
            if (residual.kind == Kind.NOT) {
                return residual.child;
            }
            return new Residual(Kind.NOT, -1, 0L, null, null, residual, null);
        }

        static Residual and(List<Residual> children) {
            return combine(Kind.AND, TRUE, FALSE, children);
        }

        static Residual or(List<Residual> children) {
            return combine(Kind.OR, FALSE, TRUE, children);
        }

        static Residual combine(Kind kind, Residual identity, Residual absorbing, List<Residual> children) {
            Set<Residual> normalized = new LinkedHashSet<>();
            for (Residual child : children) {
                if (child != null && child != identity) {
                    if (child == absorbing) {
                        return absorbing;
                    }
                    if (child.kind == kind) {
                        Collections.addAll(normalized, child.children);
                    } else {
                        normalized.add(child);
                    }
                }
            }
            if (normalized.isEmpty()) {
                return identity;
            }
            if (normalized.size() == 1) {
                return normalized.iterator().next();
            }
            return new Residual(kind, -1, 0L, null, null, null, normalized.toArray(Residual[]::new));
        }

        Residual fold() {
            switch (kind) {
                case OPAQUE:
                    return opaque(expr);
                case NOT:
                    return not(child.fold());
                case AND: {
                    List<Residual> normalized = new ArrayList<>(children.length);
                    for (Residual child : children) {
                        normalized.add(child.fold());
                    }
                    return and(normalized);
                }
                case OR: {
                    List<Residual> normalized = new ArrayList<>(children.length);
                    for (Residual child : children) {
                        normalized.add(child.fold());
                    }
                    return or(normalized);
                }
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
                case SCALAR_EQ: {
                    Symbol sym = table.symbol(id);
                    Spec domain = sym.domain();
                    String key = resolver.apply(id);
                    if (domain.kind == Spec.Kind.BOOLEAN) {
                        if ("true".equals(value)) {
                            return new Expression(List.of(Token.of(key)), true);
                        }
                        if ("false".equals(value)) {
                            return new Expression(List.of(Token.of(key)), true);
                        }
                    }
                    List<Token> tokens = List.of(Token.of(key), Token.of(Value.of(value)), Token.of(Operator.EQUAL));
                    return new Expression(tokens, true);
                }
                case SCALAR_IN: {
                    Symbol sym = table.symbol(id);
                    String key = resolver.apply(id);
                    if (sym.domain.kind == Spec.Kind.BOOLEAN && Long.bitCount(mask) == 1) {
                        int ordinal = Long.numberOfTrailingZeros(mask);
                        String value = sym.value(ordinal);
                        if ("true".equals(value)) {
                            return new Expression(List.of(Token.of(key)), true);
                        }
                        return new Expression(List.of(Token.of(key), Token.of(Operator.NOT)), true);
                    }
                    return finiteScalarExpression(key, mask, sym);
                }
                case MEMBERSHIP_CONTAINS_ALL: {
                    Symbol sym = table.symbol(id);
                    String key = resolver.apply(id);
                    List<Expression> terms = new ArrayList<>();
                    long remaining = mask;
                    while (remaining != 0L) {
                        int ordinal = Long.numberOfTrailingZeros(remaining);
                        String str = sym.value(ordinal);
                        List<Token> tokens = List.of(Token.of(key), Token.of(Value.of(str)), Token.of(Operator.CONTAINS));
                        terms.add(new Expression(tokens, true));
                        remaining &= remaining - 1L;
                    }
                    return Expression.and(terms);
                }
                case NOT:
                    return child.expression(resolver, table).negate();
                case AND: {
                    List<Expression> terms = new ArrayList<>(children.length);
                    for (Residual child : children) {
                        terms.add(child.expression(resolver, table));
                    }
                    return Expression.and(terms);
                }
                case OR: {
                    List<Expression> terms = new ArrayList<>(children.length);
                    for (Residual child : children) {
                        terms.add(child.expression(resolver, table));
                    }
                    return Expression.or(terms);
                }
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
                   && Objects.equals(child, other.child)
                   && Arrays.equals(children, other.children);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(kind, id, mask, value, expr, child) + Arrays.hashCode(children);
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
            Symbol sym = table.symbol(id);
            if (!sym.domain.contains(value)) {
                return Guard.FALSE;
            }
            Clause clause = new Clause(new int[] {id}, new long[] {sym.domain.mask(value), 0L});
            Decision decision = Decision.of(List.of(clause), table);
            return guard(decision, Residual.TRUE, true);
        }

        Guard contains(int id, String value) {
            return containsAll(id, Set.of(value));
        }

        Guard containsAll(int id, Set<String> values) {
            Symbol sym = table.symbol(id);
            if (!sym.guardable || sym.tainted || sym.domain.kind != Spec.Kind.MEMBERSHIP) {
                return Guard.FALSE;
            }
            if (!sym.domain.containsAll(values)) {
                return Guard.FALSE;
            }
            Clause clause = new Clause(new int[] {id}, new long[] {sym.domain.mask(values), 0L});
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
                    Expression termExpr = new Expression(List.of(Token.of(variable)), true);
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
            Symbol sym = table.symbol(id);
            String scalar = Value.scalarLiteral(literal);
            if (scalar == null) {
                return null;
            }
            switch (sym.domain.kind) {
                case BOOLEAN:
                case CHOICE:
                case FINITE_TEXT:
                    if (sym.domain.contains(scalar)) {
                        return Residual.scalarEq(sym.id(), scalar);
                    }
                    return Residual.FALSE;
                case OPEN_TEXT:
                    return Residual.scalarEq(sym.id(), scalar);
                default:
                    return null;
            }
        }

        private Residual containsResidual(int id, Value<?> literal) {
            if (id < 0 || literal == null) {
                return null;
            }
            Symbol sym = table.symbol(id);
            if (sym.domain.kind != Spec.Kind.MEMBERSHIP) {
                return null;
            }
            if (literal.type() == Value.Type.STRING) {
                String item = literal.getString();
                return sym.domain.contains(item)
                        ? Residual.membershipContainsAll(sym.id(), sym.domain.mask(item))
                        : Residual.FALSE;
            }
            if (literal.type() == Value.Type.LIST) {
                Set<String> required = new TreeSet<>(literal.getList());
                return sym.domain.containsAll(required)
                        ? Residual.membershipContainsAll(sym.id(), sym.domain.mask(required))
                        : Residual.FALSE;
            }
            return null;
        }

        private Residual scalarInResidual(int id, Value<?> literal) {
            if (id < 0 || literal == null || literal.type() != Value.Type.LIST) {
                return null;
            }
            Symbol sym = table.symbol(id);
            if (!sym.domain.kind.isScalar()) {
                return null;
            }
            Set<String> allowed = new TreeSet<>(literal.getList());
            allowed.removeIf(value -> !sym.domain.contains(value));
            if (allowed.isEmpty()) {
                return Residual.FALSE;
            }
            if (allowed.size() == sym.domain.values.length) {
                return Residual.TRUE;
            }
            return Residual.scalarIn(sym.id(), sym.domain.mask(allowed));
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

    static boolean sameExactValue(Spec spec, Value<?> left, Value<?> right) {
        return exactValueEquals(spec, left, right, spec.mask(left));
    }

    private static boolean exactValueEquals(Spec spec, Value<?> left, Value<?> right, long leftMask) {
        long rightMask = spec.mask(right);
        return leftMask != 0L || rightMask != 0L ? leftMask != 0L && leftMask == rightMask : Value.isEqual(left, right);
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

        Guard exactGuard(Guards guards) {
            Guard result = Guard.FALSE;
            for (GuardedValue guardedValue : guardedValues) {
                result = guards.or(result, guardedValue.guard);
            }
            return result;
        }

        Guard supportedExactGuard(Symbol sym, Guards guards) {
            Guard result = Guard.FALSE;
            Spec domain = sym.domain;
            for (GuardedValue guardedValue : guardedValues) {
                boolean supported;
                switch (domain.kind) {
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
            if (!sym.domain.kind.isScalar()) {
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
            if (sym.domain.kind != Spec.Kind.MEMBERSHIP) {
                return Guard.FALSE;
            }
            if (values.isEmpty()) {
                return exactGuard(guards);
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
            if (equals(other)) {
                return this;
            }
            if (isFalse()) {
                return other;
            }
            if (other.isFalse()) {
                return this;
            }
            if (this == TRUE || other == TRUE) {
                return TRUE;
            }
            List<Clause> merged = new ArrayList<>(clauses.size() + other.clauses.size());
            merged.addAll(clauses);
            merged.addAll(other.clauses);
            Decision normalized = normalize(merged, table);
            return equals(normalized) ? this : normalized;
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
            return normalize(clauses, table);
        }

        private static Decision normalize(List<Clause> clauses, Table table) {
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
                                if (merged.ids.length == 0) {
                                    return TRUE;
                                }
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
                if (sym.domain.kind.isScalar() ? mask0 == fullMask : mask0 == 0L && masks[offset + 1] == 0L) {
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
                if (sym.domain.kind.isScalar() ? mask0 != fullMask : mask0 != 0L || masks[offset + 1] != 0L) {
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
                    int lo = left * 2;
                    writeEntry(nextIds, nextMasks, nextIndex, ids[left], masks[lo], masks[lo + 1]);
                    left++;
                    nextIndex++;
                    continue;
                }
                if (left == ids.length || other.ids[right] < ids[left]) {
                    int ro = right * 2;
                    writeEntry(nextIds, nextMasks, nextIndex, other.ids[right], other.masks[ro], other.masks[ro + 1]);
                    right++;
                    nextIndex++;
                    continue;
                }
                int id = ids[left];
                Symbol sym = table.symbol(id);
                long nextMask0;
                long nextMask1;
                if (sym.domain.kind.isScalar()) {
                    nextMask0 = masks[left * 2] & other.masks[right * 2];
                    if (nextMask0 == 0L) {
                        return null;
                    }
                    nextMask1 = 0L;
                } else {
                    int lo = left * 2;
                    int ro = right * 2;
                    long out0 = masks[lo];
                    long out1 = masks[lo + 1];
                    long over0 = other.masks[ro];
                    long over1 = other.masks[ro + 1];
                    if ((out0 & over1) != 0L || (out1 & over0) != 0L) {
                        return null;
                    }
                    nextMask0 = out0 | over0;
                    nextMask1 = out1 | over1;
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
                int lo = left * 2;
                int ro = right * 2;
                if (sym.domain.kind.isScalar()) {
                    if ((masks[lo] & ~other.masks[ro]) != 0L) {
                        return false;
                    }
                } else if ((other.masks[ro] & ~masks[lo]) != 0L
                           || (other.masks[ro + 1] & ~masks[lo + 1]) != 0L) {
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
                    if (!sym.domain.kind.isScalar()) {
                        return null;
                    }
                    leftMask = masks[left * 2];
                    rightMask = sym.domain.mask();
                    left++;
                } else if (left == ids.length || other.ids[right] < ids[left]) {
                    id = other.ids[right];
                    Symbol sym = table.symbol(id);
                    if (!sym.domain.kind.isScalar()) {
                        return null;
                    }
                    leftMask = sym.domain.mask();
                    rightMask = other.masks[right * 2];
                    right++;
                } else {
                    id = ids[left];
                    int lo = left * 2;
                    int ro = right * 2;
                    Symbol sym = table.symbol(id);
                    if (!sym.domain.kind.isScalar()) {
                        if (masks[lo] != other.masks[ro] || masks[lo + 1] != other.masks[ro + 1]) {
                            return null;
                        }
                        nextMask0 = masks[lo];
                        nextMask1 = masks[lo + 1];
                        left++;
                        right++;
                        writeEntry(nextIds, nextMasks, nextIndex, id, nextMask0, nextMask1);
                        nextIndex++;
                        continue;
                    }
                    leftMask = masks[lo];
                    rightMask = other.masks[ro];
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
                if (!sym.domain.kind.isScalar()) {
                    continue;
                }
                int index = Arrays.binarySearch(ids, id);
                long mask = sym.domain.mask();
                long left = index >= 0 ? masks[index * 2] : mask;
                long right = other.masks[i * 2];
                long out = left & ~right;
                if (out != 0L) {
                    long over = left & right;
                    return split(index, id, out == mask ? 0L : out, 0L, over == mask ? 0L : over, 0L, other, table);
                }
            }
            for (int i = 0; i < other.ids.length; i++) {
                int id = other.ids[i];
                Symbol sym = table.symbol(id);
                if (sym.domain.kind.isScalar()) {
                    continue;
                }
                int index = Arrays.binarySearch(ids, id);
                long out = index >= 0 ? masks[index * 2] : 0L;
                long over = index >= 0 ? masks[index * 2 + 1] : 0L;
                long known = out | over;
                for (int y = 0; y < 2; y++) {
                    boolean parity = y == 0;
                    long unresolved = other.masks[i * 2 + y] & ~known;
                    if (unresolved == 0L) {
                        continue;
                    }
                    long bit = 1L << Long.numberOfTrailingZeros(unresolved);
                    if (parity) {
                        return split(index, id, out, over | bit, out | bit, over, other, table);
                    }
                    return split(index, id, out | bit, over, out, over | bit, other, table);
                }
            }
            return List.of(this);
        }

        Expression expression(IntFunction<String> resolver, Table table) {
            Expression expr = Expression.TRUE;
            for (int i = 0; i < ids.length; i++) {
                Symbol sym = table.symbol(ids[i]);
                int offset = i * 2;
                expr = sym.domain.kind.isScalar()
                        ? expr.and(expression(resolver.apply(ids[i]), masks[offset], sym))
                        : expr.and(expression(resolver.apply(ids[i]), masks[offset], masks[offset + 1], sym));
            }
            return expr;
        }

        List<Clause> split(int index, int id, long out0, long out1, long over0, long over1, Clause other, Table table) {
            Clause outside = withEntry(index, id, out0, out1);
            Clause overlap = withEntry(index, id, over0, over1);
            List<Clause> result = new ArrayList<>();
            result.add(outside);
            result.addAll(overlap.subtract(other, table));
            return result;
        }

        Clause withEntry(int index, int id, long nextMask0, long nextMask1) {
            if (nextMask0 == 0L && nextMask1 == 0L) {
                return index < 0 ? this : removeEntry(index);
            }
            if (index >= 0) {
                int offset = index * 2;
                if (masks[offset] == nextMask0 && masks[offset + 1] == nextMask1) {
                    return this;
                }
                return updateEntry(index, nextMask0, nextMask1);
            }
            return insertEntry(-index - 1, id, nextMask0, nextMask1);
        }

        Clause updateEntry(int index, long mask0, long mask1) {
            long[] nextMasks = Arrays.copyOf(masks, masks.length);
            int offset = index * 2;
            nextMasks[offset] = mask0;
            nextMasks[offset + 1] = mask1;
            return new Clause(ids, nextMasks);
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
            int[] nextIds = size == ids.length ? ids : Arrays.copyOf(ids, size);
            long[] nextMasks = size * 2 == masks.length ? masks : Arrays.copyOf(masks, size * 2);
            return new Clause(nextIds, nextMasks);
        }

        static void writeEntry(int[] ids, long[] masks, int index, int id, long mask0, long mask1) {
            int offset = index * 2;
            ids[index] = id;
            masks[offset] = mask0;
            masks[offset + 1] = mask1;
        }

        static Expression expression(String key, long allowed, Symbol sym) {
            Spec domain = sym.domain();
            if (domain.kind == Spec.Kind.BOOLEAN) {
                if (allowed == domain.mask("true")) {
                    return new Expression(List.of(Token.of(key)), true);
                }
                if (allowed == domain.mask("false")) {
                    return new Expression(List.of(Token.of(key), Token.of(Operator.NOT)), true);
                }
            }
            return finiteScalarExpression(key, allowed, sym);
        }

        static Expression expression(String key, long required, long forbidden, Symbol sym) {
            Expression expr = Expression.TRUE;
            for (int y = 0; y < 2; y++) {
                boolean negate = y != 0;
                long remaining = negate ? forbidden : required;
                while (remaining != 0L) {
                    int ordinal = Long.numberOfTrailingZeros(remaining);
                    String str = sym.value(ordinal);
                    List<Token> tokens = List.of(Token.of(key), Token.of(Value.of(str)), Token.of(Operator.CONTAINS));
                    expr = expr.and(new Expression(negate ? Token.negate(tokens) : tokens, true));
                    remaining &= remaining - 1L;
                }
            }
            return expr;
        }
    }
}
