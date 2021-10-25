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

import java.util.List;

import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.IfStatementNode;
import io.helidon.build.archetype.engine.v2.descriptor.Conditional;

/**
 * Base interface for conditional nodes.
 */
public interface ConditionalNode {

    /**
     * If {@link Conditional#ifProperties()} is non {@code null}, wrap the node into an {@link IfStatementNode}.
     *
     * @param desc     descriptor
     * @param node     node
     * @param parent   parent AST node for the visitable
     * @param location location
     * @return visitable
     */
    static Node mapConditional(Conditional desc, Node node, Node parent, Location location) {
        if (desc.ifProperties() != null) {
            IfStatementNode result = new IfStatementNode(desc.ifProperties(), parent, location);
            node.parent(result);
            result.children().add(node);
            return result;
        }
        return node;
    }
}
