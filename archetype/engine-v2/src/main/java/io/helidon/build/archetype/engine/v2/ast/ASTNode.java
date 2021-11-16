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

import java.util.Objects;

/**
 * Base class for AST nodes.
 */
public abstract class ASTNode {

    protected final Position position;

    protected ASTNode(Builder<?, ?> builder) {
        this.position = Objects.requireNonNull(builder.position, "position is null");
    }

    /**
     * Get the source position.
     *
     * @return position
     */
    public Position position() {
        return position;
    }

    /**
     * Visit a node.
     *
     * @param visitor Visitor
     * @param arg     argument
     * @param <R>     generic type of the result
     * @param <A>     generic type of the arguments
     * @return result
     */
    public abstract <A, R> R accept(Visitor<A, R> visitor, A arg);

    /**
     * Source position.
     */
    public static final class Position {

        private final int lineNo;
        private final int charNo;

        private Position(int lineNo, int charNo) {
            this.lineNo = lineNo;
            this.charNo = charNo;
        }

        /**
         * Get the current line number.
         *
         * @return line number
         */
        public int lineNumber() {
            return lineNo;
        }

        /**
         * Get the current line character number.
         *
         * @return line character number
         */
        public int charNumber() {
            return charNo;
        }

        @Override
        public String toString() {
            return "line=" + lineNo + ", char=" + charNo;
        }
    }

    /**
     * AST node builder.
     *
     * @param <T> sub-type
     * @param <U> builder sub-type
     */
    @SuppressWarnings("unchecked")
    public static abstract class Builder<T, U extends Builder<T, U>> {

        private Position position;

        /**
         * Set the position.
         *
         * @param lineNo line number
         * @param charNo line character number
         * @return this builder
         */
        public U position(int lineNo, int charNo) {
            this.position = new Position(lineNo, charNo);
            return (U) this;
        }

        /**
         * Create the new instance.
         *
         * @return new instance
         */
        public abstract T build();
    }
}
