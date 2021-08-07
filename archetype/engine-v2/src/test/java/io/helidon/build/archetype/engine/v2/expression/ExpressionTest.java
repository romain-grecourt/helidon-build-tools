/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.build.archetype.engine.v2.expression;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.helidon.build.archetype.engine.v2.expression.UnaryOperators.*;
import static io.helidon.build.archetype.engine.v2.expression.BinaryOperators.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class ExpressionTest {

    private static boolean eval(Expression expr, Map<String, String> variables) {
        return expr.eval(variables).as(Boolean.class);
    }

    private static boolean eval(Expression expr) {
        return expr.eval().as(Boolean.class);
    }

    @Test
    public void testLiteralExpression() {
        assertThat(eval(Literal.of(true)), is(true));
        assertThat(eval(Literal.of(false)), is(false));
    }

    @Test
    public void testVariable() {
        assertThat(new Variable("$foo").eval(Map.of("foo", "bar")), is("bar"));
    }

    @Test
    public void testNot() {
        assertThat(new UnaryExpression(NOT, Literal.of(false)).eval(), is(true));
        assertThat(new UnaryExpression(NOT, Literal.of(true)).eval(), is(false));
    }

    @Test
    public void testEqual() {
        assertThat(new BinaryExpression(EQUAL, Literal.of(false), Literal.of(false)).eval(), is(true));
        assertThat(new BinaryExpression(EQUAL, Literal.of(true), Literal.of(true)).eval(), is(true));
        assertThat(new BinaryExpression(EQUAL, Literal.of(true), Literal.of(false)).eval(), is(false));
        assertThat(new BinaryExpression(EQUAL, Literal.of(false), Literal.of(true)).eval(), is(false));
    }

    @Test
    public void testNotEqual() {
        assertThat(new BinaryExpression(NOT_EQUAL, Literal.of(false), Literal.of(false)).eval(), is(false));
        assertThat(new BinaryExpression(NOT_EQUAL, Literal.of(true), Literal.of(true)).eval(), is(false));
        assertThat(new BinaryExpression(NOT_EQUAL, Literal.of(true), Literal.of(false)).eval(), is(true));
        assertThat(new BinaryExpression(NOT_EQUAL, Literal.of(false), Literal.of(true)).eval(), is(true));
    }

    // TODO AND
    // TODO OR
    // TODO XOR
    // TODO contains
    // TODO NOT NOT
    // TODO NOT NOT_EQUAL
    // TODO NOT EQUAL
    // TODO NOT AND
    // TODO NOT OR
}
