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

import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.FileSetNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.FileSetsNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.IfStatementNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelKeyedListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelKeyedMapNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelKeyedValueNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelMapNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelValueNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.OutputNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.TemplateNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.TemplatesNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.TransformationNode;
import io.helidon.build.archetype.engine.v2.ast.Node;

public class OutputConverterVisitor implements Visitor<Node, Node> {

    @Override
    public Node visit(OutputNode input, Node arg) {
        OutputNode result = new OutputNode(input.parent(), input.location());
        acceptAll(input, result);
        return result;
    }

    @Override
    public Node visit(TransformationNode input, Node arg) {
        return input;
    }

    @Override
    public Node visit(FileSetsNode input, Node arg) {
        return input;
    }

    @Override
    public Node visit(FileSetNode input, Node arg) {
        return input;
    }

    @Override
    public Node visit(TemplateNode input, Node arg) {
        TemplateNode result = new TemplateNode(input.descriptor(),input.parent(), input.location());
        acceptAll(input, result);
        return result;
    }

    @Override
    public Node visit(TemplatesNode input, Node arg) {
        // TODO directory
        TemplatesNode result = new TemplatesNode(input.descriptor(), input.parent, input.location());
        acceptAll(input, result);
        return result;
    }

    @Override
    public Node visit(ModelNode input, Node arg) {
        ModelNode result = new ModelNode(input.parent(), input.location());
        acceptAll(input, result);
        return result;
    }

    @Override
    public Node visit(IfStatementNode input, Node arg) {
        if (input.children().size() == 0) {
            return null;
        } else {
            return input.children().get(0).accept(this, arg);
        }
    }

    @Override
    public Node visit(ModelKeyedValueNode input, Node arg) {
        ModelKeyedValueNode result = new ModelKeyedValueNode(input.descriptor(),
                input.value(),
                input.parent(),
                input.location()
        );
        acceptAll(input, result);
        return result;
    }

    @Override
    public Node visit(ModelValueNode<?> input, Node arg) {
        ModelValueNode<?> result = new ModelValueNode<>(
                input.descriptor(),
                input.value(),
                input.parent(),
                input.location());
        acceptAll(input, result);
        return result;
    }

    @Override
    public Node visit(ModelKeyedListNode input, Node arg) {
        ModelKeyedListNode result = new ModelKeyedListNode(input.descriptor(), input.parent(), input.location());
        acceptAll(input, result);
        return result;
    }

    @Override
    public Node visit(ModelMapNode<?> input, Node arg) {
        ModelMapNode<?> result = new ModelMapNode<?>(input.descriptor(), input.parent(), input.location());
        acceptAll(input, result);
        return result;
    }

    @Override
    public Node visit(ModelListNode<?> input, Node arg) {
        ModelListNode<?> result = new ModelListNode<?>(input.descriptor(), input.parent(), input.location());
        acceptAll(input, result);
        return result;
    }

    @Override
    public Node visit(ModelKeyedMapNode input, Node arg) {
        ModelKeyedMapNode result = new ModelKeyedMapNode(input.descriptor(), input.parent(), input.location());
        acceptAll(input, result);
        return result;
    }

    private void acceptAll(Node node, Node parent) {
        for (Node child : node.children()) {
            Node result = child.accept(this, node);
            if (result != null) {
                parent.children().add(result);
            }
        }
    }
}
