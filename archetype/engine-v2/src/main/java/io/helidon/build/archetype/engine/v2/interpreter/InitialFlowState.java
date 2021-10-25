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

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.helidon.build.archetype.engine.v2.ast.DescriptorNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodeFactory;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextBlockNode;
import io.helidon.build.archetype.engine.v2.descriptor.ArchetypeDescriptor;
import io.helidon.build.archetype.engine.v2.descriptor.ContextBlock;
import io.helidon.build.archetype.engine.v2.descriptor.Step;

import static java.util.stream.Collectors.toList;

public class InitialFlowState extends FlowState {

    private final Flow flow;

    InitialFlowState(Flow flow) {
        this.flow = flow;
    }

    @Override
    Optional<Flow.Result> result() {
        return Optional.empty();
    }

    @Override
    void build(ContextBlockNode context) {
        flow.interpreter().visit(context, null);
        getContexts(flow.entrypoint()).forEach(ctx -> ctx.accept(flow.interpreter(), null));
        LinkedList<DescriptorNode<Step>> steps = new LinkedList<>(getSteps(flow.entrypoint()));
        flow.interpreter().stack().addAll(steps);
        flow.tree().addAll(steps);
        try {
            while (!steps.isEmpty()) {
                steps.pop().accept(flow.interpreter(), null);
            }
        } catch (WaitForUserInput waitForUserInput) {
            flow.state(new WaitingFlowState(flow));
            return;
        }
        if (flow.unresolvedInputs().isEmpty()) {
            flow.state(new ReadyFlowState(flow));
            return;
        }
        throw new InterpreterException("Script interpreter finished in unexpected state.");
    }

    private List<DescriptorNode<ContextBlock>> getContexts(ArchetypeDescriptor entryPoint) {
        return entryPoint.contexts()
                         .stream()
                         .map(DescriptorNodeFactory::create)
                         .collect(toList());
    }

    private List<DescriptorNode<Step>> getSteps(ArchetypeDescriptor entryPoint) {
        if (entryPoint.steps().isEmpty()) {
            throw new InterpreterException("Archetype descriptor does not contain steps");
        }
        return entryPoint.steps()
                         .stream()
                         .map(DescriptorNodeFactory::create)
                         .collect(toList());
    }

    @Override
    public FlowStateEnum type() {
        return FlowStateEnum.INITIAL;
    }

    @Override
    boolean canBeGenerated() {
        return false;
    }
}
