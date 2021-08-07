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

import java.util.function.Function;

final class BinaryExpression implements Expression {

    private final BinaryOperator operator;
    private final Expression left;
    private final Expression right;

    BinaryExpression(BinaryOperator operator, Expression left, Expression right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    @Override
    public Value eval(Function<String, String> resolver) {
        return Literal.of(operator.evaluate(left.eval(resolver), right.eval(resolver)));
    }

    @Override
    public ExpressionKind expressionKind() {
        return ExpressionKind.BINARY;
    }

    Expression left() {
        return left;
    }

    Expression right() {
        return right;
    }

    BinaryOperator operator() {
        return operator;
    }
}
