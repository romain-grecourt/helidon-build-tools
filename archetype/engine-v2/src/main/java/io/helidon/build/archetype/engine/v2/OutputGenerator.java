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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.build.archetype.engine.v2.archive.Archetype;
import io.helidon.build.archetype.engine.v2.archive.ArchetypeException;
import io.helidon.build.archetype.engine.v2.archive.ZipArchetype;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextBooleanNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextTextNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelKeyedListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelKeyedMapNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelKeyedValueNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelMapNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelValueNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.OutputNode;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.descriptor.ModelList;
import io.helidon.build.archetype.engine.v2.descriptor.ModelMap;
import io.helidon.build.archetype.engine.v2.descriptor.Model;
import io.helidon.build.archetype.engine.v2.descriptor.ModelKeyedList;
import io.helidon.build.archetype.engine.v2.descriptor.ModelKeyedMap;
import io.helidon.build.archetype.engine.v2.descriptor.ModelKeyedValue;
import io.helidon.build.archetype.engine.v2.descriptor.Replacement;
import io.helidon.build.archetype.engine.v2.descriptor.ModelValue;
import io.helidon.build.archetype.engine.v2.interpreter.Flow;
import io.helidon.build.archetype.engine.v2.template.TemplateModel;

import static io.helidon.build.archetype.engine.v2.MustacheHandler.renderMustacheTemplate;
import static java.util.stream.Collectors.toList;

/**
 * Generate Output files from interpreter.
 */
public class OutputGenerator {

    private final TemplateModel model;
    private final Archetype archetype;
    private final Map<String, String> properties;
    private final List<OutputNode> nodes;
    private final List<DescriptorNodes.TransformationNode> transformations;
    private final List<DescriptorNodes.TemplateNode> template;
    private final List<DescriptorNodes.TemplatesNode> templates;
    private final List<DescriptorNodes.FileSetNode> file;
    private final List<DescriptorNodes.FileSetsNode> files;

    /**
     * OutputGenerator constructor.
     *
     * @param result Flow.Result from interpreter
     */
    OutputGenerator(Flow.Result result) {
        Objects.requireNonNull(result, "Flow result is null");

        this.nodes = getOutputNodes(result.outputs());
        this.model = createUniqueModel();
        this.archetype = result.archetype();
        this.properties = parseContextProperties(result.context());

        this.transformations = nodes.stream()
                                    .flatMap(output -> output.children().stream())
                                    .filter(o -> o instanceof DescriptorNodes.TransformationNode)
                                    .map(t -> (DescriptorNodes.TransformationNode) t)
                                    .collect(toList());

        this.template = nodes.stream()
                             .flatMap(output -> output.children().stream())
                             .filter(o -> o instanceof DescriptorNodes.TemplateNode)
                             .map(t -> (DescriptorNodes.TemplateNode) t)
                             .filter(t -> t.descriptor().engine().equals("mustache"))
                             .collect(toList());

        this.templates = nodes.stream()
                              .flatMap(output -> output.children().stream())
                              .filter(o -> o instanceof DescriptorNodes.TemplatesNode)
                              .map(t -> (DescriptorNodes.TemplatesNode) t)
                              .filter(t -> t.descriptor().engine().equals("mustache"))
                              .collect(toList());

        this.file = nodes.stream()
                         .flatMap(output -> output.children().stream())
                         .filter(o -> o instanceof DescriptorNodes.FileSetNode)
                         .map(t -> (DescriptorNodes.FileSetNode) t)
                         .collect(toList());

        this.files = nodes.stream()
                          .flatMap(output -> output.children().stream())
                          .filter(o -> o instanceof DescriptorNodes.FileSetsNode)
                          .map(t -> (DescriptorNodes.FileSetsNode) t)
                          .collect(toList());
    }

    private Map<String, String> parseContextProperties(Map<String, DescriptorNodes.ContextNode<?>> context) {
        if (context == null) {
            return new HashMap<>();
        }

        Map<String, String> resolved = new HashMap<>();
        for (Map.Entry<String, DescriptorNodes.ContextNode<?>> entry : context.entrySet()) {
            DescriptorNodes.ContextNode<?> node = entry.getValue();
            if (node instanceof ContextBooleanNode) {
                resolved.put(entry.getKey(), String.valueOf(((ContextBooleanNode) node).value()));
            }
            if (node instanceof ContextTextNode) {
                resolved.put(entry.getKey(), ((ContextTextNode) node).value());
            }
        }
        return resolved;
    }

    /**
     * Generate output files.
     *
     * @param outputDirectory Output directory where the files will be generated
     */
    public void generate(File outputDirectory) throws IOException {
        Objects.requireNonNull(outputDirectory, "output directory is null");

        for (DescriptorNodes.TemplateNode templateNode : template) {
            File outputFile = new File(outputDirectory, templateNode.descriptor().target());
            outputFile.getParentFile().mkdirs();
            try (InputStream inputStream = archetype.getInputStream(templateNode.descriptor().source())) {
                if (templateNode.descriptor().engine().equals("mustache")) {
                    renderMustacheTemplate(
                            inputStream,
                            templateNode.descriptor().source(),
                            new FileOutputStream(outputFile),
                            model);
                } else {
                    Files.copy(inputStream, outputFile.toPath());
                }
            }
        }

        for (DescriptorNodes.TemplatesNode templatesNode : templates) {
            Path rootDirectory = Path.of(templatesNode.location().currentDirectory())
                                     .resolve(templatesNode.directory());
            TemplateModel templatesModel = createTemplatesModel(templatesNode);

            for (String include : resolveIncludes(templatesNode)) {
                String outPath = transform(
                        targetPath(templatesNode.directory(), include),
                        templatesNode.descriptor().transformation());
                File outputFile = new File(outputDirectory, outPath);
                outputFile.getParentFile().mkdirs();
                try (InputStream inputStream = archetype.getInputStream(rootDirectory.resolve(include).toString())) {
                    renderMustacheTemplate(
                            inputStream,
                            outPath,
                            new FileOutputStream(outputFile),
                            templatesModel);
                }
            }
        }

        for (DescriptorNodes.FileSetNode fileSet : file) {
            File outputFile = new File(outputDirectory, fileSet.descriptor().target());
            outputFile.getParentFile().mkdirs();
            try (InputStream inputStream = archetype.getInputStream(fileSet.descriptor().source())) {
                Files.copy(inputStream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        for (DescriptorNodes.FileSetsNode fileSets : files) {
            Path rootDirectory = Path.of(fileSets.location().currentDirectory()).resolve(fileSets.directory());
            for (String include : resolveIncludes(fileSets)) {
                String outPath = processTransformation(
                        targetPath(fileSets.directory(), include),
                        fileSets.descriptor().transformations());
                File outputFile = new File(outputDirectory, outPath);
                outputFile.getParentFile().mkdirs();
                try (InputStream inputStream = archetype.getInputStream(rootDirectory.resolve(include).toString())) {
                    Files.copy(inputStream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private String targetPath(String directory, String filePath) {
        String resolved = directory.replaceFirst("files", "");
        return Path.of(resolved)
                   .resolve(filePath)
                   .toString();
    }

    private List<String> resolveIncludes(DescriptorNodes.TemplatesNode templatesAST) {
        return resolveIncludes(
                Path.of(templatesAST.location().currentDirectory()).resolve(templatesAST.directory()).toString(),
                templatesAST.descriptor().includes(),
                templatesAST.descriptor().excludes());
    }

    private List<String> resolveIncludes(DescriptorNodes.FileSetsNode filesAST) {
        return resolveIncludes(
                Path.of(filesAST.location().currentDirectory()).resolve(filesAST.directory()).toString(),
                filesAST.descriptor().includes(),
                filesAST.descriptor().excludes());
    }

    private List<String> resolveIncludes(String directory, List<String> includes, List<String> excludes) {
        List<String> excludesPath = getPathsFromDirectory(directory, excludes);
        List<String> includesPath = getPathsFromDirectory(directory, includes);
        return includesPath.stream()
                           .filter(s -> !excludesPath.contains(s))
                           .collect(toList());
    }

    private List<String> getPathsFromDirectory(String directory, List<String> paths) {
        List<String> resolved = new LinkedList<>();
        for (String path : paths) {
            if (path.contains("**/*")) {
                try {
                    String extension = path.substring(path.lastIndexOf("."));
                    resolved.addAll(archetype.getPaths().stream()
                                             .map(s -> getPath(directory, s))
                                             .filter(Objects::nonNull)
                                             .filter(s -> !Path.of(s).toUri().toString().contains("../"))
                                             .filter(s -> s.contains(extension))
                                             .collect(toList()));
                } catch (IndexOutOfBoundsException e) {
                    resolved.addAll(archetype.getPaths().stream()
                                             .map(s -> getPath(directory, s))
                                             .filter(Objects::nonNull)
                                             .filter(s -> !Path.of(s).toUri().toString().contains("../"))
                                             .collect(toList()));
                }
            } else {
                if (checkFullPath(path, directory)) {
                    resolved.add(path);
                }
            }
        }
        return resolved;
    }

    private boolean checkFullPath(String include, String directory) {
        if (archetype instanceof ZipArchetype) {
            include = Path.of("/" + directory).resolve(include).toString();
        }
        String path = getPath(directory, include);
        return path != null;
    }

    private String getPath(String directory, String file) {
        String path;
        try {
            if (archetype instanceof ZipArchetype) {
                directory = "/" + directory;
                path = file.startsWith(directory)
                        ? archetype.getPath(file).toString().substring(directory.length() + 1)
                        : null;
            } else {
                path = archetype.getPath(directory).relativize(Path.of(file)).toString();
            }
        } catch (ArchetypeException e) {
            return null;
        }
        return path;
    }

    private TemplateModel createTemplatesModel(DescriptorNodes.TemplatesNode templates) {
        TemplateModel templatesModel = new TemplateModel();
        Optional<ModelNode> modelAST = templates.children()
                                                .stream()
                                                .filter(o -> o instanceof ModelNode)
                                                .map(m -> (ModelNode) m)
                                                .findFirst();
        templatesModel.mergeModel(model.model());
        modelAST.ifPresent(ast -> templatesModel.mergeModel(convertASTModel(ast)));
        return templatesModel;
    }

    private String transform(String input, String transformation) {
        return transformation == null ? input
                : processTransformation(input, Arrays.asList(transformation.split(",")));
    }

    private String processTransformation(String output, List<String> applicable) {
        if (applicable.isEmpty()) {
            return output;
        }

        List<Replacement> replacements = transformations.stream()
                                                        .filter(t -> applicable.contains(t.descriptor().id()))
                                                        .flatMap((t) -> t.descriptor().replacements().stream())
                                                        .collect(toList());

        for (Replacement rep : replacements) {
            String replacement = evaluate(rep.replacement(), properties);
            output = output.replaceAll(rep.regex(), replacement);
        }
        return output;
    }

    /**
     * Resolve a property of the form <code>${prop}</code>.
     *
     * @param input      input to be resolved
     * @param properties properties values
     * @return resolved property
     */
    private String evaluate(String input, Map<String, String> properties) {
        int start = input.indexOf("${");
        int end = input.indexOf("}", start);
        int index = 0;
        String resolved = null;
        while (start >= 0 && end > 0) {
            if (resolved == null) {
                resolved = input.substring(index, start);
            } else {
                resolved += input.substring(index, start);
            }
            String propName = input.substring(start + 2, end);

            int matchStart = 0;
            do {
                matchStart = propName.indexOf("/", matchStart + 1);
            } while (matchStart > 0 && propName.charAt(matchStart - 1) == '\\');
            int matchEnd = matchStart;
            do {
                matchEnd = propName.indexOf("/", matchEnd + 1);
            } while (matchStart > 0 && propName.charAt(matchStart - 1) == '\\');

            String regexp = null;
            String replace = null;
            if (matchStart > 0 && matchEnd > matchStart) {
                regexp = propName.substring(matchStart + 1, matchEnd);
                replace = propName.substring(matchEnd + 1);
                propName = propName.substring(0, matchStart);
            }

            String propValue = properties.get(propName);
            if (propValue == null) {
                propValue = "";
            } else if (regexp != null && replace != null) {
                propValue = propValue.replaceAll(regexp, replace);
            }

            resolved += propValue;
            index = end + 1;
            start = input.indexOf("${", index);
            end = input.indexOf("}", index);
        }
        if (resolved != null) {
            return resolved + input.substring(index);
        }
        return input;
    }

    /**
     * Consume a list of output nodes from interpreter and create a unique template model.
     *
     * @return Unique template model
     */
    TemplateModel createUniqueModel() {
        Objects.requireNonNull(nodes, "outputNodes is null");

        TemplateModel templateModel = new TemplateModel();
        List<ModelNode> models = nodes.stream()
                                      .flatMap(output -> output.children().stream())
                                      .filter(o -> o instanceof ModelNode)
                                      .map(o -> (ModelNode) o)
                                      .collect(toList());

        for (ModelNode node : models) {
            templateModel.mergeModel(convertASTModel(node));
        }
        return templateModel;
    }

    private Model convertASTModel(ModelNode model) {
        Model modelDescriptor = new Model("true");
        convertKeyElements(modelDescriptor.keyedValues(),
                modelDescriptor.keyedLists(),
                modelDescriptor.keyedMaps(),
                model.children());
        return modelDescriptor;
    }

    private Collection<? extends ModelKeyedMap> convertASTKeyMaps(List<ModelKeyedMapNode> astMaps) {
        LinkedList<ModelKeyedMap> maps = new LinkedList<>();
        for (ModelKeyedMapNode map : astMaps) {
            ModelKeyedMap keyMap = new ModelKeyedMap(
                    map.descriptor().key(),
                    map.descriptor().order(),
                    "true");
            convertKeyElements(keyMap.keyValues(), keyMap.keyLists(), keyMap.keyMaps(), map.children());
            maps.add(keyMap);
        }
        return maps;
    }

    private Collection<? extends ModelKeyedList> convertASTKeyLists(List<ModelKeyedListNode> astLists) {
        LinkedList<ModelKeyedList> lists = new LinkedList<>();
        for (ModelKeyedListNode list : astLists) {
            ModelKeyedList keyList = new ModelKeyedList(
                    list.descriptor().key(),
                    list.descriptor().order(),
                    "true");
            convertElements(keyList.values(), keyList.lists(), keyList.maps(), list.children());
            lists.add(keyList);
        }
        return lists;
    }

    private Collection<? extends ModelKeyedValue> convertASTKeyValues(List<ModelKeyedValueNode> astValues) {
        LinkedList<ModelKeyedValue> values = new LinkedList<>();
        for (ModelKeyedValueNode value : astValues) {
            ModelKeyedValue keyValue = new ModelKeyedValue(
                    value.descriptor().key(),
                    value.descriptor().url(),
                    value.descriptor().file(),
                    value.descriptor().template(),
                    value.descriptor().order(),
                    "true");
            keyValue.value(value.value());
            values.add(keyValue);
        }
        return values;
    }

    private Collection<? extends ModelValue> convertASTValues(List<ModelValueNode<?>> astValues) {
        LinkedList<ModelValue> values = new LinkedList<>();
        for (ModelValueNode<?> value : astValues) {
            ModelValue valueType = new ModelValue(
                    value.descriptor().url(),
                    value.descriptor().file(),
                    value.descriptor().template(),
                    value.descriptor().order(),
                    "true");
            valueType.value(value.value());
            values.add(valueType);
        }
        return values;
    }

    private Collection<? extends ModelList> convertASTLists(List<ModelListNode<?>> astList) {
        LinkedList<ModelList> lists = new LinkedList<>();
        for (ModelListNode<?> list : astList) {
            ModelList listType = new ModelList(list.descriptor().order(), "true");
            convertElements(listType.values(), listType.lists(), listType.maps(), list.children());
            lists.add(listType);
        }
        return lists;
    }

    private Collection<? extends ModelMap> convertASTMaps(List<ModelMapNode<?>> mapNode) {
        LinkedList<ModelMap> maps = new LinkedList<>();
        for (ModelMapNode<?> map : mapNode) {
            ModelMap modelMap = new ModelMap(map.descriptor().order(), "true");
            convertKeyElements(modelMap.keyValues(), modelMap.keyLists(), modelMap.keyMaps(), map.children());
            maps.add(modelMap);
        }
        return maps;
    }

    private void convertKeyElements(LinkedList<ModelKeyedValue> modelKeyValues,
                                    LinkedList<ModelKeyedList> modelKeyLists,
                                    LinkedList<ModelKeyedMap> modelKeyMaps,
                                    List<Node> children) {

        modelKeyValues.addAll(convertASTKeyValues(children.stream()
                                                          .filter(v -> v instanceof ModelKeyedValueNode)
                                                          .map(v -> (ModelKeyedValueNode) v)
                                                          .collect(toList())));

        modelKeyLists.addAll(convertASTKeyLists(children.stream()
                                                        .filter(v -> v instanceof ModelKeyedListNode)
                                                        .map(v -> (ModelKeyedListNode) v)
                                                        .collect(toList())));

        modelKeyMaps.addAll(convertASTKeyMaps(children.stream()
                                                      .filter(v -> v instanceof ModelKeyedMapNode)
                                                      .map(v -> (ModelKeyedMapNode) v)
                                                      .collect(toList())));
    }

    private void convertElements(LinkedList<ModelValue> values,
                                 LinkedList<ModelList> lists,
                                 LinkedList<ModelMap> maps,
                                 List<Node> children) {

        values.addAll(convertASTValues(children.stream()
                                               .filter(v -> v instanceof ModelValueNode)
                                               .map(v -> (ModelValueNode<?>) v)
                                               .collect(toList())));

        lists.addAll(convertASTLists(children.stream()
                                             .filter(v -> v instanceof ModelListNode)
                                             .map(v -> (ModelListNode<?>) v)
                                             .collect(toList())));

        maps.addAll(convertASTMaps(children.stream()
                                           .filter(v -> v instanceof ModelMapNode)
                                           .map(v -> (ModelMapNode<?>) v)
                                           .collect(toList())));
    }

    private List<OutputNode> getOutputNodes(List<Node> nodes) {
        return nodes.stream()
                    .filter(o -> o instanceof OutputNode)
                    .map(o -> (OutputNode) o)
                    .collect(toList());
    }

}
