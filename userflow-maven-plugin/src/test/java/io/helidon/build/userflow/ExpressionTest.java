/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.build.userflow;

import io.helidon.build.userflow.Expression.ParserException;
import io.helidon.build.userflow.ExpressionSyntaxTree.And;
import io.helidon.build.userflow.ExpressionSyntaxTree.Is;
import io.helidon.build.userflow.ExpressionSyntaxTree.IsNot;
import io.helidon.build.userflow.ExpressionSyntaxTree.Not;
import io.helidon.build.userflow.ExpressionSyntaxTree.Or;
import io.helidon.build.userflow.ExpressionSyntaxTree.Xor;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@link Expression}.
 */
public class ExpressionTest {

    @Test
    public void testLiteral() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("\"foo\"").tree();
        assertThat(tree.isValue(), is(equalTo(true)));
        assertThat(tree.asValue().isLiteral(), is(equalTo(true)));
        assertThat(tree.asValue().value(), is(equalTo("foo")));

        tree = new Expression("\"\"").tree();
        assertThat(tree.isValue(), is(equalTo(true)));
        assertThat(tree.asValue().isLiteral(), is(equalTo(true)));
        assertThat(tree.asValue().asLiteral().value(), is(equalTo("")));

        tree = new Expression("(((\"foo\")))").tree();
        assertThat(tree.isValue(), is(equalTo(true)));
        assertThat(tree.asValue().isLiteral(), is(equalTo(true)));
        assertThat(tree.asValue().asLiteral().value(), is(equalTo("foo")));

        tree = new Expression("\"$foo==$bar&&($bob!=$alice)^^$red==$black\"").tree();
        assertThat(tree.isValue(), is(equalTo(true)));
        assertThat(tree.asValue().isLiteral(), is(equalTo(true)));
        assertThat(tree.asValue().value(), is(equalTo("$foo==$bar&&($bob!=$alice)^^$red==$black")));

        tree = new Expression("\"\\\"\"").tree();
        assertThat(tree.isValue(), is(equalTo(true)));
        assertThat(tree.asValue().isLiteral(), is(equalTo(true)));
        assertThat(tree.asValue().value(), is(equalTo("\"")));
    }

    @Test
    public void testVariable() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("$foo").tree();
        assertThat(tree.isValue(), is(equalTo(true)));
        assertThat(tree.asValue().isVariable(), is(equalTo(true)));
        assertThat(tree.asValue().value(), is(equalTo("foo")));

        tree = new Expression("$foo.bar.").tree();
        assertThat(tree.isValue(), is(equalTo(true)));
        assertThat(tree.asValue().isVariable(), is(equalTo(true)));
        assertThat(tree.asValue().value(), is(equalTo("foo.bar.")));

        tree = new Expression("$foo-bar-").tree();
        assertThat(tree.isValue(), is(equalTo(true)));
        assertThat(tree.asValue().isVariable(), is(equalTo(true)));
        assertThat(tree.asValue().value(), is(equalTo("foo-bar-")));

        tree = new Expression("$foo_bar_").tree();
        assertThat(tree.isValue(), is(equalTo(true)));
        assertThat(tree.asValue().isVariable(), is(equalTo(true)));
        assertThat(tree.asValue().value(), is(equalTo("foo_bar_")));

        tree = new Expression("((($foo)))").tree();
        assertThat(tree.isValue(), is(equalTo(true)));
        assertThat(tree.asValue().isVariable(), is(equalTo(true)));
        assertThat(tree.asValue().value(), is(equalTo("foo")));

        try {
            new Expression("$");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid empty variable")));
        }

        try {
            new Expression("$fo,o");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid ',' character found")));
        }
    }

    @Test
    public void testNotEqualWithLiterals() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("\"foo\" != \"bar\"").tree();
        assertThat(tree.isExpression(), is(equalTo(true)));
        assertThat(tree.asExpression().isNotEqual(), is(equalTo(true)));
        assertThat(tree.asExpression().asNotEqual().left().isLiteral(), is(equalTo(true)));
        assertThat(tree.asExpression().asNotEqual().left().value(), is(equalTo("foo")));
        assertThat(tree.asExpression().asNotEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(tree.asExpression().asNotEqual().right().value(), is(equalTo("bar")));
    }

    @Test
    public void testNotEqualWithVariables() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("$a != $b").tree();
        assertThat(tree.isExpression(), is(equalTo(true)));
        assertThat(tree.asExpression().isNotEqual(), is(equalTo(true)));
        assertThat(tree.asExpression().asNotEqual().left().isVariable(), is(equalTo(true)));
        assertThat(tree.asExpression().asNotEqual().left().value(), is(equalTo("a")));
        assertThat(tree.asExpression().asNotEqual().right().isVariable(), is(equalTo(true)));
        assertThat(tree.asExpression().asNotEqual().right().value(), is(equalTo("b")));
    }

    @Test
    public void testBracketedValueOperand() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("($a) == ($b)").tree();
        assertThat(tree.isExpression(), is(equalTo(true)));
        assertThat(tree.asExpression().isEqual(), is(equalTo(true)));
        assertThat(tree.asExpression().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(tree.asExpression().asEqual().left().value(), is(equalTo("a")));
        assertThat(tree.asExpression().asEqual().right().isVariable(), is(equalTo(true)));
        assertThat(tree.asExpression().asEqual().right().value(), is(equalTo("b")));
    }

    @Test
    public void testComplex() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("$a != $b || ($c == $d && $e == $f)").tree();
        assertThat(tree.isExpression(), is(equalTo(true)));
        assertThat(tree.asExpression().isOr(), is(equalTo(true)));
        Or or = tree.asExpression().asOr();
        assertThat(or.left().isNotEqual(), is(equalTo(true)));
        assertThat(or.left().asNotEqual().left().isVariable(), is(equalTo(true)));
        assertThat(or.left().asNotEqual().left().value(), is(equalTo("a")));
        assertThat(or.left().asNotEqual().right().isVariable(), is(equalTo(true)));
        assertThat(or.left().asNotEqual().right().value(), is(equalTo("b")));
        assertThat(or.right().isAnd(), is(equalTo(true)));
        assertThat(or.right().asAnd().left().isEqual(), is(equalTo(true)));
        assertThat(or.right().asAnd().left().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(or.right().asAnd().left().asEqual().left().value(), is(equalTo("c")));
        assertThat(or.right().asAnd().left().asEqual().right().isVariable(), is(equalTo(true)));
        assertThat(or.right().asAnd().left().asEqual().right().value(), is(equalTo("d")));
        assertThat(or.right().asAnd().right().isEqual(), is(equalTo(true)));
        assertThat(or.right().asAnd().right().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(or.right().asAnd().right().asEqual().left().value(), is(equalTo("e")));
        assertThat(or.right().asAnd().right().asEqual().right().isVariable(), is(equalTo(true)));
        assertThat(or.right().asAnd().right().asEqual().right().value(), is(equalTo("f")));
    }

    @Test
    public void testUnmatchedBrackets() {
        try {
            new Expression("($e==$f");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Unmatched '(' found")));
        }

        try {
            new Expression("$e==$f)");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Unmatched ')' found")));
        }
    }

    @Test
    public void testIsNot() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("($a == \"foo\") != ($b == \"bar\")").tree();
        assertThat(tree.isExpression(), is(equalTo(true)));
        assertThat(tree.asExpression().isIsNot(), is(equalTo(true)));
        IsNot isNot = tree.asExpression().asIsNot();
        assertThat(isNot.left().isEqual(), is(equalTo(true)));
        assertThat(isNot.left().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(isNot.left().asEqual().left().value(), is(equalTo("a")));
        assertThat(isNot.left().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(isNot.left().asEqual().right().value(), is(equalTo("foo")));
        assertThat(isNot.right().isEqual(), is(equalTo(true)));
        assertThat(isNot.right().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(isNot.right().asEqual().left().value(), is(equalTo("b")));
        assertThat(isNot.right().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(isNot.right().asEqual().right().value(), is(equalTo("bar")));
    }

    @Test
    public void testIs() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("($a == \"foo\") == ($b == \"bar\")").tree();
        assertThat(tree.isExpression(), is(equalTo(true)));
        assertThat(tree.asExpression().isIs(), is(equalTo(true)));
        Is is = tree.asExpression().asIs();
        assertThat(is.left().isEqual(), is(equalTo(true)));
        assertThat(is.left().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(is.left().asEqual().left().value(), is(equalTo("a")));
        assertThat(is.left().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(is.left().asEqual().right().value(), is(equalTo("foo")));
        assertThat(is.right().isEqual(), is(equalTo(true)));
        assertThat(is.right().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(is.right().asEqual().left().value(), is(equalTo("b")));
        assertThat(is.right().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(is.right().asEqual().right().value(), is(equalTo("bar")));
    }

    @Test
    public void testXor() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("($a == \"foo\") ^^ ($b == \"bar\")").tree();
        assertThat(tree.isExpression(), is(equalTo(true)));
        assertThat(tree.asExpression().isXor(), is(equalTo(true)));
        Xor xor = tree.asExpression().asXor();
        assertThat(xor.left().isEqual(), is(equalTo(true)));
        assertThat(xor.left().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(xor.left().asEqual().left().value(), is(equalTo("a")));
        assertThat(xor.left().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(xor.left().asEqual().right().value(), is(equalTo("foo")));
        assertThat(xor.right().isEqual(), is(equalTo(true)));
        assertThat(xor.right().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(xor.right().asEqual().left().value(), is(equalTo("b")));
        assertThat(xor.right().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(xor.right().asEqual().right().value(), is(equalTo("bar")));
    }

    @Test
    public void testNot() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("!($a == \"foo\")").tree();
        assertThat(tree.isExpression(), is(equalTo(true)));
        assertThat(tree.asExpression().isNot(), is(equalTo(true)));
        Not not = tree.asExpression().asNot();
        assertThat(not.right().isEqual(), is(equalTo(true)));
        assertThat(not.right().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(not.right().asEqual().left().value(), is(equalTo("a")));
        assertThat(not.right().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(not.right().asEqual().right().value(), is(equalTo("foo")));
    }

    @Test
    public void testComplexNot() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("$a == \"foo\" && !($b == \"bar\")").tree();
        assertThat(tree.isExpression(), is(equalTo(true)));
        assertThat(tree.asExpression().isAnd(), is(equalTo(true)));
        And and = tree.asExpression().asAnd();
        assertThat(and.left().isEqual(), is(equalTo(true)));
        assertThat(and.left().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(and.left().asEqual().left().value(), is(equalTo("a")));
        assertThat(and.left().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(and.left().asEqual().right().value(), is(equalTo("foo")));
        assertThat(and.right().isNot(), is(equalTo(true)));
        assertThat(and.right().asNot().right().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(and.right().asNot().right().asEqual().left().value(), is(equalTo("b")));
        assertThat(and.right().asNot().right().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(and.right().asNot().right().asEqual().right().value(), is(equalTo("bar")));
    }

    @Test
    public void testFlatAnd() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("$c == $d && $e == $f").tree();
        assertThat(tree.isExpression(), is(equalTo(true)));
        assertThat(tree.asExpression().isAnd(), is(equalTo(true)));
        And and = tree.asExpression().asAnd();
        assertThat(and.left().isEqual(), is(equalTo(true)));
        assertThat(and.left().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(and.left().asEqual().left().value(), is(equalTo("c")));
        assertThat(and.left().asEqual().right().isVariable(), is(equalTo(true)));
        assertThat(and.left().asEqual().right().value(), is(equalTo("d")));
        assertThat(and.right().isEqual(), is(equalTo(true)));
        assertThat(and.right().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(and.right().asEqual().left().value(), is(equalTo("e")));
        assertThat(and.right().asEqual().right().isVariable(), is(equalTo(true)));
        assertThat(and.right().asEqual().right().value(), is(equalTo("f")));
    }

    @Test
    public void testEqualIsEqual() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("($a == \"foo\") == ($b == \"bar\")").tree();
        assertThat(tree.isExpression(), is(equalTo(true)));
        assertThat(tree.asExpression().isIs(), is(equalTo(true)));
        Is is = tree.asExpression().asIs();
        assertThat(is.left().isEqual(), is(equalTo(true)));
        assertThat(is.left().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(is.left().asEqual().left().value(), is(equalTo("a")));
        assertThat(is.left().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(is.left().asEqual().right().value(), is(equalTo("foo")));
        assertThat(is.right().isEqual(), is(equalTo(true)));
        assertThat(is.right().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(is.right().asEqual().left().value(), is(equalTo("b")));
        assertThat(is.right().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(is.right().asEqual().right().value(), is(equalTo("bar")));
    }

    @Test
    public void testFlatAndAnd() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("$a == \"foo\" && $b == \"bar\" && $c == \"foobar\"").tree();
        assertThat(tree.isExpression(), is(equalTo(true)));
        assertThat(tree.asExpression().isAnd(), is(equalTo(true)));
        And and = tree.asExpression().asAnd();
        assertThat(and.left().isEqual(), is(equalTo(true)));
        assertThat(and.left().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(and.left().asEqual().left().value(), is(equalTo("a")));
        assertThat(and.left().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(and.left().asEqual().right().value(), is(equalTo("foo")));
        assertThat(and.right().isAnd(), is(equalTo(true)));
        assertThat(and.right().asAnd().left().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(and.right().asAnd().left().asEqual().left().value(), is(equalTo("b")));
        assertThat(and.right().asAnd().left().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(and.right().asAnd().left().asEqual().right().value(), is(equalTo("bar")));
        assertThat(and.right().asAnd().right().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(and.right().asAnd().right().asEqual().left().value(), is(equalTo("c")));
        assertThat(and.right().asAnd().right().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(and.right().asAnd().right().asEqual().right().value(), is(equalTo("foobar")));
    }

    @Test
    public void testFlatAndOr() throws ParserException {
        ExpressionSyntaxTree tree = new Expression("$a == \"foo\" && $b == \"bar\" || $c == \"foobar\"").tree();
        assertThat(tree.isExpression(), is(equalTo(true)));
        assertThat(tree.asExpression().isAnd(), is(equalTo(true)));
        And and = tree.asExpression().asAnd();
        assertThat(and.left().isEqual(), is(equalTo(true)));
        assertThat(and.left().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(and.left().asEqual().left().value(), is(equalTo("a")));
        assertThat(and.left().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(and.left().asEqual().right().value(), is(equalTo("foo")));
        assertThat(and.right().isOr(), is(equalTo(true)));
        assertThat(and.right().asOr().left().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(and.right().asOr().left().asEqual().left().value(), is(equalTo("b")));
        assertThat(and.right().asOr().left().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(and.right().asOr().left().asEqual().right().value(), is(equalTo("bar")));
        assertThat(and.right().asOr().right().asEqual().left().isVariable(), is(equalTo(true)));
        assertThat(and.right().asOr().right().asEqual().left().value(), is(equalTo("c")));
        assertThat(and.right().asOr().right().asEqual().right().isLiteral(), is(equalTo(true)));
        assertThat(and.right().asOr().right().asEqual().right().value(), is(equalTo("foobar")));
    }

    @Test
    public void testBadOperators() {
        try {
            new Expression("$e===$f)");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '=' character found")));
        }

        try {
            new Expression("$a == $b &&& $c == $d)");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '&' character found")));
        }

        try {
            new Expression("$a == $b ||| $c == $d)");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '|' character found")));
        }

        try {
            new Expression("$a == $b ^^^ $c == $d)");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '^' character found")));
        }

        try {
            new Expression("$a == $b && !!($c == $d)");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '!' character found")));
        }

        try {
            new Expression("$a = $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid ' ' character found")));
        }

        try {
            new Expression("$a !!= $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '!' character found")));
        }

        try {
            new Expression("$a !== $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '=' character found")));
        }

        try {
            new Expression("($a == \"foo\") = ($b == \"foo\")");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid ' ' character found")));
        }

        try {
            new Expression("$a | $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid ' ' character found")));
        }

        try {
            new Expression("$a & $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid ' ' character found")));
        }

        try {
            new Expression("$a ^ $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid ' ' character found")));
        }
    }

    @Test
    public void testNoLeftOperand() {
        try {
            new Expression("== $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("No left operand found for operator")));
        }

        try {
            new Expression("== $b");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("No left operand found for operator")));
        }

        try {
            new Expression("== \"foo\" && $b == \"bar\"");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("No left operand found for operator")));
        }

        try {
            new Expression("$a == \"foo\" && == \"bar\"");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '=' character found")));
        }
    }

    @Test
    public void testNoRightOperand() {
        try {
            new Expression("$a ==");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("No right operand found for operator")));
        }
        try {
            new Expression("$a == \"foo\" &&");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("No right operand found for operator")));
        }
        try {
            new Expression("$a == \"foo\" || $b ==");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("No right operand found for operator")));
        }
        try {
            new Expression("$a == \"foo\" && $b == || $c == \"bar\"");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("Invalid '|' character found")));
        }
        try {
            new Expression("$a == \"foo\" && ($b ==) || $c == \"bar\"");
            fail("An exception should have been thrown");
        } catch (ParserException ex) {
            assertThat(ex.getMessage(), is(startsWith("No right operand found for operator")));
        }
    }
}
