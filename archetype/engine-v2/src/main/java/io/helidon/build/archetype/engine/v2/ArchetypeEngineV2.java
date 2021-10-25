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

package io.helidon.build.archetype.engine.v2;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.build.archetype.engine.v2.archive.Archetype;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodeFactory;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextBlockNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextTextNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputNode;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.interpreter.Flow;
import io.helidon.build.archetype.engine.v2.ast.UserInputNode;
import io.helidon.build.archetype.engine.v2.interpreter.Visitor;
import io.helidon.build.archetype.engine.v2.prompter.Prompt;
import io.helidon.build.archetype.engine.v2.prompter.PromptFactory;
import io.helidon.build.archetype.engine.v2.prompter.Prompter;

/**
 * Archetype engine (version 2).
 */
public class ArchetypeEngineV2 {

    private final Archetype archetype;
    private final String startPoint;
    private final Prompter prompter;
    private final Map<String, String> contextValues = new HashMap<>();
    private final List<Visitor<Node, Void>> visitors = new ArrayList<>();
    private boolean skipOptional;

    /**
     * Create a new archetype engine instance.
     *
     * @param archetype          archetype
     * @param startPoint         entry point in the archetype
     * @param prompter           prompter
     * @param params             external Flow Context Values
     * @param skipOptional       mark that indicates whether to skip optional input
     * @param visitors additional Visitor for the {@code Interpreter}
     */
    public ArchetypeEngineV2(Archetype archetype,
                             String startPoint,
                             Prompter prompter,
                             Map<String, String> params,
                             boolean skipOptional,
                             List<Visitor<Node, Void>> visitors) {
        this.archetype = archetype;
        this.startPoint = startPoint;
        this.prompter = prompter;
        if (params != null) {
            contextValues.putAll(params);
        }
        this.skipOptional = skipOptional;
        if (visitors != null) {
            this.visitors.addAll(visitors);
        }
    }

    /**
     * Run the archetype.
     *
     * @param outputDirectory output directory
     */
    public void generate(File outputDirectory) {
        Flow flow = Flow.builder()
                .archetype(archetype)
                .entrypoint(startPoint)
                .skipOptional(skipOptional)
                .additionalVisitor(visitors)
                .build();

        ContextBlockNode context = new ContextBlockNode();
        initContext(context, outputDirectory);
        flow.build(context);
        while (!flow.unresolvedInputs().isEmpty()) {
            UserInputNode userInputNode = flow.unresolvedInputs().get(0);
            DescriptorNodes.ContextNode<?> contextNode;
            if (contextValues.containsKey(userInputNode.path())) {
                contextNode = DescriptorNodeFactory.create(
                        (InputNode<?>) userInputNode.children().get(0),
                        userInputNode.path(),
                        contextValues.get(userInputNode.path()));
            } else {
                Prompt<?> prompt = PromptFactory.create(userInputNode, flow.canBeGenerated());
                contextNode = prompt.acceptAndConvert(prompter, userInputNode.path());
                flow.skipOptional(prompter.skipOptional());
            }
            ContextBlockNode contextAST = new ContextBlockNode();
            contextAST.children().add(contextNode);
            flow.build(contextAST);
        }

        flow.build(new ContextBlockNode());
        Flow.Result result = flow.result().orElseThrow(() -> {
            throw new RuntimeException("No results after the Flow instance finished its work. Project cannot be generated.");
        });

        OutputGenerator outputGenerator = new OutputGenerator(result);
        try {
            outputGenerator.generate(outputDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                result.archetype().close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void initContext(ContextBlockNode context, File outputDirectory) {
        ContextTextNode currentDateNode = new ContextTextNode("current.date");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy");
        ZonedDateTime now = ZonedDateTime.now();
        currentDateNode.value(dtf.format(now));
        context.children().add(currentDateNode);
        ContextTextNode currentDirNode = new ContextTextNode("project.directory");
        currentDirNode.value(outputDirectory.toString());
        context.children().add(currentDirNode);
    }
}
