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

package io.helidon.build.archetype.engine.v2.ast;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Statement.
 */
public abstract class Statement extends Node {

    private final Kind kind;

    protected Statement(Builder<?, ?> builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
    }

    /**
     * Get the statement kind.
     *
     * @return kind
     */
    public Kind statementKind() {
        return kind;
    }

    /**
     * Visit this statement.
     *
     * @param visitor Visitor
     * @param arg     argument
     * @param <R>     generic type of the result
     * @param <A>     generic type of the arguments
     * @return result
     */
    public final <A, R> R accept(Visitor<A, R> visitor, A arg) {
        switch (kind) {
            case IF:
                return visitor.visitIf((IfStatement) this, arg);
            case EXPRESSION:
                return visitor.visitExpression((Expression) this, arg);
            case BLOCK:
                return visitor.visitBlock((Block) this, arg);
            case INPUT:
                return visitor.visitInput((Input) this, arg);
            case DATA:
                return visitor.visitData((Data) this, arg);
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Statement visitor.
     *
     * @param <A> argument
     * @param <R> type of the returned value
     */
    @SuppressWarnings("unused")
    public interface Visitor<A, R> {

        /**
         * Visit an if statement.
         *
         * @param ifStatement if statement
         * @param arg         argument
         * @return visit result
         */
        default R visitIf(IfStatement ifStatement, A arg) {
            return null;
        }

        /**
         * Visit an expression.
         *
         * @param expression expression
         * @param arg        argument
         * @return visit result
         */
        default R visitExpression(Expression expression, A arg) {
            return null;
        }

        /**
         * Visit a block statement.
         *
         * @param block block
         * @param arg   argument
         * @return visit result
         */
        default R visitBlock(Block block, A arg) {
            return null;
        }

        /**
         * Visit an input statement.
         *
         * @param input input
         * @param arg   argument
         * @return visit result
         */
        default R visitInput(Input input, A arg) {
            return null;
        }

        /**
         * Visit a data statement.
         *
         * @param data data
         * @param arg  argument
         * @return visit result
         */
        default R visitData(Data data, A arg) {
            return null;
        }
    }

    /**
     * Statements kind.
     */
    public enum Kind {

        /**
         * If statement.
         */
        IF,

        /**
         * Expression.
         */
        EXPRESSION,

        /**
         * Block.
         */
        BLOCK,

        /**
         * Input.
         */
        INPUT,

        /**
         * Data.
         */
        DATA
    }

    /**
     * Base builder class for statement types.
     *
     * @param <T> sub-type
     * @param <U> builder sub-type
     */
    public static abstract class Builder<T extends Statement, U extends Builder<T, U>> extends Node.Builder<T, U> {

        private final Kind kind;

        /**
         * Create a new statement builder.
         *
         * @param location location
         * @param position position
         * @param kind     kind
         */
        protected Builder(Path location, Position position, Kind kind) {
            super(location, position, Node.Kind.STATEMENT);
            this.kind = kind;
        }
    }
}
