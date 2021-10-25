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

import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ArchetypeNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextBlockNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextBooleanNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextEnumNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextListNode;
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
import io.helidon.build.archetype.engine.v2.descriptor.ArchetypeDescriptor;
import io.helidon.build.archetype.engine.v2.descriptor.Context;
import io.helidon.build.archetype.engine.v2.descriptor.ContextBlock;
import io.helidon.build.archetype.engine.v2.descriptor.ContextBoolean;
import io.helidon.build.archetype.engine.v2.descriptor.ContextEnum;
import io.helidon.build.archetype.engine.v2.descriptor.ContextList;
import io.helidon.build.archetype.engine.v2.descriptor.ContextText;
import io.helidon.build.archetype.engine.v2.descriptor.Exec;
import io.helidon.build.archetype.engine.v2.descriptor.Input;
import io.helidon.build.archetype.engine.v2.descriptor.InputBlock;
import io.helidon.build.archetype.engine.v2.descriptor.InputBoolean;
import io.helidon.build.archetype.engine.v2.descriptor.InputEnum;
import io.helidon.build.archetype.engine.v2.descriptor.InputList;
import io.helidon.build.archetype.engine.v2.descriptor.InputText;
import io.helidon.build.archetype.engine.v2.descriptor.InputOption;
import io.helidon.build.archetype.engine.v2.descriptor.Model;
import io.helidon.build.archetype.engine.v2.descriptor.ModelKeyedList;
import io.helidon.build.archetype.engine.v2.descriptor.ModelKeyedMap;
import io.helidon.build.archetype.engine.v2.descriptor.ModelList;
import io.helidon.build.archetype.engine.v2.descriptor.ModelMap;
import io.helidon.build.archetype.engine.v2.descriptor.Output;
import io.helidon.build.archetype.engine.v2.descriptor.Source;
import io.helidon.build.archetype.engine.v2.descriptor.Step;
import io.helidon.build.archetype.engine.v2.descriptor.Template;
import io.helidon.build.archetype.engine.v2.interpreter.InterpreterException;

import java.util.List;
import java.util.function.Supplier;

import static io.helidon.build.archetype.engine.v2.ast.ConditionalNode.mapConditional;
import static java.util.stream.Collectors.toList;

/**
 * Utility to create nodes from descriptor.
 */
public final class DescriptorNodeFactory {

    private DescriptorNodeFactory() {
    }

    static InputNode<?> input(Input input, Node parent, Location location) {
        if (input instanceof InputBoolean) {
            return inputBoolean((InputBoolean) input, parent, location);
        }
        if (input instanceof InputEnum) {
            return inputEnum((InputEnum) input, parent, location);
        }
        if (input instanceof InputList) {
            return inputList((InputList) input, parent, location);
        }
        if (input instanceof InputText) {
            return new InputTextNode((InputText) input, parent, location);
        }
        throw new InterpreterException(
                "Unsupported type of the InputNode with name %s and type %s",
                input.name(),
                input.getClass());
    }

    static ContextNode<?> context(Context context, Node parent, Location location) {
        if (context instanceof ContextBoolean) {
            return new ContextBooleanNode((ContextBoolean) context, parent, location);
        }
        if (context instanceof ContextEnum) {
            return new ContextEnumNode((ContextEnum) context, parent, location);
        }
        if (context instanceof ContextList) {
            return new ContextListNode((ContextList) context, parent, location);
        }
        if (context instanceof ContextText) {
            return new ContextTextNode((ContextText) context, parent, location);
        }
        throw new InterpreterException(
                "Unsupported type of the ContextNode with path %s and type %s",
                context.path(),
                context.getClass());
    }

//    static ContextNode<?, ?> context(InputNode<?> input, String path, String stringValue) {
//        if (input instanceof InputBooleanNode) {
//            ContextBooleanNode result = new ContextBooleanNode(path);
//            result.value(Boolean.parseBoolean(stringValue));
//            return result;
//        }
//        if (input instanceof InputEnumNode) {
//            ContextEnumNode result = new ContextEnumNode(path);
//            result.value(stringValue);
//            return result;
//        }
//        if (input instanceof InputListNode) {
//            ContextListNode result = new ContextListNode(path);
//            result.value().addAll(List.of(stringValue.split(",")));
//            return result;
//        }
//        if (input instanceof InputTextNode) {
//            ContextTextNode result = new ContextTextNode(path);
//            result.value(stringValue);
//            return result;
//        }
//        throw new InterpreterException(
//                "Unsupported type of the InputNodeAST with type %s, path %s and value %s",
//                input.getClass().getName(),
//                path,
//                stringValue);
//    }


    public static ContextNode<?> create(InputNode inputNode, String path, String s) {
        return null;
    }

    public static <T extends Object> DescriptorNode<T> create(T desc) {
        return create(desc, null, Location.create());
    }

    public static <T extends Object> DescriptorNode<T> create(T desc, Node parent, Location location) {
        return null;
    }

    public static ArchetypeNode create(ArchetypeDescriptor desc, Node parent, Location location) {
        ArchetypeNode node = new ArchetypeNode(desc, location);
        desc.attributes().forEach((key, value) -> node.descriptor().attributes().putIfAbsent(key, value));
        node.children().addAll(contextBlocks(desc::contexts, parent, location));
        node.children().addAll(steps(desc::steps, parent, location));
        node.children().addAll(inputBlocks(desc::inputBlocks, parent, location));
        node.children().addAll(sources(desc::sources, parent, location));
        node.children().addAll(execs(desc::execs, parent, location));
        if (desc.output() != null) {
            node.children().add(
                    mapConditional(
                            desc.output(),
                            output(desc.output(), node, location),
                            node,
                            location
                    ));
        }
        return node;
    }

    static OutputNode output(Output desc, Node parent, Location location) {
        OutputNode node = new OutputNode(desc, parent, location);
        if (desc.model() != null) {
            node.children().add(
                    mapConditional(
                            desc.model(),
                            model(desc.model(), parent, location),
                            parent,
                            location));
        }
        node.children().addAll(desc.transformations()
                                   .stream()
                                   .map(t -> new TransformationNode(t, parent, location))
                                   .collect(toList()));
        node.children().addAll(desc.filesList()
                                   .stream()
                                   .map(fs -> mapConditional(fs, new FileSetsNode(fs, parent, location), parent, location))
                                   .collect(toList()));
        node.children().addAll(desc.fileList()
                                   .stream()
                                   .map(fl -> mapConditional(fl, new FileSetNode(fl, parent, location), parent, location))
                                   .collect(toList()));
        node.children().addAll(desc.template()
                                   .stream()
                                   .map(t -> mapConditional(t, template(t, parent, location), parent, location))
                                   .collect(toList()));
        node.children().addAll(desc.templates()
                                   .stream()
                                   .map(t -> mapConditional(t, new TemplatesNode(t, parent, location), parent, location))
                                   .collect(toList()));
        return node;
    }

    static TemplateNode template(Template desc, Node parent, Location location) {
        TemplateNode node = new TemplateNode(desc, parent, location);
        if (desc.model() != null) {
            ModelNode model = model(desc.model(), node, location);
            node.children().add(mapConditional(desc.model(), model, node, location));
        }
        return node;
    }

    private static ModelNode model(Model desc, Node parent, Location location) {
        ModelNode result = new ModelNode(desc, parent, location);
        result.children().addAll(modelValues(desc, parent, location));
        result.children().addAll(modelLists(desc, parent, location));
        result.children().addAll(modelKeyedMaps(desc, parent, location));
        return result;
    }

    static ModelKeyedMapNode modelKeyedMap(ModelKeyedMap desc, Node parent, Location location) {
        ModelKeyedMapNode node = new ModelKeyedMapNode(desc, parent, location);
        node.children().addAll(desc.keyValues()
                                   .stream()
                                   .map(v -> mapConditional(v, new ModelKeyedValueNode(v, parent, location), parent, location))
                                   .collect(toList()));
        node.children().addAll(desc.keyLists()
                                   .stream()
                                   .map(l -> mapConditional(l, modelKeyedList(l, parent, location), parent, location))
                                   .collect(toList()));
        node.children().addAll(desc.keyMaps()
                                   .stream()
                                   .map(m -> mapConditional(m, modelKeyedMap(m, parent, location), parent, location))
                                   .collect(toList()));
        return node;
    }

    static ModelMapNode<ModelMap> modelMap(ModelMap desc, Node parent, Location location) {
        ModelMapNode<ModelMap> node = new ModelMapNode<>(desc, parent, location);
        node.children().addAll(desc.keyValues().stream()
                                   .map(v -> mapConditional(
                                           v, new ModelKeyedValueNode(v, parent, location), parent, location))
                                   .collect(toList()));
        node.children().addAll(desc.keyLists().stream()
                                   .map(l -> mapConditional(
                                           l, new ModelKeyedListNode(l, parent, location), parent, location))
                                   .collect(toList()));
        node.children().addAll(desc.keyMaps().stream()
                                   .map(m -> mapConditional(
                                           m, modelKeyedMap(m, parent, location), parent, location))
                                   .collect(toList()));
        return node;
    }

    private static Node modelList(ModelList desc, Node parent, Location location) {
        ModelListNode<ModelList> result = new ModelListNode<>(desc, parent, location);
        result.children().addAll(desc.values()
                                     .stream()
                                     .map(v -> mapConditional(v, new ModelValueNode<>(v, parent, location), parent, location))
                                     .collect(toList()));
        result.children().addAll(desc.maps()
                                     .stream()
                                     .map(m -> mapConditional(m, modelMap(m, parent, location), parent, location))
                                     .collect(toList()));
        result.children().addAll(desc.lists()
                                     .stream()
                                     .map(l -> mapConditional(l, modelList(l, parent, location), parent, location))
                                     .collect(toList()));
        return result;
    }

    private static List<Node> modelValues(Model model, Node parent, Location location) {
        return model.keyedValues()
                    .stream()
                    .map(v -> mapConditional(v, new ModelKeyedValueNode(v, parent, location), parent, location))
                    .collect(toList());
    }


    static ModelKeyedListNode modelKeyedList(ModelKeyedList desc, Node parent, Location location) {
        ModelKeyedListNode node = new ModelKeyedListNode(desc, parent, location);
        node.children().addAll(desc.values()
                                   .stream()
                                   .map(v -> mapConditional(v, new ModelValueNode<>(v, parent, location), parent, location))
                                   .collect(toList()));
        node.children().addAll(desc.maps()
                                   .stream()
                                   .map(m -> mapConditional(m, new ModelMapNode<>(m, parent, location), parent, location))
                                   .collect(toList()));
        node.children().addAll(desc.lists()
                                   .stream()
                                   .map(l -> mapConditional(l, modelList(l, parent, location), parent, location))
                                   .collect(toList()));
        return node;
    }

    private static List<Node> modelLists(Model model, Node parent, Location location) {
        return model.keyedLists()
                    .stream()
                    .map(l -> mapConditional(l, modelKeyedList(l, parent, location), parent, location))
                    .collect(toList());
    }

    private static List<Node> modelKeyedMaps(Model model, Node parent, Location location) {
        return model.keyedMaps()
                    .stream()
                    .map(m -> mapConditional(m, modelKeyedMap(m, parent, location), parent, location))
                    .collect(toList());
    }

    static InputOptionNode inputOption(InputOption desc, Node parent, Location location) {
        InputOptionNode node = new InputOptionNode(desc, parent, location);
        node.children().addAll(contextBlocks(desc::contexts, node, location));
        node.children().addAll(steps(desc::steps, node, location));
        node.children().addAll(inputBlocks(desc::inputBlocks, node, location));
        node.children().addAll(sources(desc::sources, node, location));
        node.children().addAll(execs(desc::execs, node, location));
        if (desc.output() != null) {
            node.children().add(
                    mapConditional(
                            desc.output(),
                            output(desc.output(), node, location),
                            node,
                            location));
        }
        return node;
    }

    static InputEnumNode inputEnum(InputEnum desc, Node parent, Location location) {
        InputEnumNode node = new InputEnumNode(desc, parent, location);
        node.children().addAll(contextBlocks(desc::contexts, node, location));
        node.children().addAll(steps(desc::steps, node, location));
        node.children().addAll(inputBlocks(desc::inputsBlocks, node, location));
        node.children().addAll(sources(desc::sources, node, location));
        node.children().addAll(execs(desc::execs, node, location));
        if (desc.output() != null) {
            node.children().add(
                    mapConditional(
                            desc.output(),
                            output(desc.output(), node, location),
                            node,
                            location
                    ));
        }
        node.children().addAll(options(desc::options, node, location));
        return node;
    }

    private static InputBlockNode inputBlock(InputBlock desc, Node parent, Location location) {
        InputBlockNode node = new InputBlockNode(desc, parent, location);
        node.children().addAll(contextBlocks(desc::contexts, node, location));
        node.children().addAll(steps(desc::steps, node, location));
        node.children().addAll(inputs(desc::inputs, node, location));
        node.children().addAll(inputBlocks(desc::inputBlocks, node, location));
        node.children().addAll(sources(desc::sources, node, location));
        node.children().addAll(execs(desc::execs, node, location));
        if (desc.output() != null) {
            node.children().add(mapConditional(
                    desc.output(),
                    output(desc.output(), node, location),
                    node,
                    location));
        }
        return node;
    }

    private static InputBooleanNode inputBoolean(InputBoolean desc, Node parent, Location location) {
        InputBooleanNode node = new InputBooleanNode(desc, parent, location);
        node.children().addAll(contextBlocks(desc::contexts, node, location));
        node.children().addAll(steps(desc::steps, node, location));
        node.children().addAll(inputBlocks(desc::inputBlocks, node, location));
        node.children().addAll(sources(desc::sources, node, location));
        node.children().addAll(execs(desc::execs, node, location));
        if (desc.output() != null) {
            node.children().add(mapConditional(
                    desc.output(),
                    output(desc.output(), node, location),
                    node,
                    location));
        }
        return node;
    }

    private static InputListNode inputList(InputList desc, Node parent, Location location) {
        InputListNode node = new InputListNode(desc, parent, location);
        node.children().addAll(contextBlocks(desc::contexts, node, location));
        node.children().addAll(steps(desc::steps, node, location));
        node.children().addAll(inputBlocks(desc::inputBlocks, node, location));
        node.children().addAll(sources(desc::sources, node, location));
        node.children().addAll(execs(desc::execs, node, location));
        if (desc.output() != null) {
            node.children().add(mapConditional(
                    desc.output(),
                    output(desc.output(), node, location),
                    node,
                    location));
        }
        node.children().addAll(options(desc::options, node, location));
        return node;
    }

    static StepNode step(Step desc, Node parent, Location location) {
        StepNode node = new StepNode(desc, parent, location);
        node.children().addAll(contextBlocks(desc::contexts, node, location));
        node.children().addAll(inputBlocks(desc::inputBlocks, node, location));
        node.children().addAll(sources(desc::sources, node, location));
        node.children().addAll(execs(desc::execs, node, location));
        if (desc.ifProperties() != null) {
            IfStatementNode ifNode = new IfStatementNode(desc.ifProperties(), node, location);
            node.children().forEach(child -> child.parent(ifNode));
            ifNode.children().addAll(node.children());
            parent.children().add(ifNode);
        } else {
            node.children().forEach(child -> child.parent(parent));
        }
        return node;
    }

    private static List<InputOptionNode> options(Supplier<List<InputOption>> desc, Node parent, Location location) {
        return desc.get().stream().map(o -> inputOption(o, parent, location)).collect(toList());
    }

    private static ContextBlockNode contextBlock(ContextBlock desc, Node parent, Location location) {
        ContextBlockNode result = new ContextBlockNode(desc, parent, location);
        result.children()
              .addAll(desc.nodes()
                          .stream()
                          .map(n -> context(n, result, location))
                          .collect(toList()));
        return result;
    }

    private static List<ContextBlockNode> contextBlocks(Supplier<List<ContextBlock>> desc, Node parent, Location location) {
        return desc.get().stream().map(c -> contextBlock(c, parent, location)).collect(toList());
    }

    private static List<InputBlockNode> inputBlocks(Supplier<List<InputBlock>> desc, Node parent, Location location) {
        return desc.get().stream().map(i -> inputBlock(i, parent, location)).collect(toList());
    }

    private static List<InputNode<?>> inputs(Supplier<List<Input>> desc, Node parent, Location location) {
        return desc.get().stream().map(i -> input(i, parent, location)).collect(toList());
    }

    private static List<StepNode> steps(Supplier<List<Step>> desc, Node parent, Location location) {
        return desc.get().stream().map(s -> step(s, parent, location)).collect(toList());
    }

    private static List<SourceNode> sources(Supplier<List<Source>> desc, Node parent, Location location) {
        return desc.get().stream().map(s -> new SourceNode(s, parent, location)).collect(toList());
    }

    private static List<ExecNode> execs(Supplier<List<Exec>> desc, Node parent, Location location) {
        return desc.get().stream().map(s -> new ExecNode(s, parent, location)).collect(toList());
    }
}
