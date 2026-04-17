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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.build.archetype.engine.v2.Domain.Fact;
import io.helidon.build.archetype.engine.v2.Domain.Guard;
import io.helidon.build.archetype.engine.v2.Domain.Guards;
import io.helidon.build.archetype.engine.v2.Domain.LatticeValue;
import io.helidon.build.archetype.engine.v2.Domain.Spec;
import io.helidon.build.archetype.engine.v2.Domain.Symbol;
import io.helidon.build.archetype.engine.v2.Domain.Table;

import org.junit.jupiter.api.Test;

import static io.helidon.build.archetype.engine.v2.Domain.Guard.TRUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainTest {

    @Test
    void testTopValuesFollowSpecs() {
        Spec booleanSpec = Spec.BOOLEAN;
        Spec choiceSpec = new Spec(Spec.Kind.CHOICE, "mp", "se");
        Spec membershipSpec = new Spec(Spec.Kind.MEMBERSHIP, "grpc", "rest");
        LatticeValue booleanTop = LatticeValue.top(booleanSpec);
        LatticeValue choiceTop = LatticeValue.top(choiceSpec);
        LatticeValue membershipTop = LatticeValue.top(membershipSpec);
        LatticeValue openTextTop = LatticeValue.top(Spec.OPEN_TEXT);

        assertThat(booleanTop.kind(), is(LatticeValue.Kind.FINITE_SCALAR));
        assertThat(booleanTop.scalarValues(booleanSpec), is(Set.of("false", "true")));

        assertThat(choiceTop.kind(), is(LatticeValue.Kind.FINITE_SCALAR));
        assertThat(choiceTop.scalarValues(choiceSpec), is(Set.of("mp", "se")));

        assertThat(membershipTop.kind(), is(LatticeValue.Kind.MEMBERSHIP));
        assertThat(membershipTop, is(LatticeValue.membership(0L, membershipSpec.mask())));

        assertThat(openTextTop.kind(), is(LatticeValue.Kind.OPEN_TEXT));
        assertThat(openTextTop.sample(), is(nullValue()));
    }

    @Test
    void testSpecKindIsScalar() {
        assertThat(Spec.Kind.OPEN_TEXT.isScalar(), is(false));
        assertThat(Spec.Kind.BOOLEAN.isScalar(), is(true));
        assertThat(Spec.Kind.CHOICE.isScalar(), is(true));
        assertThat(Spec.Kind.FINITE_TEXT.isScalar(), is(true));
        assertThat(Spec.Kind.MEMBERSHIP.isScalar(), is(false));
    }

    @Test
    void testOpenTextSpecUsesEmptyValuesArray() {
        assertThat(Arrays.asList(Spec.OPEN_TEXT.values()), is(List.of()));
        assertThat(Spec.OPEN_TEXT.toString(), is("OPEN_TEXT[]"));
    }

    @Test
    void testGuardAlgebraRendersFiniteConditions() {
        Table symbols = table(
                symbol(0, "enabled", Spec.BOOLEAN, true, false),
                symbol(1, "flavor", new Spec(Spec.Kind.CHOICE, "mp", "se"), true, false),
                symbol(2, "features", new Spec(Spec.Kind.MEMBERSHIP, "grpc", "rest"), true, false));
        int enabled = symbols.findId("enabled");
        int flavor = symbols.findId("flavor");
        int features = symbols.findId("features");
        Guards guards = new Guards(symbols);
        Context.Scope scope = new Context().scope();

        Guard enabledGuard = guards.eq(enabled, "true");
        Guard disabledGuard = guards.not(enabledGuard);
        Guard mpGuard = guards.eq(flavor, "mp");
        Guard nonMpGuard = guards.not(mpGuard);
        Guard restGuard = guards.contains(features, "rest");
        Guard notRestGuard = guards.not(restGuard);
        Guard enabledMp = guards.and(enabledGuard, mpGuard);

        assertThat(guards.expression(enabledGuard, scope).literal(), is("${enabled}"));
        assertThat(guards.expression(disabledGuard, scope).literal(), is("!${enabled}"));
        assertThat(guards.expression(nonMpGuard, scope).literal(), is("${flavor} != 'mp'"));
        assertThat(guards.expression(notRestGuard, scope).literal(), is("!(${features} contains 'rest')"));
        assertThat(guards.implies(enabledMp, enabledGuard), is(true));
        assertThat(guards.equivalent(guards.or(mpGuard, nonMpGuard), TRUE), is(true));
    }

    @Test
    void testGuardImplicationKeepsBroaderPureDecision() {
        Table symbols = table(
                symbol(0, "enabled", Spec.BOOLEAN, true, false),
                symbol(1, "flavor", new Spec(Spec.Kind.CHOICE, "mp", "se"), true, false));
        int enabled = symbols.findId("enabled");
        int flavor = symbols.findId("flavor");
        Guards guards = new Guards(symbols);

        Guard enabledGuard = guards.eq(enabled, "true");
        Guard mpGuard = guards.eq(flavor, "mp");
        Guard residual = guards.residualGuard(Expression.create("${unsupported}"));
        Guard enabledMpResidual = guards.and(guards.and(enabledGuard, mpGuard), residual);
        Guard enabledResidual = guards.and(enabledGuard, residual);

        assertThat(guards.implies(enabledMpResidual, enabledGuard), is(true));
        assertThat(guards.implies(enabledMpResidual, enabledResidual), is(true));
        assertThat(guards.or(enabledMpResidual, enabledGuard), is(enabledGuard));
    }

    @Test
    void testGuardOrMergesPureDecisionBranches() {
        Table symbols = table(
                symbol(0, "enabled", Spec.BOOLEAN, true, false),
                symbol(1, "flavor", new Spec(Spec.Kind.CHOICE, "mp", "se"), true, false));
        int enabled = symbols.findId("enabled");
        int flavor = symbols.findId("flavor");
        Guards guards = new Guards(symbols);

        Guard enabledGuard = guards.eq(enabled, "true");
        Guard mpGuard = guards.eq(flavor, "mp");
        Guard seGuard = guards.eq(flavor, "se");
        Guard enabledMp = guards.and(enabledGuard, mpGuard);
        Guard enabledSe = guards.and(enabledGuard, seGuard);

        assertThat(guards.or(enabledMp, enabledSe), is(enabledGuard));
    }

    @Test
    void testGuardAndNarrowsMergedScalarChoice() {
        Table symbols = table(symbol(0, "flavor", new Spec(Spec.Kind.CHOICE, "mp", "nima", "se"), true, false));
        int flavor = symbols.findId("flavor");
        Guards guards = new Guards(symbols);
        Context.Scope scope = new Context().scope();

        Guard mpGuard = guards.eq(flavor, "mp");
        Guard seGuard = guards.eq(flavor, "se");
        Guard mpOrSe = guards.or(mpGuard, seGuard);

        assertThat(guards.expression(mpOrSe, scope).literal(), is("${flavor} != 'nima'"));
        assertThat(guards.equivalent(guards.and(mpOrSe, mpGuard), mpGuard), is(true));
    }

    @Test
    void testGuardContainsAllKeepsBroaderMembershipRequirement() {
        Table symbols = table(symbol(0, "features", new Spec(Spec.Kind.MEMBERSHIP, "grpc", "metrics", "rest"),
                true, false));
        int features = symbols.findId("features");
        Guards guards = new Guards(symbols);

        Guard restGuard = guards.contains(features, "rest");
        Guard grpcGuard = guards.contains(features, "grpc");
        Guard restAndGrpc = guards.and(restGuard, grpcGuard);
        Guard bulk = guards.containsAll(features, Set.of("grpc", "rest"));

        assertThat(guards.equivalent(restAndGrpc, bulk), is(true));
        assertThat(guards.implies(bulk, restGuard), is(true));
        assertThat(guards.or(bulk, restGuard), is(restGuard));
    }

    @Test
    void testFactExactCasesUseFiniteMasksInsteadOfRawLiteralShape() {
        Table symbols = table(
                symbol(0, "enabled", Spec.BOOLEAN, true, false),
                symbol(1, "features", new Spec(Spec.Kind.MEMBERSHIP, "grpc", "rest"), true, false));
        int enabled = symbols.findId("enabled");
        int features = symbols.findId("features");
        Guards guards = new Guards(symbols);
        Symbol enabledSymbol = symbols.symbol(enabled);
        Symbol featuresSymbol = symbols.symbol(features);

        Fact booleanTrue = Fact.exact(
                TRUE,
                enabledSymbol.domain(),
                LatticeValue.finiteScalar(enabledSymbol.domain().mask("true")),
                Value.of(true));
        Fact stringTrue = Fact.exact(
                TRUE,
                enabledSymbol.domain(),
                LatticeValue.finiteScalar(enabledSymbol.domain().mask("true")),
                Value.of("true"));
        Fact mergedBoolean = Fact.merge(booleanTrue, stringTrue, guards);

        assertThat(mergedBoolean.guardedValues().size(), is(1));
        assertThat(mergedBoolean.match(enabledSymbol, Value.of("true"), guards), is(TRUE));
        assertThat(mergedBoolean.scalarAny(enabledSymbol, Set.of("true"), guards), is(TRUE));

        Fact grpcRest = Fact.exact(
                TRUE,
                featuresSymbol.domain(),
                LatticeValue.membership(
                        featuresSymbol.domain().mask(Set.of("grpc", "rest")),
                        featuresSymbol.domain().mask(Set.of("grpc", "rest"))),
                Value.of(List.of("grpc", "rest")));
        Fact restGrpc = Fact.exact(
                TRUE,
                featuresSymbol.domain(),
                LatticeValue.membership(
                        featuresSymbol.domain().mask(Set.of("grpc", "rest")),
                        featuresSymbol.domain().mask(Set.of("grpc", "rest"))),
                Value.of(List.of("rest", "grpc")));
        Fact mergedMembership = Fact.merge(grpcRest, restGrpc, guards);

        assertThat(mergedMembership.guardedValues().size(), is(1));
        assertThat(mergedMembership.listContains(featuresSymbol, Set.of("grpc", "rest"), guards), is(TRUE));
    }

    @Test
    void testSymbolTableRejectsUnknownSymbolId() {
        Table symbols = table(symbol(0, "enabled", Spec.BOOLEAN, true, false));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> symbols.symbol(99));

        assertThat(ex.getMessage(), containsString("Unknown symbol id"));
    }

    @Test
    void testGuardsRejectUnknownGuardId() {
        Table symbols = table(symbol(0, "enabled", Spec.BOOLEAN, true, false));
        int enabled = symbols.findId("enabled");
        Guards guards = new Guards(symbols);
        Guard valid = guards.eq(enabled, "true");
        Guard invalid = new Guard(99, valid.residual());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guards.expression(invalid, new Context().scope()));

        assertThat(ex.getMessage(), containsString("Unknown guard id"));
    }

    private static Table table(Symbol... symbols) {
        List<Symbol> values = List.of(symbols);
        Map<String, Integer> ids = new LinkedHashMap<>();
        for (Symbol symbol : values) {
            ids.put(symbol.name(), symbol.id());
        }
        return new Table(values, ids);
    }

    private static Symbol symbol(int id, String name, Spec spec, boolean guardable, boolean tainted) {
        return new Symbol(id, name, spec, guardable, tainted);
    }
}
