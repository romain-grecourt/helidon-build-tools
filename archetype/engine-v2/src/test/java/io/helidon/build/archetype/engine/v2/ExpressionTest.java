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
package io.helidon.build.archetype.engine.v2;

import io.helidon.build.archetype.engine.v2.Expression.ExpressionException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

public class ExpressionTest {

    @Test
    public void testStringLiteral() {
        assertThat(new Expression("'foo' == 'foo'").eval(Map.of()), is(true));
        assertThat(new Expression("'foo' != 'bar'").eval(Map.of()), is(true));
    }

    @Test
    public void testBooleanLiteral() {
        assertThat(new Expression("true").eval(Map.of()), is(true));
        assertThat(new Expression("false").eval(Map.of()), is(false));
        assertThat(new Expression("true == true").eval(Map.of()), is(true));
        assertThat(new Expression("false == false").eval(Map.of()), is(true));
        assertThat(new Expression("true == false").eval(Map.of()), is(false));
        assertThat(new Expression("false == true").eval(Map.of()), is(false));
        assertThat(new Expression("true != false").eval(Map.of()), is(true));
        assertThat(new Expression("false != true").eval(Map.of()), is(true));
        assertThat(new Expression("true != true").eval(Map.of()), is(false));
        assertThat(new Expression("false != false").eval(Map.of()), is(false));
    }

    @Test
    public void testVariable() {
        assertThat(new Expression("$foo == 'bar'").eval(Map.of("foo", "bar")), is(true));
        assertThat(new Expression("$foo == 'foo'").eval(Map.of("foo", "bar")), is(false));
        assertThat(new Expression("$foo != 'foo'").eval(Map.of("foo", "bar")), is(true));
        assertThat(new Expression("$foo == $bar").eval(Map.of("foo", "bar", "bar", "bar")), is(true));
        assertThat(new Expression("$foo == $bar").eval(Map.of("foo", "bar", "bar", "foo")), is(false));
    }

    @Test
    public void testAnd() {
        assertThat(new Expression("true && true").eval(), is(true));
        assertThat(new Expression("false && false").eval(), is(false));
        assertThat(new Expression("true && false").eval(), is(false));
        assertThat(new Expression("false && true").eval(), is(false));
    }

    @Test
    public void testOr() {
        assertThat(new Expression("true || true").eval(), is(true));
        assertThat(new Expression("false || false").eval(), is(false));
        assertThat(new Expression("true || false").eval(), is(true));
        assertThat(new Expression("false || true").eval(), is(true));
    }

    @Test
    public void testNot() {
        assertThat(new Expression("!true").eval(), is(false));
        assertThat(new Expression("!false").eval(), is(true));
    }

    @Test
    public void testBadOperators() {
        try {
            new Expression("$e===$f");
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid character '='")));
        }

        try {
            new Expression("$a == $b &&& $c == $d)");
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid character '&'")));
        }

        try {
            new Expression("$a == $b ||| $c == $d)");
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid character '|'")));
        }

        try {
            new Expression("$a == $b && !!($c == $d)");
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid character '!'")));
        }

        try {
            new Expression("$a = $b");
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid character ' '")));
        }

        try {
            new Expression("$a !!= $b");
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid character '!'")));
        }

        try {
            new Expression("$a !== $b");
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid character '='")));
        }

        try {
            new Expression("($a == 'foo') = ($b == 'foo')");
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid character ' '")));
        }

        try {
            new Expression("$a | $b");
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid character ' '")));
        }

        try {
            new Expression("$a & $b");
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid character ' '")));
        }

        try {
            new Expression("$a ^ $b");
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid character '^'")));
        }
    }

    @Test
    public void testNoLeftOperand() {
        try {
            new Expression("== $b").eval(Map.of("b", "foo"));
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Missing operand")));
        }

        try {
            new Expression("== $b").eval(Map.of("b", "foo"));
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Missing operand")));
        }

        try {
            new Expression("== 'foo' && $b == 'bar'").eval(Map.of("b", "foo"));
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Missing operand")));
        }

        try {
            new Expression("$a == 'foo' && == 'bar'");
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid character '='")));
        }
    }

    @Test
    public void testNoRightOperand() {
        try {
            new Expression("$a ==").eval(Map.of("a", "foo"));
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Missing operand")));
        }

        try {
            new Expression("$a == 'foo' &&").eval(Map.of("a", "foo"));
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Missing operand")));
        }

        try {
            new Expression("$a == 'foo' || $b ==")
                    .eval(Map.of("a", "foo", "b", "bar"));
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Missing operand")));
        }

        try {
            new Expression("$a == 'foo' && $b == || $c == 'bar'");
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid character '|'")));
        }

        try {
            new Expression("$a == 'foo' && ($b ==) || $c == 'bar'");
            fail("An exception should have been thrown");
        } catch (ExpressionException ex) {
            assertThat(ex.getMessage(), is(startsWith("Missing operator")));
        }
    }

    // TODO test precedence
    // TODO test compound
    // TODO test contains / null
    // TODO test normalization
}
