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
import java.util.Set;
import java.util.function.Function;

import io.helidon.build.archetype.engine.v2.Domain.Value.BooleanSet;
import io.helidon.build.archetype.engine.v2.Domain.Value.ChoiceSet;
import io.helidon.build.archetype.engine.v2.Domain.Value.ListSummary;
import io.helidon.build.archetype.engine.v2.Domain.Value.OpenText;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class DomainTest {

    @Test
    void testTopValuesFollowSpecs() {
        Domain.Value booleanTop = Domain.Value.top(new Domain.Spec.Boolean());
        Domain.Value choiceTop = Domain.Value.top(new Domain.Spec.Choice(Set.of("mp", "se")));
        Domain.Value membershipTop = Domain.Value.top(new Domain.Spec.Membership(Set.of("rest", "grpc")));
        Domain.Value openTextTop = Domain.Value.top(new Domain.Spec.OpenText());

        assertThat(booleanTop, isSubtype(BooleanSet.class,
                hasProperty("values", BooleanSet::values, is(Set.of(false, true)))));

        assertThat(choiceTop, isSubtype(ChoiceSet.class,
                hasProperty("values", ChoiceSet::values, is(Set.of("mp", "se")))));

        assertThat(membershipTop, isSubtype(ListSummary.class,
                hasProperty("possible", ListSummary::possible, is(Set.of("grpc", "rest")))));

        assertThat(openTextTop, isSubtype(OpenText.class,
                hasProperty("sample", OpenText::sample, is(nullValue()))));
    }

    @Test
    void testGuardAlgebraRendersFiniteConditions() {
        Domain.Symbol.Table.Builder builder = Domain.Symbol.Table.builder();
        int enabled = builder.define("enabled", new Domain.Spec.Boolean(), true, false);
        int flavor = builder.define("flavor", new Domain.Spec.Choice(Set.of("mp", "se")), true, false);
        int features = builder.define("features", new Domain.Spec.Membership(Set.of("rest", "grpc")), true, false);
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
        assertThat(guards.equivalent(guards.or(mpGuard, nonMpGuard), guards.trueGuard()), is(true));
    }

    @Test
    void testGuardImplicationKeepsBroaderPureDecision() {
        Domain.Symbol.Table.Builder builder = Domain.Symbol.Table.builder();
        int enabled = builder.define("enabled", new Domain.Spec.Boolean(), true, false);
        int flavor = builder.define("flavor", new Domain.Spec.Choice(Set.of("mp", "se")), true, false);
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
        int enabled = builder.define("enabled", new Domain.Spec.Boolean(), true, false);
        int flavor = builder.define("flavor", new Domain.Spec.Choice(Set.of("mp", "se")), true, false);
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
        int flavor = builder.define("flavor", new Domain.Spec.Choice(Set.of("mp", "nima", "se")), true, false);
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
        int features = builder.define("features", new Domain.Spec.Membership(Set.of("grpc", "metrics", "rest")), true, false);
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
