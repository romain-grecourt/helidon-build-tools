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

package io.helidon.build.archetype.engine.v2.descriptor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.build.common.xml.SimpleXMLParser;

/**
 * {@link ArchetypeDescriptor} reader.
 */
public class ArchetypeDescriptorReader implements SimpleXMLParser.Reader {

    private final LinkedList<String> stack;
    private final LinkedList<Object> objectTracking;

    private ArchetypeDescriptor.InputOption currentOption;
    private ArchetypeDescriptor.Step currentStep;
    private ArchetypeDescriptor.Output currentOutput;

    private boolean topLevelOutput = false;

    private final Map<String, String> archetypeAttributes = new HashMap<>();
    private final LinkedList<ArchetypeDescriptor.ContextBlock> context = new LinkedList<>();
    private final LinkedList<ArchetypeDescriptor.Step> steps = new LinkedList<>();
    private final LinkedList<ArchetypeDescriptor.InputBlock> inputs = new LinkedList<>();
    private final LinkedList<ArchetypeDescriptor.Source> source = new LinkedList<>();
    private final LinkedList<ArchetypeDescriptor.Exec> exec = new LinkedList<>();
    private ArchetypeDescriptor.Output output = null;
    private String help = null;

    private ArchetypeDescriptorReader() {
        stack = new LinkedList<>();
        objectTracking = new LinkedList<>();
    }

    /**
     * Read the descriptor from the given input stream.
     * @param is input stream
     * @return descriptor, never {@code null}
     */
    static ArchetypeDescriptor read(InputStream is) {
        try {
            ArchetypeDescriptorReader reader = new ArchetypeDescriptorReader();
            SimpleXMLParser.parse(is, reader);
            return new ArchetypeDescriptor(reader.archetypeAttributes, reader.context, reader.steps, reader.inputs,
                    reader.source, reader.exec, reader.output, reader.help);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    @SuppressWarnings("checkstyle:MethodLength")
    public void startElement(String qName, Map<String, String> attributes) {
        String parent = stack.peek();
        if (parent == null) {
            if (!"archetype-script".equals(qName)) {
                throw new IllegalStateException("Invalid root element '" + qName + "'");
            }
            archetypeAttributes.putAll(attributes);
            stack.push("archetype-script");
        } else {
            switch (parent) {
                case "archetype-script":
                    switch (qName) {
                        case "exec":
                            exec.add(new ArchetypeDescriptor.Exec(attributes.get("url"), attributes.get("src")));
                            objectTracking.add(exec.getLast());
                            stack.push(qName);
                            break;
                        case "source":
                            source.add(new ArchetypeDescriptor.Source(attributes.get("url"), attributes.get("src")));
                            objectTracking.add(source.getLast());
                            stack.push("source");
                            break;
                        case "step":
                            steps.add(new ArchetypeDescriptor.Step(attributes.get("label"), attributes.get("if")));
                            currentStep = steps.getLast();
                            objectTracking.add(steps.getLast());
                            stack.push(qName);
                            break;
                        case "output":
                            topLevelOutput = true;
                            currentOutput = new ArchetypeDescriptor.Output(attributes.get("if"));
                            objectTracking.add(currentOutput);
                            stack.push(qName);
                            break;
                        case "context":
                            context.add(new ArchetypeDescriptor.ContextBlock());
                            objectTracking.add(context.getLast());
                            stack.push(qName);
                            break;
                        case "input":
                            inputs.add(new ArchetypeDescriptor.InputBlock());
                            objectTracking.add(inputs.getLast());
                            stack.push(qName);
                            break;
                        case "help":
                            objectTracking.add("help");
                            stack.push(qName);
                            break;
                        default:
                            throw new IllegalStateException("Invalid top-level element: " + qName);
                    }
                    break;
                case "exec":
                case "source":
                    break;
                case "context/list":
                    validateChild("value", "context/list", qName);
                    objectTracking.add(parent + "/value");
                    stack.push(parent + "/value");
                    break;
                case "context/enum":
                    validateChild("value", "context/enum", qName);
                    objectTracking.add(parent + "/value");
                    stack.push(parent + "/value");
                    break;
                case "step":
                    switch (qName) {
                        case "context":
                            currentStep.contexts().add(new ArchetypeDescriptor.ContextBlock());
                            objectTracking.add(currentStep.contexts().getLast());
                            stack.push("context");
                            break;
                        case "exec":
                            currentStep.execs().add(new ArchetypeDescriptor.Exec(attributes.get("url"), attributes.get("src")));
                            objectTracking.add(currentStep.execs().getLast());
                            stack.push("step/" + qName);
                            break;
                        case "source":
                            currentStep.sources().add(new ArchetypeDescriptor.Source(attributes.get("url"), attributes.get("src")));
                            objectTracking.add(currentStep.sources().getLast());
                            stack.push("step/" + qName);
                            break;
                        case "input":
                            currentStep.inputBlocks().add(new ArchetypeDescriptor.InputBlock());
                            objectTracking.add(currentStep.inputBlocks().getLast());
                            stack.push("input");
                            break;
                        case "help":
                            objectTracking.add("help");
                            stack.push("step/help");
                            break;
                        default:
                            throw new IllegalStateException("Invalid element: " + qName + "with parent: " + parent);
                    }
                    break;
                case "context":
                    switch (qName) {
                        case "boolean":
                            ((ArchetypeDescriptor.ContextBlock) objectTracking.getLast()).nodes().add(new ArchetypeDescriptor.ContextBoolean(attributes.get("path")));
                            break;
                        case "list":
                            ((ArchetypeDescriptor.ContextBlock) objectTracking.getLast()).nodes().add(new ArchetypeDescriptor.ContextList(attributes.get("path")));
                            break;
                        case "enum":
                            ((ArchetypeDescriptor.ContextBlock) objectTracking.getLast()).nodes().add(new ArchetypeDescriptor.ContextEnum(attributes.get("path")));
                            break;
                        case "text":
                            ((ArchetypeDescriptor.ContextBlock) objectTracking.getLast()).nodes().add(new ArchetypeDescriptor.ContextText(attributes.get("path")));
                            break;
                        default:
                            throw new IllegalStateException("Invalid Context child element: " +  qName);
                    }
                    objectTracking.add("context/" + qName);
                    stack.push("context/" + qName);
                    break;
                case "input":
                    if (!(objectTracking.getLast() instanceof ArchetypeDescriptor.InputBlock)) {
                        throw new IllegalStateException("Invalid object stack element for Input");
                    }
                    ArchetypeDescriptor.InputBlock currentInput = (ArchetypeDescriptor.InputBlock) objectTracking.getLast();
                    switch (qName) {
                        case "text":
                            addInputText(currentInput, attributes);
                            objectTracking.add(currentInput.inputs().getLast());
                            stack.push("input/text");
                            break;
                        case "boolean":
                            currentInput.inputs().add(new ArchetypeDescriptor.InputBoolean(
                                    attributes.get("label"),
                                    attributes.get("name"),
                                    attributes.get("default"),
                                    attributes.get("prompt"),
                                    parseBoolean(attributes.get("optional") == null ? "false" : attributes.get("optional"))
                            ));
                            objectTracking.add(currentInput.inputs().getLast());
                            stack.push("input/boolean");
                            break;
                        case "enum":
                            currentInput.inputs().add(new ArchetypeDescriptor.InputEnum(
                                    attributes.get("label"),
                                    attributes.get("name"),
                                    attributes.get("default"),
                                    attributes.get("prompt"),
                                    parseBoolean(attributes.get("optional") == null ? "false" : attributes.get("optional"))
                            ));
                            objectTracking.add(currentInput.inputs().getLast());
                            stack.push("input/enum");
                            break;
                        case "list":
                            currentInput.inputs().add(new ArchetypeDescriptor.InputList(
                                    attributes.get("label"),
                                    attributes.get("name"),
                                    attributes.get("default"),
                                    attributes.get("prompt"),
                                    parseBoolean(attributes.get("optional") == null ? "false" : attributes.get("optional")),
                                    attributes.get("min"),
                                    attributes.get("max"),
                                    attributes.get("help")
                            ));
                            objectTracking.add(currentInput.inputs().getLast());
                            stack.push("input/list");
                            break;
                        case "output":
                            currentInput.output(new ArchetypeDescriptor.Output(attributes.get("if")));
                            currentOutput = currentInput.output();
                            objectTracking.add(currentInput.output());
                            stack.push("output");
                            break;
                        case "context":
                            currentInput.contexts().add(new ArchetypeDescriptor.ContextBlock());
                            objectTracking.add(currentInput.contexts().getLast());
                            stack.push("context");
                            break;
                        case "exec":
                            currentInput.execs().add(new ArchetypeDescriptor.Exec(attributes.get("url"), attributes.get("src")));
                            objectTracking.add(currentInput.execs().getLast());
                            stack.push("exec");
                            break;
                        case "source":
                            currentInput.sources().add(new ArchetypeDescriptor.Source(attributes.get("url"), attributes.get("src")));
                            objectTracking.add(currentInput.sources().getLast());
                            stack.push("source");
                            break;
                        case "input":
                            currentInput.inputBlocks().add(new ArchetypeDescriptor.InputBlock());
                            currentInput = currentInput.inputBlocks().getLast();
                            objectTracking.add(currentInput.inputBlocks().getLast());
                            stack.push("input");
                            break;
                        case "step":
                            currentInput.steps().add(new ArchetypeDescriptor.Step(attributes.get("label"), attributes.get("if")));
                            currentStep = currentInput.steps().getLast();
                            objectTracking.add(currentInput.steps().getLast());
                            stack.push("step");
                            break;
                        default:
                            throw new IllegalStateException("Invalid Input child element: " +  qName);
                    }
                    break;
                case "input/boolean":
                    if (!(objectTracking.getLast() instanceof ArchetypeDescriptor.InputBoolean)) {
                        throw new IllegalStateException("Invalid object stack element for Boolean");
                    }
                    ArchetypeDescriptor.InputBoolean tempBool = (ArchetypeDescriptor.InputBoolean) objectTracking.getLast();
                    switch (qName) {
                        case "context":
                            tempBool.contexts().add(new ArchetypeDescriptor.ContextBlock());
                            objectTracking.add(tempBool.contexts().getLast());
                            stack.push("context");
                            break;
                        case "exec":
                            tempBool.execs().add(new ArchetypeDescriptor.Exec(attributes.get("url"), attributes.get("src")));
                            objectTracking.add(tempBool.execs().getLast());
                            stack.push("exec");
                            break;
                        case "source":
                            tempBool.sources().add(new ArchetypeDescriptor.Source(attributes.get("url"), attributes.get("src")));
                            objectTracking.add(tempBool.sources().getLast());
                            stack.push("source");
                            break;
                        case "input":
                            ArchetypeDescriptor.InputBlock input = new ArchetypeDescriptor.InputBlock();
                            tempBool.inputBlocks().add(input);
                            objectTracking.add(input);
                            stack.push("input");
                            break;
                        case "step":
                            tempBool.steps().add(new ArchetypeDescriptor.Step(attributes.get("label"), attributes.get("if")));
                            currentStep = tempBool.steps().getLast();
                            objectTracking.add(tempBool.steps().getLast());
                            stack.push("step");
                            break;
                        case "output":
                            tempBool.output(new ArchetypeDescriptor.Output(attributes.get("if")));
                            currentOutput = tempBool.output();
                            objectTracking.add(tempBool.output());
                            stack.push("output");
                            break;
                        case "help":
                            objectTracking.add(objectTracking.getLast());
                            stack.push("input/boolean/help");
                            break;
                        default:
                            throw new IllegalStateException("Invalid Context child element: " +  qName);
                    }
                    break;
                case "input/enum":
                    if (!(objectTracking.getLast() instanceof ArchetypeDescriptor.InputEnum)) {
                        throw new IllegalStateException("Invalid object stack element for Enum");
                    }
                    ArchetypeDescriptor.InputEnum tempEnum = (ArchetypeDescriptor.InputEnum) objectTracking.getLast();
                    switch (qName) {
                        case "context":
                            tempEnum.contexts().add(new ArchetypeDescriptor.ContextBlock());
                            objectTracking.add(tempEnum.contexts().getLast());
                            stack.push("context");
                            break;
                        case "exec":
                            tempEnum.execs().add(new ArchetypeDescriptor.Exec(attributes.get("url"), attributes.get("src")));
                            objectTracking.add(tempEnum.execs().getLast());
                            stack.push("exec");
                            break;
                        case "source":
                            tempEnum.sources().add(new ArchetypeDescriptor.Source(attributes.get("url"), attributes.get("src")));
                            objectTracking.add(tempEnum.sources().getLast());
                            stack.push("source");
                            break;
                        case "input":
                            tempEnum.inputsBlocks().add(new ArchetypeDescriptor.InputBlock());
                            objectTracking.add(tempEnum.inputsBlocks().getLast());
                            stack.push("input");
                            break;
                        case "step":
                            tempEnum.steps().add(new ArchetypeDescriptor.Step(attributes.get("label"), attributes.get("if")));
                            currentStep = tempEnum.steps().getLast();
                            objectTracking.add(tempEnum.steps().getLast());
                            stack.push("step");
                            break;
                        case "output":
                            tempEnum.output(new ArchetypeDescriptor.Output(attributes.get("if")));
                            currentOutput = tempEnum.output();
                            objectTracking.add(tempEnum.output());
                            stack.push("output");
                            break;
                        case "help":
                            objectTracking.add("help");
                            stack.push("input/enum/help");
                            break;
                        case "option":
                            tempEnum.options().add(new ArchetypeDescriptor.InputOption(attributes.get("label"), attributes.get("value")));
                            currentOption = tempEnum.options().getLast();
                            objectTracking.add(tempEnum.options().getLast());
                            stack.push("option");
                            break;
                        default:
                            throw new IllegalStateException("Invalid Context child element: " +  qName);
                    }
                    break;
                case "input/list":
                    if (!(objectTracking.getLast() instanceof ArchetypeDescriptor.InputList)) {
                        throw new IllegalStateException("Invalid object stack element for List");
                    }
                    ArchetypeDescriptor.InputList tempList = (ArchetypeDescriptor.InputList) objectTracking.getLast();
                    switch (qName) {
                        case "context":
                            tempList.contexts().add(new ArchetypeDescriptor.ContextBlock());
                            objectTracking.add(tempList.contexts().getLast());
                            stack.push("context");
                            break;
                        case "exec":
                            tempList.execs().add(new ArchetypeDescriptor.Exec(attributes.get("url"), attributes.get("src")));
                            objectTracking.add(tempList.execs().getLast());
                            stack.push("exec");
                            break;
                        case "source":
                            tempList.sources().add(new ArchetypeDescriptor.Source(attributes.get("url"), attributes.get("src")));
                            objectTracking.add(tempList.sources().getLast());
                            stack.push("source");
                            break;
                        case "input":
                            tempList.inputBlocks().add(new ArchetypeDescriptor.InputBlock());
                            objectTracking.add(tempList.inputBlocks().getLast());
                            stack.push("input");
                            break;
                        case "step":
                            tempList.steps().add(new ArchetypeDescriptor.Step(attributes.get("label"), attributes.get("if")));
                            currentStep = tempList.steps().getLast();
                            objectTracking.add(tempList.steps().getLast());
                            stack.push("step");
                            break;
                        case "output":
                            tempList.output(new ArchetypeDescriptor.Output(attributes.get("if")));
                            currentOutput = tempList.output();
                            objectTracking.add(tempList.output());
                            stack.push("output");
                            break;
                        case "help":
                            objectTracking.add(objectTracking.getLast());
                            stack.push("input/list/help");
                            break;
                        case "option":
                            tempList.options().add(new ArchetypeDescriptor.InputOption(attributes.get("label"), attributes.get("value")));
                            currentOption = tempList.options().getLast();
                            objectTracking.add(tempList.options().getLast());
                            stack.push("option");
                            break;
                        default:
                            throw new IllegalStateException("Invalid Context child element: " +  qName);
                    }
                    break;
                case "option":
                    switch (qName) {
                        case "context":
                            currentOption.contexts().add(new ArchetypeDescriptor.ContextBlock());
                            objectTracking.add(currentOption.contexts().getLast());
                            stack.push("context");
                            break;
                        case "exec":
                            currentOption.execs().add(new ArchetypeDescriptor.Exec(attributes.get("url"), attributes.get("src")));
                            objectTracking.add(currentOption.execs().getLast());
                            stack.push("exec");
                            break;
                        case "source":
                            currentOption.sources().add(new ArchetypeDescriptor.Source(attributes.get("url"), attributes.get("src")));
                            objectTracking.add(currentOption.sources().getLast());
                            stack.push("source");
                            break;
                        case "input":
                            currentOption.inputBlocks().add(new ArchetypeDescriptor.InputBlock());
                            objectTracking.add(currentOption.inputBlocks().getLast());
                            stack.push("input");
                            break;
                        case "step":
                            currentOption.steps().add(new ArchetypeDescriptor.Step(attributes.get("label"), attributes.get("if")));
                            currentStep = currentOption.steps().getLast();
                            objectTracking.add(currentOption.steps().getLast());
                            stack.push("step");
                            break;
                        case "output":
                            currentOption.output(new ArchetypeDescriptor.Output(attributes.get("if")));
                            currentOutput = currentOption.output();
                            objectTracking.add(currentOption.output());
                            stack.push("output");
                            break;
                        case "help":
                            objectTracking.add(objectTracking.getLast());
                            stack.push("input/option/help");
                            break;
                        default:
                            throw new IllegalStateException("Invalid option child element: " +  qName);
                    }
                    break;
                case "output":
                    switch (qName) {
                        case "transformation":
                            currentOutput.transformations().add(new ArchetypeDescriptor.Transformation(attributes.get("id")));
                            objectTracking.add(currentOutput.transformations().getLast());
                            stack.push("output/transformation");
                            break;
                        case "file":
                            currentOutput.fileList().add(new ArchetypeDescriptor.FileSet(
                                    readRequiredAttribute("source", qName, attributes),
                                    readRequiredAttribute("target", qName, attributes),
                                    attributes.get("if")));
                            objectTracking.add(currentOutput.fileList().getLast());
                            stack.push("output/file");
                            break;
                        case "files":
                            currentOutput.filesList().add(new ArchetypeDescriptor.FileSets(
                                    attributes.get("transformations"),
                                    attributes.get("if")));
                            objectTracking.add(currentOutput.filesList().getLast());
                            stack.push("output/files");
                            break;
                        case "template":
                            currentOutput.template().add(new ArchetypeDescriptor.Template(
                                    attributes.get("engine"),
                                    attributes.get("source"),
                                    attributes.get("target"),
                                    attributes.get("if")
                            ));
                            objectTracking.add(currentOutput.template().getLast());
                            stack.push("output/template");
                            break;
                        case "templates":
                            currentOutput.templates().add(new ArchetypeDescriptor.Templates(
                                    attributes.get("engine"),
                                    attributes.get("transformations"),
                                    attributes.get("if")
                            ));
                            objectTracking.add(currentOutput.templates().getLast());
                            stack.push("output/templates");
                            break;
                        case "model":
                            currentOutput.model(new ArchetypeDescriptor.Model(attributes.get("if")));
                            objectTracking.add(currentOutput.model());
                            stack.push("model");
                            break;
                        default:
                            throw new IllegalStateException("Invalid element: " + qName + " with parent: " + parent);
                    }
                    break;
                case "output/files":
                    validateChilds(qName, parent, "directory", "excludes", "includes");
                    objectTracking.add(currentOutput.filesList().getLast());
                    stack.push("output/files/" + qName);
                    break;
                case "output/files/includes":
                    validateChild("include", parent, qName);
                    objectTracking.add("output/files/includes/" + qName);
                    stack.push("output/files/includes/" + qName);
                    break;
                case "output/files/excludes":
                    validateChild("exclude", parent, qName);
                    objectTracking.add("output/files/excludes/" + qName);
                    stack.push("output/files/excludes/" + qName);
                    break;
                case "output/transformation":
                    validateChild("replace", parent, qName);
                    currentOutput.transformations().getLast().replacements().add(new ArchetypeDescriptor.Replacement(
                            readRequiredAttribute("regex", qName, attributes),
                            readRequiredAttribute("replacement", qName, attributes)));
                    objectTracking.add(currentOutput.transformations().getLast().replacements().getLast());
                    stack.push("output/transformation/replace");
                    break;
                case "output/template":
                    validateChild("model", parent, qName);
                    currentOutput.template().getLast().model(new ArchetypeDescriptor.Model(attributes.get("if")));
                    objectTracking.add(currentOutput.template().getLast().model());
                    stack.push("model");
                    break;
                case "output/templates":
                    switch (qName) {
                        case "model":
                            currentOutput.templates().getLast().model(new ArchetypeDescriptor.Model(attributes.get("if")));
                            objectTracking.add(currentOutput.templates().getLast().model());
                            stack.push("model");
                            break;
                        case "directory":
                        case "includes":
                        case "excludes":
                            objectTracking.add(currentOutput.templates().getLast());
                            stack.push("output/templates/" + qName);
                            break;
                        default:
                            throw new IllegalStateException("Invalid element: " + qName + "with parent: " + parent);
                    }
                    break;
                case "output/templates/includes":
                    validateChild("include", parent, qName);
                    objectTracking.add(parent + "/" + qName);
                    stack.push(parent + "/" + qName);
                    break;
                case "output/templates/excludes":
                    validateChild("exclude", parent, qName);
                    objectTracking.add(parent + "/" + qName);
                    stack.push(parent + "/" + qName);
                    break;
                case "model":
                    if (!(objectTracking.getLast() instanceof ArchetypeDescriptor.Model)) {
                        throw new IllegalStateException("Invalid object stack element for Model");
                    }
                    ArchetypeDescriptor.Model model = (ArchetypeDescriptor.Model) objectTracking.getLast();
                    switch (qName) {
                        case "value":
                            addModelKeyValue(model, parent, attributes);
                            objectTracking.add(model.keyedValues().getLast());
                            stack.push(qName);
                            break;
                        case "list":
                            model.keyedLists().add(new ArchetypeDescriptor.ModelKeyedList(
                                    readRequiredAttribute("key", qName, attributes),
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(model.keyedLists().getLast());
                            stack.push("model/" + qName);
                            break;
                        case "map":
                            model.keyedMaps().add(new ArchetypeDescriptor.ModelKeyedMap(
                                    readRequiredAttribute("key", qName, attributes),
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(model.keyedMaps().getLast());
                            stack.push("model/" + qName);
                            break;
                        default:
                            throw new IllegalStateException("Invalid element: " + qName + "with parent: " + parent);
                    }
                    break;
                case "model/list":
                    if (!(objectTracking.getLast() instanceof ArchetypeDescriptor.ModelKeyedList)) {
                        throw new ClassCastException("wrong object in stack");
                    }
                    ArchetypeDescriptor.ModelKeyedList keyListML = (ArchetypeDescriptor.ModelKeyedList) objectTracking.getLast();
                    switch (qName) {
                        case "value":
                            keyListML.values().add(new ArchetypeDescriptor.ModelValue(
                                    attributes.get("url"),
                                    attributes.get("file"),
                                    attributes.get("template"),
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(keyListML.values().getLast());
                            stack.push(qName);
                            break;
                        case "list":
                            keyListML.lists().add(new ArchetypeDescriptor.ModelList(
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(keyListML.lists().getLast());
                            stack.push("model/list/" + qName);
                            break;
                        case "map":
                            keyListML.maps().add(new ArchetypeDescriptor.ModelMap(
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(keyListML.maps().getLast());
                            stack.push("model/list/" + qName);
                            break;
                        default:
                            throw new IllegalStateException("Invalid element: " + qName + "with parent: " + parent);
                    }
                    break;
                case "model/list/list":
                    if (!(objectTracking.getLast() instanceof ArchetypeDescriptor.ModelList)) {
                        throw new ClassCastException("wrong object in stack");
                    }
                    ArchetypeDescriptor.ModelList listLL = (ArchetypeDescriptor.ModelList) objectTracking.getLast();
                    switch (qName) {
                        case "value":
                            listLL.values().add(new ArchetypeDescriptor.ModelValue(
                                    attributes.get("url"),
                                    attributes.get("file"),
                                    attributes.get("template"),
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(listLL.values().getLast());
                            stack.push(qName);
                            break;
                        case "list":
                            listLL.lists().add(new ArchetypeDescriptor.ModelList(
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(listLL.lists().getLast());
                            stack.push("model/list/list");
                            break;
                        case "map":
                            listLL.maps().add(new ArchetypeDescriptor.ModelMap(
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(listLL.maps().getLast());
                            stack.push("model/list/map");
                            break;
                        default:
                            throw new IllegalStateException("Invalid element: " + qName + "with parent: " + parent);
                    }
                    break;
                case "model/list/map":
                    if (!(objectTracking.getLast() instanceof ArchetypeDescriptor.ModelMap)) {
                        throw new ClassCastException("wrong object in stack");
                    }
                    ArchetypeDescriptor.ModelMap mapLM = (ArchetypeDescriptor.ModelMap) objectTracking.getLast();
                    switch (qName) {
                        case "value":
                            mapLM.keyValues().add(new ArchetypeDescriptor.ModelKeyedValue(
                                    attributes.get("key"),
                                    attributes.get("url"),
                                    attributes.get("file"),
                                    attributes.get("template"),
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(mapLM.keyValues().getLast());
                            stack.push(qName);
                            break;
                        case "list":
                            mapLM.keyLists().add(new ArchetypeDescriptor.ModelKeyedList(
                                    attributes.get("key"),
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(mapLM.keyLists().getLast());
                            stack.push("model/map/list");
                            break;
                        case "map":
                            mapLM.keyMaps().add(new ArchetypeDescriptor.ModelKeyedMap(
                                    attributes.get("key"),
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(mapLM.keyMaps().getLast());
                            stack.push("model/map");
                            break;
                        default:
                            throw new IllegalStateException("Invalid element: " + qName + "with parent: " + parent);
                    }
                    break;
                case "model/map/list":
                    if (!(objectTracking.getLast() instanceof ArchetypeDescriptor.ModelKeyedList)) {
                        throw new ClassCastException("Wrong object in the stack");
                    }
                    ArchetypeDescriptor.ModelKeyedList keyListMML = (ArchetypeDescriptor.ModelKeyedList) objectTracking.getLast();
                    switch (qName) {
                        case "value":
                            keyListMML.values().add(new ArchetypeDescriptor.ModelValue(
                                    attributes.get("url"),
                                    attributes.get("file"),
                                    attributes.get("template"),
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if"))
                            );
                            objectTracking.add(keyListMML.values().getLast());
                            stack.push(qName);
                            break;
                        case "list":
                            keyListMML.lists().add(new ArchetypeDescriptor.ModelList(
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(keyListMML.lists().getLast());
                            stack.push("model/list/" + qName);
                            break;
                        case "map":
                            keyListMML.maps().add(new ArchetypeDescriptor.ModelMap(
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(keyListMML.maps().getLast());
                            stack.push("model/list/" + qName);
                            break;
                        default:
                            throw new IllegalStateException("Invalid element: " + qName + " with parent: " + parent);
                    }
                    break;
                case "model/map":
                    if (!(objectTracking.getLast() instanceof ArchetypeDescriptor.ModelKeyedMap)) {
                        throw new ClassCastException("Wrong object in the stack");
                    }
                    ArchetypeDescriptor.ModelKeyedMap map = (ArchetypeDescriptor.ModelKeyedMap) objectTracking.getLast();
                    switch (qName) {
                        case "value":
                            map.keyValues().add(new ArchetypeDescriptor.ModelKeyedValue(
                                    attributes.get("key"),
                                    attributes.get("url"),
                                    attributes.get("file"),
                                    attributes.get("template"),
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            stack.push(qName);
                            objectTracking.add(map.keyValues().getLast());
                            break;
                        case "list":
                            map.keyLists().add(new ArchetypeDescriptor.ModelKeyedList(
                                    attributes.get("key"),
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(map.keyLists().getLast());
                            stack.push("model/map/" + qName);
                            break;
                        case "map":
                            map.keyMaps().add(new ArchetypeDescriptor.ModelKeyedMap(
                                    attributes.get("key"),
                                    parseOrder(attributes.get("order")),
                                    attributes.get("if")
                            ));
                            objectTracking.add(map.keyMaps().getLast());
                            stack.push("model/" + qName);
                            break;
                        default:
                            throw new IllegalStateException("Invalid element: " + qName + " with parent: " + parent);
                    }
                    break;
                default:
                    throw new IllegalStateException("Invalid element: " + qName + " with parent: " + parent);
            }
        }
    }

    @Override
    public void endElement(String name) {
        if (name.equals("output") && topLevelOutput) {
            output = currentOutput;
            topLevelOutput = false;
        }
        objectTracking.pollLast();
        stack.pop();
    }

    @Override
    public void elementText(String value) {
        if (stack.isEmpty()) {
            return;
        }
        switch (stack.peek()) {
            case "help":
                help = value;
                break;
            case "context/list/value":
                ArchetypeDescriptor.Context node = context.getLast().nodes().getLast();
                if (!(node instanceof ArchetypeDescriptor.ContextList)) {
                    throw new IllegalStateException("Unable to add 'value' to context node");
                }
                ((ArchetypeDescriptor.ContextList) node).values().add(value);
                break;
            case "context/enum/value":
                node = context.getLast().nodes().getLast();
                if (!(node instanceof ArchetypeDescriptor.ContextEnum)) {
                    throw new IllegalStateException("Unable to add 'value' to context node");
                }
                ((ArchetypeDescriptor.ContextEnum) node).values().add(value);
                break;
            case "context/boolean":
                node = context.getLast().nodes().getLast();
                if (!(node instanceof ArchetypeDescriptor.ContextBoolean)) {
                    throw new IllegalStateException("Unable to add 'value' to context node");
                }
                ((ArchetypeDescriptor.ContextBoolean) node).bool(parseBoolean(value));
                break;
            case "context/text":
                node = context.getLast().nodes().getLast();
                if (!(node instanceof ArchetypeDescriptor.ContextText)) {
                    throw new IllegalStateException("Unable to add 'value' to context node");
                }
                ((ArchetypeDescriptor.ContextText) node).text(value);
                break;
            case "step/help":
                currentStep.help(value);
                break;
            case "input/boolean/help":
                if (!(objectTracking.getLast() instanceof ArchetypeDescriptor.InputBoolean)) {
                    throw new IllegalStateException("Unable to add 'value' to input boolean node");
                }
                ((ArchetypeDescriptor.InputBoolean) objectTracking.getLast()).help(value);
                break;
            case "input/enum/help":
                if (!(objectTracking.getLast() instanceof ArchetypeDescriptor.InputEnum)) {
                    throw new IllegalStateException("Unable to add 'value' to input enum node");
                }
                ((ArchetypeDescriptor.InputEnum) objectTracking.getLast()).help(value);
                break;
            case "input/list/help":
                if (!(objectTracking.getLast() instanceof ArchetypeDescriptor.InputList)) {
                    throw new IllegalStateException("Unable to add 'value' to input list node");
                }
                ((ArchetypeDescriptor.InputList) objectTracking.getLast()).help(value);
                break;
            case "output/files/includes/include":
                currentOutput.filesList().getLast().includes().add(value);
                break;
            case "output/files/excludes/exclude":
                currentOutput.filesList().getLast().excludes().add(value);
                break;
            case "output/files/directory":
                currentOutput.filesList().getLast().directory(value);
                break;
            case "output/templates/directory":
                currentOutput.templates().getLast().directory(value);
                break;
            case "output/templates/includes/include":
                currentOutput.templates().getLast().includes().add(value);
                break;
            case "output/templates/excludes/exclude":
                currentOutput.templates().getLast().excludes().add(value);
                break;
            case "value":
                if (!(objectTracking.getLast() instanceof ArchetypeDescriptor.ModelValue)) {
                    throw new ClassCastException("No value at top of stack");
                }
                ((ArchetypeDescriptor.ModelValue) objectTracking.getLast()).value(value);
                break;
            default:
        }
    }

    private void addModelKeyValue(ArchetypeDescriptor.Model currentModel, String parent, Map<String, String> attributes) {
        currentModel.keyedValues().add(new ArchetypeDescriptor.ModelKeyedValue(
                readRequiredAttribute("key", parent, attributes),
                attributes.get("url"),
                attributes.get("file"),
                attributes.get("template"),
                parseOrder(attributes.get("order")),
                attributes.get("if")));
    }

    private void addInputText(ArchetypeDescriptor.InputBlock input, Map<String, String> attributes) {
        input.inputs().add(new ArchetypeDescriptor.InputText(
                attributes.get("label"),
                attributes.get("name"),
                attributes.get("default"),
                attributes.get("prompt"),
                parseBoolean(attributes.get("optional") == null ? "false" : attributes.get("optional")),
                attributes.get("placeholder")
        ));
    }

    private void validateChilds(String child, String parent, String... validChilds) {
        if (!Arrays.stream(validChilds).collect(Collectors.toList()).contains(child)) {
            throw new IllegalStateException(String.format(
                    "Invalid child '%s' for '%s'", child, parent));
        }
    }

    private int parseOrder(String order) {
        return Integer.parseInt(order == null ? "100" : order);
    }

    private boolean parseBoolean(String value) {
        return Boolean.parseBoolean(value == null ? "true" : value);
    }

}
