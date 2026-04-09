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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.build.archetype.engine.v2.Domain.Spec.FiniteText;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static io.helidon.build.common.test.utils.TestFiles.testResourcePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class FlowTest {

    @Test
    void testIrLoweringBuildsInputsBranchesAndEmits() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/ir-lowering.xml");

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
        Node script = load("flow/definitions-and-uses.xml");

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
        Node script = load("flow/nested-definitions.xml");

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

    @Test
    void testIrLoweringKeepsDeclaredChoiceForTextPresetAndFallbackVariable() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/declared-choice-text-preset.xml");

        Flow flow = new Flow(scope);
        flow.process(script);
        Flow.Model model = flow.model();
        Domain.Symbol symbol = model.symbol("media.json-lib").symbol();
        Node impossible = findCondition(script, "${media} contains 'json' && ${media.json-lib} == 'jsonp'");

        assertThat(symbol.domain(), instanceOf(Domain.Spec.Choice.class));
        assertThat(((Domain.Spec.Choice) symbol.domain()).values(), is(Set.of("jackson", "jsonb")));
        assertThat(symbol.guardable(), is(true));
        assertThat(symbol.tainted(), is(false));
        assertThat(model.guards().equivalent(model.activeGuard(impossible), model.falseGuard()), is(true));
    }

    @Test
    void testIrLoweringPromotesFiniteLocalTextVariable() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/finite-local-text-variable.xml");

        Flow flow = new Flow(scope);
        flow.process(script);
        Flow.Model model = flow.model();
        Domain.Symbol symbol = model.symbol("json-lib").symbol();
        Node impossible = findCondition(script, "${json-lib} == 'jsonp'");

        assertThat(symbol.domain(), isSubtype(FiniteText.class,
                hasProperty("values", FiniteText::values, is(Set.of("jackson", "jsonb")))));
        assertThat(symbol.guardable(), is(true));
        assertThat(symbol.tainted(), is(false));
        assertThat(model.guards().equivalent(model.activeGuard(impossible), model.falseGuard()), is(true));
    }

    @Test
    void testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/literal-list-contains-finite-scalar.xml");

        Flow flow = new Flow(scope);
        flow.process(script);
        Flow.Model model = flow.model();
        Node allowed = findCondition(script, "['jsonb','jackson'] contains ${json-lib}");
        Node impossible = findCondition(script, "['jsonp'] contains ${json-lib}");

        assertThat(model.guards().equivalent(model.activeGuard(allowed), model.trueGuard()), is(true));
        assertThat(model.guards().equivalent(model.activeGuard(impossible), model.falseGuard()), is(true));
    }

    @Test
    void testIrLoweringKeepsUnsupportedLiteralListContainsResidual() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/literal-list-contains-unsupported.xml");

        Flow flow = new Flow(scope);
        flow.process(script);
        Flow.Model model = flow.model();
        Node condition = findCondition(script, "['json'] contains true");

        assertThat(model.activeGuard(condition).equals(model.trueGuard()), is(false));
        assertThat(model.activeGuard(condition).equals(model.falseGuard()), is(false));
    }

    static Node load(String path) {
        return Script.load(testResourcePath(FlowTest.class, path));
    }

    static Node findCondition(Node script, String expression) {
        for (Node node : script.traverse(Node.Kind.CONDITION::equals)) {
            if (expression.equals(node.expression().literal())) {
                return node;
            }
        }
        throw new IllegalArgumentException("Missing condition: " + expression);
    }

    @SafeVarargs
    @SuppressWarnings({"unchecked", "SameParameterValue"})
    static <T, U extends T> Matcher<T> isSubtype(Class<U> type, Matcher<U>... matchers) {
        var list = new ArrayList<Matcher<? super T>>();
        list.add(instanceOf(type));
        for (Matcher<U> matcher : matchers) {
            list.add((Matcher<T>) matcher);
        }
        return allOf(list);
    }

    @SuppressWarnings("SameParameterValue")
    static <T, U> Matcher<T> hasProperty(String name, Function<T, U> extractor, Matcher<U> subMatcher) {
        return new FeatureMatcher<>(subMatcher, "has property " + name, name) {
            @Override
            protected U featureValueOf(T target) {
                return extractor.apply(target);
            }
        };
    }
}
