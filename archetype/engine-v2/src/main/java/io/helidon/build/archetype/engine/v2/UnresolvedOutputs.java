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

import io.helidon.build.archetype.engine.v2.ast.Output;
import io.helidon.build.archetype.engine.v2.ast.Visitor;

import java.util.LinkedList;
import java.util.List;

/**
 * Unresolved output nodes.
 */
class UnresolvedOutputs {

    private final List<UnresolvedOutput> entries = new LinkedList<>();

    /**
     * Add an unresolved output node.
     *
     * @param output output
     * @param ctx    context
     */
    void add(Output output, Context ctx) {
        entries.add(new UnresolvedOutput(output, ctx));
    }

    /**
     * Visit the unresolved output nodes.
     *
     * @param visitor visitor
     * @param builder output  builder
     */
    void accept(UnresolvedVisitor visitor, Output.Builder builder) {
        for (UnresolvedOutput entry : entries) {
            visitor.visit(entry, builder);
        }
    }

    /**
     * Unresolved visitor.
     */
    interface UnresolvedVisitor extends Visitor<Output.Builder, Void> {

        /**
         * Visit an unresolved output.
         *
         * @param entry   entry
         * @param builder output builder
         */
        default void visit(UnresolvedOutput entry, Output.Builder builder) {
        }
    }

    /**
     * Unresolved output.
     */
    static class UnresolvedOutput {

        private final Context ctx;
        private final Output output;

        private UnresolvedOutput(Output output, Context ctx) {
            this.ctx = ctx;
            this.output = output;
        }

        /**
         * Get the context.
         *
         * @return context
         */
        Context ctx() {
            return ctx;
        }

        /**
         * Get the output.
         *
         * @return output
         */
        Output output() {
            return output;
        }
    }
}
