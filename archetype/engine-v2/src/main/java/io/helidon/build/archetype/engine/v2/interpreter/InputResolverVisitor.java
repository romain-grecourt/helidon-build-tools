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

import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextEnumNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputEnumNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputOptionNode;
import io.helidon.build.archetype.engine.v2.ast.Node;

import java.util.List;

import static java.util.stream.Collectors.toList;

class InputResolverVisitor implements Visitor<Node, Void> {

    @Override
    public Void visit(InputEnumNode input, Node arg) {
        if (arg instanceof ContextEnumNode) {
            List<Node> resolvedOptions = input.childrenOf(InputOptionNode.class)
                                              .filter(o -> o.is((ContextEnumNode) arg))
                                              .collect(toList());
            input.children().removeIf(c -> c instanceof InputOptionNode);
            input.children().addAll(resolvedOptions);
        }
        return null;
    }

    @Override
    public Void visit(InputListNode input, Node arg) {
        if (arg instanceof ContextListNode) {
            List<Node> resolvedOptions = input.childrenOf(InputOptionNode.class)
                                              .filter(o -> o.is((ContextListNode) arg))
                                              .collect(toList());
            input.children().removeIf(c -> c instanceof InputOptionNode);
            input.children().addAll(resolvedOptions);
        }
        return null;
    }
}
