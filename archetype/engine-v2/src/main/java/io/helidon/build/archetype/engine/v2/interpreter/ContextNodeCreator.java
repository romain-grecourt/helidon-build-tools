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

import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextBooleanNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextEnumNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextTextNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputBooleanNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputEnumNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputTextNode;
import io.helidon.build.archetype.engine.v2.ast.Node;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Create {@code ContextNodeAST} instance using default values of the corresponding {@code InputNodeAST} node.
 */
class ContextNodeCreator implements Visitor<Node, ContextNode<?>> {

    @Override
    public ContextNode<?> visit(InputEnumNode input, Node arg) {
        if (input.defaultValue() == null) {
            return null;
        }
        ContextEnumNode result = new ContextEnumNode(input.path());
        result.value(input.defaultValue());
        return result;
    }

    @Override
    public ContextNode visit(InputListNode input, Node arg) {
        if (input.defaultValue() == null) {
            return null;
        }
        ContextListNode result = new ContextListNode(input.path());
        List<String> values = Stream.of(input.defaultValue().split(","))
                                    .map(String::trim)
                                    .collect(Collectors.toList());
        result.descriptor().values().addAll(values);
        return result;
    }

    @Override
    public ContextNode visit(InputBooleanNode input, Node arg) {
        if (input.defaultValue() == null) {
            return null;
        }
        ContextBooleanNode result = new ContextBooleanNode(input.path());
        result.value(input.defaultValue().trim().equalsIgnoreCase("yes"));
        return result;
    }

    @Override
    public ContextNode visit(InputTextNode input, Node arg) {
        if (input.defaultValue() == null && input.placeHolder() == null) {
            return null;
        }
        ContextTextNode result = new ContextTextNode(input.path());
        result.value(input.placeHolder() != null ? input.placeHolder() : input.defaultValue());
        return result;
    }
}
