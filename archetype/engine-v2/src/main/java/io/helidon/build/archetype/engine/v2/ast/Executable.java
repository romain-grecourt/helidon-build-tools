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
 * Executable block.
 */
public final class Executable extends Block {

    private final Kind kind;

    private Executable(Builder builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
    }

    /**
     * Get the executable block kind.
     *
     * @return kind
     */
    public Kind executableKind() {
        return kind;
    }

    /**
     * Visit this executable block.
     *
     * @param visitor Visitor
     * @param arg     argument
     * @param <R>     generic type of the result
     * @param <A>     generic type of the arguments
     * @return result
     */
    public <A, R> R accept(Visitor<A, R> visitor, A arg) {
        switch (kind) {
            case SCRIPT:
                return visitor.visitScript(this, arg);
            case STEP:
                return visitor.visitStep(this, arg);
            case OPTION:
                return visitor.visitOption(this, arg);
            case INPUT:
                return visitor.visitInput(this, arg);
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Executable blocks kind.
     */
    public enum Kind {

        /**
         * Script.
         */
        SCRIPT,

        /**
         * Step.
         */
        STEP,

        /**
         * Option.
         */
        OPTION,

        /**
         * Input.
         */
        INPUT,
    }

    /**
     * Executable block visitor.
     *
     * @param <A> argument
     * @param <R> type of the returned value
     */
    @SuppressWarnings("unused")
    public interface Visitor<A, R> {

        /**
         * Visit a script.
         *
         * @param script script
         * @param arg    argument
         * @return visit result
         */
        default R visitScript(Executable script, A arg) {
            return null;
        }

        /**
         * Visit a statement.
         *
         * @param step step
         * @param arg  argument
         * @return visit result
         */
        default R visitStep(Executable step, A arg) {
            return null;
        }

        /**
         * Visit an option.
         *
         * @param option option
         * @param arg    argument
         * @return visit result
         */
        default R visitOption(Executable option, A arg) {
            return null;
        }

        /**
         * Visit an input.
         *
         * @param input input
         * @param arg   argument
         * @return visit result
         */
        default R visitInput(Executable input, A arg) {
            return null;
        }
    }

    /**
     * Executable builder.
     */
    public static class Builder extends Block.Builder<Executable, Builder> {

        private final Kind kind;

        /**
         * Create a new executable builder.
         *
         * @param location location
         * @param position position
         */
        Builder(Path location, Position position, Kind kind) {
            super(location, position, Block.Kind.EXECUTABLE);
            this.kind = kind;
        }

        @Override
        public Executable build() {
            return new Executable(this);
        }
    }
}
