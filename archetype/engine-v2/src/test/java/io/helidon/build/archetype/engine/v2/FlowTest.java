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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class FlowTest {

    @Test
    void testIrLoweringBuildsInputsBranchesAndEmits() {
        Context.Scope scope = new Context().scope();
        Node script = Nodes.script(
                Nodes.inputs(
                        Nodes.inputBoolean("enabled"),
                        Nodes.inputEnum("flavor",
                                Nodes.inputOption("mp", "mp"),
                                Nodes.inputOption("se", "se")),
                        Nodes.inputList("features",
                                Nodes.inputOption("rest", "rest"))),
                Nodes.condition("${enabled}",
                        Nodes.step("setup"),
                        Nodes.condition("${features} contains 'rest'",
                                Nodes.file("src/main/resources/pom.xml", "pom.xml"))));

        Flow flow = new Flow(scope);
        flow.process(script);
        Flow.Ir ir = flow.ir();

        assertThat(ir.symbols().size(), is(3));
        assertThat(ir.symbols().symbols().stream().map(Domain.Symbol::name).collect(Collectors.toList()),
                containsInAnyOrder("enabled", "flavor", "features"));
        assertThat(ir.ops().stream().filter(op -> op.kind() == Flow.Op.Kind.DECLARE_INPUT).count(), is(3L));
        assertThat(ir.ops().stream().filter(op -> op.kind() == Flow.Op.Kind.DECLARE_OPTION).count(), is(3L));
        assertThat(ir.ops().stream().filter(op -> op.kind() == Flow.Op.Kind.EMIT).count(), is(2L));
        assertThat(ir.blocks().stream()
                        .map(Flow.Block::terminator)
                        .filter(t -> t.kind() == Flow.Terminator.Kind.BRANCH)
                        .map(t -> ir.guards().toExpression(t.guard(), scope).literal())
                        .collect(Collectors.toList()),
                containsInAnyOrder("${enabled}", "${features} contains 'rest'"));
    }

    @Test
    void testAnalyzeMergesBranchValuesAndUseGuards() {
        Domain.Symbol.Table.Builder builder = Domain.Symbol.Table.builder();
        int enabled = builder.define("enabled", new Domain.Spec.Boolean(), true, false);
        int flavor = builder.define("flavor", new Domain.Spec.Choice(Set.of("mp", "se")), true, false);
        Domain.Symbol.Table symbols = builder.build();
        Domain.Guards guards = new Domain.Guards(symbols);
        Flow.SourceAnchor anchor = new Flow.SourceAnchor(Nodes.script(), "");

        Flow.Op declareEnabled = Flow.Op.declareInput(0, 0, anchor, enabled, enabled);
        Flow.Op defineMp = Flow.Op.defineValue(1, 1, anchor, flavor, Expression.create("'mp'"));
        Flow.Op defineSe = Flow.Op.defineValue(2, 2, anchor, flavor, Expression.create("'se'"));
        Flow.Op useFlavor = Flow.Op.recordUse(3, 3, anchor, flavor, 17);

        Flow.Block entry = new Flow.Block(0, List.of(0),
                Flow.Terminator.branch(anchor, guards.eq(enabled, "true"), 1, 2));
        Flow.Block mp = new Flow.Block(1, List.of(1), Flow.Terminator.jump(anchor, 3));
        Flow.Block se = new Flow.Block(2, List.of(2), Flow.Terminator.jump(anchor, 3));
        Flow.Block exit = new Flow.Block(3, List.of(3), Flow.Terminator.ret(anchor));

        Flow.Ir ir = new Flow.Ir(List.of(entry, mp, se, exit), symbols,
                List.of(declareEnabled, defineMp, defineSe, useFlavor), guards);
        Flow flow = new Flow(new Context().scope());
        flow.process(ir, anchor.node());
        Flow.Analysis analysis = flow.analysis();

        Flow.State beforeUse = analysis.beforeByOp().get(3);
        Domain.Symbol.Fact fact = beforeUse.env().get(flavor);

        assertThat(guards.equivalent(beforeUse.path(), guards.trueGuard()), is(true));
        assertThat(guards.equivalent(fact.definedUnder(), guards.trueGuard()), is(true));
        assertThat(fact.value(), instanceOf(Domain.Value.ChoiceSet.class));
        assertThat(((Domain.Value.ChoiceSet) fact.value()).values(), is(Set.of("mp", "se")));
        assertThat(analysis.usesById().size(), is(1));
        assertThat(analysis.usesById().get(0).symbolId(), is(flavor));
        assertThat(guards.equivalent(analysis.usesById().get(0).guard(), guards.trueGuard()), is(true));
    }

    @Test
    void testIrLoweringCapturesDefinitionsAndConditionUses() {
        Context.Scope scope = new Context().scope();
        Node script = Nodes.script(
                Nodes.inputs(Nodes.inputBoolean("enabled")),
                Nodes.variables(Nodes.variableBoolean("flag", true)),
                Nodes.condition("${enabled} && ${flag}",
                        Nodes.step("Go")));

        Flow flow = new Flow(scope);
        flow.process(script);
        Flow.Ir ir = flow.ir();
        Flow.Analysis analysis = flow.analysis();

        assertThat(ir.ops().stream().filter(op -> op.kind() == Flow.Op.Kind.DEFINE_VALUE).count(), is(1L));
        assertThat(ir.ops().stream().filter(op -> op.kind() == Flow.Op.Kind.RECORD_USE).count(), is(2L));
        assertThat(analysis.usesById().stream()
                        .map(use -> ir.symbols().symbol(use.symbolId()).name())
                        .collect(Collectors.toSet()),
                containsInAnyOrder("enabled", "flag"));
    }

    @Test
    void testIrLoweringGuardsNestedBooleanAndOptionDefinitions() {
        Context.Scope scope = new Context().scope();
        Node script = Nodes.script(
                Nodes.inputs(
                        Nodes.inputBoolean("enabled",
                                Nodes.variables(Nodes.variableBoolean("enabled-flag", true))),
                        Nodes.inputEnum("flavor",
                                Nodes.inputOption("mp", "mp",
                                        Nodes.variables(Nodes.variableBoolean("mp-flag", true))),
                                Nodes.inputOption("se", "se",
                                        Nodes.variables(Nodes.variableBoolean("se-flag", true))))));

        Flow flow = new Flow(scope);
        flow.process(script);
        Flow.Ir ir = flow.ir();
        Flow.Analysis analysis = flow.analysis();
        Map<String, Domain.Guard> pathsBySymbol = ir.ops().stream()
                .filter(op -> op.kind() == Flow.Op.Kind.DEFINE_VALUE)
                .collect(Collectors.toMap(
                        op -> ir.symbols().symbol(op.symbolId()).name(),
                        op -> analysis.beforeByOp().get(op.id()).path()));
        int enabled = ir.symbols().findId("enabled");
        int flavor = ir.symbols().findId("flavor");

        assertThat(ir.blocks().stream()
                        .map(Flow.Block::terminator)
                        .filter(t -> t.kind() == Flow.Terminator.Kind.BRANCH)
                        .count(),
                is(3L));
        assertThat(ir.guards().equivalent(pathsBySymbol.get("enabled-flag"), ir.guards().eq(enabled, "true")), is(true));
        assertThat(ir.guards().equivalent(pathsBySymbol.get("mp-flag"), ir.guards().eq(flavor, "mp")), is(true));
        assertThat(ir.guards().equivalent(pathsBySymbol.get("se-flag"), ir.guards().eq(flavor, "se")), is(true));
    }
}
