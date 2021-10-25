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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.helidon.build.archetype.engine.v2.PropertyEvaluator;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes;
import io.helidon.build.archetype.engine.v2.ast.Node;

public class ReadyFlowState extends FlowState {

    private final Flow flow;
    private final OutputConverterVisitor outputConverterVisitor = new OutputConverterVisitor();
    private final ContextConvertor contextToStringConvertor = new ContextConvertor();

    ReadyFlowState(Flow flow) {
        this.flow = flow;
    }

    @Override
    Optional<Flow.Result> result() {
        return Optional.empty();
    }

    @Override
    void build(DescriptorNodes.ContextBlockNode context) {
        Node lastNode = flow.interpreter().stack().peek();
        flow.interpreter().visit(context, lastNode);
        Flow.Result result = new Flow.Result(flow.archetype());

        result.context().putAll(flow.interpreter().contextByPath());

        flow.tree().forEach(step -> traverseTree(step, result));

        Map<String, String> contextValuesMap = convertContext(result.context());
        result.outputs().forEach(o -> traverseOutput(o, contextValuesMap));
        flow.state(new DoneFlowState(result));
    }

    private void traverseOutput(Node node, Map<String, String> contextValuesMap) {
        if (node instanceof DescriptorNodes.ModelValueNode) {
            String value = ((DescriptorNodes.ModelValueNode) node).value();
            if (value != null && value.contains("${")) {
                value = PropertyEvaluator.resolve(value, contextValuesMap);
                ((DescriptorNodes.ModelValueNode) node).value(value);
            }
        }
        node.children().forEach(child -> traverseOutput((Node) child, contextValuesMap));
    }

    private Map<String, String> convertContext(Map<String, DescriptorNodes.ContextNode> context) {
        Map<String, String> result = new HashMap<>();
        context.forEach((key, value) -> {
            result.putIfAbsent(key, value.accept(contextToStringConvertor, null).replaceAll("[\\['\\]]", ""));
        });
        return result;
    }

    private void traverseTree(Node node, Flow.Result result) {
        if (node instanceof DescriptorNodes.OutputNode) {
            result.outputs().add(node.accept(outputConverterVisitor, null));
        } else {
            node.children().forEach(child -> traverseTree((Node) child, result));
        }
    }

    @Override
    public FlowStateEnum type() {
        return FlowStateEnum.READY;
    }

    @Override
    boolean canBeGenerated() {
        return true;
    }
}
