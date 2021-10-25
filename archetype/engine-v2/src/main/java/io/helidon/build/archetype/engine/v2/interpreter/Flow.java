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

package io.helidon.build.archetype.engine.v2.interpreter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.build.archetype.engine.v2.archive.Archetype;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextNode;
import io.helidon.build.archetype.engine.v2.descriptor.ArchetypeDescriptor;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.UserInputNode;
import io.helidon.build.archetype.engine.v2.descriptor.Step;

/**
 * Resolver for the archetype output files.
 */
public class Flow {

    private final Interpreter interpreter;
    private final Archetype archetype;
    private final ArchetypeDescriptor entryPoint;
    private final LinkedList<DescriptorNode<Step>> tree = new LinkedList<>();
    private FlowState state;
    private boolean skipOptional;

    /**
     * Returns the flag that indicates whether the interpreter skips optional inputs and uses their default values
     * or stops its execution and waits for user input.
     *
     * @return true if the interpreter skips optional inputs, false - otherwise.
     */
    public boolean skipOptional() {
        return skipOptional;
    }

    /**
     * Set the flag that indicates whether the interpreter has to skip optional inputs and uses their default values or it has
     * to stop its execution and waits for user input.
     *
     * @param skipOptional true if the interpreter has to skip optional inputs, false - otherwise.
     */
    public void skipOptional(boolean skipOptional) {
        this.skipOptional = skipOptional;
        interpreter.skipOptional(skipOptional);
    }

    /**
     * Get the mark that indicates whether the project can be generated if optional inputs will be skipped.
     *
     * @return true if the project can be generated if optional inputs will be skipped, false - otherwise.
     */
    public boolean canBeGenerated() {
        return state.canBeGenerated();
    }

    /**
     * Returns result.
     *
     * @return Flow.Result
     */
    public Optional<Flow.Result> result() {
        return state.result();
    }

    /**
     * Returns unresolved inputs.
     *
     * @return List of the unresolved inputs
     */
    public List<UserInputNode> unresolvedInputs() {
        return interpreter.unresolvedInputs();
    }

    /**
     * Returns Map that contains path to the context node and corresponding context node.
     *
     * @return Map
     */
    public Map<String, ContextNode<?>> contextByPath() {
        return interpreter.contextByPath();
    }

    Interpreter interpreter() {
        return interpreter;
    }

    /**
     * Returns current state of the Flow.
     *
     * @return FlowState
     */
    public FlowState state() {
        return state;
    }

    void state(FlowState state) {
        this.state = state;
    }

    /**
     * Returns archetype.
     *
     * @return archetype
     */
    public Archetype archetype() {
        return archetype;
    }

    /**
     * Returns the descriptor for the entry point.
     *
     * @return ArchetypeDescriptor
     */
    public ArchetypeDescriptor entrypoint() {
        return entryPoint;
    }

    private Flow(Builder builder) {
        this.archetype = builder.archetype;
        entryPoint = archetype.getDescriptor(builder.entrypoint);
        this.skipOptional = builder.skipOptional;
        interpreter = new Interpreter(archetype, builder.entrypoint, skipOptional, builder.additionalVisitors);
        state = new InitialFlowState(this);
    }

    List<DescriptorNode<Step>> tree() {
        return tree;
    }

    /**
     * Build the flow, that can be used to create a new project.
     *
     * @param context initial context
     * @return current state of the flow.
     */
    public FlowState build(DescriptorNodes.ContextBlockNode context) {
        state.build(context);
        return state;
    }

    /**
     * Create a new builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link Flow} builder.
     */
    public static final class Builder {

        private Archetype archetype;
        private String entrypoint = "flavor.xml";
        private final List<Visitor<Node, Void>> additionalVisitors = new ArrayList<>();
        private boolean skipOptional = false;

        private Builder() {
        }

        /**
         * Sets the {@code skipOptional} flag.
         *
         * @param skipOptional skipOptional
         * @return this builder
         */
        public Builder skipOptional(boolean skipOptional) {
            this.skipOptional = skipOptional;
            return this;
        }

        /**
         * Sets the archetype archive.
         *
         * @param archetype the archetype archive
         * @return this builder
         */
        public Builder archetype(Archetype archetype) {
            this.archetype = archetype;
            return this;
        }

        /**
         * Sets the entry point.
         *
         * @param entrypoint the entry point
         * @return this builder
         */
        public Builder entrypoint(String entrypoint) {
            this.entrypoint = entrypoint;
            return this;
        }

        /**
         * Add an additional visitor.
         *
         * @param visitor the visitor to add
         * @return this builder
         */
        public Builder additionalVisitor(Visitor<Node, Void> visitor) {
            additionalVisitors.add(visitor);
            return this;
        }

        /**
         * Add additional visitors.
         *
         * @param visitors the visitors to add
         * @return this builder
         */
        public Builder additionalVisitor(List<Visitor<Node, Void>> visitors) {
            additionalVisitors.addAll(visitors);
            return this;
        }

        /**
         * Build the {@link Flow} instance.
         *
         * @return created instance
         */
        public Flow build() {
            if (archetype == null) {
                throw new InterpreterException("Archetype must be specified.");
            }
            return new Flow(this);
        }
    }

    /**
     * Interpreter result.
     */
    public static class Result {

        private final Map<String, ContextNode<?>> context = new HashMap<>();
        private final List<Node> outputs = new ArrayList<>();
        private final Archetype archetype;

        /**
         * Create a new instance.
         *
         * @param archetype archetype archive
         */
        public Result(Archetype archetype) {
            this.archetype = archetype;
        }

        /**
         * Get the archetype archive.
         *
         * @return archetype
         */
        public Archetype archetype() {
            return archetype;
        }

        /**
         * Get the flow context map.
         *
         * @return  flow context map
         */
        public Map<String, ContextNode<?>> context() {
            return context;
        }

        /**
         * Get the output nodes for this interpretation.
         *
         * @return output nodes
         */
        public List<Node> outputs() {
            return outputs;
        }
    }
}
