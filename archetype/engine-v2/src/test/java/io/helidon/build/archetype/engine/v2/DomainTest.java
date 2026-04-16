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
import java.util.Set;
import java.util.function.Function;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static io.helidon.build.archetype.engine.v2.Domain.Guard.TRUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainTest {

    @Test
    void testTopValuesFollowSpecs() {
        Domain.Spec booleanSpec = Domain.Spec.booleanSpec();
        Domain.Spec choiceSpec = Domain.Spec.choice(Set.of("mp", "se"));
        Domain.Spec membershipSpec = Domain.Spec.finiteMembership(Set.of("rest", "grpc"));
        Domain.LatticeValue booleanTop = Domain.LatticeValue.top(booleanSpec);
        Domain.LatticeValue choiceTop = Domain.LatticeValue.top(choiceSpec);
        Domain.LatticeValue membershipTop = Domain.LatticeValue.top(membershipSpec);
        Domain.LatticeValue openTextTop = Domain.LatticeValue.top(Domain.Spec.OPEN_TEXT);

        assertThat(booleanTop.kind(), is(Domain.LatticeValue.Kind.FINITE_SCALAR));
        assertThat(booleanTop.scalarValues(booleanSpec), is(Set.of("false", "true")));

        assertThat(choiceTop.kind(), is(Domain.LatticeValue.Kind.FINITE_SCALAR));
        assertThat(choiceTop.scalarValues(choiceSpec), is(Set.of("mp", "se")));

        assertThat(membershipTop.kind(), is(Domain.LatticeValue.Kind.MEMBERSHIP));
        assertThat(membershipTop.possibleValues(membershipSpec), is(Set.of("grpc", "rest")));

        assertThat(openTextTop.kind(), is(Domain.LatticeValue.Kind.OPEN_TEXT));
        assertThat(openTextTop.sample(), is(nullValue()));
    }

    @Test
    void testGuardAlgebraRendersFiniteConditions() {
        Domain.Symbol.Table.Builder builder = Domain.Symbol.Table.builder();
        int enabled = builder.define("enabled", Domain.Spec.booleanSpec(), true, false);
        int flavor = builder.define("flavor", Domain.Spec.choice(Set.of("mp", "se")), true, false);
        int features = builder.define("features", Domain.Spec.finiteMembership(Set.of("rest", "grpc")), true, false);
        Domain.Symbol.Table symbols = builder.build();
        Domain.Guards guards = new Domain.Guards(symbols);
        Context.Scope scope = new Context().scope();

        Domain.Guard enabledGuard = guards.eq(enabled, "true");
        Domain.Guard disabledGuard = guards.not(enabledGuard);
        Domain.Guard mpGuard = guards.eq(flavor, "mp");
        Domain.Guard nonMpGuard = guards.not(mpGuard);
        Domain.Guard restGuard = guards.contains(features, "rest");
        Domain.Guard notRestGuard = guards.not(restGuard);
        Domain.Guard enabledMp = guards.and(enabledGuard, mpGuard);

        assertThat(guards.toExpression(enabledGuard, scope).literal(), is("${enabled}"));
        assertThat(guards.toExpression(disabledGuard, scope).literal(), is("!${enabled}"));
        assertThat(guards.toExpression(nonMpGuard, scope).literal(), is("${flavor} != 'mp'"));
        assertThat(guards.toExpression(notRestGuard, scope).literal(), is("!(${features} contains 'rest')"));
        assertThat(guards.implies(enabledMp, enabledGuard), is(true));
        assertThat(guards.equivalent(guards.or(mpGuard, nonMpGuard), TRUE), is(true));
    }

    @Test
    void testGuardImplicationKeepsBroaderPureDecision() {
        Domain.Symbol.Table.Builder builder = Domain.Symbol.Table.builder();
        int enabled = builder.define("enabled", Domain.Spec.booleanSpec(), true, false);
        int flavor = builder.define("flavor", Domain.Spec.choice(Set.of("mp", "se")), true, false);
        Domain.Symbol.Table symbols = builder.build();
        Domain.Guards guards = new Domain.Guards(symbols);

        Domain.Guard enabledGuard = guards.eq(enabled, "true");
        Domain.Guard mpGuard = guards.eq(flavor, "mp");
        Domain.Guard residual = guards.residualGuard(Expression.create("${unsupported}"));
        Domain.Guard enabledMpResidual = guards.and(guards.and(enabledGuard, mpGuard), residual);
        Domain.Guard enabledResidual = guards.and(enabledGuard, residual);

        assertThat(guards.implies(enabledMpResidual, enabledGuard), is(true));
        assertThat(guards.implies(enabledMpResidual, enabledResidual), is(true));
        assertThat(guards.or(enabledMpResidual, enabledGuard), is(enabledGuard));
    }

    @Test
    void testGuardOrMergesPureDecisionBranches() {
        Domain.Symbol.Table.Builder builder = Domain.Symbol.Table.builder();
        int enabled = builder.define("enabled", Domain.Spec.booleanSpec(), true, false);
        int flavor = builder.define("flavor", Domain.Spec.choice(Set.of("mp", "se")), true, false);
        Domain.Symbol.Table symbols = builder.build();
        Domain.Guards guards = new Domain.Guards(symbols);

        Domain.Guard enabledGuard = guards.eq(enabled, "true");
        Domain.Guard mpGuard = guards.eq(flavor, "mp");
        Domain.Guard seGuard = guards.eq(flavor, "se");
        Domain.Guard enabledMp = guards.and(enabledGuard, mpGuard);
        Domain.Guard enabledSe = guards.and(enabledGuard, seGuard);

        assertThat(guards.or(enabledMp, enabledSe), is(enabledGuard));
    }

    @Test
    void testGuardAndNarrowsMergedScalarChoice() {
        Domain.Symbol.Table.Builder builder = Domain.Symbol.Table.builder();
        int flavor = builder.define("flavor", Domain.Spec.choice(Set.of("mp", "nima", "se")), true, false);
        Domain.Symbol.Table symbols = builder.build();
        Domain.Guards guards = new Domain.Guards(symbols);
        Context.Scope scope = new Context().scope();

        Domain.Guard mpGuard = guards.eq(flavor, "mp");
        Domain.Guard seGuard = guards.eq(flavor, "se");
        Domain.Guard mpOrSe = guards.or(mpGuard, seGuard);

        assertThat(guards.toExpression(mpOrSe, scope).literal(), is("${flavor} != 'nima'"));
        assertThat(guards.equivalent(guards.and(mpOrSe, mpGuard), mpGuard), is(true));
    }

    @Test
    void testGuardContainsAllKeepsBroaderMembershipRequirement() {
        Domain.Symbol.Table.Builder builder = Domain.Symbol.Table.builder();
        int features = builder.define("features", Domain.Spec.finiteMembership(Set.of("grpc", "metrics", "rest")), true, false);
        Domain.Symbol.Table symbols = builder.build();
        Domain.Guards guards = new Domain.Guards(symbols);

        Domain.Guard restGuard = guards.contains(features, "rest");
        Domain.Guard grpcGuard = guards.contains(features, "grpc");
        Domain.Guard restAndGrpc = guards.and(restGuard, grpcGuard);
        Domain.Guard bulk = guards.containsAll(features, Set.of("grpc", "rest"));

        assertThat(guards.equivalent(restAndGrpc, bulk), is(true));
        assertThat(guards.implies(bulk, restGuard), is(true));
        assertThat(guards.or(bulk, restGuard), is(restGuard));
    }

    @Test
    void testFactExactCasesUseFiniteMasksInsteadOfRawLiteralShape() {
        Domain.Symbol.Table.Builder builder = Domain.Symbol.Table.builder();
        int enabled = builder.define("enabled", Domain.Spec.booleanSpec(), true, false);
        int features = builder.define("features", Domain.Spec.finiteMembership(Set.of("grpc", "rest")), true, false);
        Domain.Symbol.Table symbols = builder.build();
        Domain.Guards guards = new Domain.Guards(symbols);
        Domain.Symbol enabledSymbol = symbols.symbol(enabled);
        Domain.Symbol featuresSymbol = symbols.symbol(features);

        Domain.Symbol.Fact booleanTrue = Domain.Symbol.Fact.exact(
                TRUE,
                enabledSymbol.domain(),
                Domain.LatticeValue.finiteScalar(enabledSymbol.scalarMask("true")),
                Value.of(true));
        Domain.Symbol.Fact stringTrue = Domain.Symbol.Fact.exact(
                TRUE,
                enabledSymbol.domain(),
                Domain.LatticeValue.finiteScalar(enabledSymbol.scalarMask("true")),
                Value.of("true"));
        Domain.Symbol.Fact mergedBoolean = Domain.Symbol.Fact.merge(booleanTrue, stringTrue, guards);

        assertThat(mergedBoolean.exactCases().size(), is(1));
        assertThat(mergedBoolean.match(enabledSymbol, Value.of("true"), guards), is(TRUE));
        assertThat(mergedBoolean.scalarAny(enabledSymbol, Set.of("true"), guards), is(TRUE));

        Domain.Symbol.Fact grpcRest = Domain.Symbol.Fact.exact(
                TRUE,
                featuresSymbol.domain(),
                Domain.LatticeValue.membership(
                        featuresSymbol.membershipMask(Set.of("grpc", "rest")),
                        featuresSymbol.membershipMask(Set.of("grpc", "rest"))),
                Value.of(List.of("grpc", "rest")));
        Domain.Symbol.Fact restGrpc = Domain.Symbol.Fact.exact(
                TRUE,
                featuresSymbol.domain(),
                Domain.LatticeValue.membership(
                        featuresSymbol.membershipMask(Set.of("grpc", "rest")),
                        featuresSymbol.membershipMask(Set.of("grpc", "rest"))),
                Value.of(List.of("rest", "grpc")));
        Domain.Symbol.Fact mergedMembership = Domain.Symbol.Fact.merge(grpcRest, restGrpc, guards);

        assertThat(mergedMembership.exactCases().size(), is(1));
        assertThat(mergedMembership.listContains(featuresSymbol, Set.of("grpc", "rest"), guards), is(TRUE));
    }

    @Test
    void testSymbolTableRejectsUnknownSymbolId() {
        Domain.Symbol.Table.Builder builder = Domain.Symbol.Table.builder();
        builder.define("enabled", Domain.Spec.booleanSpec(), true, false);
        Domain.Symbol.Table symbols = builder.build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> symbols.symbol(99));

        assertThat(ex.getMessage(), containsString("Unknown symbol id"));
    }

    @Test
    void testGuardsRejectUnknownGuardId() {
        Domain.Symbol.Table.Builder builder = Domain.Symbol.Table.builder();
        int enabled = builder.define("enabled", Domain.Spec.booleanSpec(), true, false);
        Domain.Symbol.Table symbols = builder.build();
        Domain.Guards guards = new Domain.Guards(symbols);
        Domain.Guard valid = guards.eq(enabled, "true");
        Domain.Guard invalid = new Domain.Guard(99, valid.residual());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guards.toExpression(invalid, new Context().scope()));

        assertThat(ex.getMessage(), containsString("Unknown guard id"));
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    static <T, U extends T> Matcher<T> isSubtype(Class<U> type, Matcher<U>... matchers) {
        var list = new ArrayList<Matcher<? super T>>();
        list.add(instanceOf(type));
        for (Matcher<U> matcher : matchers) {
            list.add((Matcher<T>) matcher);
        }
        return allOf(list);
    }

    static <T, U> Matcher<T> hasProperty(String name, Function<T, U> extractor, Matcher<U> subMatcher) {
        return new FeatureMatcher<>(subMatcher, "has property " + name, name) {
            @Override
            protected U featureValueOf(T target) {
                return extractor.apply(target);
            }
        };
    }
}
