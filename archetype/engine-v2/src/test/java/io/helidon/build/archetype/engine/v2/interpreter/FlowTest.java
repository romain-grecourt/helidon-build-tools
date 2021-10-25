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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.build.archetype.engine.v2.ArchetypeBaseTest;
import io.helidon.build.archetype.engine.v2.archive.Archetype;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class FlowTest extends ArchetypeBaseTest {

    private Archetype archetype;

    @Test
    public void testInnerOutputsElements() {
        archetype = getArchetype("interpreter-test-resources");

        Flow flow = Flow.builder()
                        .archetype(archetype)
                        .entrypoint("inner-output-elements-test.xml")
                        .skipOptional(false)
                        .additionalVisitor(DebugVisitor.create())
                        .build();
        flow.build(new DescriptorNodes.ContextBlockNode());
        assertResult(flow, 6, 3);

        DescriptorNodes.OutputNode output = (DescriptorNodes.OutputNode) flow.result().get().outputs().stream()
                                                                             .filter(child -> child instanceof DescriptorNodes.OutputNode)
                                                                             .filter(o -> o.children().size() == 1)
                                                                             .findFirst().orElse(null);
        assertThat(output, notNullValue());
        DescriptorNodes.TemplatesNode templatesAST = (DescriptorNodes.TemplatesNode) output.children().get(0);
        assertThat(templatesAST.children().size(), is(0));

        output = (DescriptorNodes.OutputNode) flow.result().get().outputs().stream()
                                                  .filter(child -> child instanceof DescriptorNodes.OutputNode)
                                                  .filter(o -> o.children().size() == 2)
                                                  .findFirst().orElse(null);
        assertThat(output, notNullValue());
        templatesAST = (DescriptorNodes.TemplatesNode) output.children().stream()
                                                             .filter(ch -> ch instanceof DescriptorNodes.TemplatesNode)
                                                             .findFirst()
                                                             .orElse(null);
        assertThat(templatesAST, notNullValue());
        assertThat(templatesAST.children().size(), is(1));
        assertThat(templatesAST.children().get(0) instanceof DescriptorNodes.ModelNode, is(true));

        DescriptorNodes.ModelNode modelAST = (DescriptorNodes.ModelNode) templatesAST.children().get(0);
        assertThat(modelAST.children().size(), is(3));
        DescriptorNodes.ModelValueNode valueTypeAST = modelAST.children().stream()
                                                              .filter(c -> c instanceof DescriptorNodes.ModelValueNode)
                                                              .map(c -> (DescriptorNodes.ModelValueNode) c)
                                                              .findFirst().get();
        assertThat(valueTypeAST.value(), is("some value"));
        List<DescriptorNodes.ModelKeyedListNode> list = modelAST.children().stream()
                                                                .filter(c -> c instanceof DescriptorNodes.ModelKeyedListNode)
                                                                .map(c -> (DescriptorNodes.ModelKeyedListNode) c)
                                                                .collect(Collectors.toList());
        assertThat(list.size(), is(1));
        assertThat(list.get(0).key(), is("dependencies"));
        assertThat(list.get(0).children().size(), is(3));
        List<DescriptorNodes.ModelValueNode> values = list.get(0).children().stream()
                                                          .filter(c -> c instanceof DescriptorNodes.ModelValueNode)
                                                          .map(c -> (DescriptorNodes.ModelValueNode) c).
                                                          collect(Collectors.toList());
        assertThat(values.size(), is(1));
        assertThat(values.get(0).value(), is("you depend on ME"));
        List<DescriptorNodes.ModelListNode> innerList = list.get(0).children().stream()
                                                            .filter(c -> c instanceof DescriptorNodes.ModelListNode)
                                                            .map(c -> (DescriptorNodes.ModelListNode) c).
                                                            collect(Collectors.toList());
        assertThat(innerList.size(), is(1));
        assertThat(innerList.get(0).order(), is(101));
        assertThat(innerList.get(0).children().size(), is(2));
        List<DescriptorNodes.ModelMapNode> innerMap = list.get(0).children().stream()
                                                          .filter(c -> c instanceof DescriptorNodes.ModelMapNode)
                                                          .map(c -> (DescriptorNodes.ModelMapNode) c).
                                                          collect(Collectors.toList());
        assertThat(innerMap.size(), is(1));
        assertThat(innerMap.get(0).order(), is(10));
        assertThat(innerMap.get(0).children().size(), is(2));
        List<DescriptorNodes.ModelKeyedMapNode> map = modelAST.children().stream()
                                                              .filter(c -> c instanceof DescriptorNodes.ModelKeyedMapNode)
                                                              .map(c -> (DescriptorNodes.ModelKeyedMapNode) c)
                                                              .collect(Collectors.toList());
        assertThat(map.size(), is(1));
        assertThat(map.get(0).key(), is("foo"));
        List<DescriptorNodes.ModelKeyedValueNode> valueList = modelAST.children().stream()
                                                                      .filter(c -> c instanceof DescriptorNodes.ModelKeyedValueNode)
                                                                      .map(c -> (DescriptorNodes.ModelKeyedValueNode) c)
                                                                      .collect(Collectors.toList());
        assertThat(valueList.size(), is(1));
        assertThat(valueList.get(0).key(), is("key value"));

        output = (DescriptorNodes.OutputNode) flow.result().get().outputs().stream()
                                                  .filter(child -> child instanceof DescriptorNodes.OutputNode)
                                                  .filter(o -> o.children().size() == 3)
                                                  .findFirst().orElse(null);
        assertThat(output, notNullValue());
        templatesAST = (DescriptorNodes.TemplatesNode) output.children().stream()
                                                             .filter(ch -> ch instanceof DescriptorNodes.TemplatesNode)
                                                             .findFirst()
                                                             .orElse(null);
        assertThat(templatesAST, notNullValue());
        assertThat(templatesAST.children().size(), is(0));
    }

    @Test
    public void testIfStatement() {
        archetype = getArchetype("interpreter-test-resources");

        Flow flow = Flow.builder()
                        .archetype(archetype)
                        .entrypoint("if-output-test.xml")
                        .skipOptional(false)
                        .additionalVisitor(DebugVisitor.create())
                        .build();

        flow.build(new DescriptorNodes.ContextBlockNode());
        assertResult(flow, 4, 1);

        DescriptorNodes.OutputNode output = (DescriptorNodes.OutputNode) flow.result().get().outputs().stream()
                                                                             .filter(child -> child instanceof DescriptorNodes.OutputNode)
                                                                             .filter(o -> o.children().size() > 0)
                                                                             .findFirst().orElse(null);
        assertThat(output, notNullValue());
        assertThat(((DescriptorNodes.TransformationNode) output.children().get(0)).id(), is("t1"));
    }

    @Test
    public void testOutput() {
        archetype = getArchetype("interpreter-test-resources");
        Flow flow = Flow.builder()
                        .archetype(archetype)
                        .entrypoint("output-test.xml")
                        .skipOptional(false)
                        .additionalVisitor(DebugVisitor.create())
                        .build();
        flow.build(new DescriptorNodes.ContextBlockNode());
        flow.build(getUserInput("User boolean input label"));

        assertResult(flow, 1, 1);

        DescriptorNodes.OutputNode output = (DescriptorNodes.OutputNode) flow.result().get().outputs().stream()
                                                                             .filter(child -> child instanceof DescriptorNodes.OutputNode)
                                                                             .findFirst().get();
        assertThat(output.children().size(), is(4));
    }

    @Test
    public void testSource() {
        archetype = getArchetype("interpreter-test-resources");
        Flow flow = Flow.builder()
                        .archetype(archetype)
                        .entrypoint("source_script_test.xml")
                        .skipOptional(false)
                        .additionalVisitor(DebugVisitor.create())
                        .build();
        flow.build(new DescriptorNodes.ContextBlockNode());

        flow.build(getUserInput("User boolean input label"));
        DescriptorNodes.ContextBooleanNode contextNode = (DescriptorNodes.ContextBooleanNode) flow.contextByPath().get("bool-input");
        assertThat(contextNode.bool(), is(true));

        DescriptorNodes.SourceNode sourceAST = (DescriptorNodes.SourceNode) flow.tree().get(0).children().get(0);
        DescriptorNodes.InputBlockNode inputAST = (DescriptorNodes.InputBlockNode) sourceAST.children().get(0);
        assertThat(inputAST.location().currentDirectory(), is(""));
        assertThat(inputAST.location().scriptDirectory(), is("inner"));

        assertResult(flow, 1, 1);
    }

    @Test
    public void testExec() {
        archetype = getArchetype("interpreter-test-resources");
        Flow flow = Flow.builder()
                        .archetype(archetype)
                        .entrypoint("exec_script_test.xml")
                        .skipOptional(false)
                        .additionalVisitor(DebugVisitor.create())
                        .build();
        flow.build(new DescriptorNodes.ContextBlockNode());

        flow.build(getUserInput("User boolean input label"));
        DescriptorNodes.ContextBooleanNode contextNode = (DescriptorNodes.ContextBooleanNode) flow.contextByPath().get("bool-input");
        assertThat(contextNode.bool(), is(true));

        DescriptorNodes.ExecNode execAST = (DescriptorNodes.ExecNode) flow.tree().get(0).children().get(0);
        DescriptorNodes.InputBlockNode inputAST = (DescriptorNodes.InputBlockNode) execAST.children().get(0);
        assertThat(inputAST.location().currentDirectory(), is("inner"));
        assertThat(inputAST.location().scriptDirectory(), is("inner"));

        assertResult(flow, 1, 1);
    }

    @Test
    public void testInputList() {
        archetype = getArchetype("interpreter-test-resources");
        Flow flow = Flow.builder()
                        .archetype(archetype)
                        .entrypoint("input-list.xml")
                        .skipOptional(false)
                        .additionalVisitor(DebugVisitor.create())
                        .build();
        flow.build(new DescriptorNodes.ContextBlockNode());
        flow.build(getUserInput("Select the list1"));
        DescriptorNodes.ContextListNode contextNode = (DescriptorNodes.ContextListNode) flow.contextByPath().get("list1");
        assertThat(contextNode.values(), containsInAnyOrder("option1", "option2"));

        assertResult(flow, 5, 4);
    }

    @Test
    public void testInputEnum() {
        archetype = getArchetype("interpreter-test-resources");
        Flow flow = Flow.builder()
                        .archetype(archetype)
                        .entrypoint("input-enum.xml")
                        .skipOptional(false)
                        .additionalVisitor(DebugVisitor.create())
                        .build();
        flow.build(new DescriptorNodes.ContextBlockNode());
        flow.build(getUserInput("Enum option 1"));
        DescriptorNodes.ContextEnumNode contextNode = (DescriptorNodes.ContextEnumNode) flow.contextByPath().get("enum1");
        assertThat(contextNode.value(), is("option1"));

        contextNode = (DescriptorNodes.ContextEnumNode) flow.contextByPath().get("enum_input_context");
        assertThat(contextNode.value(), is("enum context value"));

        assertResult(flow, 5, 2);
    }

    @Test
    public void testInputBoolean() {
        archetype = getArchetype("interpreter-test-resources");
        Flow flow = Flow.builder()
                        .archetype(archetype)
                        .entrypoint("input-boolean.xml")
                        .skipOptional(false)
                        .additionalVisitor(DebugVisitor.create())
                        .build();
        flow.build(new DescriptorNodes.ContextBlockNode());
        flow.build(getUserInput("User boolean input label"));
        DescriptorNodes.ContextBooleanNode contextNode = (DescriptorNodes.ContextBooleanNode) flow.contextByPath().get("bool-input");
        assertThat(contextNode.bool(), is(true));

        contextNode = (DescriptorNodes.ContextBooleanNode) flow.contextByPath().get("bool_input_context");
        assertThat(contextNode.bool(), is(true));

        assertResult(flow, 6, 2);
    }

    @Test
    public void testCanBeGenerated() {
        archetype = getArchetype("interpreter-test-resources");
        Flow flow = Flow.builder()
                        .archetype(archetype)
                        .entrypoint("can-be-generated-test.xml")
                        .skipOptional(false)
                        .additionalVisitor(DebugVisitor.create())
                        .build();
        FlowState state = flow.build(new DescriptorNodes.ContextBlockNode());
        assertThat(state.canBeGenerated(), is(false));
        state = flow.build(getUserInput("User boolean input label"));
        assertThat(state.canBeGenerated(), is(true));
    }

    @Test
    public void testInputText() {
        archetype = getArchetype("interpreter-test-resources");
        Flow flow = Flow.builder()
                        .archetype(archetype)
                        .entrypoint("input-text.xml")
                        .skipOptional(false)
                        .additionalVisitor(DebugVisitor.create())
                        .build();
        flow.build(new DescriptorNodes.ContextBlockNode());
        FlowState state = flow.build(getUserInput("User text input"));
        DescriptorNodes.ContextTextNode contextNode = (DescriptorNodes.ContextTextNode) flow.contextByPath().get("user_text_input");
        assertThat(contextNode.value(), is("user text input"));

        contextNode = (DescriptorNodes.ContextTextNode) flow.contextByPath().get("user_text_input_context");
        assertThat(contextNode.value(), is("text input from context"));

        assertResult(flow, 5, 0);
    }

    @Test
    void testBuildFlowMp() {
        archetype = getArchetype("archetype");
        List<String> labels = new ArrayList<>();
        labels.add("Helidon MP");
        labels.add("Bare Helidon MP project suitable to start from scratch");
        labels.add("Apache Maven");
        labels.add("Docker support");
        labels.add("Do you want a native-image Dockerfile");
        labels.add("Do you want a jlink Dockerfile");
        labels.add("Kubernetes Support");
        Flow flow = Flow.builder()
                        .archetype(archetype)
                        .entrypoint("flavor.xml")
                        .skipOptional(false)
                        .additionalVisitor(DebugVisitor.create())
                        .build();
        FlowState state = flow.build(new DescriptorNodes.ContextBlockNode());
        for (String label : labels) {
            state = flow.build(getUserInput(label));
        }
        assertThat(state.type(), is(FlowStateEnum.READY));
    }

    @AfterEach
    public void clean() throws IOException {
        if (archetype != null) {
            archetype.close();
        }
        archetype = null;
    }

    private static DescriptorNodes.ContextBlockNode getUserInput(String label) {
        Map<String, DescriptorNodes.ContextBlockNode> userInputs = new HashMap<>();

        DescriptorNodes.ContextBlockNode context = new DescriptorNodes.ContextBlockNode();
        DescriptorNodes.ContextTextNode contextText = new DescriptorNodes.ContextTextNode("user_text_input");
        contextText.value("user text input");
        context.children().add(contextText);
        userInputs.put("User text input", context);

        context = new DescriptorNodes.ContextBlockNode();
        DescriptorNodes.ContextEnumNode contextEnum = new DescriptorNodes.ContextEnumNode("enum1");
        contextEnum.value("option1");
        context.children().add(contextEnum);
        userInputs.put("Enum option 1", context);

        context = new DescriptorNodes.ContextBlockNode();
        DescriptorNodes.ContextListNode contextList = new DescriptorNodes.ContextListNode("list1");
        contextList.values().addAll(List.of("option1", "option2"));
        context.children().add(contextList);
        userInputs.put("Select the list1", context);

        context = new DescriptorNodes.ContextBlockNode();
        DescriptorNodes.ContextBooleanNode contextBool = new DescriptorNodes.ContextBooleanNode("bool-input");
        contextBool.bool(true);
        context.children().add(contextBool);
        userInputs.put("User boolean input label", context);

        context = new DescriptorNodes.ContextBlockNode();
        contextEnum = new DescriptorNodes.ContextEnumNode("flavor");
        contextEnum.value("mp");
        context.children().add(contextEnum);
        userInputs.put("Helidon MP", context);

        context = new DescriptorNodes.ContextBlockNode();
        contextEnum = new DescriptorNodes.ContextEnumNode("base");
        contextEnum.value("bare");
        context.children().add(contextEnum);
        userInputs.put("Bare Helidon MP project suitable to start from scratch", context);

        context = new DescriptorNodes.ContextBlockNode();
        contextList = new DescriptorNodes.ContextListNode("build-system");
        contextList.values().add("maven");
        context.children().add(contextList);
        userInputs.put("Apache Maven", context);

        context = new DescriptorNodes.ContextBlockNode();
        contextBool = new DescriptorNodes.ContextBooleanNode("docker");
        contextBool.bool(true);
        context.children().add(contextBool);
        userInputs.put("Docker support", context);

        context = new DescriptorNodes.ContextBlockNode();
        contextBool = new DescriptorNodes.ContextBooleanNode("docker.native-image");
        contextBool.bool(true);
        context.children().add(contextBool);
        userInputs.put("Do you want a native-image Dockerfile", context);

        context = new DescriptorNodes.ContextBlockNode();
        contextBool = new DescriptorNodes.ContextBooleanNode("docker.jlink");
        contextBool.bool(false);
        context.children().add(contextBool);
        userInputs.put("Do you want a jlink Dockerfile", context);

        context = new DescriptorNodes.ContextBlockNode();
        contextBool = new DescriptorNodes.ContextBooleanNode("kubernetes");
        contextBool.bool(true);
        context.children().add(contextBool);
        userInputs.put("Kubernetes Support", context);

        return userInputs.get(label);
    }

    private void assertResult(Flow flow, int expectedContextValuesCount, int expectedOutputCount) {
        FlowState state = flow.state();

        assertThat(state.type(), is(FlowStateEnum.READY));
        state = flow.build(new DescriptorNodes.ContextBlockNode());

        assertThat(state.type(), is(FlowStateEnum.DONE));
        Flow.Result result = flow.result().orElse(null);
        assertThat(result, notNullValue());

        assertThat(result.context().size(), is(expectedContextValuesCount));

        assertThat(result.outputs().size(), is(expectedOutputCount));
    }
}
