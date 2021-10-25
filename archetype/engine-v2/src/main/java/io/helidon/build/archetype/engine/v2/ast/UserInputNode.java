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

import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.StepNode;
import io.helidon.build.archetype.engine.v2.interpreter.Visitor;

/**
 * User input.
 */
public class UserInputNode extends Node {

    private final String label;
    private final String help;
    private final String path;

    public UserInputNode(StepNode stepNode, InputNode<?> inputNode) {
        super(null, inputNode.location());
        this.label = inputNode.descriptor().label();
        this.help = stepNode.descriptor().help();
        this.path = inputNode.path();
    }

    /**
     * Get the label.
     *
     * @return label
     */
    public String label() {
        return label;
    }

    /**
     * Get the help.
     *
     * @return help
     */
    public String help() {
        return help;
    }

    /**
     * Get the path.
     *
     * @return path
     */
    public String path() {
        return path;
    }

    @Override
    public <A, R> R accept(Visitor<A, R> visitor, A arg) {
        return visitor.visit(this, arg);
    }
}
