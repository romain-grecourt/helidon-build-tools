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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.build.archetype.engine.v2.Domain.Fact;
import io.helidon.build.archetype.engine.v2.Domain.Guard;
import io.helidon.build.archetype.engine.v2.Domain.GuardedValue;
import io.helidon.build.archetype.engine.v2.Domain.LatticeValue;
import io.helidon.build.archetype.engine.v2.Domain.Spec;
import io.helidon.build.archetype.engine.v2.Domain.Symbol;

import org.junit.jupiter.api.Test;

import static io.helidon.build.archetype.engine.v2.Domain.Guard.FALSE;
import static io.helidon.build.archetype.engine.v2.Domain.Guard.TRUE;
import static io.helidon.build.archetype.engine.v2.Node.Kind.CONDITION;
import static io.helidon.build.archetype.engine.v2.Node.Kind.FILE;
import static io.helidon.build.archetype.engine.v2.Node.Kind.INPUT_BOOLEAN;
import static io.helidon.build.archetype.engine.v2.Node.Kind.INPUT_ENUM;
import static io.helidon.build.archetype.engine.v2.Node.Kind.INPUT_OPTION;
import static io.helidon.build.archetype.engine.v2.Node.Kind.PRESET_TEXT;
import static io.helidon.build.archetype.engine.v2.Node.Kind.STEP;
import static io.helidon.build.archetype.engine.v2.Node.Kind.VARIABLE_BOOLEAN;
import static io.helidon.build.archetype.engine.v2.Nodes.find;
import static io.helidon.build.common.test.utils.TestFiles.testResourcePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowTest {

    @Test
    void testFlowTracksInputDomainsAndNodeGuards() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/ir-lowering.xml");

        Flow flow = new Flow(scope);
        flow.process(script);

        Symbol enabled = symbol(flow, "enabled");
        Symbol flavor = symbol(flow, "flavor");
        Symbol features = symbol(flow, "features");
        Node setup = find(script, STEP::equals, "name", "setup");
        Node pom = find(script, FILE::equals, "target", "pom.xml");

        assertThat(enabled.domain().kind(), is(Spec.Kind.BOOLEAN));
        assertThat(flavor.domain().kind(), is(Spec.Kind.CHOICE));
        assertThat(Arrays.asList(flavor.domain().values()), is(List.of("mp", "se")));
        assertThat(features.domain().kind(), is(Spec.Kind.MEMBERSHIP));
        assertThat(Arrays.asList(features.domain().values()), is(List.of("rest")));
        assertThat(flow.guards().equivalent(flow.activeGuard(setup), flow.guards().eq(enabled.id(), "true")), is(true));
        assertThat(flow.guards().equivalent(flow.activeGuard(pom), flow.guards().contains(features.id(), "rest")), is(true));
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
    void testFlowRejectsUnknownSymbolId() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/ir-lowering.xml");

        Flow flow = new Flow(scope);
        flow.process(script);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> flow.symbol(99));
        assertThat(ex.getMessage(), containsString("Unknown symbol id"));
    }

    @Test
    void testAnalyzeMergesBranchValuesAndUseGuards() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/branch-value-merge.xml");

        Flow flow = new Flow(scope);
        flow.process(script);

        Symbol enabled = symbol(flow, "enabled");
        Symbol flavor = symbol(flow, "flavor");
        Node step = find(script, STEP::equals, "name", "Observe");
        IrAnalyzer.IrState before = flow.before(step);
        Fact fact = before.env().get(flavor.id());
        Map<String, Guard> exactByValue = exactGuards(fact);

        assertThat(flow.guards().equivalent(before.path(), TRUE), is(true));
        assertThat(flow.guards().equivalent(fact.guard(), TRUE), is(true));
        assertThat(fact.lattice().kind(), is(LatticeValue.Kind.FINITE_SCALAR));
        assertThat(fact.lattice().scalarValues(flavor.domain()), is(Set.of("mp", "se")));
        assertThat(exactByValue.keySet(), containsInAnyOrder("mp", "se"));
        assertThat(flow.guards().equivalent(exactByValue.get("mp"), flow.guards().eq(enabled.id(), "true")), is(true));
        assertThat(flow.guards().equivalent(exactByValue.get("se"), flow.guards().eq(enabled.id(), "false")), is(true));
    }

    @Test
    void testAnalyzeMergesSameValueAcrossMultiplePaths() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/same-exact-value-merge.xml");

        Flow flow = new Flow(scope);
        flow.process(script);

        Symbol primary = symbol(flow, "primary");
        Symbol secondary = symbol(flow, "secondary");
        Symbol flavor = symbol(flow, "flavor");
        Node step = find(script, STEP::equals, "name", "Observe");
        IrAnalyzer.IrState before = flow.before(step);
        Fact fact = before.env().get(flavor.id());
        Map<String, Guard> exactByValue = exactGuards(fact);

        assertThat(flow.guards().equivalent(before.path(), TRUE), is(true));
        assertThat(fact.lattice().scalarValues(flavor.domain()), is(Set.of("mp", "se")));
        assertThat(exactByValue.keySet(), containsInAnyOrder("mp", "se"));
        assertThat(flow.guards().equivalent(exactByValue.get("mp"),
                flow.guards().or(
                        flow.guards().eq(primary.id(), "true"),
                        flow.guards().and(
                                flow.guards().eq(primary.id(), "false"),
                                flow.guards().eq(secondary.id(), "true")))),
                is(true));
        assertThat(flow.guards().equivalent(exactByValue.get("se"),
                flow.guards().and(
                        flow.guards().eq(primary.id(), "false"),
                        flow.guards().eq(secondary.id(), "false"))),
                is(true));
    }

    @Test
    void testAnalyzeMergesEquivalentBooleanExactValuesAcrossLiteralTypes() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/boolean-exact-value-merge.xml");

        Flow flow = new Flow(scope);
        flow.process(script);

        Symbol flag = symbol(flow, "flag");
        Node step = find(script, STEP::equals, "name", "Observe");
        Fact fact = flow.before(step).env().get(flag.id());

        assertThat(fact.values().size(), is(1));
        assertThat(Value.scalarLiteral(fact.values().get(0).value()), is("true"));
        assertThat(flow.guards().equivalent(fact.values().get(0).guard(), TRUE), is(true));
    }

    @Test
    void testIrLoweringCapturesDefinitionsAndVisibleFactsForConditions() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/definitions-and-uses.xml");

        Flow flow = new Flow(scope);
        flow.process(script);

        Node condition = find(script, CONDITION::equals, n -> "${enabled} && ${flag}".equals(n.expression().literal()));
        IrAnalyzer.IrState before = flow.before(condition);
        Symbol flag = symbol(flow, "flag");
        Fact fact = before.env().get(flag.id());
        Set<String> names = before.env().keySet().stream().map(flow::symbol).map(Symbol::name).collect(Collectors.toSet());

        assertThat(names, is(Set.of("enabled", "flag")));
        assertThat(flow.declaredValue(condition, "flag").getBoolean(), is(true));
        assertThat(flow.guards().equivalent(fact.guard(), TRUE), is(true));
        assertThat(fact.values().size(), is(1));
        assertThat(Value.scalarLiteral(fact.values().get(0).value()), is("true"));
    }

    @Test
    void testIrLoweringGuardsNestedBooleanAndOptionDefinitions() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/nested-definitions.xml");

        Flow flow = new Flow(scope);
        flow.process(script);

        Symbol enabled = symbol(flow, "enabled");
        Symbol flavor = symbol(flow, "flavor");
        Node enabledFlag = find(script, VARIABLE_BOOLEAN::equals, "path", "enabled-flag");
        Node mpFlag = find(script, VARIABLE_BOOLEAN::equals, "path", "mp-flag");
        Node seFlag = find(script, VARIABLE_BOOLEAN::equals, "path", "se-flag");

        assertThat(flow.guards().equivalent(flow.activeGuard(enabledFlag), flow.guards().eq(enabled.id(), "true")), is(true));
        assertThat(flow.guards().equivalent(flow.activeGuard(mpFlag), flow.guards().eq(flavor.id(), "mp")), is(true));
        assertThat(flow.guards().equivalent(flow.activeGuard(seFlag), flow.guards().eq(flavor.id(), "se")), is(true));
    }

    @Test
    void testActiveGuardUsesDirectGuardsForInputAndOptionNodes() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/nested-definitions.xml");

        Flow flow = new Flow(scope);
        flow.process(script);

        Symbol enabled = symbol(flow, "enabled");
        Symbol flavor = symbol(flow, "flavor");
        Node enabledInput = find(script, INPUT_BOOLEAN::equals, "id", "enabled");
        Node mpOption = find(script, INPUT_OPTION::equals, "mp");
        Node seOption = find(script, INPUT_OPTION::equals, "se");

        assertThat(flow.guards().equivalent(flow.activeGuard(enabledInput), flow.guards().eq(enabled.id(), "true")), is(true));
        assertThat(flow.guards().equivalent(flow.activeGuard(mpOption), flow.guards().eq(flavor.id(), "mp")), is(true));
        assertThat(flow.guards().equivalent(flow.activeGuard(seOption), flow.guards().eq(flavor.id(), "se")), is(true));
    }

    @Test
    void testIrLoweringKeepsDeclaredChoiceWhenPresetPrecedesMatchingInput() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/declared-choice-text-preset.xml");
        List<Node> nodes = script.collect();

        Flow flow = new Flow(scope);
        flow.process(script);
        Node preset = find(script, PRESET_TEXT::equals, "path", "media.json-lib");
        Node jsonLibInput = find(script, INPUT_ENUM::equals, "id", "json-lib");
        Symbol symbol = symbol(flow, "media.json-lib");
        Node impossible = find(script, CONDITION::equals,
                n -> "${media} contains 'json' && ${media.json-lib} == 'jsonp'".equals(n.expression().literal()));

        assertThat(nodes.indexOf(preset) < nodes.indexOf(jsonLibInput), is(true));
        assertThat(symbol.domain().kind(), is(Spec.Kind.CHOICE));
        assertThat(Arrays.asList(symbol.domain().values()), is(List.of("jackson", "jsonb")));
        assertThat(symbol.guardable(), is(true));
        assertThat(symbol.tainted(), is(false));
        assertThat(flow.guards().equivalent(flow.activeGuard(impossible), FALSE), is(true));
    }

    @Test
    void testIrLoweringPromotesFiniteLocalTextVariable() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/finite-local-text-variable.xml");

        Flow flow = new Flow(scope);
        flow.process(script);
        Symbol symbol = symbol(flow, "json-lib");
        Node impossible = find(script, CONDITION::equals, n -> "${json-lib} == 'jsonp'".equals(n.expression().literal()));

        assertThat(symbol.domain().kind(), is(Spec.Kind.FINITE_TEXT));
        assertThat(Arrays.asList(symbol.domain().values()), is(List.of("jackson", "jsonb")));
        assertThat(symbol.guardable(), is(true));
        assertThat(symbol.tainted(), is(false));
        assertThat(flow.guards().equivalent(flow.activeGuard(impossible), FALSE), is(true));
    }

    @Test
    void testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/literal-list-contains-finite-scalar.xml");

        Flow flow = new Flow(scope);
        flow.process(script);
        Node allowed = find(script, CONDITION::equals,
                n -> "['jsonb','jackson'] contains ${json-lib}".equals(n.expression().literal()));
        Node impossible = find(script, CONDITION::equals,
                n -> "['jsonp'] contains ${json-lib}".equals(n.expression().literal()));

        assertThat(flow.guards().equivalent(flow.activeGuard(allowed), TRUE), is(true));
        assertThat(flow.guards().equivalent(flow.activeGuard(impossible), FALSE), is(true));
    }

    @Test
    void testIrLoweringHandlesMembershipSymbolContainsLiteralList() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/literal-list-contains-membership.xml");

        Flow flow = new Flow(scope);
        flow.process(script);
        int features = symbol(flow, "features").id();
        Node required = find(script, CONDITION::equals,
                n -> "${features} contains ['grpc','rest']".equals(n.expression().literal()));
        Node impossible = find(script, CONDITION::equals,
                n -> "${features} contains ['rest','websocket']".equals(n.expression().literal()));
        Guard expected = flow.guards().containsAll(features, Set.of("grpc", "rest"));

        assertThat(flow.guards().equivalent(flow.activeGuard(required), expected), is(true));
        assertThat(flow.guards().equivalent(flow.activeGuard(impossible), FALSE), is(true));
    }

    @Test
    void testIrLoweringKeepsUnsupportedLiteralListContainsResidual() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/literal-list-contains-unsupported.xml");

        Flow flow = new Flow(scope);
        flow.process(script);
        Node condition = find(script, CONDITION::equals,
                n -> "['json'] contains true".equals(n.expression().literal()));

        assertThat(flow.activeGuard(condition).equals(TRUE), is(false));
        assertThat(flow.activeGuard(condition).equals(FALSE), is(false));
    }

    @Test
    void testIrLoweringFallsBackToOriginalConditionWhenMixedControlIsNotFinite() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/mixed-open-text-condition.xml");

        Flow flow = new Flow(scope);
        flow.process(script);
        Node condition = find(script, CONDITION::equals,
                n -> "${enabled} && ${name} == 'blue'".equals(n.expression().literal()));

        assertThat(flow.activeGuard(condition).equals(TRUE), is(false));
        assertThat(flow.activeGuard(condition).equals(FALSE), is(false));
        assertThat(flow.activationCondition(condition).literal(), is("${enabled} && ${name} == 'blue'"));
    }

    static Node load(String path) {
        return Script.load(testResourcePath(FlowTest.class, path));
    }

    static Symbol symbol(Flow flow, String name) {
        Flow.SymbolInfo info = flow.symbol(name);
        if (info == null) {
            throw new IllegalArgumentException("Missing symbol: " + name);
        }
        return info.symbol();
    }

    static Map<String, Guard> exactGuards(Fact fact) {
        return fact.values().stream()
                .collect(Collectors.toMap(it -> Value.scalarLiteral(it.value()), GuardedValue::guard));
    }
}
