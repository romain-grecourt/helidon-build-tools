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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntFunction;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.archetype.engine.v2.Expression.Token;
import io.helidon.build.common.Lists;

final class Domain {

    private Domain() {
    }

    static final class Spec {
        static final Spec OPEN_TEXT = new Spec(Kind.OPEN_TEXT, SubKind.OPEN_TEXT, null, null, 0L);
        static final Spec BOOLEAN = finiteScalar(SubKind.BOOLEAN, Set.of("false", "true"));

        enum Kind {
            OPEN_TEXT,
            FINITE_SCALAR,
            FINITE_MEMBERSHIP
        }

        enum SubKind {
            OPEN_TEXT,
            BOOLEAN,
            CHOICE,
            FINITE_TEXT,
            MEMBERSHIP
        }

        private final Kind kind;
        private final SubKind subKind;
        private final Map<String, Integer> ordinals;
        private final String[] values;
        private final long mask;

        private Spec(Kind kind, SubKind subKind, Map<String, Integer> ordinals, String[] values, long mask) {
            this.kind = kind;
            this.subKind = subKind;
            this.ordinals = ordinals;
            this.values = values;
            this.mask = mask;
        }

        static Spec booleanSpec() {
            return BOOLEAN;
        }

        static Spec choice(Set<String> values) {
            return finiteScalar(SubKind.CHOICE, values);
        }

        static Spec finiteText(Set<String> values) {
            return finiteScalar(SubKind.FINITE_TEXT, values);
        }

        static Spec finiteScalar(SubKind subKind, Set<String> values) {
            if (subKind != SubKind.BOOLEAN && subKind != SubKind.CHOICE && subKind != SubKind.FINITE_TEXT) {
                throw new IllegalArgumentException("Invalid finite scalar subKind: " + subKind);
            }
            Map<String, Integer> ordinals = stringOrdinals(values, "Finite scalar");
            return new Spec(Kind.FINITE_SCALAR, subKind, ordinals, values(ordinals), mask(ordinals.size()));
        }

        static Spec finiteMembership(Set<String> items) {
            Map<String, Integer> ordinals = stringOrdinals(items, "Finite membership");
            return new Spec(Kind.FINITE_MEMBERSHIP, SubKind.MEMBERSHIP, ordinals, values(ordinals), mask(ordinals.size()));
        }

        Kind kind() {
            return kind;
        }

        SubKind subKind() {
            return subKind;
        }

        boolean scalar() {
            return kind == Kind.FINITE_SCALAR;
        }

        boolean membership() {
            return kind == Kind.FINITE_MEMBERSHIP;
        }

        boolean booleanLike() {
            return subKind == SubKind.BOOLEAN;
        }

        Set<String> values() {
            return ordinals == null ? Set.of() : ordinals.keySet();
        }

        long mask() {
            return mask;
        }

        long mask(String value) {
            if (ordinals == null) {
                throw new IllegalStateException("Mask requested for non-finite spec: " + subKind);
            }
            Integer ordinal = ordinals.get(value);
            if (ordinal == null) {
                throw new IllegalArgumentException("Unknown value for " + subKind + " spec: " + value);
            }
            return 1L << ordinal;
        }

        long mask(Set<String> values) {
            long mask = 0L;
            for (String value : values) {
                mask |= mask(value);
            }
            return mask;
        }

        String value(int ordinal) {
            if (values == null) {
                throw new IllegalStateException("Ordinal requested for non-finite spec: " + subKind);
            }
            return values[ordinal];
        }

        private static Map<String, Integer> stringOrdinals(Set<String> values, String label) {
            if (values.isEmpty()) {
                throw new IllegalArgumentException(label + " requires at least one value");
            }
            if (values.size() > Long.SIZE) {
                throw new IllegalArgumentException(label + " supports at most " + Long.SIZE + " values");
            }
            Map<String, Integer> ordinals = new LinkedHashMap<>();
            int ordinal = 0;
            for (String value : new TreeSet<>(values)) {
                ordinals.put(value, ordinal++);
            }
            return ordinals;
        }

        private static String[] values(Map<String, Integer> ordinals) {
            String[] values = new String[ordinals.size()];
            ordinals.forEach((value, ordinal) -> values[ordinal] = value);
            return values;
        }

        private static long mask(int size) {
            return size == Long.SIZE ? -1L : (1L << size) - 1L;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Spec)) {
                return false;
            }
            Spec other = (Spec) o;
            return kind == other.kind
                   && subKind == other.subKind
                   && mask == other.mask
                   && Objects.equals(ordinals, other.ordinals);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, subKind, ordinals, mask);
        }

        @Override
        public String toString() {
            return ordinals == null ? subKind.name() : subKind + values().toString();
        }
    }

    static final class Symbol {
        private final int id;
        private final String name;
        private final Spec domain;
        private final boolean guardable;
        private final boolean tainted;
        private final boolean scalar;
        private final boolean member;
        private final Map<String, Integer> ordinals;
        private final long mask;

        Symbol(int id, String name, Spec domain, boolean guardable, boolean tainted) {
            this.id = id;
            this.name = name;
            this.domain = domain;
            this.guardable = guardable;
            this.tainted = tainted;
            this.scalar = domain.kind() == Spec.Kind.FINITE_SCALAR;
            this.member = domain.kind() == Spec.Kind.FINITE_MEMBERSHIP;
            this.ordinals = scalar || member ? domain.ordinals : null;
            this.mask = scalar || member ? domain.mask : 0L;
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

        long scalarMask(String value) {
            if (!scalar) {
                throw new IllegalStateException("Scalar mask requested for non-maskable symbol: " + name);
            }
            return domain.mask(value);
        }

        long membershipMask(String value) {
            if (!member) {
                throw new IllegalStateException("Membership mask requested for non-maskable symbol: " + name);
            }
            return domain.mask(value);
        }

        long membershipMask(Set<String> values) {
            if (!member) {
                throw new IllegalStateException("Membership mask requested for non-maskable symbol: " + name);
            }
            long mask = 0L;
            for (String value : values) {
                mask |= membershipMask(value);
            }
            return mask;
        }

        String scalarValue(int ordinal) {
            if (!scalar) {
                throw new IllegalStateException("Scalar value requested for non-maskable symbol: " + name);
            }
            return domain.value(ordinal);
        }

        String membershipItem(int ordinal) {
            if (!member) {
                throw new IllegalStateException("Membership item requested for non-maskable symbol: " + name);
            }
            return domain.value(ordinal);
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
            private final LatticeValue value;
            private final List<ExactCase> exactCases;

            Fact(Guard definedUnder, LatticeValue value) {
                this(definedUnder, value, List.of());
            }

            Fact(Guard definedUnder, LatticeValue value, List<ExactCase> exactCases) {
                this.definedUnder = definedUnder;
                this.value = value;
                this.exactCases = exactCases;
            }

            Guard definedUnder() {
                return definedUnder;
            }

            LatticeValue value() {
                return value;
            }

            List<ExactCase> exactCases() {
                return exactCases;
            }

            Guard exactDefined(Guards guards) {
                Guard result = Guard.FALSE;
                for (ExactCase exactCase : exactCases) {
                    result = guards.or(result, exactCase.guard);
                }
                return result;
            }

            Guard supportedExactDefined(Symbol symbol, Guards guards) {
                Guard result = Guard.FALSE;
                for (ExactCase exactCase : exactCases) {
                    if (exactCase.supportedExact(symbol.domain())) {
                        result = guards.or(result, exactCase.guard);
                    }
                }
                return result;
            }

            Guard match(Symbol symbol, Value<?> candidate, Guards guards) {
                Guard result = Guard.FALSE;
                for (ExactCase exactCase : exactCases) {
                    if (exactCase.matches(symbol.domain(), candidate)) {
                        result = guards.or(result, exactCase.guard);
                    }
                }
                return result;
            }

            Guard scalarAny(Symbol symbol, Set<String> values, Guards guards) {
                if (!symbol.scalar) {
                    return Guard.FALSE;
                }
                long allowedMask = 0L;
                for (String value : values) {
                    Integer ordinal = symbol.ordinals.get(value);
                    if (ordinal != null) {
                        allowedMask |= 1L << ordinal;
                    }
                }
                if (allowedMask == 0L) {
                    return Guard.FALSE;
                }
                Guard result = Guard.FALSE;
                for (ExactCase exactCase : exactCases) {
                    if ((exactCase.scalarMask & allowedMask) != 0L) {
                        result = guards.or(result, exactCase.guard);
                    }
                }
                return result;
            }

            Guard listContains(Symbol symbol, Set<String> required, Guards guards) {
                if (!symbol.member) {
                    return Guard.FALSE;
                }
                if (required.isEmpty()) {
                    return exactDefined(guards);
                }
                if (!symbol.domain().values().containsAll(required)) {
                    return Guard.FALSE;
                }
                long requiredMask = symbol.membershipMask(required);
                Guard result = Guard.FALSE;
                for (ExactCase exactCase : exactCases) {
                    if (exactCase.containsAll(requiredMask)) {
                        result = guards.or(result, exactCase.guard);
                    }
                }
                return result;
            }

            static Fact exact(Guard definedUnder,
                              Spec spec,
                              LatticeValue value,
                              Value<?> exactValue) {
                if (exactValue == null || !exactValue.isPresent()) {
                    return new Fact(definedUnder, value);
                }
                return new Fact(definedUnder, value, List.of(new ExactCase(exactValue, definedUnder, spec)));
            }

            static Fact merge(Fact left, Fact right, Guards guards) {
                Guard definedUnder = guards.or(left.definedUnder, right.definedUnder);
                LatticeValue value = LatticeValue.join(left.value, right.value);
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
                    if (current.sameValue(next)) {
                        merged.set(i, current.withGuard(guards.or(current.guard, next.guard)));
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
                private final Value<?> value;
                private final Guard guard;
                private final long scalarMask;
                private final long listMask;

                ExactCase(Value<?> value, Guard guard, Spec spec) {
                    this(value, guard, exactScalarMask(spec, value), exactListMask(spec, value));
                }

                private ExactCase(Value<?> value, Guard guard, long scalarMask, long listMask) {
                    this.value = value;
                    this.guard = guard;
                    this.scalarMask = scalarMask;
                    this.listMask = listMask;
                }

                Value<?> value() {
                    return value;
                }

                Guard guard() {
                    return guard;
                }

                long scalarMask() {
                    return scalarMask;
                }

                long listMask() {
                    return listMask;
                }

                ExactCase withGuard(Guard nextGuard) {
                    return guard.equals(nextGuard) ? this : new ExactCase(value, nextGuard, scalarMask, listMask);
                }

                boolean matches(Spec spec, Value<?> candidate) {
                    return exactValueEquals(spec, value, candidate, scalarMask, listMask);
                }

                boolean sameValue(ExactCase other) {
                    if (scalarMask != 0L || other.scalarMask != 0L) {
                        return scalarMask != 0L && scalarMask == other.scalarMask;
                    }
                    if (listMask != 0L || other.listMask != 0L) {
                        return listMask != 0L && listMask == other.listMask;
                    }
                    return Value.isEqual(value, other.value);
                }

                boolean containsAll(long requiredMask) {
                    return requiredMask == 0L ? value.type() == Value.Type.LIST : listMask != 0L && (requiredMask & ~listMask) == 0L;
                }

                boolean supportedExact(Spec spec) {
                    switch (spec.kind()) {
                        case FINITE_SCALAR:
                            return scalarMask != 0L;
                        case FINITE_MEMBERSHIP:
                            return value.type() == Value.Type.LIST && (listMask != 0L || value.getList().isEmpty());
                        case OPEN_TEXT:
                        default:
                            return true;
                    }
                }

                String scalarLiteral() {
                    return Value.scalarLiteral(value);
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
                    return sameValue(other)
                           && (guard == other.guard || guard.equals(other.guard));
                }

                @Override
                public int hashCode() {
                    return 31 * exactValueHash(value, scalarMask, listMask) + guard.hashCode();
                }
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

            Symbol symbol(int symbolId) {
                if (symbolId < 0 || symbolId >= symbols.size()) {
                    throw new IllegalArgumentException("Unknown symbol id: " + symbolId);
                }
                return symbols.get(symbolId);
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
                case FINITE_SCALAR:
                    return finiteScalar(spec.mask());
                case FINITE_MEMBERSHIP:
                    return membership(0L, spec.mask());
                default:
                    return TOP;
            }
        }

        static LatticeValue openText(String sample) {
            return new LatticeValue(Kind.OPEN_TEXT, sample, 0L, 0L, 0L);
        }

        static LatticeValue finiteScalar(long scalarMask) {
            if (scalarMask == 0L) {
                throw new IllegalArgumentException("Finite scalar mask must not be empty");
            }
            return new LatticeValue(Kind.FINITE_SCALAR, null, scalarMask, 0L, 0L);
        }

        static LatticeValue membership(long requiredMask, long possibleMask) {
            if ((requiredMask & ~possibleMask) != 0L) {
                throw new IllegalArgumentException("Membership required mask must be included in possible mask");
            }
            return new LatticeValue(Kind.MEMBERSHIP, null, 0L, requiredMask, possibleMask);
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
            if (kind != Kind.FINITE_SCALAR || !spec.scalar()) {
                throw new IllegalArgumentException("Scalar values requested for non-finite scalar");
            }
            return values(spec, scalarMask);
        }

        String singletonScalar(Spec spec) {
            if (kind != Kind.FINITE_SCALAR || Long.bitCount(scalarMask) != 1) {
                return null;
            }
            return spec.value(Long.numberOfTrailingZeros(scalarMask));
        }

        Set<String> possibleValues(Spec spec) {
            if (kind != Kind.MEMBERSHIP || !spec.membership()) {
                throw new IllegalArgumentException("Possible values requested for non-membership");
            }
            return values(spec, possibleMask);
        }

        private static Set<String> values(Spec spec, long mask) {
            Set<String> values = new TreeSet<>();
            long remaining = mask;
            while (remaining != 0L) {
                int ordinal = Long.numberOfTrailingZeros(remaining);
                values.add(spec.value(ordinal));
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
        private final int symbolId;
        private final long mask;
        private final String value;
        private final Expression expr;
        private final List<Residual> children;

        Residual(Kind kind, int symbolId, long mask, String value, Expression expr, List<Residual> children) {
            this.kind = kind;
            this.symbolId = symbolId;
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

        static Residual scalarEq(int symbolId, String value) {
            return new Residual(Kind.SCALAR_EQ, symbolId, 0L, value, null, List.of());
        }

        static Residual scalarIn(int symbolId, long mask) {
            if (mask == 0L) {
                return FALSE;
            }
            return new Residual(Kind.SCALAR_IN, symbolId, mask, null, null, List.of());
        }

        static Residual membershipContainsAll(int symbolId, long mask) {
            if (mask == 0L) {
                return TRUE;
            }
            return new Residual(Kind.MEMBERSHIP_CONTAINS_ALL, symbolId, mask, null, null, List.of());
        }

        static Residual not(Residual child) {
            if (child == TRUE) {
                return FALSE;
            }
            if (child == FALSE) {
                return TRUE;
            }
            if (child.kind == Kind.NOT) {
                return child.onlyChild();
            }
            return new Residual(Kind.NOT, -1, 0L, null, null, List.of(child));
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
                    return not(onlyChild().fold());
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

        Expression toExpression(IntFunction<String> keyResolver, Symbol.Table symbols) {
            switch (kind) {
                case TRUE:
                    return Expression.TRUE;
                case OPAQUE:
                    return expr;
                case DEFINED:
                    return Expression.create("${" + keyResolver.apply(symbolId) + "}");
                case SCALAR_EQ: {
                    Symbol symbol = symbols.symbol(symbolId);
                    String key = keyResolver.apply(symbolId);
                    if (symbol.domain().booleanLike()) {
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
                    Symbol symbol = symbols.symbol(symbolId);
                    String key = keyResolver.apply(symbolId);
                    if (mask == symbol.mask) {
                        return Expression.TRUE;
                    }
                    if (symbol.domain().booleanLike() && Long.bitCount(mask) == 1) {
                        String value = symbol.scalarValue(Long.numberOfTrailingZeros(mask));
                        return "true".equals(value) ? Expression.create("${" + key + "}") : Expression.create("!${" + key + "}");
                    }
                    long excludedMask = symbol.mask & ~mask;
                    if (Long.bitCount(excludedMask) == 1) {
                        return Expression.create("${" + key + "} != '" + symbol.scalarValue(Long.numberOfTrailingZeros(excludedMask)) + "'");
                    }
                    List<Expression> terms = new ArrayList<>();
                    long remaining = mask;
                    while (remaining != 0L) {
                        int ordinal = Long.numberOfTrailingZeros(remaining);
                        terms.add(Expression.create("${" + key + "} == '" + symbol.scalarValue(ordinal) + "'"));
                        remaining &= remaining - 1L;
                    }
                    return Expression.FALSE.or(terms);
                }
                case MEMBERSHIP_CONTAINS_ALL: {
                    Symbol symbol = symbols.symbol(symbolId);
                    String key = keyResolver.apply(symbolId);
                    List<Expression> terms = new ArrayList<>();
                    long remaining = mask;
                    while (remaining != 0L) {
                        int ordinal = Long.numberOfTrailingZeros(remaining);
                        terms.add(Expression.create("${" + key + "} contains '" + symbol.membershipItem(ordinal) + "'"));
                        remaining &= remaining - 1L;
                    }
                    return Expression.TRUE.and(terms);
                }
                case NOT:
                    return negate(onlyChild().toExpression(keyResolver, symbols));
                case AND:
                    return Expression.TRUE.and(Lists.map(children, child -> child.toExpression(keyResolver, symbols)));
                case OR:
                    return Expression.FALSE.or(Lists.map(children, child -> child.toExpression(keyResolver, symbols)));
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
                    && symbolId == other.symbolId
                    && mask == other.mask
                    && Objects.equals(value, other.value)
                    && Objects.equals(expr, other.expr)
                    && children.equals(other.children);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, symbolId, mask, value, expr, children);
        }

        private Residual onlyChild() {
            if (children.size() != 1) {
                throw new IllegalStateException(kind + " residual requires exactly one child");
            }
            return children.get(0);
        }

        private static Expression negate(Expression expr) {
            if (expr == Expression.TRUE) {
                return Expression.FALSE;
            }
            if (expr == Expression.FALSE) {
                return Expression.TRUE;
            }
            return Expression.create("!(" + expr.literal() + ")");
        }
    }

    static final class Guards {
        private static final int COMPACT_MAX_VARIABLES = 4;
        private static final int COMPACT_MAX_TOKENS = 16;
        private final Symbol.Table symbols;
        private final List<Decision> decisions = new ArrayList<>();
        private final Map<Decision, Integer> ids = new HashMap<>();
        private final Map<Guard, Map<Guard, Guard>> orCache = new HashMap<>();
        private final Map<Long, Boolean> subsetCache = new HashMap<>();

        Guards(Symbol.Table symbols) {
            this.symbols = symbols;
            register(Decision.FALSE);
            register(Decision.TRUE);
        }

        Guard and(Guard left, Guard right) {
            if (left.isFalse() || right.isFalse()) {
                return Guard.FALSE;
            }
            Decision decision = decision(left).and(decision(right), symbols);
            Residual leftResidual = left.residual();
            Residual rightResidual = right.residual();
            Residual residual = leftResidual.equals(rightResidual)
                    ? leftResidual
                    : Residual.and(List.of(leftResidual, rightResidual));
            return guard(decision, residual);
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
                Guard cached = cachedOr(left, right);
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
                result = guard(leftDecision.orWithoutSubsetChecks(rightDecision, symbols), left.residual());
            } else if (leftDecision.equals(rightDecision)) {
                result = guard(leftDecision, Residual.or(List.of(left.residual(), right.residual())), false);
            } else if (right.isPure() && subsetOf(left.id(), right.id())) {
                return right;
            } else if (left.isPure() && subsetOf(right.id(), left.id())) {
                return left;
            } else if (left.isPure() && right.isPure()) {
                result = guard(leftDecision.orWithoutSubsetChecks(rightDecision, symbols), Residual.TRUE);
            } else {
                result = residualGuard(rawExpression(left).or(rawExpression(right)));
            }
            if (cacheable && cacheable(result)) {
                cacheOr(left, right, result);
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
                return guard(Decision.TRUE.subtract(decision(guard), symbols), Residual.TRUE);
            }
            return residualGuard(negate(rawExpression(guard)));
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
                return leftDecision.subtract(rightDecision, symbols).isFalse();
            }
            return equivalentExpressions(rawExpression(left), rawExpression(right));
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
            return equivalentExpressions(rawExpression(left), rawExpression(right));
        }

        Guard eq(int symbolId, String value) {
            Symbol symbol = guardableScalar(symbolId);
            if (!symbol.domain().values().contains(value)) {
                return Guard.FALSE;
            }
            DecisionShape shape = new DecisionShape(symbolId, new ScalarShape(symbol.scalarMask(value)));
            return guard(Decision.of(List.of(shape), symbols), Residual.TRUE);
        }

        Guard contains(int symbolId, String value) {
            return containsAll(symbolId, Set.of(value));
        }

        Guard containsAll(int symbolId, Set<String> values) {
            Symbol symbol = symbols.symbol(symbolId);
            if (!symbol.guardable() || symbol.tainted() || symbol.domain().kind() != Spec.Kind.FINITE_MEMBERSHIP) {
                return Guard.FALSE;
            }
            if (!symbol.domain().values().containsAll(values)) {
                return Guard.FALSE;
            }
            MembershipShape membership = MembershipShape.required(symbol, values);
            DecisionShape shape = new DecisionShape(symbolId, membership);
            return guard(Decision.of(List.of(shape), symbols), Residual.TRUE);
        }

        Expression toExpression(Guard guard, Scope scope) {
            Expression supported = decision(guard).toExpression(symbolId -> scope.key(symbols.symbol(symbolId).name()), symbols);
            Expression residual = guard.residual().toExpression(symbolId -> scope.key(symbols.symbol(symbolId).name()), symbols);
            return toExpression(supported, residual);
        }

        private Expression toExpression(Expression supported, Expression residual) {
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
            if (symbol.domain().kind() == Spec.Kind.FINITE_SCALAR) {
                return symbol;
            }
            throw new IllegalArgumentException("Symbol is not scalar guardable: " + symbol.name());
        }

        private Guard guard(Decision decision, Residual residual) {
            return guard(decision, residual, true);
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
            return guard(Decision.TRUE, residual(expr));
        }

        private Residual residual(Expression expr) {
            Expression normalized = expr.fold();
            if (normalized == Expression.TRUE) {
                return Residual.TRUE;
            }
            if (normalized == Expression.FALSE) {
                return Residual.FALSE;
            }
            Deque<ResidualValue> stack = new ArrayDeque<>();
            for (Token token : normalized.tokens()) {
                if (token.isVariable()) {
                    String variable = token.variable();
                    int symbolId = symbols.findId(variable);
                    stack.push(ResidualValue.variable(variable, symbolId));
                    continue;
                }
                if (token.isOperand()) {
                    stack.push(ResidualValue.literal(token.operand()));
                    continue;
                }
                switch (token.operator()) {
                    case NOT:
                        stack.push(not(stack.pop()));
                        break;
                    case AND:
                        stack.push(and(stack.pop(), stack.pop()));
                        break;
                    case OR:
                        stack.push(or(stack.pop(), stack.pop()));
                        break;
                    case EQUAL:
                        stack.push(compare(true, stack.pop(), stack.pop()));
                        break;
                    case NOT_EQUAL:
                        stack.push(compare(false, stack.pop(), stack.pop()));
                        break;
                    case CONTAINS:
                        stack.push(contains(stack.pop(), stack.pop()));
                        break;
                    default:
                        return Residual.opaque(normalized);
                }
            }
            return stack.size() == 1 ? stack.pop().residual : Residual.opaque(normalized);
        }

        private ResidualValue not(ResidualValue value) {
            return ResidualValue.of(Residual.not(value.residual), negate(value.expr));
        }

        private ResidualValue and(ResidualValue right, ResidualValue left) {
            return ResidualValue.of(Residual.and(
                    List.of(left.residual, right.residual)),
                    left.expr.and(right.expr));
        }

        private ResidualValue or(ResidualValue right, ResidualValue left) {
            return ResidualValue.of(Residual.or(
                    List.of(left.residual, right.residual)),
                    left.expr.or(right.expr));
        }

        private ResidualValue compare(boolean equal, ResidualValue right, ResidualValue left) {
            Residual direct = compareResidual(left, right);
            if (direct == null) {
                direct = compareResidual(right, left);
            }
            Expression expr = combine(left.expr, equal ? "==" : "!=", right.expr);
            if (direct == null) {
                return ResidualValue.of(Residual.opaque(expr), expr);
            }
            return ResidualValue.of(equal ? direct : Residual.not(direct), expr);
        }

        private Residual compareResidual(ResidualValue symbolValue, ResidualValue literalValue) {
            if (symbolValue.symbolId < 0 || literalValue.literal == null) {
                return null;
            }
            Symbol symbol = symbols.symbol(symbolValue.symbolId);
            Spec domain = symbol.domain();
            if (domain.kind() != Spec.Kind.FINITE_SCALAR && domain.kind() != Spec.Kind.OPEN_TEXT) {
                return null;
            }
            String scalar = literalScalar(literalValue.literal);
            if (scalar == null) {
                return null;
            }
            if (domain.kind() == Spec.Kind.FINITE_SCALAR) {
                if (domain.values().contains(scalar)) {
                    return Residual.scalarEq(symbol.id(), scalar);
                }
                return Residual.FALSE;
            }
            return Residual.scalarEq(symbol.id(), scalar);
        }

        private ResidualValue contains(ResidualValue right, ResidualValue left) {
            Residual direct = containsResidual(left, right);
            if (direct == null) {
                direct = scalarInResidual(right, left);
            }
            Expression expr = combine(left.expr, "contains", right.expr);
            if (direct == null) {
                return ResidualValue.of(Residual.opaque(expr), expr);
            }
            return ResidualValue.of(direct, expr);
        }

        private Residual containsResidual(ResidualValue symbolValue, ResidualValue literalValue) {
            Value<?> literal = literalValue.literal;
            if (symbolValue.symbolId < 0 || literal == null) {
                return null;
            }
            Symbol symbol = symbols.symbol(symbolValue.symbolId);
            Spec domain = symbol.domain();
            if (domain.kind() != Spec.Kind.FINITE_MEMBERSHIP) {
                return null;
            }
            if (literal.type() == Value.Type.STRING) {
                String item = literal.getString();
                return domain.values().contains(item)
                        ? Residual.membershipContainsAll(symbol.id(), domain.mask(item))
                        : Residual.FALSE;
            }
            if (literal.type() == Value.Type.LIST) {
                Set<String> required = new TreeSet<>(literal.getList());
                return domain.values().containsAll(required)
                        ? Residual.membershipContainsAll(symbol.id(), domain.mask(required))
                        : Residual.FALSE;
            }
            return null;
        }

        private Residual scalarInResidual(ResidualValue symbolValue, ResidualValue literalValue) {
            Value<?> literal = literalValue.literal;
            if (symbolValue.symbolId < 0 || literal == null || literal.type() != Value.Type.LIST) {
                return null;
            }
            Symbol symbol = symbols.symbol(symbolValue.symbolId);
            Spec domain = symbol.domain();
            if (domain.kind() != Spec.Kind.FINITE_SCALAR) {
                return null;
            }
            Set<String> allowed = new TreeSet<>(literal.getList());
            allowed.retainAll(domain.values());
            if (allowed.isEmpty()) {
                return Residual.FALSE;
            }
            if (allowed.size() == domain.values().size()) {
                return Residual.TRUE;
            }
            return Residual.scalarIn(symbol.id(), domain.mask(allowed));
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

        private void cacheOr(Guard left, Guard right, Guard result) {
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
            boolean result = decision(leftId).subsetOf(decision(rightId));
            subsetCache.put(key, result);
            return result;
        }

        private Expression rawExpression(Guard guard) {
            Expression supported = decision(guard).toExpression(symbolId -> symbols.symbol(symbolId).name(), symbols);
            Expression residual = guard.residual().toExpression(symbolId -> symbols.symbol(symbolId).name(), symbols);
            return toExpression(supported, residual);
        }

        private static Expression negate(Expression expr) {
            if (expr == Expression.TRUE) {
                return Expression.FALSE;
            }
            if (expr == Expression.FALSE) {
                return Expression.TRUE;
            }
            return Expression.create("!(" + expr.literal() + ")");
        }

        private static Expression combine(Expression left, String operator, Expression right) {
            return Expression.create("(" + left.literal() + ") " + operator + " (" + right.literal() + ")");
        }

        private static String literalScalar(Value<?> value) {
            switch (value.type()) {
                case BOOLEAN:
                    return String.valueOf(value.getBoolean());
                case STRING:
                case DYNAMIC:
                    return value.getString();
                default:
                    return null;
            }
        }

        private static boolean equivalentExpressions(Expression left, Expression right) {
            return left.equals(right) || left.fold().equals(right.fold());
        }

        private boolean cacheable(Guard guard) {
            Residual residual = guard.residual();
            if (residual.isTrue() || residual.isFalse()) {
                return true;
            }
            Expression expr = residual.toExpression(symbolId -> symbols.symbol(symbolId).name(), symbols);
            return expr.tokens().size() <= COMPACT_MAX_TOKENS && expr.variableCountAtMost(COMPACT_MAX_VARIABLES);
        }

        private static final class ResidualValue {
            private final Residual residual;
            private final int symbolId;
            private final Value<?> literal;
            private final Expression expr;

            private ResidualValue(Residual residual, int symbolId, Value<?> literal, Expression expr) {
                this.residual = residual;
                this.symbolId = symbolId;
                this.literal = literal;
                this.expr = expr;
            }

            static ResidualValue of(Residual residual, Expression expr) {
                return new ResidualValue(residual, -1, null, expr);
            }

            static ResidualValue variable(String name, int symbolId) {
                Expression expr = Expression.create("${" + name + "}");
                return new ResidualValue(Residual.opaque(expr), symbolId, null, expr);
            }

            static ResidualValue literal(Value<?> literal) {
                Expression expr = new Expression(List.of(Token.of(literal)), true);
                return new ResidualValue(Residual.opaque(expr), -1, literal, expr);
            }
        }
    }

    private static long exactScalarMask(Spec spec, Value<?> value) {
        if (spec.kind() != Spec.Kind.FINITE_SCALAR) {
            return 0L;
        }
        String literal = Value.scalarLiteral(value);
        if (literal == null) {
            return 0L;
        }
        Integer ordinal = spec.ordinals.get(literal);
        return ordinal == null ? 0L : 1L << ordinal;
    }

    private static long exactListMask(Spec spec, Value<?> value) {
        if (spec.kind() != Spec.Kind.FINITE_MEMBERSHIP || value == null || value.type() != Value.Type.LIST) {
            return 0L;
        }
        long mask = 0L;
        for (String item : new TreeSet<>(value.getList())) {
            Integer ordinal = spec.ordinals.get(item);
            if (ordinal == null) {
                return 0L;
            }
            mask |= 1L << ordinal;
        }
        return mask;
    }

    private static boolean exactValueEquals(Spec spec, Value<?> left, Value<?> right, long leftScalarMask, long leftListMask) {
        long rightScalarMask = exactScalarMask(spec, right);
        if (leftScalarMask != 0L || rightScalarMask != 0L) {
            return leftScalarMask != 0L && leftScalarMask == rightScalarMask;
        }
        long rightListMask = exactListMask(spec, right);
        if (leftListMask != 0L || rightListMask != 0L) {
            return leftListMask != 0L && leftListMask == rightListMask;
        }
        return Value.isEqual(left, right);
    }

    static boolean sameExactValue(Spec spec, Value<?> left, Value<?> right) {
        return exactValueEquals(spec, left, right, exactScalarMask(spec, left), exactListMask(spec, left));
    }

    private static int exactValueHash(Value<?> value, long scalarMask, long listMask) {
        if (scalarMask != 0L) {
            return Long.hashCode(scalarMask);
        }
        if (listMask != 0L) {
            return Long.hashCode(listMask);
        }
        return Value.hash(value);
    }

    private static final class Decision {
        private static final Decision FALSE = new Decision(List.of());
        private static final Decision TRUE = new Decision(List.of(new DecisionShape()));
        private static final int TRY_MERGE_MAX_SHAPES = 32;

        private final List<DecisionShape> shapes;

        Decision(List<DecisionShape> shapes) {
            this.shapes = shapes;
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
                        if (left.subsetOf(right)) {
                            normalized.remove(i);
                            changed = true;
                            break;
                        }
                        if (right.subsetOf(left)) {
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
            normalized.sort(Comparator.comparing(shape -> shape.literal(symbols)));
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
            if (this == TRUE) {
                return other;
            }
            if (other == TRUE) {
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
                        if (candidate.subsetOf(existing)) {
                            candidate = null;
                            break;
                        }
                        if (existing.subsetOf(candidate)) {
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
            result.sort(Comparator.comparing(shape -> shape.literal(symbols)));
            return new Decision(result);
        }

        Decision subtract(Decision other, Symbol.Table symbols) {
            if (isFalse() || other.isFalse()) {
                return this;
            }
            if (other == TRUE) {
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
            Expression expr = Expression.FALSE;
            for (DecisionShape shape : shapes) {
                expr = expr.or(shape.toExpression(keyResolver, symbols));
            }
            return expr;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Decision && shapes.equals(((Decision) o).shapes);
        }

        @Override
        public int hashCode() {
            return shapes.hashCode();
        }

        boolean subsetOf(Decision other) {
            if (equals(other) || isFalse() || other == TRUE) {
                return true;
            }
            if (other.isFalse()) {
                return false;
            }
            for (DecisionShape left : shapes) {
                boolean covered = false;
                for (DecisionShape right : other.shapes) {
                    if (left.subsetOf(right)) {
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
    }

    private static final class DecisionShape {
        private static final int[] NO_SYMBOL_IDS = new int[0];
        private static final ScalarShape[] NO_SCALARS = new ScalarShape[0];
        private static final MembershipShape[] NO_MEMBERSHIPS = new MembershipShape[0];

        private final int[] scalarIds;
        private final ScalarShape[] scalarShapes;
        private final int[] membershipIds;
        private final MembershipShape[] membershipShapes;
        private final int hashCode;

        DecisionShape() {
            this(NO_SYMBOL_IDS, NO_SCALARS, NO_SYMBOL_IDS, NO_MEMBERSHIPS);
        }

        DecisionShape(int symbolId, ScalarShape scalarShape) {
            this(new int[] {symbolId}, new ScalarShape[] {scalarShape}, NO_SYMBOL_IDS, NO_MEMBERSHIPS);
        }

        DecisionShape(int symbolId, MembershipShape membershipShape) {
            this(NO_SYMBOL_IDS, NO_SCALARS, new int[] {symbolId}, new MembershipShape[] {membershipShape});
        }

        DecisionShape(int[] scalarIds, ScalarShape[] scalarShapes, int[] membershipIds, MembershipShape[] membershipShapes) {
            this.scalarIds = scalarIds;
            this.scalarShapes = scalarShapes;
            this.membershipIds = membershipIds;
            this.membershipShapes = membershipShapes;
            this.hashCode = 31 * entriesHash(scalarIds, scalarShapes) + entriesHash(membershipIds, membershipShapes);
        }

        boolean isTrue() {
            return scalarShapes.length == 0 && membershipShapes.length == 0;
        }

        DecisionShape normalized(Symbol.Table symbols) {
            int nextScalarCount = scalarShapes.length;
            int nextMembershipCount = membershipShapes.length;
            boolean changed = false;
            for (int i = 0; i < scalarShapes.length; i++) {
                if (scalarShapes[i].isFull(symbols.symbol(scalarIds[i]))) {
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
                if (!scalarShapes[i].isFull(symbols.symbol(scalarIds[i]))) {
                    nextScalarIds[scalarIndex] = scalarIds[i];
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
                    nextMembershipIds[membershipIndex] = membershipIds[i];
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
                           && scalarIds[leftScalar] < other.scalarIds[rightScalar]) {
                    nextScalars.add(scalarIds[leftScalar], scalarShapes[leftScalar]);
                    leftScalar++;
                } else if (leftScalar == scalarShapes.length
                        || other.scalarIds[rightScalar] < scalarIds[leftScalar]) {
                    nextScalars.add(other.scalarIds[rightScalar], other.scalarShapes[rightScalar]);
                    rightScalar++;
                } else {
                    int symbolId = scalarIds[leftScalar];
                    ScalarShape intersection = scalarShapes[leftScalar].intersect(other.scalarShapes[rightScalar]);
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
                           && membershipIds[leftMembership] < other.membershipIds[rightMembership]) {
                    nextMemberships.add(membershipIds[leftMembership], membershipShapes[leftMembership]);
                    leftMembership++;
                } else if (leftMembership == membershipShapes.length
                        || other.membershipIds[rightMembership] < membershipIds[leftMembership]) {
                    nextMemberships.add(other.membershipIds[rightMembership], other.membershipShapes[rightMembership]);
                    rightMembership++;
                } else {
                    int symbolId = membershipIds[leftMembership];
                    MembershipShape intersection = membershipShapes[leftMembership]
                            .intersect(other.membershipShapes[rightMembership], symbols.symbol(symbolId));
                    if (intersection == null) {
                        return null;
                    }
                    nextMemberships.add(symbolId, intersection);
                    leftMembership++;
                    rightMembership++;
                }
            }
            return new DecisionShape(
                    trim(nextScalars.symbolIds, nextScalars.size),
                    trim(nextScalars.shapes, nextScalars.size, NO_SCALARS),
                    trim(nextMemberships.symbolIds, nextMemberships.size),
                    trim(nextMemberships.shapes, nextMemberships.size, NO_MEMBERSHIPS));
        }

        boolean subsetOf(DecisionShape other) {
            if (this == other) {
                return true;
            }
            if (scalarShapes.length < other.scalarShapes.length || membershipShapes.length < other.membershipShapes.length) {
                return false;
            }
            int leftScalar = 0;
            for (int rightScalar = 0; rightScalar < other.scalarShapes.length; rightScalar++) {
                int symbolId = other.scalarIds[rightScalar];
                while (leftScalar < scalarShapes.length && scalarIds[leftScalar] < symbolId) {
                    leftScalar++;
                }
                if (leftScalar == scalarShapes.length
                    || scalarIds[leftScalar] != symbolId
                    || !scalarShapes[leftScalar].subsetOf(other.scalarShapes[rightScalar])) {
                    return false;
                }
            }
            int leftMembership = 0;
            for (int rightMembership = 0; rightMembership < other.membershipShapes.length; rightMembership++) {
                int symbolId = other.membershipIds[rightMembership];
                while (leftMembership < membershipShapes.length && membershipIds[leftMembership] < symbolId) {
                    leftMembership++;
                }
                if (leftMembership == membershipShapes.length || membershipIds[leftMembership] != symbolId) {
                    return false;
                }
                if (!membershipShapes[leftMembership].subsetOf(other.membershipShapes[rightMembership])) {
                    return false;
                }
            }
            return true;
        }

        DecisionShape tryMerge(DecisionShape other, Symbol.Table symbols) {
            if (!Arrays.equals(membershipIds, other.membershipIds)
                    || !Arrays.equals(membershipShapes, other.membershipShapes)) {
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
                           && scalarIds[leftScalar] < other.scalarIds[rightScalar]) {
                    symbolId = scalarIds[leftScalar];
                    leftShape = scalarShapes[leftScalar];
                    rightShape = null;
                    leftScalar++;
                } else if (leftScalar == scalarShapes.length
                        || other.scalarIds[rightScalar] < scalarIds[leftScalar]) {
                    symbolId = other.scalarIds[rightScalar];
                    leftShape = null;
                    rightShape = other.scalarShapes[rightScalar];
                    rightScalar++;
                } else {
                    symbolId = scalarIds[leftScalar];
                    leftShape = scalarShapes[leftScalar];
                    rightShape = other.scalarShapes[rightScalar];
                    leftScalar++;
                    rightScalar++;
                }
                Symbol symbol = symbols.symbol(symbolId);
                long fullMask = symbol.mask;
                long leftMask = leftShape == null ? fullMask : leftShape.allowedMask;
                long rightMask = rightShape == null ? fullMask : rightShape.allowedMask;
                if (leftMask != rightMask) {
                    if (diffSymbolId != null) {
                        return null;
                    }
                    diffSymbolId = symbolId;
                    long unionMask = leftMask | rightMask;
                    if (unionMask != fullMask) {
                        nextScalars.add(symbolId, new ScalarShape(unionMask));
                    }
                } else if (leftMask != fullMask) {
                    nextScalars.add(symbolId, leftShape == null ? rightShape : leftShape);
                }
            }
            if (diffSymbolId != null) {
                int[] ids = trim(nextScalars.symbolIds, nextScalars.size);
                ScalarShape[] shapes = trim(nextScalars.shapes, nextScalars.size, NO_SCALARS);
                return new DecisionShape(ids, shapes, membershipIds, membershipShapes);
            }
            return null;
        }

        List<DecisionShape> subtract(DecisionShape other, Symbol.Table symbols) {
            DecisionShape intersection = intersect(other, symbols);
            if (intersection == null) {
                return List.of(this);
            }
            if (subsetOf(other)) {
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
            Expression expr = Expression.TRUE;
            for (int i = 0; i < scalarShapes.length; i++) {
                Symbol symbol = symbols.symbol(scalarIds[i]);
                expr = expr.and(scalarShapes[i].toExpression(keyResolver.apply(scalarIds[i]), symbol));
            }
            for (int i = 0; i < membershipShapes.length; i++) {
                Symbol symbol = symbols.symbol(membershipIds[i]);
                expr = expr.and(membershipShapes[i].toExpression(keyResolver.apply(membershipIds[i]), symbol));
            }
            return expr;
        }

        String literal(Symbol.Table symbols) {
            StringBuilder builder = new StringBuilder();
            builder.append('{');
            for (int i = 0; i < scalarShapes.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(scalarIds[i]).append(':');
                scalarShapes[i].appendLiteral(builder, symbols.symbol(scalarIds[i]));
            }
            builder.append("}|{");
            for (int i = 0; i < membershipShapes.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(membershipIds[i]).append(':');
                membershipShapes[i].appendLiteral(builder, symbols.symbol(membershipIds[i]));
            }
            builder.append('}');
            return builder.toString();
        }

        DecisionPartition splitAgainst(DecisionShape other, Symbol.Table symbols) {
            int leftScalar = 0;
            for (int rightScalar = 0; rightScalar < other.scalarShapes.length; rightScalar++) {
                int symbolId = other.scalarIds[rightScalar];
                while (leftScalar < scalarShapes.length && scalarIds[leftScalar] < symbolId) {
                    leftScalar++;
                }
                Symbol symbol = symbols.symbol(symbolId);
                long leftMask = leftScalar < scalarShapes.length && scalarIds[leftScalar] == symbolId
                        ? scalarShapes[leftScalar].allowedMask
                        : symbol.mask;
                long rightMask = other.scalarShapes[rightScalar].allowedMask;
                long outsideMask = leftMask & ~rightMask;
                if (outsideMask != 0L) {
                    long overlapMask = leftMask & rightMask;
                    return new DecisionPartition(
                            withScalar(symbolId, new ScalarShape(outsideMask), symbol),
                            withScalar(symbolId, new ScalarShape(overlapMask), symbol));
                }
            }
            int leftMembership = 0;
            for (int rightMembership = 0; rightMembership < other.membershipShapes.length; rightMembership++) {
                int symbolId = other.membershipIds[rightMembership];
                while (leftMembership < membershipShapes.length && membershipIds[leftMembership] < symbolId) {
                    leftMembership++;
                }
                Symbol symbol = symbols.symbol(symbolId);
                MembershipShape left = leftMembership < membershipShapes.length && membershipIds[leftMembership] == symbolId
                        ? membershipShapes[leftMembership]
                        : MembershipShape.EMPTY;
                MembershipShape right = other.membershipShapes[rightMembership];
                long knownMask = left.requiredMask | left.forbiddenMask;
                long unresolvedRequiredMask = right.requiredMask & ~knownMask;
                if (unresolvedRequiredMask != 0L) {
                    String item = symbol.membershipItem(Long.numberOfTrailingZeros(unresolvedRequiredMask));
                    return new DecisionPartition(
                            withMembershipForbidden(symbolId, item, symbol),
                            withMembershipRequired(symbolId, item, symbol));
                }
                long unresolvedForbiddenMask = right.forbiddenMask & ~knownMask;
                if (unresolvedForbiddenMask != 0L) {
                    String item = symbol.membershipItem(Long.numberOfTrailingZeros(unresolvedForbiddenMask));
                    return new DecisionPartition(
                            withMembershipRequired(symbolId, item, symbol),
                            withMembershipForbidden(symbolId, item, symbol));
                }
            }
            return null;
        }

        DecisionShape withScalar(int symbolId, ScalarShape nextShape, Symbol symbol) {
            if (nextShape.isFull(symbol)) {
                int index = Arrays.binarySearch(scalarIds, symbolId);
                if (index < 0) {
                    return this;
                }
                if (scalarShapes.length == 1) {
                    return new DecisionShape(NO_SYMBOL_IDS, NO_SCALARS, membershipIds, membershipShapes);
                }
                return new DecisionShape(
                        removeIndex(scalarIds, index),
                        removeIndex(scalarShapes, index),
                        membershipIds,
                        membershipShapes);
            }
            int index = Arrays.binarySearch(scalarIds, symbolId);
            if (index >= 0) {
                if (scalarShapes[index].equals(nextShape)) {
                    return this;
                }
                ScalarShape[] nextScalars = Arrays.copyOf(scalarShapes, scalarShapes.length);
                nextScalars[index] = nextShape;
                return new DecisionShape(scalarIds, nextScalars, membershipIds, membershipShapes);
            }
            int insertion = -index - 1;
            int[] nextScalarIds = insert(scalarIds, insertion, symbolId);
            ScalarShape[] nextScalars = insert(scalarShapes, insertion, nextShape);
            return new DecisionShape(nextScalarIds, nextScalars, membershipIds, membershipShapes);
        }

        DecisionShape withMembershipRequired(int symbolId, String item, Symbol symbol) {
            int index = Arrays.binarySearch(membershipIds, symbolId);
            MembershipShape current = index < 0 ? MembershipShape.EMPTY : membershipShapes[index];
            MembershipShape nextShape = current.withRequired(item, symbol);
            if (nextShape.equals(current)) {
                return this;
            }
            return withMembership(index, symbolId, nextShape);
        }

        DecisionShape withMembershipForbidden(int symbolId, String item, Symbol symbol) {
            int index = Arrays.binarySearch(membershipIds, symbolId);
            MembershipShape current = index < 0 ? MembershipShape.EMPTY : membershipShapes[index];
            MembershipShape nextShape = current.withForbidden(item, symbol);
            if (nextShape.equals(current)) {
                return this;
            }
            return withMembership(index, symbolId, nextShape);
        }

        DecisionShape withMembership(int index, int symbolId, MembershipShape nextShape) {
            if (nextShape.isEmpty()) {
                if (index < 0) {
                    return this;
                }
                if (membershipShapes.length == 1) {
                    return new DecisionShape(scalarIds, scalarShapes, NO_SYMBOL_IDS, NO_MEMBERSHIPS);
                }
                int[] ids = removeIndex(membershipIds, index);
                MembershipShape[] shapes = removeIndex(membershipShapes, index);
                return new DecisionShape(scalarIds, scalarShapes, ids, shapes);
            }
            if (index >= 0) {
                MembershipShape[] shapes = Arrays.copyOf(membershipShapes, membershipShapes.length);
                shapes[index] = nextShape;
                return new DecisionShape(scalarIds, scalarShapes, membershipIds, shapes);
            }
            int insertion = -index - 1;
            int[] ids = insert(membershipIds, insertion, symbolId);
            MembershipShape[] shapes = insert(membershipShapes, insertion, nextShape);
            return new DecisionShape(scalarIds, scalarShapes, ids, shapes);
        }

        static int entriesHash(int[] symbolIds, Object[] values) {
            int result = 1;
            for (int i = 0; i < symbolIds.length; i++) {
                result = 31 * result + Integer.hashCode(symbolIds[i]);
                result = 31 * result + values[i].hashCode();
            }
            return result;
        }

        static int[] removeIndex(int[] values, int index) {
            int[] next = new int[values.length - 1];
            System.arraycopy(values, 0, next, 0, index);
            System.arraycopy(values, index + 1, next, index, values.length - index - 1);
            return next;
        }

        static <T> T[] removeIndex(T[] values, int index) {
            T[] next = Arrays.copyOf(values, values.length - 1);
            System.arraycopy(values, index + 1, next, index, values.length - index - 1);
            return next;
        }

        static int[] insert(int[] values, int insertion, int value) {
            int[] next = new int[values.length + 1];
            System.arraycopy(values, 0, next, 0, insertion);
            next[insertion] = value;
            System.arraycopy(values, insertion, next, insertion + 1, values.length - insertion);
            return next;
        }

        static <T> T[] insert(T[] values, int insertion, T value) {
            T[] next = Arrays.copyOf(values, values.length + 1);
            System.arraycopy(next, insertion, next, insertion + 1, values.length - insertion);
            next[insertion] = value;
            return next;
        }

        static int[] trim(int[] values, int size) {
            return size == 0 ? DecisionShape.NO_SYMBOL_IDS : size == values.length ? values : Arrays.copyOf(values, size);
        }

        static <T> T[] trim(T[] values, int size, T[] empty) {
            return size == 0 ? empty : size == values.length ? values : Arrays.copyOf(values, size);
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
                    && Arrays.equals(scalarIds, other.scalarIds)
                    && Arrays.equals(scalarShapes, other.scalarShapes)
                    && Arrays.equals(membershipIds, other.membershipIds)
                    && Arrays.equals(membershipShapes, other.membershipShapes);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private static final class ScalarBuilder {
            private final int[] symbolIds;
            private final ScalarShape[] shapes;
            private int size;

            ScalarBuilder(int capacity) {
                this.symbolIds = capacity == 0 ? NO_SYMBOL_IDS : new int[capacity];
                this.shapes = capacity == 0 ? NO_SCALARS : new ScalarShape[capacity];
            }

            void add(int symbolId, ScalarShape shape) {
                symbolIds[size] = symbolId;
                shapes[size] = shape;
                size++;
            }
        }

        private static final class MembershipBuilder {
            private final int[] symbolIds;
            private final MembershipShape[] shapes;
            private int size;

            MembershipBuilder(int capacity) {
                this.symbolIds = capacity == 0 ? NO_SYMBOL_IDS : new int[capacity];
                this.shapes = capacity == 0 ? NO_MEMBERSHIPS : new MembershipShape[capacity];
            }

            void add(int symbolId, MembershipShape shape) {
                symbolIds[size] = symbolId;
                shapes[size] = shape;
                size++;
            }
        }
    }

    private static final class ScalarShape {
        private final long allowedMask;

        ScalarShape(long allowedMask) {
            this.allowedMask = allowedMask;
        }

        ScalarShape intersect(ScalarShape other) {
            if (this == other || equals(other)) {
                return this;
            }
            long intersectionMask = allowedMask & other.allowedMask;
            if (intersectionMask == 0L) {
                return null;
            }
            if (intersectionMask == allowedMask) {
                return this;
            }
            if (intersectionMask == other.allowedMask) {
                return other;
            }
            return new ScalarShape(intersectionMask);
        }

        boolean subsetOf(ScalarShape other) {
            return this == other || (allowedMask & ~other.allowedMask) == 0L;
        }

        boolean isFull(Symbol symbol) {
            if (!symbol.scalar) {
                throw new IllegalStateException("Scalar shape maskability mismatch");
            }
            return allowedMask == symbol.mask;
        }

        Expression toExpression(String key, Symbol symbol) {
            if (symbol.domain().booleanLike()) {
                if (isSingleton(symbol, "true")) {
                    return Expression.create("${" + key + "}");
                }
                if (isSingleton(symbol, "false")) {
                    return Expression.create("!${" + key + "}");
                }
            }
            if (isFull(symbol)) {
                return Expression.TRUE;
            }
            long excludedMask = symbol.mask & ~allowedMask;
            if (Long.bitCount(excludedMask) == 1) {
                String excluded = symbol.scalarValue(Long.numberOfTrailingZeros(excludedMask));
                return Expression.create("${" + key + "} != '" + excluded + "'");
            }
            Expression expr = Expression.FALSE;
            long remaining = allowedMask;
            while (remaining != 0L) {
                int ordinal = Long.numberOfTrailingZeros(remaining);
                String str = symbol.scalarValue(ordinal);
                expr = expr.or(Expression.create("${" + key + "} == '" + str + "'"));
                remaining &= remaining - 1L;
            }
            return expr;
        }

        private boolean isSingleton(Symbol symbol, String value) {
            return allowedMask == symbol.scalarMask(value);
        }

        void appendLiteral(StringBuilder builder, Symbol symbol) {
            builder.append('[');
            boolean first = true;
            long remaining = allowedMask;
            while (remaining != 0L) {
                if (!first) {
                    builder.append(", ");
                }
                int ordinal = Long.numberOfTrailingZeros(remaining);
                builder.append(symbol.scalarValue(ordinal));
                remaining &= remaining - 1L;
                first = false;
            }
            builder.append(']');
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ScalarShape && allowedMask == ((ScalarShape) o).allowedMask;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(allowedMask);
        }
    }

    private static final class MembershipShape {
        private static final MembershipShape EMPTY = new MembershipShape(0L, 0L);
        private final long requiredMask;
        private final long forbiddenMask;

        private MembershipShape(long requiredMask, long forbiddenMask) {
            this.requiredMask = requiredMask;
            this.forbiddenMask = forbiddenMask;
        }

        MembershipShape(Symbol symbol, long requiredMask, long forbiddenMask) {
            if (!symbol.member) {
                throw new IllegalArgumentException("Masked membership constraint requires a maskable symbol");
            }
            long fullMask = symbol.mask;
            if ((requiredMask & ~fullMask) != 0L || (forbiddenMask & ~fullMask) != 0L) {
                throw new IllegalArgumentException("Membership mask must stay within the symbol domain");
            }
            if ((requiredMask & forbiddenMask) != 0L) {
                throw new IllegalArgumentException("Membership required and forbidden items must be disjoint");
            }
            this.requiredMask = requiredMask;
            this.forbiddenMask = forbiddenMask;
        }

        static MembershipShape required(Symbol symbol, Set<String> items) {
            return items.isEmpty() ? EMPTY : new MembershipShape(symbol, symbol.membershipMask(items), 0L);
        }

        boolean isEmpty() {
            return requiredMask == 0L && forbiddenMask == 0L;
        }

        MembershipShape intersect(MembershipShape other, Symbol symbol) {
            if (isEmpty()) {
                return other;
            }
            if (other.isEmpty()) {
                return this;
            }
            if (equals(other)) {
                return this;
            }
            if ((requiredMask & other.forbiddenMask) != 0L || (forbiddenMask & other.requiredMask) != 0L) {
                return null;
            }
            long nextRequiredMask = requiredMask | other.requiredMask;
            long nextForbiddenMask = forbiddenMask | other.forbiddenMask;
            if (nextRequiredMask == requiredMask && nextForbiddenMask == forbiddenMask) {
                return this;
            }
            if (nextRequiredMask == other.requiredMask && nextForbiddenMask == other.forbiddenMask) {
                return other;
            }
            return new MembershipShape(symbol, nextRequiredMask, nextForbiddenMask);
        }

        boolean subsetOf(MembershipShape other) {
            if (this == other || equals(other) || other.isEmpty()) {
                return true;
            }
            if (isEmpty()) {
                return false;
            }
            return (other.requiredMask & ~requiredMask) == 0L && (other.forbiddenMask & ~forbiddenMask) == 0L;
        }

        MembershipShape withRequired(String item, Symbol symbol) {
            long itemMask = symbol.membershipMask(item);
            if ((requiredMask & itemMask) != 0L) {
                return this;
            }
            return new MembershipShape(symbol, requiredMask | itemMask, forbiddenMask);
        }

        MembershipShape withForbidden(String item, Symbol symbol) {
            long itemMask = symbol.membershipMask(item);
            if ((forbiddenMask & itemMask) != 0L) {
                return this;
            }
            return new MembershipShape(symbol, requiredMask, forbiddenMask | itemMask);
        }

        Expression toExpression(String key, Symbol symbol) {
            Expression expr = Expression.TRUE;
            long remaining = requiredMask;
            while (remaining != 0L) {
                int ordinal = Long.numberOfTrailingZeros(remaining);
                String str = symbol.membershipItem(ordinal);
                expr = expr.and(Expression.create("${" + key + "} contains '" + str + "'"));
                remaining &= remaining - 1L;
            }
            remaining = forbiddenMask;
            while (remaining != 0L) {
                int ordinal = Long.numberOfTrailingZeros(remaining);
                String str = symbol.membershipItem(ordinal);
                expr = expr.and(Expression.create("!(${" + key + "} contains '" + str + "')"));
                remaining &= remaining - 1L;
            }
            return expr;
        }

        void appendLiteral(StringBuilder builder, Symbol symbol) {
            appendItems(builder, symbol, requiredMask);
            builder.append('/');
            appendItems(builder, symbol, forbiddenMask);
        }

        private static void appendItems(StringBuilder builder, Symbol symbol, long mask) {
            builder.append('[');
            long remaining = mask;
            boolean first = true;
            while (remaining != 0L) {
                if (!first) {
                    builder.append(", ");
                }
                int ordinal = Long.numberOfTrailingZeros(remaining);
                builder.append(symbol.membershipItem(ordinal));
                remaining &= remaining - 1L;
                first = false;
            }
            builder.append(']');
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MembershipShape)) {
                return false;
            }
            MembershipShape other = (MembershipShape) o;
            return requiredMask == other.requiredMask && forbiddenMask == other.forbiddenMask;
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(requiredMask) + Long.hashCode(forbiddenMask);
        }
    }

    private static final class DecisionPartition {
        private final DecisionShape outside;
        private final DecisionShape overlap;

        DecisionPartition(DecisionShape outside, DecisionShape overlap) {
            this.outside = outside;
            this.overlap = overlap;
        }
    }
}
