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

import java.util.List;
import java.util.Map;

import io.helidon.build.archetype.engine.v2.ast.Expression;
import io.helidon.build.archetype.engine.v2.ast.Expression.FormatException;
import io.helidon.build.archetype.engine.v2.ast.Expression.UnresolvedVariableException;
import io.helidon.build.archetype.engine.v2.ast.Value;
import io.helidon.build.archetype.engine.v2.ast.Value.ValueTypeException;

import org.junit.jupiter.api.Test;

import static io.helidon.build.archetype.engine.v2.ast.Expression.parse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link Expression}.
 */
class ExpressionTest {

    @Test
    public void testEvaluateWithVariables() {
        Expression exp;
        Map<String, Value> variables;

        exp = Expression.parse("${var1} contains ${var2}");
        variables = Map.of(
                "var1", Value.create(List.of("a", "b", "c")),
                "var2", Value.create("b"));
        assertThat(exp.eval(variables::get), is(true));

        exp = Expression.parse("!(${array} contains 'basic-auth' == false && ${var})");
        variables = Map.of(
                "var", Value.create(true),
                "array", Value.create(List.of("a", "b", "c")));
        assertThat(exp.eval(variables::get), is(false));

        exp = Expression.parse("!${var}");
        variables = Map.of("var", Value.create(true));
        assertThat(exp.eval(variables::get), is(false));

        exp = Expression.parse("['', 'adc', 'def'] contains ${var1} == ${var4} && ${var2} || !${var3}");
        variables = Map.of(
                "var1", Value.create("abc"),
                "var2", Value.create(false),
                "var3", Value.create(true),
                "var4", Value.create(true));
        assertThat(exp.eval(variables::get), is(false));

        exp = Expression.parse("${var1} contains ${var2} == ${var3} && ${var4} || ${var5}");
        variables = Map.of(
                "var1", Value.create(List.of("a", "b", "c")),
                "var2", Value.create("c"),
                "var3", Value.create(true),
                "var4", Value.create(true),
                "var5", Value.create(false));
        assertThat(exp.eval(variables::get), is(true));

        exp = Expression.parse(" ${var1} == ${var1} && ${var2} contains ''");
        variables = Map.of(
                "var1", Value.create("foo"),
                "var2", Value.create(List.of("d", "")));
        assertThat(exp.eval(variables::get), is(true));
    }

    @Test
    public void testVariable() {
        //noinspection SpellCheckingInspection
        Exception e = assertThrows(FormatException.class, () -> Expression.parse("${varia!ble}"));
        //noinspection SpellCheckingInspection
        assertThat(e.getMessage(), containsString("Unexpected token - ${varia!ble}"));
    }

    @Test
    public void testEvaluate() {
        assertThat(parse("['', 'adc', 'def'] contains 'foo'").eval(), is(false));
        assertThat(parse("!(['', 'adc', 'def'] contains 'foo' == false && false)").eval(), is(true));
        assertThat(parse("!false").eval(), is(true));
        assertThat(parse("['', 'adc', 'def'] contains 'foo' == false && true || !false").eval(), is(true));
        assertThat(parse("['', 'adc', 'def'] contains 'foo' == false && true || !true").eval(), is(true));
        assertThat(parse("['', 'adc', 'def'] contains 'def'").eval(), is(true));
        assertThat(parse("['', 'adc', 'def'] contains 'foo' == true && false").eval(), is(false));
        assertThat(parse("['', 'adc', 'def'] contains 'foo' == false && true").eval(), is(true));
        assertThat(parse(" 'aaa' == 'aaa' && ['', 'adc', 'def'] contains ''").eval(), is(true));
        assertThat(parse("true && \"bar\" == 'foo1' || true").eval(), is(true));
        assertThat(parse("true && \"bar\" == 'foo1' || false").eval(), is(false));
        assertThat(parse("('def' != 'def1') && false == true").eval(), is(false));
        assertThat(parse("('def' != 'def1') && false").eval(), is(false));
        assertThat(parse("('def' != 'def1') && true").eval(), is(true));
        assertThat(parse("'def' != 'def1'").eval(), is(true));
        assertThat(parse("'def' == 'def'").eval(), is(true));
        assertThat(parse("'def' != 'def'").eval(), is(false));
        assertThat(parse("true==((true|| false)&&true)").eval(), is(true));
        assertThat(parse("false==((true|| false)&&true)").eval(), is(false));
        assertThat(parse("false==((true|| false)&&false)").eval(), is(true));
        assertThat(parse("true == 'def'").eval(), is(false));

        Throwable e;

        e = assertThrows(ValueTypeException.class, () -> parse("'true' || 'def'").eval());
        assertThat(e.getMessage(), startsWith( "Cannot get a value of"));

        e = assertThrows(ValueTypeException.class, () -> parse("['', 'adc', 'def'] contains ['', 'adc', 'def']").eval());
        assertThat(e.getMessage(), startsWith( "Cannot get a value of"));

        e = assertThrows(UnresolvedVariableException.class, () -> parse("true == ${def}").eval());
        assertThat(e.getMessage(), containsString("Unresolved variable"));
    }

    @Test
    public void testContainsOperator() {
        assertThat(parse("['', 'adc', 'def'] contains 'foo'").eval(), is(false));

        FormatException e = assertThrows(FormatException.class, () -> parse("['', 'adc', 'def'] contains != 'foo'").eval());
        assertThat(e.getMessage(), startsWith("Missing operand"));

        assertThat(parse("!(['', 'adc', 'def'] contains 'foo')").eval(), is(true));

        e = assertThrows(FormatException.class, () -> parse("!['', 'adc', 'def'] contains 'basic-auth'"));
        assertThat(e.getMessage(), containsString("Invalid operand"));
    }

    @Test
    public void testUnaryLogicalExpression() {
        assertThat(parse("!true").eval(), is(false));
        assertThat(parse("!false").eval(), is(true));
        assertThat(parse("!('foo' != 'bar')").eval(), is(false));
        assertThat(parse("'foo1' == 'bar' && !('foo' != 'bar')").eval(), is(false));

        FormatException e = assertThrows(FormatException.class, () -> parse("!'string type'").eval());
        assertThat(e.getMessage(), containsString("Invalid operand"));
    }

    @Test
    public void testExpressionWithParenthesis() {
        assertThat(parse("(\"foo\") != \"bar\"").eval(), is(true));
        assertThat(parse("((\"foo\")) != \"bar\"").eval(), is(true));

        assertThat(parse("((\"foo\") != \"bar\")").eval(), is(true));
        assertThat(parse("\"foo\" != (\"bar\")").eval(), is(true));
        assertThat(parse("(\"foo\"==\"bar\")|| ${foo1}").eval(s -> Value.create(true)), is(true));
        assertThat(parse("(\"foo\"==\"bar\"|| true)").eval(), is(true));
        assertThat(parse("${foo}==(true|| false)").eval(s -> Value.create(true)), is(true));
        assertThat(parse("true==((${var}|| false)&&true)").eval(s -> Value.create(true)), is(true));

        FormatException e;

        e = assertThrows(FormatException.class, () -> parse("\"foo\"==((\"bar\"|| 'foo1')&&true))"));
        assertThat(e.getMessage(), containsString("Unmatched parenthesis"));

        e = assertThrows(FormatException.class, () -> parse("\"foo\")==((\"bar\"|| 'foo1')&&true))"));
        assertThat(e.getMessage(), containsString("Unmatched parenthesis"));

        e = assertThrows(FormatException.class, () -> parse("\"foo\"(==((\"bar\"|| 'foo1')&&true))"));
        assertThat(e.getMessage(), containsString("Invalid parenthesis"));

        e = assertThrows(FormatException.class, () -> parse(")\"foo\"(==((\"bar\"|| 'foo1')&&true))"));
        assertThat(e.getMessage(), containsString("Unmatched parenthesis"));
    }

    @Test
    public void testLiteralWithParenthesis() {
        assertThat(parse("(true)").eval(), is(true));
        assertThat(parse("((true))").eval(), is(true));
        assertThat(parse("((${var}))").eval(s -> Value.create(true)), is(true));
        assertThat(parse("(\"value\") == (\"value\")").eval(), is(true));
        assertThat(parse("((\"value\")) == ((\"value\"))").eval(), is(true));
        assertThat(parse("\"(value)\" == \"(value)\"").eval(), is(true));

        FormatException e;

        e = assertThrows(FormatException.class, () -> parse("((((\"value\"))").eval());
        assertThat(e.getMessage(), startsWith("Unmatched parenthesis"));

        e = assertThrows(FormatException.class, () -> parse(")\"value\"(").eval());
        assertThat(e.getMessage(), startsWith("Unmatched parenthesis"));

        e = assertThrows(FormatException.class, () -> parse("(\"value\"()").eval());
        assertThat(e.getMessage(), startsWith("Unmatched parenthesis"));

        assertThat(parse("([]) == []").eval(), is(true));
        assertThat(parse("(([])) == []").eval(), is(true));
        assertThat(parse("(['']) == ['']").eval(), is(true));
        assertThat(parse("(([''])) == ['']").eval(), is(true));
        assertThat(parse("(['', 'adc', 'def']) contains 'def'").eval(), is(true));
        assertThat(parse("((['', 'adc', 'def'])) contains 'def'").eval(), is(true));

        e = assertThrows(FormatException.class, () -> parse("((['', 'adc', 'def'])))").eval());
        assertThat(e.getMessage(), startsWith("Unmatched parenthesis"));

        e = assertThrows(FormatException.class, () -> parse("(((['', 'adc', 'def']))").eval());
        assertThat(e.getMessage(), startsWith("Unmatched parenthesis"));
    }

    @Test
    public void testPrecedence() {
        assertThat(parse("\"foo\"==\"bar\"|| true").eval(), is(true));
        assertThat(parse("\"foo\"==\"bar\" && true || false").eval(), is(false));
        assertThat(parse("true && \"bar\" != 'foo1'").eval(), is(true));
        assertThat(parse("true && ${bar} == 'foo1' || false").eval(s -> Value.create("foo1")), is(true));
    }

    @Test
    public void testEqualPrecedence() {
        assertThat(parse("\"foo\"!=\"bar\"==true").eval(), is(true));
        assertThat(parse("'foo'!=${var}==true").eval(s -> Value.create("bar")), is(true));
    }

    @Test
    public void testIncorrectOperator() {
        FormatException e = assertThrows(FormatException.class, () -> parse("'foo' !== 'bar'"));
        assertThat(e.getMessage(), startsWith("Unexpected token"));
    }

    @Test
    public void simpleNotEqualStringLiterals() {
        assertThat(parse("'foo' != 'bar'").eval(), is(true));
    }

    @Test
    public void testStringLiteralWithDoubleQuotes() {
        assertThat(parse("[\"value\"] == \"value\"").eval(), is(false));
    }

    @Test
    public void testStringLiteralWithSingleQuotes() {
        assertThat(parse("['value'] == 'value'").eval(), is(false));
    }

    @Test
    public void testStringLiteralWithWhitespaces() {
        assertThat(parse("[' value '] != 'value'").eval(), is(true));
    }

    @Test
    public void testBooleanLiteral() {
        assertThat(parse("true").eval(), is(true));
        assertThat(parse("false").eval(), is(false));
        assertThat(parse("true == true").eval(), is(true));
        assertThat(parse("false == false").eval(), is(true));
        assertThat(parse("true != false").eval(), is(true));
    }

    @Test
    public void testEmptyStringArrayLiteral() {
        assertThat(parse("[] == []").eval(), is(true));
    }

    @Test
    public void testArrayWithEmptyLiteral() {
        assertThat(parse("[''] contains ''").eval(), is(true));
    }

    @Test
    public void testArrayWithStringLiterals() {
        assertThat(parse("['foo'] contains 'foo'").eval(), is(true));
        assertThat(parse("['foo'] contains 'bar'").eval(), is(false));
    }
}
