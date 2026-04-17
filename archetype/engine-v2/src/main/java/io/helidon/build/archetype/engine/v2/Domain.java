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
                    default:
                        return false;
                }
            }
        }

        private final Kind kind;
        private final String[] values;
        private final long mask;
        private final boolean scalar;

        Spec(Kind kind, String... values) {
            if (values.length > Long.SIZE) {
                throw new IllegalArgumentException("Too many values: " + values.length + " > " + Long.SIZE);
            }
            this.kind = kind;
            this.values = values;
            this.scalar = kind.isScalar();
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

        boolean scalar() {
            return scalar;
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

        boolean sameValue(Value<?> left, Value<?> right) {
            long leftMask = mask(left);
            long rightMask = mask(right);
            return leftMask != 0L || rightMask != 0L ? leftMask != 0L && leftMask == rightMask : Value.isEqual(left, right);
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

        private Expression expression(String key, long required, long forbidden) {
            Expression expr = Expression.TRUE;
            for (int y = 0; y < 2; y++) {
                boolean negate = y != 0;
                long remaining = negate ? forbidden : required;
                while (remaining != 0L) {
                    Expression expr0 = Expression.contains(key, value(Long.numberOfTrailingZeros(remaining)));
                    expr = expr.and(negate ? expr0.negate() : expr0);
                    remaining &= remaining - 1L;
                }
            }
            return expr;
        }

        private Expression expression(String key, long allowed) {
            if (allowed == domain.mask) {
                return Expression.TRUE;
            }
            long excluded = domain.mask & ~allowed;
            if (Long.bitCount(excluded) == 1) {
                return Expression.equal(key, value(Long.numberOfTrailingZeros(excluded))).negate();
            }
            List<Expression> terms = new ArrayList<>();
            long remaining = allowed;
            while (remaining != 0L) {
                terms.add(Expression.equal(key, value(Long.numberOfTrailingZeros(remaining))));
                remaining &= remaining - 1L;
            }
            return Expression.or(terms);
        }
    }

    static final class Table {
        private final List<Symbol> symbols;
        private final Map<String, Integer> ids;

        Table(List<Symbol> symbols, Map<String, Integer> ids) {
            this.symbols = symbols;
            this.ids = ids;
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
                values.add(spec.values[Long.numberOfTrailingZeros(remaining)]);
                remaining &= remaining - 1L;
            }
            return values;
        }

        String singletonScalar(Spec spec) {
            return spec.values[Long.numberOfTrailingZeros(scalarMask)];
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
                    String key = resolver.apply(id);
                    if (sym.domain.kind == Spec.Kind.BOOLEAN) {
                        if ("true".equals(value)) {
                            return Expression.variable(key);
                        }
                        if ("false".equals(value)) {
                            return Expression.variable(key).negate();
                        }
                    }
                    return Expression.equal(key, value);
                }
                case SCALAR_IN: {
                    Symbol sym = table.symbol(id);
                    String key = resolver.apply(id);
                    if (sym.domain.kind == Spec.Kind.BOOLEAN && Long.bitCount(mask) == 1) {
                        String value = sym.value(Long.numberOfTrailingZeros(mask));
                        if ("true".equals(value)) {
                            return Expression.variable(key);
                        }
                        return Expression.variable(key).negate();
                    }
                    return sym.expression(key, mask);
                }
                case MEMBERSHIP_CONTAINS_ALL: {
                    Symbol sym = table.symbol(id);
                    String key = resolver.apply(id);
                    List<Expression> terms = new ArrayList<>();
                    long remaining = mask;
                    while (remaining != 0L) {
                        terms.add(Expression.contains(key, sym.value(Long.numberOfTrailingZeros(remaining))));
                        remaining &= remaining - 1L;
                    }
                    return Expression.and(terms);
                }
                case NOT:
                    return child.expression(resolver, table).negate();
                case AND:
                case OR: {
                    List<Expression> terms = new ArrayList<>(children.length);
                    for (Residual child : children) {
                        terms.add(child.expression(resolver, table));
                    }
                    return kind == Kind.AND ? Expression.and(terms) : Expression.or(terms);
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
            Residual leftResidual = left.residual;
            Residual rightResidual = right.residual;
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
            if (left.residual.equals(right.residual)) {
                if (subsetOf(left.id(), right.id())) {
                    return right;
                }
                if (subsetOf(right.id(), left.id())) {
                    return left;
                }
                Decision decision = leftDecision.or(rightDecision, table);
                result = guard(decision, left.residual, true);
            } else if (leftDecision.equals(rightDecision)) {
                result = guard(leftDecision, Residual.or(List.of(left.residual, right.residual)), false);
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
            if (left.equals(right) || left.isFalse() || right.equals(Guard.TRUE)) {
                return true;
            }
            if (left.residual.equals(right.residual)
                && subsetOf(left.id(), right.id())
                || right.isPure() && subsetOf(left.id(), right.id())) {
                return true;
            }
            Decision leftDecision = decision(left);
            Decision rightDecision = decision(right);
            if (left.isPure() && right.isPure()) {
                return leftDecision.subtract(rightDecision, table).isFalse();
            }
            return expression(left).equivalent(expression(right));
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
            Expression residual = guard.residual.expression(id -> scope.key(table.symbol(id).name()), table);
            return supported.and(residual);
        }

        Guard residualGuard(Expression expr) {
            return guard(Decision.TRUE, residual(expr), true);
        }

        private Guard guard(Decision decision, Residual residual, boolean foldConstants) {
            Residual normalized = foldConstants ? residual.fold() : residual;
            if (decision.isFalse() || normalized.isFalse()) {
                return Guard.FALSE;
            }
            if (decision.equals(Decision.TRUE) && normalized.isTrue()) {
                return Guard.TRUE;
            }
            int id = register(decision);
            return new Guard(id, normalized);
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
            Residual[] residuals = new Residual[capacity];
            int[] ids = new int[capacity];
            Value<?>[] literals = new Value<?>[capacity];
            Expression[] expressions = new Expression[capacity];
            int size = 0;
            for (Token token : normalized.tokens()) {
                if (token.isVariable()) {
                    String variable = token.variable();
                    Expression termExpr = Expression.variable(variable);
                    residuals[size] = Residual.opaque(termExpr);
                    ids[size] = table.findId(variable);
                    literals[size] = null;
                    expressions[size] = termExpr;
                    size++;
                    continue;
                }
                if (token.isOperand()) {
                    Value<?> literal = token.operand();
                    Expression termExpr = Expression.value(literal);
                    residuals[size] = Residual.opaque(termExpr);
                    ids[size] = -1;
                    literals[size] = literal;
                    expressions[size] = termExpr;
                    size++;
                    continue;
                }
                Operator op = token.operator();
                switch (op) {
                    case NOT: {
                        int index = size - 1;
                        residuals[index] = Residual.not(residuals[index]);
                        ids[index] = -1;
                        literals[index] = null;
                        expressions[index] = expressions[index].negate();
                        break;
                    }
                    case AND: {
                        int right = --size;
                        int left = --size;
                        residuals[left] = Residual.and(List.of(residuals[left], residuals[right]));
                        ids[left] = -1;
                        literals[left] = null;
                        expressions[left] = expressions[left].and(expressions[right]);
                        size = left + 1;
                        break;
                    }
                    case OR: {
                        int right = --size;
                        int left = --size;
                        residuals[left] = Residual.or(List.of(residuals[left], residuals[right]));
                        ids[left] = -1;
                        literals[left] = null;
                        expressions[left] = expressions[left].or(expressions[right]);
                        size = left + 1;
                        break;
                    }
                    case EQUAL:
                    case NOT_EQUAL: {
                        int right = --size;
                        int left = --size;
                        Residual direct = compareResidual(ids[left], literals[right]);
                        if (direct == null) {
                            direct = compareResidual(ids[right], literals[left]);
                        }
                        Expression combined = Expression.create(expressions[left], op, expressions[right]);
                        if (direct == null) {
                            residuals[left] = Residual.opaque(combined);
                        } else {
                            residuals[left] = op == Operator.EQUAL ? direct : Residual.not(direct);
                        }
                        ids[left] = -1;
                        literals[left] = null;
                        expressions[left] = combined;
                        size = left + 1;
                        break;
                    }
                    case CONTAINS: {
                        int right = --size;
                        int left = --size;
                        Residual direct = containsResidual(ids[left], literals[right]);
                        if (direct == null) {
                            direct = scalarInResidual(ids[right], literals[left]);
                        }
                        Expression combined = Expression.create(expressions[left], Operator.CONTAINS, expressions[right]);
                        residuals[left] = direct == null ? Residual.opaque(combined) : direct;
                        ids[left] = -1;
                        literals[left] = null;
                        expressions[left] = combined;
                        size = left + 1;
                        break;
                    }
                    default:
                        return Residual.opaque(normalized);
                }
            }
            return size == 1 ? residuals[0] : Residual.opaque(normalized);
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
            if (!sym.domain.scalar) {
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
            Expression residual = guard.residual.expression(id -> table.symbol(id).name(), table);
            return supported.and(residual);
        }

        private boolean cacheable(Guard guard) {
            Residual residual = guard.residual;
            if (residual.isTrue() || residual.isFalse()) {
                return true;
            }
            Expression expr = residual.expression(id -> table.symbol(id).name(), table);
            return expr.tokens().size() <= COMPACT_MAX_TOKENS && expr.variableCountAtMost(COMPACT_MAX_VARIABLES);
        }
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
            for (GuardedValue guardedValue : guardedValues) {
                boolean supported;
                switch (sym.domain.kind) {
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
            for (GuardedValue guardedValue : guardedValues) {
                if (sym.domain.sameValue(guardedValue.value, candidate)) {
                    result = guards.or(result, guardedValue.guard);
                }
            }
            return result;
        }

        Guard scalarAny(Symbol sym, Set<String> values, Guards guards) {
            if (!sym.domain.scalar) {
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
            if (!sym.domain.containsAll(values)) {
                return Guard.FALSE;
            }
            long required = sym.domain.mask(values);
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
            return clauses.isEmpty() ? FALSE : normalize(clauses, table);
        }

        static Decision normalize(List<Clause> clauses, Table table) {
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
        private static final Clause EMPTY = new Clause(new int[0], new long[0]);

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
                int offset = i * 2;
                long mask = masks[offset];
                if (sym.domain.scalar && mask == sym.domain.mask
                    || !sym.domain.scalar && mask == 0L && masks[offset + 1] == 0L) {
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
                int offset = i * 2;
                long mask = masks[offset];
                if (sym.domain.scalar && mask != sym.domain.mask
                    || !sym.domain.scalar && (mask != 0L || masks[offset + 1] != 0L)) {
                    set(nextIds, nextMasks, nextIndex, ids[i], mask, masks[offset + 1]);
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
            int n = 0;
            for (int l = 0, r = 0; l < ids.length || r < other.ids.length; ) {
                if (r == other.ids.length || l < ids.length && ids[l] < other.ids[r]) {
                    int lo = l * 2;
                    set(nextIds, nextMasks, n, ids[l], masks[lo], masks[lo + 1]);
                    l++;
                    n++;
                    continue;
                }
                if (l == ids.length || other.ids[r] < ids[l]) {
                    int ro = r * 2;
                    set(nextIds, nextMasks, n, other.ids[r], other.masks[ro], other.masks[ro + 1]);
                    r++;
                    n++;
                    continue;
                }
                int id = ids[l];
                Symbol sym = table.symbol(id);
                long nextMask0;
                long nextMask1;
                if (sym.domain.scalar) {
                    nextMask0 = masks[l * 2] & other.masks[r * 2];
                    if (nextMask0 == 0L) {
                        return null;
                    }
                    nextMask1 = 0L;
                } else {
                    int lo = l * 2;
                    int ro = r * 2;
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
                set(nextIds, nextMasks, n, id, nextMask0, nextMask1);
                l++;
                r++;
                n++;
            }
            return n == 0 ? EMPTY : create(nextIds, nextMasks, n);
        }

        boolean subsetOf(Clause other, Table table) {
            if (this == other) {
                return true;
            }
            if (ids.length < other.ids.length) {
                return false;
            }
            for (int l = 0, r = 0; r < other.ids.length; r++) {
                int id = other.ids[r];
                while (l < ids.length && ids[l] < id) {
                    l++;
                }
                if (l == ids.length || ids[l] != id) {
                    return false;
                }
                Symbol sym = table.symbol(id);
                int lo = l * 2;
                int ro = r * 2;
                if (sym.domain.scalar && (masks[lo] & ~other.masks[ro]) != 0L
                    || !sym.domain.scalar && (other.masks[ro] & ~masks[lo]) != 0L
                    || !sym.domain.scalar && (other.masks[ro + 1] & ~masks[lo + 1]) != 0L) {
                    return false;
                }
            }
            return true;
        }

        Clause tryMerge(Clause other, Table table) {
            boolean diff = false;
            int[] nextIds = new int[ids.length + other.ids.length];
            long[] nextMasks = new long[(ids.length + other.ids.length) * 2];
            int n = 0;
            for (int l = 0, r = 0; l < ids.length || r < other.ids.length; ) {
                int id;
                long leftMask;
                long rightMask;
                if (r == other.ids.length || l < ids.length && ids[l] < other.ids[r]) {
                    id = ids[l];
                    Symbol sym = table.symbol(id);
                    if (!sym.domain.scalar) {
                        return null;
                    }
                    leftMask = masks[l * 2];
                    rightMask = sym.domain.mask;
                    l++;
                } else if (l == ids.length || other.ids[r] < ids[l]) {
                    id = other.ids[r];
                    Symbol sym = table.symbol(id);
                    if (!sym.domain.scalar) {
                        return null;
                    }
                    leftMask = sym.domain.mask;
                    rightMask = other.masks[r * 2];
                    r++;
                } else {
                    id = ids[l];
                    int lo = l * 2;
                    int ro = r * 2;
                    Symbol sym = table.symbol(id);
                    if (!sym.domain.scalar) {
                        if (masks[lo] == other.masks[ro] && masks[lo + 1] == other.masks[ro + 1]) {
                            set(nextIds, nextMasks, n, id, masks[lo], masks[lo + 1]);
                            l++;
                            r++;
                            n++;
                            continue;
                        }
                        return null;
                    }
                    leftMask = masks[lo];
                    rightMask = other.masks[ro];
                    l++;
                    r++;
                }
                long fullMask = table.symbol(id).domain.mask;
                if (leftMask != rightMask) {
                    if (diff) {
                        return null;
                    }
                    diff = true;
                    long unionMask = leftMask | rightMask;
                    if (unionMask != fullMask) {
                        set(nextIds, nextMasks, n, id, unionMask, 0L);
                        n++;
                    }
                } else if (leftMask != fullMask) {
                    set(nextIds, nextMasks, n, id, leftMask, 0L);
                    n++;
                }
            }
            return diff ? n == 0 ? EMPTY : create(nextIds, nextMasks, n) : null;
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
                if (!sym.domain.scalar) {
                    continue;
                }
                int index = Arrays.binarySearch(ids, id);
                long mask = sym.domain.mask;
                long left = index >= 0 ? masks[index * 2] : mask;
                long right = other.masks[i * 2];
                long out = left & ~right;
                if (out != 0L) {
                    long over = left & right;
                    Clause outside = with(index, id, out == mask ? 0L : out, 0L);
                    Clause overlap = with(index, id, over == mask ? 0L : over, 0L);
                    return split(outside, overlap, other, table);
                }
            }
            for (int i = 0; i < other.ids.length; i++) {
                int id = other.ids[i];
                Symbol sym = table.symbol(id);
                if (sym.domain.scalar) {
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
                        Clause outside = with(index, id, out, over | bit);
                        Clause overlap = with(index, id, out | bit, over);
                        return split(outside, overlap, other, table);
                    }
                    Clause outside = with(index, id, out | bit, over);
                    Clause overlap = with(index, id, out, over | bit);
                    return split(outside, overlap, other, table);
                }
            }
            return List.of(this);
        }

        Expression expression(IntFunction<String> resolver, Table table) {
            Expression expr = Expression.TRUE;
            for (int i = 0; i < ids.length; i++) {
                Symbol sym = table.symbol(ids[i]);
                String key = resolver.apply(ids[i]);
                int offset = i * 2;
                long allowed = masks[offset];
                switch (sym.domain.kind) {
                    case BOOLEAN: {
                        if (allowed == sym.domain.mask("true")) {
                            expr = expr.and(Expression.variable(key));
                        } else if (allowed == sym.domain.mask("false")) {
                            expr = expr.and(Expression.variable(key).negate());
                        }
                        break;
                    }
                    case CHOICE:
                    case FINITE_TEXT:
                        expr = expr.and(sym.expression(key, allowed));
                        break;
                    default:
                        expr = expr.and(sym.expression(key, allowed, masks[offset + 1]));
                }
            }
            return expr;
        }

        List<Clause> split(Clause outside, Clause overlap, Clause other, Table table) {
            List<Clause> result = new ArrayList<>();
            result.add(outside);
            result.addAll(overlap.subtract(other, table));
            return result;
        }

        Clause with(int index, int id, long mask0, long mask1) {
            if (mask0 == 0L && mask1 == 0L) {
                return index < 0 ? this : ids.length == 1 ? EMPTY : remove(index);
            }
            if (index >= 0) {
                int offset = index * 2;
                if (masks[offset] == mask0 && masks[offset + 1] == mask1) {
                    return this;
                }
                return update(index, mask0, mask1);
            }
            return insert(-index - 1, id, mask0, mask1);
        }

        Clause update(int index, long mask0, long mask1) {
            long[] nextMasks = Arrays.copyOf(masks, masks.length);
            int offset = index * 2;
            nextMasks[offset] = mask0;
            nextMasks[offset + 1] = mask1;
            return new Clause(ids, nextMasks);
        }

        Clause remove(int index) {
            int[] nextIds = new int[ids.length - 1];
            System.arraycopy(ids, 0, nextIds, 0, index);
            System.arraycopy(ids, index + 1, nextIds, index, ids.length - index - 1);
            int offset = index * 2;
            long[] nextMasks = new long[masks.length - 2];
            System.arraycopy(masks, 0, nextMasks, 0, offset);
            System.arraycopy(masks, offset + 2, nextMasks, offset, masks.length - offset - 2);
            return new Clause(nextIds, nextMasks);
        }

        Clause insert(int offset, int id, long mask0, long mask1) {
            int[] nextIds = new int[ids.length + 1];
            System.arraycopy(ids, 0, nextIds, 0, offset);
            nextIds[offset] = id;
            System.arraycopy(ids, offset, nextIds, offset + 1, ids.length - offset);
            int maskOffset = offset * 2;
            long[] nextMasks = new long[masks.length + 2];
            System.arraycopy(masks, 0, nextMasks, 0, maskOffset);
            nextMasks[maskOffset] = mask0;
            nextMasks[maskOffset + 1] = mask1;
            System.arraycopy(masks, maskOffset, nextMasks, maskOffset + 2, masks.length - maskOffset);
            return new Clause(nextIds, nextMasks);
        }

        int compare(Clause other) {
            int compared = Arrays.compare(ids, other.ids);
            return compared != 0 ? compared : Arrays.compare(masks, other.masks);
        }

        static Clause create(int[] ids, long[] masks, int size) {
            int[] nextIds = size == ids.length ? ids : Arrays.copyOf(ids, size);
            long[] nextMasks = size * 2 == masks.length ? masks : Arrays.copyOf(masks, size * 2);
            return new Clause(nextIds, nextMasks);
        }

        static void set(int[] ids, long[] masks, int index, int id, long mask0, long mask1) {
            int offset = index * 2;
            ids[index] = id;
            masks[offset] = mask0;
            masks[offset + 1] = mask1;
        }
    }
}
