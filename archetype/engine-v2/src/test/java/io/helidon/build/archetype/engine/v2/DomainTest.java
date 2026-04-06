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

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class DomainTest {

    @Test
    void testTopValuesFollowSpecs() {
        Domain.Value booleanTop = Domain.Value.top(new Domain.Spec.Boolean());
        Domain.Value choiceTop = Domain.Value.top(new Domain.Spec.Choice(Set.of("mp", "se")));
        Domain.Value membershipTop = Domain.Value.top(new Domain.Spec.Membership(Set.of("rest", "grpc")));
        Domain.Value openTextTop = Domain.Value.top(new Domain.Spec.OpenText());

        assertThat(booleanTop, instanceOf(Domain.Value.BooleanSet.class));
        assertThat(((Domain.Value.BooleanSet) booleanTop).values(), is(Set.of(false, true)));

        assertThat(choiceTop, instanceOf(Domain.Value.ChoiceSet.class));
        assertThat(((Domain.Value.ChoiceSet) choiceTop).values(), is(Set.of("mp", "se")));

        assertThat(membershipTop, instanceOf(Domain.Value.ListSummary.class));
        assertThat(((Domain.Value.ListSummary) membershipTop).possible(), is(Set.of("grpc", "rest")));

        assertThat(openTextTop, instanceOf(Domain.Value.OpenText.class));
        assertThat(((Domain.Value.OpenText) openTextTop).sample(), is((String) null));
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
}
