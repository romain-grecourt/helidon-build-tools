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

/**
 * Evaluate an expression.
 *
 * @param <T> Result type.
 */
interface ExpressionHandler<T> {

    /**
     * Evaluate an expression and return the {@code Literal} as a result.
     *
     * @return Literal.
     */
    Literal<T> evaluate() throws ExpressionParserException;

    /**
     * Try to convert {@code AbstractSyntaxTree} to the {@code Literal} if possible or throw an {@code IllegalArgumentException}.
     *
     * @param ast AbstractSyntaxTree
     * @return Literal
     */
    default Literal<?> asLiteral(AbstractSyntaxTree ast) {
        if (ast.isVariable()) {
            if (ast.asVariable().value() == null) {
                throw new IllegalArgumentException(
                        String.format("Variable %s must be initialized", ast.asVariable().name()));
            }
            return ast.asVariable().value();
        }
        if (ast.isLiteral()) {
            return ast.asLiteral();
        }
        throw new IllegalArgumentException("Unexpected type of the AbstractSyntaxTree. "
                + "Expected type is Literal or Variable, but the type is " + ast.getClass());
    }
}
