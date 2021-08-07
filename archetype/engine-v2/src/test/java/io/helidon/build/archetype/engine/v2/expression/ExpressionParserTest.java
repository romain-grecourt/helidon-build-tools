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

import static io.helidon.build.archetype.engine.v2.expression.BinaryOperators.*;
import static io.helidon.build.archetype.engine.v2.expression.UnaryOperators.NOT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

public class ExpressionParserTest {

    private static String v(Expression expr) {
        assertThat(expr.expressionKind(), is(ExpressionKind.VALUE));
        assertThat(expr, is(instanceOf(Value.class)));
        return ((Value) expr).as(String.class);
    }

    private static String $(Expression expr) {
        assertThat(expr.expressionKind(), is(ExpressionKind.VARIABLE));
        assertThat(expr, is(instanceOf(Variable.class)));
        return ((Variable) expr).name();
    }

    private static BinaryExpression b(Expression expr) {
        assertThat(expr.expressionKind(), is(ExpressionKind.BINARY));
        assertThat(expr, is(instanceOf(BinaryExpression.class)));
        return (BinaryExpression) expr;
    }

    private static UnaryExpression u(Expression expr) {
        assertThat(expr.expressionKind(), is(ExpressionKind.UNARY));
        assertThat(expr, is(instanceOf(UnaryExpression.class)));
        return (UnaryExpression) expr;
    }

    private static Expression parse(String line) {
        return new ExpressionParser(line).parse();
    }

    @Test
    public void testLiteral() {
        Expression expr = parse("'foo'");
        assertThat(v(expr), is("foo"));

        expr = parse("''");
        assertThat(v(expr), is(""));

        expr = parse("((('foo')))");
        assertThat(v(expr), is(""));

        expr = parse("'$foo==$bar&&($bob!=$alice)^$red==$black'");
        assertThat(v(expr), is("$foo==$bar&&($bob!=$alice)^$red==$black"));

        expr = parse("'\\''");
        assertThat(v(expr), is("\""));

        expr = parse("'\\'");
        assertThat(v(expr), is("\\"));
    }

    @Test
    public void testVariable() {
        Expression expr = parse("$foo");
        assertThat($(expr), is("foo"));

        expr = parse("$foo.bar.");
        assertThat($(expr), is("foo.bar."));

        expr = parse("$foo-bar-");
        assertThat($(expr), is("foo-bar-"));

        expr = parse("$foo_bar_");
        assertThat($(expr), is("foo_bar_"));

        expr = parse("((($foo)))");
        assertThat($(expr), is("foo"));

        try {
            parse("$");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid empty variable")));
        }

        try {
            parse("$fo,o");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid ',' character found")));
        }
    }

    @Test
    public void testNotEqualWithLiterals() {
        Expression expr = parse("'foo' != 'bar'");
        assertThat(b(expr).operator(), is(NOT_EQUAL));
        assertThat(v(b(expr).left()), is("foo"));
        assertThat(v(b(expr).right()), is("bar"));
    }

    @Test
    public void testNotEqualWithVariables() {
        Expression expr = parse("$a != $b");
        assertThat(b(expr).operator(), is(NOT_EQUAL));
        assertThat($(b(expr).left()), is("a"));
        assertThat($(b(expr).right()), is("b"));
    }

    @Test
    public void testBracketedValueOperand() {
        Expression expr = parse("($a) == ($b)");
        assertThat(b(expr).operator(), is(EQUAL));
        assertThat($(b(expr).left()), is("a"));
        assertThat($(b(expr).right()), is("b"));
    }

    @Test
    public void testComplex() {
        Expression expr = parse("$a != $b || ($c == $d && $e == $f)");
        assertThat(b(expr).operator(), is(OR));
        assertThat(b(b(expr).left()).operator(), is(NOT_EQUAL));
        assertThat(b((b(b(expr).left()))).left(), is("a"));
        assertThat(b((b(b(expr).left()))).right(), is("a"));
        assertThat(b(b(expr).right()).operator(), is(AND));
        assertThat(b(b(b(b(expr).right())).left()).operator(), is(EQUAL));
        assertThat($(b(b(b(b(expr).right())).left()).left()), is("c"));
        assertThat($(b(b(b(b(expr).right())).left()).right()), is("d"));
        assertThat(b(b(b(b(expr).right())).right()).operator(), is(EQUAL));
        assertThat($(b(b(b(b(expr).right())).right()).left()), is("e"));
        assertThat($(b(b(b(b(expr).right())).right()).right()), is("f"));
    }

    @Test
    public void testUnmatchedBrackets() {
        try {
            parse("($e==$f");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Unmatched '(' found")));
        }

        try {
            parse("$e==$f)");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Unmatched ')' found")));
        }
    }

    @Test
    public void testIsNot() {
        Expression expr = parse("($a == 'foo') != ($b == 'bar')");
        assertThat(b(expr).operator(), is(NOT_EQUAL));
        assertThat(b(b(expr).left()).operator(), is(EQUAL));
        assertThat($(b(b(expr).left()).left()), is("a"));
        assertThat(v(b(b(expr).left()).right()), is("foo"));
        assertThat(b(b(expr).right()).operator(), is(EQUAL));
        assertThat($(b(b(expr).right()).left()), is("b"));
        assertThat(v(b(b(expr).right()).right()), is("bar"));
    }

    @Test
    public void testEqual() {
        Expression expr = parse("($a == \"foo\") == ($b == \"bar\")");
        assertThat(b(expr).operator(), is(EQUAL));
        assertThat(b(b(expr).left()).operator(), is(EQUAL));
        assertThat($(b(b(expr).left()).left()), is("a"));
        assertThat(v(b(b(expr).left()).right()), is("foo"));
        assertThat(b(b(expr).right()).operator(), is(EQUAL));
        assertThat($(b(b(expr).right()).left()), is("b"));
        assertThat(v(b(b(expr).right()).right()), is("bar"));
    }

    @Test
    public void testXor() {
        Expression expr = parse("($a == \"foo\") ^ ($b == \"bar\")");
        assertThat(b(expr).operator(), is(XOR));
        assertThat(b(b(expr).left()).operator(), is(EQUAL));
        assertThat($(b(b(expr).left()).left()), is("a"));
        assertThat(v(b(b(expr).left()).right()), is("foo"));
        assertThat(b(b(expr).right()).operator(), is(EQUAL));
        assertThat($(b(b(expr).right()).left()), is("b"));
        assertThat(v(b(b(expr).right()).right()), is("bar"));
    }

    @Test
    public void testNot() {
        Expression expr = parse("!($a == 'foo')");
        assertThat(u(expr).operator(), is(NOT));
        assertThat(b(u(expr).operand()).operator(), is(EQUAL));
        assertThat($(b(u(expr).operand()).left()), is("a"));
        assertThat(v(b(u(expr).operand()).left()), is("foo"));
    }

    @Test
    public void testComplexNot() {
        Expression expr = parse("$a == 'foo' && !($b == 'bar')");
        assertThat(b(expr).operator(), is(AND));
        assertThat(b(b(expr).left()).operator(), is(EQUAL));
        assertThat($(b(b(expr).left()).left()), is("a"));
        assertThat(v(b(b(expr).left()).left()), is("foo"));
        assertThat(u(b(expr).right()).operator(), is(NOT));
        assertThat(b(u(b(expr).right()).operand()).operator(), is(EQUAL));
        assertThat($(b(u(b(expr).right()).operand()).left()), is("b"));
        assertThat(v(b(u(b(expr).right()).operand()).left()), is("bar"));
    }

    @Test
    public void testAnd1() {
        Expression expr = parse("$c == $d && $e == $f");
        assertThat(b(expr).operator(), is(AND));
        assertThat(b(b(expr).left()).operator(), is(EQUAL));
        assertThat($(b(b(expr).left()).left()), is("c"));
        assertThat($(b(b(expr).left()).right()), is("d"));
        assertThat(b(b(expr).right()).operator(), is(EQUAL));
        assertThat($(b(b(expr).right()).left()), is("e"));
        assertThat($(b(b(expr).right()).right()), is("f"));
    }

    @Test
    public void testAnd2() {
        Expression expr = parse("$a == \"foo\" && $b == \"bar\" && $c == \"foobar\"");
        assertThat(b(expr).operator(), is(AND));
        assertThat(b(b(expr).left()).operator(), is(EQUAL));
        assertThat($(b(b(expr).left()).left()), is("a"));
        assertThat(v(b(b(expr).left()).right()), is("foo"));
        assertThat(b(b(expr).right()).operator(), is(AND));
        assertThat(b(b(b(expr).right()).left()).operator(), is(EQUAL));
        assertThat($(b(b(b(expr).right()).left()).left()), is("b"));
        assertThat(v(b(b(b(expr).right()).left()).left()), is("bar"));
        assertThat(b(b(b(expr).right()).right()).operator(), is(EQUAL));
        assertThat($(b(b(b(expr).right()).left()).left()), is("c"));
        assertThat(v(b(b(b(expr).right()).left()).left()), is("foobar"));
    }

    @Test
    public void testFlatAndOr() {
        Expression expr = parse("$a == \"foo\" && $b == \"bar\" || $c == \"foobar\"");
        assertThat(b(expr).operator(), is(AND));
        assertThat(b(b(expr).left()).operator(), is(EQUAL));
        assertThat($(b(b(expr).left()).left()), is("a"));
        assertThat(v(b(b(expr).left()).right()), is("foo"));
        assertThat(b(b(expr).right()).operator(), is(OR));
        assertThat(b(b(b(expr).right()).left()).operator(), is(EQUAL));
        assertThat($(b(b(b(expr).right()).left()).left()), is("b"));
        assertThat(v(b(b(b(expr).right()).left()).left()), is("bar"));
        assertThat(b(b(b(expr).right()).right()).operator(), is(EQUAL));
        assertThat($(b(b(b(expr).right()).left()).left()), is("c"));
        assertThat(v(b(b(b(expr).right()).left()).left()), is("foobar"));
    }

    @Test
    public void testBadOperators() {
        try {
            parse("$e===$f)");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '=' character found")));
        }

        try {
            parse("$a == $b &&& $c == $d)");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '&' character found")));
        }

        try {
            parse("$a == $b ||| $c == $d)");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '|' character found")));
        }

        try {
            parse("$a == $b ^^^ $c == $d)");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '^' character found")));
        }

        try {
            parse("$a == $b && !!($c == $d)");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '!' character found")));
        }

        try {
            parse("$a = $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid ' ' character found")));
        }

        try {
            parse("$a !!= $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '!' character found")));
        }

        try {
            parse("$a !== $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '=' character found")));
        }

        try {
            parse("($a == \"foo\") = ($b == \"foo\")");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid ' ' character found")));
        }

        try {
            parse("$a | $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid ' ' character found")));
        }

        try {
            parse("$a & $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid ' ' character found")));
        }

        try {
            parse("$a ^ $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid ' ' character found")));
        }
    }

    @Test
    public void testNoLeftOperand() {
        try {
            parse("== $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("No left operand found for operator")));
        }

        try {
            parse("== $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("No left operand found for operator")));
        }

        try {
            parse("== \"foo\" && $b == \"bar\"");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("No left operand found for operator")));
        }

        try {
            parse("$a == \"foo\" && == \"bar\"");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '=' character found")));
        }
    }

    @Test
    public void testNoRightOperand() {
        try {
            parse("$a ==");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("No right operand found for operator")));
        }
        try {
            parse("$a == \"foo\" &&");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("No right operand found for operator")));
        }
        try {
            parse("$a == \"foo\" || $b ==");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("No right operand found for operator")));
        }
        try {
            parse("$a == \"foo\" && $b == || $c == \"bar\"");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '|' character found")));
        }
        try {
            parse("$a == \"foo\" && ($b ==) || $c == \"bar\"");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("No right operand found for operator")));
        }
    }
}
