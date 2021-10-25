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

import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import io.helidon.build.archetype.engine.v2.PropertyEvaluator;
import io.helidon.build.archetype.engine.v2.archive.Archetype;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ArchetypeNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextBlockNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextBooleanNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextTextNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ExecNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.FileSetNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.FileSetsNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.IfStatementNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputBlockNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputBooleanNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputEnumNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputOptionNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputTextNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelKeyedListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelKeyedMapNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelKeyedValueNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelMapNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelValueNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.OutputNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.SourceNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.StepNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.TemplateNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.TemplatesNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.TransformationNode;
import io.helidon.build.archetype.engine.v2.ast.Location;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodeFactory;
import io.helidon.build.archetype.engine.v2.descriptor.ArchetypeDescriptor;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.UserInputNode;

/**
 * Interpret user inputs and produce new steps.
 */
public class Interpreter implements Visitor<Node, Void> {

    private final Archetype archetype;
    private final Map<String, ContextNode<?>> contextByPath = new HashMap<>();
    private final InputResolverVisitor inputResolverVisitor = new InputResolverVisitor();
    private final UserInputVisitor userInputVisitor = new UserInputVisitor();
    private final ContextConvertor contextConvertor = new ContextConvertor();
    private final ContextNodeCreator defaultValueCreator = new ContextNodeCreator();
    private final List<UserInputNode> unresolvedInputs = new ArrayList<>();
    private final Deque<Node> stack = new ArrayDeque<>();
    private final List<Visitor<Node, Void>> additionalVisitors;
    private boolean skipOptional;
    private final String entrypoint;
    private boolean canBeGenerated = false;

    Interpreter(Archetype archetype, String entrypoint, boolean skipOptional, List<Visitor<Node, Void>> additionalVisitors) {
        this.archetype = archetype;
        this.additionalVisitors = additionalVisitors;
        this.entrypoint = entrypoint;
        this.skipOptional = skipOptional;
    }

    boolean canBeGenerated() {
        return canBeGenerated;
    }

    void skipOptional(boolean skipOptional) {
        this.skipOptional = skipOptional;
    }

    Map<String, ContextNode<?>> contextByPath() {
        return contextByPath;
    }

    Queue<Node> stack() {
        return stack;
    }

    List<UserInputNode> unresolvedInputs() {
        return unresolvedInputs;
    }

    @Override
    public Void visit(ArchetypeNode node, Node parent) {
        pushToStack(node);
        acceptAll(node, parent);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(StepNode node, Node parent) {
        applyAdditionalVisitors(node);
        pushToStack(node);
        acceptAll(node);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(InputBlockNode node, Node parent) {
        applyAdditionalVisitors(node);
        pushToStack(node);
        acceptAll(node);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(InputBooleanNode node, Node parent) {
        applyAdditionalVisitors(node);
        node.defaultValue(replaceDefaultValue(node.defaultValue()));
        validate(node);
        pushToStack(node);
        boolean result = resolve(node);
        if (!result) {
            InputNode<?> unresolvedUserInputNode = userInputVisitor.visit(node, parent);
            processUnresolvedInput(node, unresolvedUserInputNode);
        } else {
            if (((ContextBooleanNode) getContextNode(node)).value()) {
                acceptAll(node);
            } else {
                node.children().clear();
            }
        }
        stack.pop();
        return null;
    }

    @Override
    public Void visit(InputEnumNode node, Node parent) {
        applyAdditionalVisitors(node);
        node.defaultValue(replaceDefaultValue(node.defaultValue()));
        validate(node);
        pushToStack(node);
        boolean result = resolve(node);
        if (!result) {
            InputNode<?> unresolvedUserInputNode = userInputVisitor.visit(node, parent);
            processUnresolvedInput(node, unresolvedUserInputNode);
        }
        acceptAll(node);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(InputListNode node, Node parent) {
        applyAdditionalVisitors(node);
        node.defaultValue(replaceDefaultValue(node.defaultValue()));
        validate(node);
        pushToStack(node);
        boolean result = resolve(node);
        if (!result) {
            InputNode<?> unresolvedUserInputNode = userInputVisitor.visit(node, parent);
            processUnresolvedInput(node, unresolvedUserInputNode);
        }
        acceptAll(node);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(InputTextNode node, Node parent) {
        applyAdditionalVisitors(node);
        node.defaultValue(replaceDefaultValue(node.defaultValue()));
        node.placeHolder(replaceDefaultValue(node.placeHolder()));
        validate(node);
        pushToStack(node);
        boolean result = resolve(node);
        if (!result) {
            InputNode<?> unresolvedUserInputNode = userInputVisitor.visit(node, parent);
            processUnresolvedInput(node, unresolvedUserInputNode);
        }
        stack.pop();
        return null;
    }

    @Override
    public Void visit(ExecNode node, Node parent) {
        applyAdditionalVisitors(node);
        if (pushToStack(node)) {
            ArchetypeDescriptor descriptor = archetype
                    .getDescriptor(resolveScriptPath(node.location().scriptDirectory(), node.descriptor().src()));
            String currentDir = Paths
                    .get(resolveScriptPath(node.location().scriptDirectory(), node.descriptor().src()))
                    .getParent().toString();
            Location location = Location.create(currentDir, currentDir);
            ArchetypeNode archetypeNode = DescriptorNodeFactory.create(descriptor, node, location);
            node.help(archetypeNode.descriptor().help());
            node.children().addAll(archetypeNode.children());
            archetypeNode.accept(this, node);
        }
        stack.pop();
        return null;
    }

    @Override
    public Void visit(SourceNode node, Node parent) {
        applyAdditionalVisitors(node);
        if (pushToStack((node))) {
            ArchetypeDescriptor descriptor = archetype.getDescriptor(
                    resolveScriptPath(node.location().scriptDirectory(), node.descriptor().source()));
            String currentDir = Paths
                    .get(resolveScriptPath(node.location().scriptDirectory(), node.descriptor().source()))
                    .getParent().toString();
            Location location = Location.create(currentDir, node.location().currentDirectory());
            ArchetypeNode archetypeNode = DescriptorNodeFactory.create(descriptor, node, location);
            node.children().addAll(archetypeNode.children());
            archetypeNode.accept(this, node);
        }
        stack.pop();
        return null;
    }

    @Override
    public Void visit(ContextBlockNode context, Node parent) {
        if (context.parent() == null && parent != null) {
            context.parent(parent);
        }
        applyAdditionalVisitors(context);
        cleanUnresolvedInputs(context);
        acceptAll(context);
        return null;
    }

    @Override
    public Void visit(ContextBooleanNode contextBoolean, Node parent) {
        if (contextBoolean.parent() == null && parent != null) {
            contextBoolean.parent(parent);
        }
        applyAdditionalVisitors(contextBoolean);
        contextByPath.putIfAbsent(contextBoolean.path(), contextBoolean);
        return null;
    }

    @Override
    public Void visit(DescriptorNodes.ContextEnumNode contextEnum, Node parent) {
        if (contextEnum.parent() == null && parent != null) {
            contextEnum.parent(parent);
        }
        applyAdditionalVisitors(contextEnum);
        contextByPath.putIfAbsent(contextEnum.path(), contextEnum);
        return null;
    }

    @Override
    public Void visit(DescriptorNodes.ContextListNode contextList, Node parent) {
        if (contextList.parent() == null && parent != null) {
            contextList.parent(parent);
        }
        applyAdditionalVisitors(contextList);
        contextByPath.putIfAbsent(contextList.path(), contextList);
        return null;
    }

    @Override
    public Void visit(ContextTextNode contextText, Node parent) {
        if (contextText.parent() == null && parent != null) {
            contextText.parent(parent);
        }
        applyAdditionalVisitors(contextText);
        contextByPath.putIfAbsent(contextText.path(), contextText);
        return null;
    }

    @Override
    public Void visit(InputOptionNode input, Node parent) {
        applyAdditionalVisitors(input);
        pushToStack(input);
        acceptAll(input);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(OutputNode output, Node parent) {
        applyAdditionalVisitors(output);
        pushToStack(output);
        acceptAll(output);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(TransformationNode transformation, Node parent) {
        applyAdditionalVisitors(transformation);
        return null;
    }

    @Override
    public Void visit(FileSetsNode fileSets, Node parent) {
        applyAdditionalVisitors(fileSets);
        return null;
    }

    @Override
    public Void visit(FileSetNode fileSet, Node parent) {
        applyAdditionalVisitors(fileSet);
        return null;
    }

    @Override
    public Void visit(TemplateNode template, Node parent) {
        applyAdditionalVisitors(template);
        pushToStack(template);
        acceptAll(template);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(TemplatesNode templates, Node parent) {
        applyAdditionalVisitors(templates);
        pushToStack(templates);
        acceptAll(templates);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(ModelNode model, Node parent) {
        applyAdditionalVisitors(model);
        pushToStack(model);
        acceptAll(model);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(IfStatementNode input, Node parent) {
        applyAdditionalVisitors(input);
        pushToStack(input);
        Map<String, String> contextValuesMap = convertContext();
        if (input.expression().evaluate(contextValuesMap)) {
            acceptAll(input);
        } else {
            input.children().clear();
        }
        stack.pop();
        return null;
    }

    @Override
    public Void visit(ModelKeyedValueNode value, Node parent) {
        applyAdditionalVisitors(value);
        pushToStack(value);
        acceptAll(value);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(ModelValueNode<?> value, Node parent) {
        applyAdditionalVisitors(value);
        pushToStack(value);
        acceptAll(value);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(ModelKeyedListNode list, Node parent) {
        applyAdditionalVisitors(list);
        pushToStack(list);
        acceptAll(list);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(ModelMapNode<?> node, Node parent) {
        applyAdditionalVisitors(node);
        pushToStack(node);
        acceptAll(node);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(ModelListNode<?> node, Node parent) {
        applyAdditionalVisitors(node);
        pushToStack(node);
        acceptAll(node);
        stack.pop();
        return null;
    }

    @Override
    public Void visit(ModelKeyedMapNode map, Node parent) {
        applyAdditionalVisitors(map);
        pushToStack(map);
        acceptAll(map);
        stack.pop();
        return null;
    }

    private void acceptAll(Node node) {
        while (node.hasNext()) {
            Node next = node.next();
            next.accept(this, node);
        }
    }

    private void acceptAll(Node node, Node parent) {
        while (node.hasNext()) {
            Node next = node.next();
            next.accept(this, parent);
        }
    }

    private boolean pushToStack(Node node) {
        if (node != stack.peek()) {
            stack.push(node);
            return true;
        }
        return false;
    }

    private boolean resolve(InputNode<?> input) {
        ContextNode<?> contextNode = contextByPath.get(input.path());
        if (input.descriptor().isOptional() && skipOptional && contextNode == null) {
            contextNode = input.accept(defaultValueCreator, input);
            if (contextNode != null) {
                contextByPath.put(contextNode.path(), contextNode);
            }
        }
        if (contextNode != null) {
            input.accept(inputResolverVisitor, contextNode);
        }
        return contextNode != null;
    }

    private ContextNode<?> getContextNode(InputNode<?> input) {
        return contextByPath.get(input.path());
    }

    /**
     * Create unresolvedInput, add it to the {@code unresolvedInputs} and throw {@link WaitForUserInput} to stop the
     * interpreting process.
     *
     * @param inputNode     initial unresolved {@code InputNodeAST} from the AST tree.
     * @param userInputNode userInput that will be sent to the user
     */
    private void processUnresolvedInput(InputNode<?> inputNode, InputNode<?> userInputNode) {
        StepNode stepNode = getParentStep(inputNode);
        UserInputNode unresolvedInput = new UserInputNode(stepNode, inputNode);
        unresolvedInput.children().add(userInputNode);
        unresolvedInputs.add(unresolvedInput);
        if (inputNode.descriptor().isOptional()) {
            updateCanBeGenerated();
        }
        throw new WaitForUserInput();
    }

    private void updateCanBeGenerated() {
        if (canBeGenerated) {
            return;
        }
        ContextBlockNode context = new ContextBlockNode();
        context.children().addAll(contextByPath.values());
        FlowState state = Flow.builder()
                              .archetype(archetype)
                              .entrypoint(entrypoint)
                              .skipOptional(true)
                              .build()
                              .build(context);
        if (state.type() == FlowStateEnum.READY) {
            canBeGenerated = true;
        }
    }

    private StepNode getParentStep(Node node) {
        if (node.parent() == null) {
            return (StepNode) node;
        }
        if (node.parent() instanceof StepNode) {
            return (StepNode) node.parent();
        }
        return getParentStep(node.parent());
    }

    private void cleanUnresolvedInputs(ContextBlockNode context) {
        unresolvedInputs.removeIf(input -> context
                .children().stream()
                .anyMatch(contextNote -> ((ContextNode<?>) contextNote).path().equals(input.path())));
    }

    private String resolveScriptPath(String currentDirectory, String scriptSrc) {
        return Paths.get(currentDirectory).resolve(scriptSrc).normalize().toString();
    }

    private String resolveDirectory(String currentValue, Location location) {
        if (currentValue.startsWith("/")) {
            return currentValue;
        }
        return Paths.get(location.currentDirectory()).resolve(currentValue).normalize().toString();
    }

    private void validate(InputNode<?> input) {
        if (input.descriptor().isOptional() && input.defaultValue() == null) {
            if (!(input instanceof InputTextNode && ((InputTextNode) input).placeHolder() != null)) {
                throw new InterpreterException(
                        "Input node %s is optional but it does not have a default value",
                        input.path());
            }
        }
    }

    private void applyAdditionalVisitors(Node node) {
        additionalVisitors.forEach(visitor -> node.accept(visitor, null));
    }

    private Map<String, String> convertContext() {
        Map<String, String> result = new HashMap<>();
        contextByPath.forEach((key, value) -> result.putIfAbsent(key, value.accept(contextConvertor, null)));
        return result;
    }

    private String replaceDefaultValue(String defaultValue) {
        if (defaultValue == null) {
            return null;
        }
        if (!defaultValue.contains("${")) {
            return defaultValue;
        }
        Map<String, String> properties = convertContext();
        properties.replaceAll((key, value) -> value.replaceAll("[\\['\\]]", ""));
        return PropertyEvaluator.resolve(defaultValue, properties);
    }
}
