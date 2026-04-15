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

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static io.helidon.build.archetype.engine.v2.Domain.Guard.FALSE;
import static io.helidon.build.archetype.engine.v2.Domain.Guard.TRUE;
import static io.helidon.build.common.test.utils.TestFiles.testResourcePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void testModelRejectsDetachedNode() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/ir-lowering.xml");

        Flow flow = new Flow(scope);
        flow.process(script);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> flow.key(Nodes.step("Detached")));
        assertThat(ex.getMessage(), containsString("Node is not part of this flow model"));
    }

    @Test
    void testAnalyzeMergesBranchValuesAndUseGuards() {
        Domain.Symbol.Table.Builder builder = Domain.Symbol.Table.builder();
        int enabled = builder.define("enabled", Domain.Spec.booleanSpec(), true, false);
        int flavor = builder.define("flavor", Domain.Spec.choice(Set.of("mp", "se")), true, false);
        Domain.Symbol.Table symbols = builder.build();
        Domain.Guards guards = new Domain.Guards(symbols);
        Flow.SourceAnchor anchor = new Flow.SourceAnchor(Nodes.script());

        Flow.Op declareEnabled = Flow.Op.declareInput(0, 0, anchor, enabled);
        Flow.Op defineMp = Flow.Op.defineValue(1, 1, anchor, flavor, Expression.create("'mp'"));
        Flow.Op defineSe = Flow.Op.defineValue(2, 2, anchor, flavor, Expression.create("'se'"));
        Flow.Op useFlavor = Flow.Op.recordUse(3, 3, anchor, flavor);

        Flow.Block entry = new Flow.Block(List.of(0),
                Flow.Terminator.branch(anchor, guards.eq(enabled, "true"), 1, 2),
                Flow.ControlPath.ROOT_ID);
        Flow.Block mp = new Flow.Block(List.of(1), Flow.Terminator.jump(anchor, 3), 1);
        Flow.Block se = new Flow.Block(List.of(2), Flow.Terminator.jump(anchor, 3), 2);
        Flow.Block exit = new Flow.Block(List.of(3), Flow.Terminator.ret(anchor), Flow.ControlPath.ROOT_ID);
        List<Flow.ControlPath> controlPaths = List.of(
                Flow.ControlPath.ROOT,
                new Flow.ControlPath(Flow.ControlPath.ROOT_ID, guards.eq(enabled, "true")),
                new Flow.ControlPath(Flow.ControlPath.ROOT_ID, guards.eq(enabled, "false")));

        Flow.Ir ir = new Flow.Ir(
                List.of(entry, mp, se, exit),
                symbols,
                List.of(declareEnabled, defineMp, defineSe, useFlavor),
                guards,
                controlPaths);
        Flow flow = new Flow(new Context().scope());
        flow.process(ir, anchor.node());
        Flow.Analysis analysis = flow.analysis();

        Flow.State beforeUse = analysis.beforeByOp().get(3);
        Domain.Symbol.Fact fact = beforeUse.env().get(flavor);
        Map<String, Domain.Guard> exactByValue = fact.exactCases().stream()
                .collect(Collectors.toMap(Domain.Symbol.Fact.ExactCase::scalarLiteral, Domain.Symbol.Fact.ExactCase::guard));

        assertThat(guards.equivalent(beforeUse.path(), TRUE), is(true));
        assertThat(guards.equivalent(fact.definedUnder(), TRUE), is(true));
        assertThat(fact.value().kind(), is(Domain.LatticeValue.Kind.FINITE_SCALAR));
        assertThat(fact.value().scalarValues(symbols.symbol(flavor).domain()), is(Set.of("mp", "se")));
        assertThat(exactByValue.keySet(), containsInAnyOrder("mp", "se"));
        assertThat(guards.equivalent(exactByValue.get("mp"), guards.eq(enabled, "true")), is(true));
        assertThat(guards.equivalent(exactByValue.get("se"), guards.eq(enabled, "false")), is(true));
        assertThat(analysis.usesById().size(), is(1));
        assertThat(analysis.usesById().get(0).symbolId(), is(flavor));
        assertThat(guards.equivalent(analysis.usesById().get(0).guard(), TRUE), is(true));
    }

    @Test
    void testAnalyzeMergesSameExactValueAcrossMultiplePaths() {
        Domain.Symbol.Table.Builder builder = Domain.Symbol.Table.builder();
        int primary = builder.define("primary", Domain.Spec.booleanSpec(), true, false);
        int secondary = builder.define("secondary", Domain.Spec.booleanSpec(), true, false);
        int flavor = builder.define("flavor", Domain.Spec.choice(Set.of("mp", "se")), true, false);
        Domain.Symbol.Table symbols = builder.build();
        Domain.Guards guards = new Domain.Guards(symbols);
        Flow.SourceAnchor anchor = new Flow.SourceAnchor(Nodes.script());

        Flow.Op declarePrimary = Flow.Op.declareInput(0, 0, anchor, primary);
        Flow.Op declareSecondary = Flow.Op.declareInput(1, 0, anchor, secondary);
        Flow.Op definePrimaryMp = Flow.Op.defineValue(2, 1, anchor, flavor, Expression.create("'mp'"));
        Flow.Op defineSecondaryMp = Flow.Op.defineValue(3, 3, anchor, flavor, Expression.create("'mp'"));
        Flow.Op defineSe = Flow.Op.defineValue(4, 4, anchor, flavor, Expression.create("'se'"));
        Flow.Op useFlavor = Flow.Op.recordUse(5, 5, anchor, flavor);

        Domain.Guard primaryTrue = guards.eq(primary, "true");
        Domain.Guard primaryFalse = guards.eq(primary, "false");
        Domain.Guard secondaryTrue = guards.eq(secondary, "true");
        Domain.Guard secondaryFalse = guards.eq(secondary, "false");

        Flow.Block entry = new Flow.Block(List.of(0, 1),
                Flow.Terminator.branch(anchor, primaryTrue, 1, 2),
                Flow.ControlPath.ROOT_ID);
        Flow.Block primaryMp = new Flow.Block(List.of(2), Flow.Terminator.jump(anchor, 5), 1);
        Flow.Block secondaryBranch = new Flow.Block(List.of(),
                Flow.Terminator.branch(anchor, secondaryTrue, 3, 4),
                2);
        Flow.Block secondaryMp = new Flow.Block(List.of(3), Flow.Terminator.jump(anchor, 5), 3);
        Flow.Block se = new Flow.Block(List.of(4), Flow.Terminator.jump(anchor, 5), 4);
        Flow.Block exit = new Flow.Block(List.of(5), Flow.Terminator.ret(anchor), Flow.ControlPath.ROOT_ID);
        List<Flow.ControlPath> controlPaths = List.of(
                Flow.ControlPath.ROOT,
                new Flow.ControlPath(Flow.ControlPath.ROOT_ID, primaryTrue),
                new Flow.ControlPath(Flow.ControlPath.ROOT_ID, primaryFalse),
                new Flow.ControlPath(2, secondaryTrue),
                new Flow.ControlPath(2, secondaryFalse));

        Flow.Ir ir = new Flow.Ir(
                List.of(entry, primaryMp, secondaryBranch, secondaryMp, se, exit),
                symbols,
                List.of(declarePrimary, declareSecondary, definePrimaryMp, defineSecondaryMp, defineSe, useFlavor),
                guards,
                controlPaths);
        Flow flow = new Flow(new Context().scope());
        flow.process(ir, anchor.node());
        Flow.Analysis analysis = flow.analysis();

        Flow.State beforeUse = analysis.beforeByOp().get(5);
        Domain.Symbol.Fact fact = beforeUse.env().get(flavor);
        Map<String, Domain.Guard> exactByValue = fact.exactCases().stream()
                .collect(Collectors.toMap(Domain.Symbol.Fact.ExactCase::scalarLiteral, Domain.Symbol.Fact.ExactCase::guard));

        assertThat(guards.equivalent(beforeUse.path(), TRUE), is(true));
        assertThat(fact.value().scalarValues(symbols.symbol(flavor).domain()), is(Set.of("mp", "se")));
        assertThat(exactByValue.keySet(), containsInAnyOrder("mp", "se"));
        assertThat(guards.equivalent(exactByValue.get("mp"), guards.or(primaryTrue, guards.and(primaryFalse,
                secondaryTrue))), is(true));
        assertThat(guards.equivalent(exactByValue.get("se"), guards.and(primaryFalse, secondaryFalse)), is(true));
    }

    @Test
    void testAnalyzeMergesEquivalentBooleanExactValuesAcrossLiteralTypes() {
        Domain.Symbol.Table.Builder builder = Domain.Symbol.Table.builder();
        int enabled = builder.define("enabled", Domain.Spec.booleanSpec(), true, false);
        int flag = builder.define("flag", Domain.Spec.booleanSpec(), true, false);
        Domain.Symbol.Table symbols = builder.build();
        Domain.Guards guards = new Domain.Guards(symbols);
        Flow.SourceAnchor anchor = new Flow.SourceAnchor(Nodes.script());

        Flow.Op declareEnabled = Flow.Op.declareInput(0, 0, anchor, enabled);
        Flow.Op defineBoolean = Flow.Op.defineValue(1, 1, anchor, flag, Expression.create("true"));
        Flow.Op defineString = Flow.Op.defineValue(2, 2, anchor, flag, Expression.create("'true'"));
        Flow.Op useFlag = Flow.Op.recordUse(3, 3, anchor, flag);

        Domain.Guard enabledTrue = guards.eq(enabled, "true");
        Flow.Block entry = new Flow.Block(List.of(0),
                Flow.Terminator.branch(anchor, enabledTrue, 1, 2),
                Flow.ControlPath.ROOT_ID);
        Flow.Block trueBranch = new Flow.Block(List.of(1), Flow.Terminator.jump(anchor, 3), 1);
        Flow.Block falseBranch = new Flow.Block(List.of(2), Flow.Terminator.jump(anchor, 3), 2);
        Flow.Block exit = new Flow.Block(List.of(3), Flow.Terminator.ret(anchor), Flow.ControlPath.ROOT_ID);
        List<Flow.ControlPath> controlPaths = List.of(
                Flow.ControlPath.ROOT,
                new Flow.ControlPath(Flow.ControlPath.ROOT_ID, enabledTrue),
                new Flow.ControlPath(Flow.ControlPath.ROOT_ID, guards.eq(enabled, "false")));

        Flow.Ir ir = new Flow.Ir(
                List.of(entry, trueBranch, falseBranch, exit),
                symbols,
                List.of(declareEnabled, defineBoolean, defineString, useFlag),
                guards,
                controlPaths);
        Flow flow = new Flow(new Context().scope());
        flow.process(ir, anchor.node());
        Flow.Analysis analysis = flow.analysis();

        Domain.Symbol.Fact fact = analysis.beforeByOp().get(3).env().get(flag);

        assertThat(fact.exactCases().size(), is(1));
        assertThat(fact.exactCases().get(0).scalarLiteral(), is("true"));
        assertThat(guards.equivalent(fact.exactCases().get(0).guard(), TRUE), is(true));
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

        assertThat(symbol.domain().kind(), is(Domain.Spec.Kind.FINITE_SCALAR));
        assertThat(symbol.domain().subKind(), is(Domain.Spec.SubKind.CHOICE));
        assertThat(symbol.domain().values(), is(Set.of("jackson", "jsonb")));
        assertThat(symbol.guardable(), is(true));
        assertThat(symbol.tainted(), is(false));
        assertThat(flow.guards().equivalent(model.activeGuard(impossible), FALSE), is(true));
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

        assertThat(symbol.domain().kind(), is(Domain.Spec.Kind.FINITE_SCALAR));
        assertThat(symbol.domain().subKind(), is(Domain.Spec.SubKind.FINITE_TEXT));
        assertThat(symbol.domain().values(), is(Set.of("jackson", "jsonb")));
        assertThat(symbol.guardable(), is(true));
        assertThat(symbol.tainted(), is(false));
        assertThat(flow.guards().equivalent(model.activeGuard(impossible), FALSE), is(true));
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

        assertThat(flow.guards().equivalent(model.activeGuard(allowed), TRUE), is(true));
        assertThat(flow.guards().equivalent(model.activeGuard(impossible), FALSE), is(true));
    }

    @Test
    void testIrLoweringHandlesMembershipSymbolContainsLiteralList() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/literal-list-contains-membership.xml");

        Flow flow = new Flow(scope);
        flow.process(script);
        Flow.Model model = flow.model();
        int features = model.findSymbolId("features");
        Node required = findCondition(script, "${features} contains ['grpc','rest']");
        Node impossible = findCondition(script, "${features} contains ['rest','websocket']");
        Domain.Guard expected = flow.guards().containsAll(features, Set.of("grpc", "rest"));

        assertThat(flow.guards().equivalent(model.activeGuard(required), expected), is(true));
        assertThat(flow.guards().equivalent(model.activeGuard(impossible), FALSE), is(true));
    }

    @Test
    void testIrLoweringKeepsUnsupportedLiteralListContainsResidual() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/literal-list-contains-unsupported.xml");

        Flow flow = new Flow(scope);
        flow.process(script);
        Flow.Model model = flow.model();
        Node condition = findCondition(script, "['json'] contains true");

        assertThat(model.activeGuard(condition).equals(TRUE), is(false));
        assertThat(model.activeGuard(condition).equals(FALSE), is(false));
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
