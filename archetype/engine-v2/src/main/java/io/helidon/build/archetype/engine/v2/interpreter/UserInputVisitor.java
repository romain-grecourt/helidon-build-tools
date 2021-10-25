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

import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputBooleanNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputEnumNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputOptionNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputTextNode;
import io.helidon.build.archetype.engine.v2.ast.Location;
import io.helidon.build.archetype.engine.v2.ast.Node;

import static java.util.stream.Collectors.toList;

/**
 * Visitor to prepare user input AST node.
 */
public class UserInputVisitor implements Visitor<InputNode, Node> {

    @Override
    public InputNode visit(InputEnumNode input, Node arg) {
        InputEnumNode result = new InputEnumNode(input.descriptor(), null, Location.create());
        result.children().addAll(input.children()
                                      .stream()
                                      .filter(c -> c instanceof InputOptionNode)
                                      .map(o -> copyOption((InputOptionNode) o))
                                      .collect(toList()));
        return result;
    }

    @Override
    public InputNode visit(InputListNode input, Node arg) {
        InputListNode result = new InputListNode(input.descriptor(), null, Location.create());
        result.children().addAll(input.children()
                                      .stream()
                                      .filter(c -> c instanceof InputOptionNode)
                                      .map(o -> copyOption((InputOptionNode) o))
                                      .collect(toList()));
        return result;
    }

    @Override
    public InputNode visit(InputBooleanNode input, Node arg) {
        return new InputBooleanNode(input.descriptor(), null, Location.create());
    }

    @Override
    public InputNode visit(InputTextNode input, Node arg) {
        return new InputTextNode(input.descriptor(), null, Location.create());
    }

    private InputOptionNode copyOption(InputOptionNode optionFrom) {
        return new InputOptionNode(optionFrom.descriptor(), null, Location.create());
    }
}
