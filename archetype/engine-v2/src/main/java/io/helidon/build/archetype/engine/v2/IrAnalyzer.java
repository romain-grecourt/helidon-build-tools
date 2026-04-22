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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import io.helidon.build.archetype.engine.v2.Domain.Fact;
import io.helidon.build.archetype.engine.v2.Domain.Guard;
import io.helidon.build.archetype.engine.v2.Domain.GuardedValue;
import io.helidon.build.archetype.engine.v2.Domain.LatticeValue;
import io.helidon.build.archetype.engine.v2.Domain.Spec;
import io.helidon.build.archetype.engine.v2.Domain.Symbol;
import io.helidon.build.archetype.engine.v2.Expression.Token;
import io.helidon.build.archetype.engine.v2.Ir.Block;
import io.helidon.build.archetype.engine.v2.Ir.Op;

final class IrAnalyzer {
    private static final int EXACT_CASE_MAX = 16;
    private static final int COVERAGE_MAX = 16;

    private final Ir ir;
    private final BlockAnalysis[] blocks;
    private final OpAnalysis[] ops;

    IrAnalyzer(Ir ir) {
        this.ir = ir;
        Block[] blocks = ir.blocks();
        this.blocks = new BlockAnalysis[blocks.length];
        for (int i = 0; i < this.blocks.length; i++) {
            this.blocks[i] = new BlockAnalysis();
        }
        Op[] ops = ir.ops();
        this.ops = new OpAnalysis[ops.length];
        for (int i = 0; i < this.ops.length; i++) {
            this.ops[i] = new OpAnalysis();
        }
    }

    void analyze() {
        analyzeControls();
        analyzeFacts();
        for (BlockAnalysis block : blocks) {
            block.state = new IrState(block.guard, env(block.facts));
        }
        for (OpAnalysis op : ops) {
            op.beforeState = new IrState(op.beforeGuard, env(op.beforeFacts));
            op.afterState = new IrState(op.beforeGuard, env(op.afterFacts));
        }
    }

    IrState entryState(int blockId) {
        return blocks[blockId].state;
    }

    IrState beforeState(int opId) {
        return ops[opId].beforeState;
    }

    IrState afterState(int opId) {
        return ops[opId].afterState;
    }

    private void analyzeControls() {
        Guard[] guards = new Guard[ir.controls().length];
        guards[0] = Guard.TRUE;
        BitSet seen = new BitSet(ir.blocks().length);
        seen.set(0);
        Deque<Integer> pending = new ArrayDeque<>(ir.controls().length);
        ir.visitDataflowBlocks(blockId -> {
            int current = ir.blocks()[blockId].controlId();
            Guard entry = guards[current];
            if (entry == null) {
                pending.clear();
                while (true) {
                    Guard cached = guards[current];
                    if (cached != null) {
                        entry = cached;
                        break;
                    }
                    int parentId = ir.controls()[current].parentId();
                    if (parentId < 0) {
                        entry = Guard.TRUE;
                        guards[current] = Guard.TRUE;
                        break;
                    }
                    pending.addLast(current);
                    current = parentId;
                }
                while (!pending.isEmpty()) {
                    current = pending.removeLast();
                    entry = ir.guards().and(entry, ir.controls()[current].guard());
                    guards[current] = entry;
                }
            }
            blocks[blockId].guard = entry;
            for (int opId : ir.blocks()[blockId].opIds()) {
                ops[opId].beforeGuard = entry;
            }
            return targetId -> {
                if (seen.get(targetId)) {
                    return false;
                }
                seen.set(targetId);
                return true;
            };
        });
    }

    private void analyzeFacts() {
        blocks[0].facts = EnvAnalysis.EMPTY;
        ir.visitDataflowBlocks(blockId -> {
            BlockAnalysis block = blocks[blockId];
            if (block.guard.equals(Guard.FALSE)) {
                return targetId -> false;
            }
            EnvAnalysis outgoing = block.facts;
            for (int id : ir.blocks()[blockId].opIds()) {
                OpAnalysis op = ops[id];
                op.beforeFacts = outgoing;
                outgoing = transfer(id, op.beforeGuard, outgoing);
                op.afterFacts = outgoing;
            }
            EnvAnalysis facts = outgoing;
            return targetId -> {
                BlockAnalysis target = blocks[targetId];
                if (target.guard.equals(Guard.FALSE)) {
                    return false;
                }
                if (target.facts == null) {
                    target.facts = mergeEnv(EnvAnalysis.EMPTY, facts);
                    return true;
                }
                EnvAnalysis merged = mergeEnv(target.facts, facts);
                if (merged == target.facts) {
                    return false;
                }
                target.facts = merged;
                return true;
            };
        });
    }

    private EnvAnalysis transfer(int id, Guard currentGuard, EnvAnalysis state) {
        Op op = ir.ops()[id];
        FactAnalysis fact;
        switch (op.kind()) {
            case DECLARE_INPUT:
                Coverage defined = coverage(op.blockId());
                LatticeValue lattice = LatticeValue.top(ir.table().symbol(op.symbolId()).domain());
                FactAnalysis declared = new FactAnalysis(defined, lattice);
                FactAnalysis existing = state.facts.get(op.symbolId());
                if (existing == null) {
                    return define(state, op.symbolId(), declared);
                }
                fact = mergeFact(op.symbolId(), existing, declared);
                return define(state, op.symbolId(), fact);
            case DEFINE_VALUE:
                Symbol sym = ir.table().symbol(op.symbolId());
                fact = evaluateFact(id, currentGuard, sym, state);
                return define(state, op.symbolId(), fact);
            default:
                return state;
        }
    }

    private EnvAnalysis mergeEnv(EnvAnalysis current, EnvAnalysis incoming) {
        if (current == incoming || current.facts.equals(incoming.facts)) {
            return current;
        }
        Map<Integer, FactAnalysis> next = null;
        for (Map.Entry<Integer, FactAnalysis> entry : incoming.facts.entrySet()) {
            int id = entry.getKey();
            FactAnalysis left = current.facts.get(id);
            FactAnalysis right = entry.getValue();
            FactAnalysis merged;
            if (left == null) {
                merged = right;
            } else if (left == right || left.equals(right)) {
                merged = left;
            } else {
                merged = mergeFact(id, left, right);
            }
            if (merged != left) {
                if (next == null) {
                    next = new LinkedHashMap<>(current.facts);
                }
                next.put(id, merged);
            }
        }
        return next == null ? current : new EnvAnalysis(next);
    }

    private EnvAnalysis define(EnvAnalysis state, int id, FactAnalysis fact) {
        FactAnalysis existing = state.facts.get(id);
        if (Objects.equals(existing, fact)) {
            return state;
        }
        Map<Integer, FactAnalysis> facts = new LinkedHashMap<>(state.facts);
        facts.put(id, fact);
        return new EnvAnalysis(facts);
    }

    private FactAnalysis evaluateFact(int opId, Guard guard, Symbol sym, EnvAnalysis state) {
        Op op = ir.ops()[opId];
        Expression expression = op.expression();
        LatticeValue lattice = lattice(expression, sym, state);
        Value<?> value = value(expression, guard, state);
        if (value.isPresent()) {
            return FactAnalysis.exact(op.blockId(), lattice, value);
        }
        return new FactAnalysis(coverage(op.blockId()), lattice);
    }

    private LatticeValue lattice(Expression expression, Symbol sym, EnvAnalysis state) {
        List<Token> tokens = expression.tokens();
        if (tokens.size() == 1) {
            Token token = tokens.get(0);
            if (token.isOperand()) {
                return literalValue(sym.domain(), token.operand());
            }
            if (token.isVariable()) {
                int id = ir.table().findId(token.variable());
                if (id >= 0) {
                    FactAnalysis fact = state.facts.get(id);
                    if (fact != null) {
                        return fact.lattice;
                    }
                }
            }
        }
        return LatticeValue.top(sym.domain());
    }

    private Value<?> value(Expression expression, Guard guard, EnvAnalysis state) {
        List<Token> tokens = expression.tokens();
        if (tokens.size() == 1) {
            Token token = tokens.get(0);
            if (token.isOperand()) {
                return token.operand();
            }
            if (token.isVariable()) {
                int id = ir.table().findId(token.variable());
                if (id >= 0) {
                    Symbol sym = ir.table().symbol(id);
                    FactAnalysis fact = state.facts.get(id);
                    if (fact != null) {
                        Value<?> exactFromValue = value(sym.domain(), fact, guard);
                        if (exactFromValue.isPresent()) {
                            return exactFromValue;
                        }
                        if (!fact.exact.values.isEmpty() && implies(guard, fact.exact.coverage)) {
                            Value<?> value = Value.empty();
                            boolean found = false;
                            for (CoveredValue exactCase : fact.exact.values) {
                                if (overlaps(guard, exactCase.coverage)) {
                                    if (!found) {
                                        value = exactCase.value;
                                        found = true;
                                    } else if (!Value.isEqual(value, exactCase.value)) {
                                        return Value.empty();
                                    }
                                }
                            }
                            return found ? value : Value.empty();
                        }
                    }
                }
            }
        }
        return Value.empty();
    }

    private Value<?> value(Spec spec, FactAnalysis fact, Guard guard) {
        return implies(guard, fact.defined) ? fact.lattice.value(spec) : Value.empty();
    }

    private FactAnalysis mergeFact(int id, FactAnalysis left, FactAnalysis right) {
        Coverage defined = Coverage.merge(left.defined, right.defined, -1);
        LatticeValue lattice = LatticeValue.join(left.lattice, right.lattice);
        if (left.exact.values.isEmpty()) {
            return right.exact.values.isEmpty()
                    ? new FactAnalysis(defined, lattice)
                    : new FactAnalysis(defined, lattice, right.exact);
        }
        if (right.exact.values.isEmpty()) {
            return new FactAnalysis(defined, lattice, left.exact);
        }
        Coverage coverage = Coverage.merge(left.exact.coverage, right.exact.coverage, COVERAGE_MAX);
        if (coverage == null) {
            return new FactAnalysis(defined, lattice);
        }
        Symbol sym = ir.table().symbol(id);
        List<CoveredValue> merged = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            for (CoveredValue value : i == 0 ? left.exact.values : right.exact.values) {
                boolean found = false;
                for (int j = 0; j < merged.size(); j++) {
                    CoveredValue current = merged.get(j);
                    if (sym.domain().sameValue(current.value, value.value)) {
                        Coverage next = Coverage.merge(current.coverage, value.coverage, COVERAGE_MAX);
                        if (next == null) {
                            return new FactAnalysis(defined, lattice);
                        }
                        merged.set(j, new CoveredValue(current.value, next));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    if (merged.size() >= EXACT_CASE_MAX) {
                        return new FactAnalysis(defined, lattice);
                    }
                    merged.add(value);
                }
            }
        }
        return new FactAnalysis(defined, lattice, new ExactValues(coverage, merged));
    }

    private Map<Integer, Fact> env(EnvAnalysis analysis) {
        if (analysis == null || analysis.facts.isEmpty()) {
            return Map.of();
        }
        if (analysis.env == null) {
            Map<Integer, Fact> env = new LinkedHashMap<>();
            for (Map.Entry<Integer, FactAnalysis> entry : analysis.facts.entrySet()) {
                env.put(entry.getKey(), fact(entry.getKey(), entry.getValue()));
            }
            analysis.env = env;
        }
        return analysis.env;
    }

    private Fact fact(int id, FactAnalysis analysis) {
        if (analysis.fact == null) {
            Symbol sym = ir.table().symbol(id);
            List<GuardedValue> values = new ArrayList<>(analysis.exact.values.size());
            for (CoveredValue value : analysis.exact.values) {
                Guard guard = guard(value.coverage);
                values.add(new GuardedValue(value.value, guard, sym.domain().mask(value.value)));
            }
            analysis.fact = new Fact(guard(analysis.defined), analysis.lattice, values);
        }
        return analysis.fact;
    }

    private boolean implies(Guard left, Coverage right) {
        return right.ids.length != 0 && ir.guards().implies(left, guard(right));
    }

    private boolean overlaps(Guard left, Coverage right) {
        return right.ids.length != 0 && !ir.guards().and(left, guard(right)).equals(Guard.FALSE);
    }

    private Coverage coverage(int id) {
        BlockAnalysis analysis = blocks[id];
        if (analysis.coverage == null) {
            analysis.coverage = new Coverage(id);
        }
        return analysis.coverage;
    }

    private Guard guard(Coverage coverage) {
        if (coverage.guard == null) {
            if (coverage.ids.length == 0) {
                return Guard.FALSE;
            }
            Guard guard = blocks[coverage.ids[0]].guard;
            for (int i = 1; i < coverage.ids.length; i++) {
                guard = ir.guards().or(guard, blocks[coverage.ids[i]].guard);
            }
            coverage.guard = guard;
        }
        return coverage.guard;
    }

    private static LatticeValue literalValue(Spec spec, Value<?> literal) {
        switch (literal.type()) {
            case BOOLEAN:
                String scalar = String.valueOf(literal.getBoolean());
                if (spec.scalar() && spec.contains(scalar)) {
                    return LatticeValue.finiteScalar(spec.mask(scalar));
                }
                return spec.kind() == Spec.Kind.OPEN_TEXT ? LatticeValue.openText(scalar) : LatticeValue.top(spec);
            case STRING:
                if (spec.scalar() && spec.contains(literal.getString())) {
                    return LatticeValue.finiteScalar(spec.mask(literal.getString()));
                }
                return LatticeValue.openText(literal.getString());
            case LIST:
                if (spec.kind() == Spec.Kind.MEMBERSHIP) {
                    long listMask = spec.mask(new TreeSet<>(literal.getList()));
                    return LatticeValue.membership(listMask, listMask);
                }
                return LatticeValue.top(spec);
            default:
                return LatticeValue.top(spec);
        }
    }

    static final class IrState {
        static final IrState EMPTY = new IrState(Guard.FALSE, Map.of());

        private final Guard path;
        private final Map<Integer, Fact> env;

        IrState(Guard path, Map<Integer, Fact> env) {
            this.path = path;
            this.env = env;
        }

        Guard path() {
            return path;
        }

        Map<Integer, Fact> env() {
            return env;
        }
    }

    private static final class BlockAnalysis {
        private Coverage coverage;
        private Guard guard = Guard.FALSE;
        private EnvAnalysis facts;
        private IrState state = IrState.EMPTY;
    }

    private static final class OpAnalysis {
        private Guard beforeGuard = Guard.FALSE;
        private EnvAnalysis beforeFacts = EnvAnalysis.EMPTY;
        private EnvAnalysis afterFacts = EnvAnalysis.EMPTY;
        private IrState beforeState = IrState.EMPTY;
        private IrState afterState = IrState.EMPTY;
    }

    private static final class EnvAnalysis {
        private static final EnvAnalysis EMPTY = new EnvAnalysis(Map.of());

        private final Map<Integer, FactAnalysis> facts;
        private Map<Integer, Fact> env;

        EnvAnalysis(Map<Integer, FactAnalysis> facts) {
            this.facts = facts;
        }
    }

    private static final class FactAnalysis {
        private final Coverage defined;
        private final LatticeValue lattice;
        private final ExactValues exact;
        private Fact fact;

        FactAnalysis(Coverage defined, LatticeValue lattice) {
            this(defined, lattice, ExactValues.EMPTY);
        }

        FactAnalysis(Coverage defined, LatticeValue lattice, ExactValues exact) {
            this.defined = defined;
            this.lattice = lattice;
            this.exact = exact;
        }

        static FactAnalysis exact(int id, LatticeValue lattice, Value<?> value) {
            Coverage defined = new Coverage(id);
            if (!value.isPresent()) {
                return new FactAnalysis(defined, lattice);
            }
            List<CoveredValue> values = List.of(new CoveredValue(value, defined));
            return new FactAnalysis(defined, lattice, new ExactValues(defined, values));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof FactAnalysis)) {
                return false;
            }
            FactAnalysis other = (FactAnalysis) o;
            return defined.equals(other.defined)
                   && lattice.equals(other.lattice)
                   && exact.equals(other.exact);
        }

        @Override
        public int hashCode() {
            return Objects.hash(defined, lattice, exact, fact);
        }
    }

    private static final class ExactValues {
        private static final ExactValues EMPTY = new ExactValues(Coverage.EMPTY, List.of());

        private final Coverage coverage;
        private final List<CoveredValue> values;

        ExactValues(Coverage coverage, List<CoveredValue> values) {
            this.coverage = coverage;
            this.values = values;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ExactValues)) {
                return false;
            }
            ExactValues other = (ExactValues) o;
            return coverage.equals(other.coverage) && values.equals(other.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(coverage, values);
        }
    }

    private static final class CoveredValue {
        private final Value<?> value;
        private final Coverage coverage;

        CoveredValue(Value<?> value, Coverage coverage) {
            this.value = value;
            this.coverage = coverage;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CoveredValue)) {
                return false;
            }
            CoveredValue other = (CoveredValue) o;
            return coverage.equals(other.coverage) && Value.isEqual(value, other.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Value.hash(value), coverage);
        }
    }

    private static final class Coverage {
        private static final Coverage EMPTY = new Coverage();

        private final int[] ids;
        private Guard guard;

        Coverage(int... ids) {
            this.ids = ids;
        }

        static Coverage merge(Coverage left, Coverage right, int maxEntries) {
            if (left.ids.length == 0) {
                return right;
            }
            if (right.ids.length == 0 || left == right || left.equals(right)) {
                return left;
            }
            int[] merged = new int[left.ids.length + right.ids.length];
            int l = 0;
            int r = 0;
            int size = 0;
            while (l < left.ids.length || r < right.ids.length) {
                int next;
                if (r >= right.ids.length) {
                    next = left.ids[l++];
                } else if (l >= left.ids.length) {
                    next = right.ids[r++];
                } else {
                    int leftId = left.ids[l];
                    int rightId = right.ids[r];
                    if (leftId == rightId) {
                        next = leftId;
                        l++;
                        r++;
                    } else if (leftId < rightId) {
                        next = leftId;
                        l++;
                    } else {
                        next = rightId;
                        r++;
                    }
                }
                if (maxEntries > 0 && size == maxEntries) {
                    return null;
                }
                merged[size++] = next;
            }
            if (matches(merged, size, left.ids)) {
                return left;
            }
            if (matches(merged, size, right.ids)) {
                return right;
            }
            return new Coverage(size == merged.length ? merged : Arrays.copyOf(merged, size));
        }

        static boolean matches(int[] merged, int size, int[] other) {
            if (size != other.length) {
                return false;
            }
            for (int i = 0; i < size; i++) {
                if (merged[i] != other[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Coverage)) {
                return false;
            }
            Coverage other = (Coverage) o;
            return Arrays.equals(ids, other.ids);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(ids), guard);
        }
    }
}
