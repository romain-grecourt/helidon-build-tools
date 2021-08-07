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

import java.util.Map;
import java.util.function.Function;

// TODO rename into ExpressionNode and make it package private
// TODO Variable is still a Value (ValueKind.VARIABLE)
//      - variable.get() returns the variable name
// TODO Value is not an ExpressionNode (thus Literal is not)
// TODO re-create NullaryOperation to represent an operation without operand that holds a Value (i.e Variable or Literal)
//      evaluator checks the ValueKind and if VARIABLE, creates a Value from the variable
// TODO rename BinaryExpression and UnaryExpression to BinaryOperation and UnaryOperation
// TODO make Expression an evaluator that is recursion less
//    - uses an ExpressionNode[] and a volatile pointer at the current instruction
//    - operations evaluate off values not expressions
//    - test by building a tree with a huge depth (E.g. 1000) and doing an evaluation (No stack overflow)
public interface Expression {

    Value eval(Function<String, String> resolver) throws EvaluationException;

    default Value eval(Map<String, String> variables) throws EvaluationException {
        return eval(variables::get);
    }

    default Value eval() throws EvaluationException {
        return eval(v -> null);
    }

    ExpressionKind expressionKind();

    static Expression parse(String line) throws ParserException {
        return new ExpressionParser(line).parse();
    }
}
