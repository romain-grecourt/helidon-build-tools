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

/**
 * Output block.
 */
public class Output extends Block {

    private Output(Output.Builder builder) {
        super(builder);
    }

    /**
     * Output visitor.
     *
     * @param <R> result type
     * @param <A> argument type
     */
    public interface Visitor<R, A> {

        /**
         * Visit an output block.
         *
         * @param block block
         * @param arg   argument
         * @return visit result
         */
        default R visitOutput(Output block, A arg) {
            return null;
        }
    }

    /**
     * Visit this output.
     *
     * @param visitor visitor
     * @param arg     argument
     * @param <R>     result type
     * @param <A>     argument type
     * @return visit result
     */
    public <R, A> R accept(Visitor<R, A> visitor, A arg) {
        return visitor.visitOutput(this, arg);
    }

    @Override
    public <R, A> R accept(Block.Visitor<R, A> visitor, A arg) {
        return visitor.visitOutput(this, arg);
    }

    /**
     * Create a new Output block builder.
     *
     * @param location location
     * @param position position
     * @param kind     block kind
     * @return builder
     */
    public static Builder builder(Path location, Position position, Kind kind) {
        return new Builder(location, position, kind);
    }

    /**
     * Output block builder.
     */
    public static class Builder extends Block.Builder {

        private Builder(Path location, Position position, Kind kind) {
            super(location, position, kind);
        }

        @Override
        protected Block build0(){
            // TODO
            return null;
        }
    }
}
