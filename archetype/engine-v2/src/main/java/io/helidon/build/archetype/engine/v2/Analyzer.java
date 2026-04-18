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
import java.util.Deque;
import java.util.IdentityHashMap;
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
import io.helidon.build.archetype.engine.v2.Flow.State;
import io.helidon.build.archetype.engine.v2.Ir.Block;
import io.helidon.build.archetype.engine.v2.Ir.Control;
import io.helidon.build.archetype.engine.v2.Ir.Op;

final class Analyzer {
    private static final int EXACT_CASE_MAX = 16;
    private static final int COVERAGE_MAX = 16;

    private final Ir ir;
    private final Map<Integer, Map<FactAnalysis, Fact>> facts = new LinkedHashMap<>();
    private final Map<Map<Integer, FactAnalysis>, Map<Integer, Fact>> envs = new IdentityHashMap<>();
    private final BlockAnalysis[] blocks;
    private final OpAnalysis[] ops;

    Analyzer(Ir ir) {
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
        analyzeControl();
        analyzeFacts();
        for (BlockAnalysis block : blocks) {
            block.state = new State(block.guard, env(block.facts));
        }
        for (OpAnalysis op : ops) {
            op.beforeState = new State(op.beforeGuard, env(op.beforeFacts));
            op.afterState = new State(op.beforeGuard, env(op.afterFacts));
        }
    }

    State entryState(int blockId) {
        return blocks[blockId].state;
    }

    State beforeState(int opId) {
        return ops[opId].beforeState;
    }

    State afterState(int opId) {
        return ops[opId].afterState;
    }

    private void analyzeControl() {
        Block[] blocks = ir.blocks();
        Control[] controls = ir.controls();
        ControlPass pass = new ControlPass(blocks.length, controls.length);
        pass.reachable[0] = true;
        pass.guards[0] = Guard.TRUE;
        for (int id = 0; id < pass.reachable.length; id++) {
            if (pass.reachable[id]) {
                Ir.Terminator term = blocks[id].term();
                switch (term.kind()) {
                    case GOTO:
                        pass.reachable[term.targetId()] = true;
                        break;
                    case BRANCH:
                        pass.reachable[term.trueId()] = true;
                        pass.reachable[term.falseId()] = true;
                        break;
                    case RETURN:
                    case UNREACHABLE:
                    default:
                        break;
                }
            }
        }

        for (int id = 0; id < pass.reachable.length; id++) {
            if (pass.reachable[id]) {
                int ctrlId = blocks[id].controlId();
                this.blocks[id].guard = computeControlGuard(ctrlId, controls, pass);
            }
        }
        for (int id = 0; id < pass.reachable.length; id++) {
            if (pass.reachable[id]) {
                Guard entry = this.blocks[id].guard;
                for (int opId : blocks[id].opIds()) {
                    ops[opId].beforeGuard = entry;
                }
            }
        }
    }

    private Guard computeControlGuard(int id, Control[] irControls, ControlPass pass) {
        Guard cached = pass.guards[id];
        if (cached != null) {
            return cached;
        }
        int parentId = irControls[id].parentId();
        if (parentId < 0) {
            pass.guards[id] = Guard.TRUE;
            return Guard.TRUE;
        }
        Guard guard = computeControlGuard(parentId, irControls, pass);
        Guard materialized = ir.guards().and(guard, irControls[id].edgeGuard());
        pass.guards[id] = materialized;
        return materialized;
    }

    private void analyzeFacts() {
        Block[] irBlocks = ir.blocks();
        Deque<Integer> stack = new ArrayDeque<>();
        boolean[] seen = new boolean[irBlocks.length];
        seen[0] = true;
        stack.add(0);
        while (!stack.isEmpty()) {
            int blockId = stack.removeFirst();
            BlockAnalysis blockAnalysis = blocks[blockId];
            if (!blockAnalysis.guard.equals(Guard.FALSE)) {
                Map<Integer, FactAnalysis> current = blockAnalysis.facts;
                for (int id : irBlocks[blockId].opIds()) {
                    OpAnalysis op = ops[id];
                    op.beforeFacts = current;
                    current = transfer(id, op.beforeGuard, current);
                    op.afterFacts = current;
                }
                Ir.Terminator term = irBlocks[blockId].term();
                switch (term.kind()) {
                    case GOTO: {
                        int targetId = term.targetId();
                        BlockAnalysis target = blocks[targetId];
                        if (!target.guard.equals(Guard.FALSE)) {
                            Map<Integer, FactAnalysis> merged = mergeEnv(target.facts, current);
                            if (merged != target.facts || !seen[targetId]) {
                                target.facts = merged;
                                seen[targetId] = true;
                                stack.addLast(targetId);
                            }
                        }
                        break;
                    }
                    case BRANCH: {
                        int trueId = term.trueId();
                        BlockAnalysis trueBlock = blocks[trueId];
                        if (!trueBlock.guard.equals(Guard.FALSE)) {
                            Map<Integer, FactAnalysis> merged = mergeEnv(trueBlock.facts, current);
                            if (merged != trueBlock.facts || !seen[trueId]) {
                                trueBlock.facts = merged;
                                seen[trueId] = true;
                                stack.addLast(trueId);
                            }
                        }
                        int falseId = term.falseId();
                        BlockAnalysis falseBlock = blocks[falseId];
                        if (!falseBlock.guard.equals(Guard.FALSE)) {
                            Map<Integer, FactAnalysis> merged = mergeEnv(falseBlock.facts, current);
                            if (merged != falseBlock.facts || !seen[falseId]) {
                                falseBlock.facts = merged;
                                seen[falseId] = true;
                                stack.addLast(falseId);
                            }
                        }
                        break;
                    }
                    case RETURN:
                    case UNREACHABLE:
                    default:
                        break;
                }
            }
        }
    }

    private Map<Integer, FactAnalysis> transfer(int id, Guard currentGuard, Map<Integer, FactAnalysis> state) {
        Op op = ir.ops()[id];
        switch (op.kind()) {
            case DECLARE_INPUT: {
                Coverage defined = coverage(op.blockId());
                LatticeValue lattice = LatticeValue.top(ir.table().symbol(op.symbolId()).domain());
                FactAnalysis declared = new FactAnalysis(defined, lattice);
                FactAnalysis existing = state.get(op.symbolId());
                if (existing == null) {
                    return define(state, op.symbolId(), declared);
                }
                FactAnalysis fact = mergeFact(op.symbolId(), existing, declared);
                return define(state, op.symbolId(), fact);
            }
            case DEFINE_VALUE: {
                Symbol sym = ir.table().symbol(op.symbolId());
                FactAnalysis fact = evaluateFact(id, currentGuard, sym, state);
                return define(state, op.symbolId(), fact);
            }
            default:
                return state;
        }
    }

    private Map<Integer, FactAnalysis> mergeEnv(Map<Integer, FactAnalysis> current, Map<Integer, FactAnalysis> incoming) {
        if (current == incoming || current.equals(incoming)) {
            return current;
        }
        Map<Integer, FactAnalysis> next = null;
        for (Map.Entry<Integer, FactAnalysis> entry : incoming.entrySet()) {
            int id = entry.getKey();
            FactAnalysis left = current.get(id);
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
                    next = new LinkedHashMap<>(current);
                }
                next.put(id, merged);
            }
        }
        return next == null ? current : next;
    }

    private Map<Integer, FactAnalysis> define(Map<Integer, FactAnalysis> state, int id, FactAnalysis fact) {
        FactAnalysis existing = state.get(id);
        if (Objects.equals(existing, fact)) {
            return state;
        }
        Map<Integer, FactAnalysis> env = new LinkedHashMap<>(state);
        env.put(id, fact);
        return env;
    }

    private FactAnalysis evaluateFact(int opId, Guard guard, Symbol sym, Map<Integer, FactAnalysis> state) {
        Op op = ir.ops()[opId];
        Expression expression = op.expression();
        LatticeValue lattice = lattice(expression, sym, state);
        Value<?> value = value(expression, guard, state);
        if (value.isPresent()) {
            return FactAnalysis.exact(op.blockId(), lattice, value);
        }
        return new FactAnalysis(coverage(op.blockId()), lattice);
    }

    private LatticeValue lattice(Expression expression, Symbol sym, Map<Integer, FactAnalysis> state) {
        List<Token> tokens = expression.tokens();
        if (tokens.size() == 1) {
            Token token = tokens.get(0);
            if (token.isOperand()) {
                return literalValue(sym.domain(), token.operand());
            }
            if (token.isVariable()) {
                int id = ir.table().findId(token.variable());
                if (id >= 0) {
                    FactAnalysis fact = state.get(id);
                    if (fact != null) {
                        return fact.lattice;
                    }
                }
            }
        }
        return LatticeValue.top(sym.domain());
    }

    private Value<?> value(Expression expression, Guard guard, Map<Integer, FactAnalysis> state) {
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
                    FactAnalysis fact = state.get(id);
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

    private Map<Integer, Fact> env(Map<Integer, FactAnalysis> env) {
        if (env.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Fact> materialized = envs.get(env);
        if (materialized == null) {
            materialized = new LinkedHashMap<>();
            for (Map.Entry<Integer, FactAnalysis> entry : env.entrySet()) {
                materialized.put(entry.getKey(), fact(entry.getKey(), entry.getValue()));
            }
            envs.put(env, materialized);
        }
        return materialized;
    }

    private Fact fact(int id, FactAnalysis analysis) {
        Map<FactAnalysis, Fact> cache = facts.computeIfAbsent(id, unused -> new IdentityHashMap<>());
        Fact fact = cache.get(analysis);
        if (fact == null) {
            Symbol sym = ir.table().symbol(id);
            List<GuardedValue> values = new ArrayList<>(analysis.exact.values.size());
            for (CoveredValue value : analysis.exact.values) {
                Guard guard = guard(value.coverage);
                values.add(new GuardedValue(value.value, guard, sym.domain().mask(value.value)));
            }
            fact = new Fact(guard(analysis.defined), analysis.lattice, values);
            cache.put(analysis, fact);
        }
        return fact;
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
            case BOOLEAN: {
                String scalar = String.valueOf(literal.getBoolean());
                if (spec.scalar() && spec.contains(scalar)) {
                    return LatticeValue.finiteScalar(spec.mask(scalar));
                }
                return spec.kind() == Spec.Kind.OPEN_TEXT ? LatticeValue.openText(scalar) : LatticeValue.top(spec);
            }
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

    private static final class ControlPass {
        private final boolean[] reachable;
        private final Guard[] guards;

        ControlPass(int blockCount, int controlCount) {
            this.reachable = new boolean[blockCount];
            this.guards = new Guard[controlCount];
        }
    }

    private static final class BlockAnalysis {
        private Coverage coverage;
        private Guard guard = Guard.FALSE;
        private Map<Integer, FactAnalysis> facts = Map.of();
        private State state = State.EMPTY;
    }

    private static final class OpAnalysis {
        private Guard beforeGuard = Guard.FALSE;
        private Map<Integer, FactAnalysis> beforeFacts = Map.of();
        private Map<Integer, FactAnalysis> afterFacts = Map.of();
        private State beforeState = State.EMPTY;
        private State afterState = State.EMPTY;
    }

    private static final class FactAnalysis {
        private final Coverage defined;
        private final LatticeValue lattice;
        private final ExactValues exact;

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
    }
}
