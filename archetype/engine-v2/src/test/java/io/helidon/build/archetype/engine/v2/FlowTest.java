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

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static io.helidon.build.archetype.engine.v2.Domain.Guard.FALSE;
import static io.helidon.build.archetype.engine.v2.Domain.Guard.TRUE;
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

        Domain.Symbol enabled = symbol(flow, "enabled");
        Domain.Symbol flavor = symbol(flow, "flavor");
        Domain.Symbol features = symbol(flow, "features");
        Node setup = findStep(script, "setup");
        Node pom = findFile(script, "pom.xml");

        assertThat(enabled.domain().booleanLike(), is(true));
        assertThat(flavor.domain().kind(), is(Domain.Spec.Kind.FINITE_SCALAR));
        assertThat(flavor.domain().values(), is(Set.of("mp", "se")));
        assertThat(features.domain().kind(), is(Domain.Spec.Kind.FINITE_MEMBERSHIP));
        assertThat(features.domain().values(), is(Set.of("rest")));
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
    void testAnalyzeMergesBranchValuesAndUseGuards() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/branch-value-merge.xml");

        Flow flow = new Flow(scope);
        flow.process(script);

        Domain.Symbol enabled = symbol(flow, "enabled");
        Domain.Symbol flavor = symbol(flow, "flavor");
        Flow.State before = flow.before(findStep(script, "Observe"));
        Domain.Symbol.Fact fact = before.env().get(flavor.id());
        Map<String, Domain.Guard> exactByValue = exactGuards(fact);

        assertThat(flow.guards().equivalent(before.path(), TRUE), is(true));
        assertThat(flow.guards().equivalent(fact.definedUnder(), TRUE), is(true));
        assertThat(fact.value().kind(), is(Domain.LatticeValue.Kind.FINITE_SCALAR));
        assertThat(fact.value().scalarValues(flavor.domain()), is(Set.of("mp", "se")));
        assertThat(exactByValue.keySet(), containsInAnyOrder("mp", "se"));
        assertThat(flow.guards().equivalent(exactByValue.get("mp"), flow.guards().eq(enabled.id(), "true")), is(true));
        assertThat(flow.guards().equivalent(exactByValue.get("se"), flow.guards().eq(enabled.id(), "false")), is(true));
    }

    @Test
    void testAnalyzeMergesSameExactValueAcrossMultiplePaths() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/same-exact-value-merge.xml");

        Flow flow = new Flow(scope);
        flow.process(script);

        Domain.Symbol primary = symbol(flow, "primary");
        Domain.Symbol secondary = symbol(flow, "secondary");
        Domain.Symbol flavor = symbol(flow, "flavor");
        Flow.State before = flow.before(findStep(script, "Observe"));
        Domain.Symbol.Fact fact = before.env().get(flavor.id());
        Map<String, Domain.Guard> exactByValue = exactGuards(fact);

        assertThat(flow.guards().equivalent(before.path(), TRUE), is(true));
        assertThat(fact.value().scalarValues(flavor.domain()), is(Set.of("mp", "se")));
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

        Domain.Symbol flag = symbol(flow, "flag");
        Domain.Symbol.Fact fact = flow.before(findStep(script, "Observe")).env().get(flag.id());

        assertThat(fact.exactCases().size(), is(1));
        assertThat(fact.exactCases().get(0).scalarLiteral(), is("true"));
        assertThat(flow.guards().equivalent(fact.exactCases().get(0).guard(), TRUE), is(true));
    }

    @Test
    void testIrLoweringCapturesDefinitionsAndVisibleFactsForConditions() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/definitions-and-uses.xml");

        Flow flow = new Flow(scope);
        flow.process(script);

        Node condition = findCondition(script, "${enabled} && ${flag}");
        Flow.State before = flow.before(condition);
        Domain.Symbol flag = symbol(flow, "flag");
        Domain.Symbol.Fact fact = before.env().get(flag.id());
        Set<String> names = before.env().keySet().stream().map(flow::symbol).map(Domain.Symbol::name).collect(Collectors.toSet());

        assertThat(names, is(Set.of("enabled", "flag")));
        assertThat(flow.declaredValue(condition, "flag").getBoolean(), is(true));
        assertThat(flow.guards().equivalent(fact.definedUnder(), TRUE), is(true));
        assertThat(fact.exactCases().size(), is(1));
        assertThat(fact.exactCases().get(0).scalarLiteral(), is("true"));
    }

    @Test
    void testIrLoweringGuardsNestedBooleanAndOptionDefinitions() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/nested-definitions.xml");

        Flow flow = new Flow(scope);
        flow.process(script);

        Domain.Symbol enabled = symbol(flow, "enabled");
        Domain.Symbol flavor = symbol(flow, "flavor");
        Node enabledFlag = findNodeByPath(script, Node.Kind.VARIABLE_BOOLEAN, "enabled-flag");
        Node mpFlag = findNodeByPath(script, Node.Kind.VARIABLE_BOOLEAN, "mp-flag");
        Node seFlag = findNodeByPath(script, Node.Kind.VARIABLE_BOOLEAN, "se-flag");

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

        Domain.Symbol enabled = symbol(flow, "enabled");
        Domain.Symbol flavor = symbol(flow, "flavor");
        Node enabledInput = findInput(script, Node.Kind.INPUT_BOOLEAN, "enabled");
        Node mpOption = findOption(script, "mp");
        Node seOption = findOption(script, "se");

        assertThat(flow.guards().equivalent(flow.activeGuard(enabledInput), flow.guards().eq(enabled.id(), "true")), is(true));
        assertThat(flow.guards().equivalent(flow.activeGuard(mpOption), flow.guards().eq(flavor.id(), "mp")), is(true));
        assertThat(flow.guards().equivalent(flow.activeGuard(seOption), flow.guards().eq(flavor.id(), "se")), is(true));
    }

    @Test
    void testIrLoweringKeepsDeclaredChoiceForTextPresetAndFallbackVariable() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/declared-choice-text-preset.xml");

        Flow flow = new Flow(scope);
        flow.process(script);
        Domain.Symbol symbol = symbol(flow, "media.json-lib");
        Node impossible = findCondition(script, "${media} contains 'json' && ${media.json-lib} == 'jsonp'");

        assertThat(symbol.domain().kind(), is(Domain.Spec.Kind.FINITE_SCALAR));
        assertThat(symbol.domain().subKind(), is(Domain.Spec.SubKind.CHOICE));
        assertThat(symbol.domain().values(), is(Set.of("jackson", "jsonb")));
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
        Domain.Symbol symbol = symbol(flow, "json-lib");
        Node impossible = findCondition(script, "${json-lib} == 'jsonp'");

        assertThat(symbol.domain().kind(), is(Domain.Spec.Kind.FINITE_SCALAR));
        assertThat(symbol.domain().subKind(), is(Domain.Spec.SubKind.FINITE_TEXT));
        assertThat(symbol.domain().values(), is(Set.of("jackson", "jsonb")));
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
        Node allowed = findCondition(script, "['jsonb','jackson'] contains ${json-lib}");
        Node impossible = findCondition(script, "['jsonp'] contains ${json-lib}");

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
        Node required = findCondition(script, "${features} contains ['grpc','rest']");
        Node impossible = findCondition(script, "${features} contains ['rest','websocket']");
        Domain.Guard expected = flow.guards().containsAll(features, Set.of("grpc", "rest"));

        assertThat(flow.guards().equivalent(flow.activeGuard(required), expected), is(true));
        assertThat(flow.guards().equivalent(flow.activeGuard(impossible), FALSE), is(true));
    }

    @Test
    void testIrLoweringKeepsUnsupportedLiteralListContainsResidual() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/literal-list-contains-unsupported.xml");

        Flow flow = new Flow(scope);
        flow.process(script);
        Node condition = findCondition(script, "['json'] contains true");

        assertThat(flow.activeGuard(condition).equals(TRUE), is(false));
        assertThat(flow.activeGuard(condition).equals(FALSE), is(false));
    }

    @Test
    void testIrLoweringFallsBackToOriginalConditionWhenMixedControlIsNotFinite() {
        Context.Scope scope = new Context().scope();
        Node script = load("flow/mixed-open-text-condition.xml");

        Flow flow = new Flow(scope);
        flow.process(script);
        Node condition = findCondition(script, "${enabled} && ${name} == 'blue'");

        assertThat(flow.activeGuard(condition).equals(TRUE), is(false));
        assertThat(flow.activeGuard(condition).equals(FALSE), is(false));
        assertThat(flow.activationCondition(condition).literal(), is("${enabled} && ${name} == 'blue'"));
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

    static Node findStep(Node script, String name) {
        for (Node node : script.traverse(Node.Kind.STEP::equals)) {
            if (name.equals(node.attribute("name").getString())) {
                return node;
            }
        }
        throw new IllegalArgumentException("Missing step: " + name);
    }

    static Node findFile(Node script, String target) {
        for (Node node : script.traverse(Node.Kind.FILE::equals)) {
            if (target.equals(node.attribute("target").getString())) {
                return node;
            }
        }
        throw new IllegalArgumentException("Missing file: " + target);
    }

    static Node findInput(Node script, Node.Kind kind, String id) {
        for (Node node : script.traverse(kind::equals)) {
            if (id.equals(node.attribute("id").getString())) {
                return node;
            }
        }
        throw new IllegalArgumentException("Missing " + kind + " id: " + id);
    }

    static Node findOption(Node script, String value) {
        for (Node node : script.traverse(Node.Kind.INPUT_OPTION::equals)) {
            if (value.equals(node.value().getString())) {
                return node;
            }
        }
        throw new IllegalArgumentException("Missing option: " + value);
    }

    static Node findNodeByPath(Node script, Node.Kind kind, String path) {
        for (Node node : script.traverse(kind::equals)) {
            if (path.equals(node.attribute("path").getString())) {
                return node;
            }
        }
        throw new IllegalArgumentException("Missing " + kind + " path: " + path);
    }

    static Domain.Symbol symbol(Flow flow, String name) {
        Flow.SymbolInfo info = flow.symbol(name);
        if (info == null) {
            throw new IllegalArgumentException("Missing symbol: " + name);
        }
        return info.symbol();
    }

    static Map<String, Domain.Guard> exactGuards(Domain.Symbol.Fact fact) {
        return fact.exactCases().stream()
                .collect(Collectors.toMap(Domain.Symbol.Fact.ExactCase::scalarLiteral, Domain.Symbol.Fact.ExactCase::guard));
    }
}
