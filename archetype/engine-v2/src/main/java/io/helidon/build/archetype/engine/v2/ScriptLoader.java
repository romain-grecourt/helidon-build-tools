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

import io.helidon.build.archetype.engine.v2.ast.ASTNode;
import io.helidon.build.archetype.engine.v2.ast.BooleanInput;
import io.helidon.build.archetype.engine.v2.ast.BooleanInputValue;
import io.helidon.build.archetype.engine.v2.ast.EnumInput;
import io.helidon.build.archetype.engine.v2.ast.EnumInputValue;
import io.helidon.build.archetype.engine.v2.ast.File;
import io.helidon.build.archetype.engine.v2.ast.Files;
import io.helidon.build.archetype.engine.v2.ast.AbstractFiles;
import io.helidon.build.archetype.engine.v2.ast.Help;
import io.helidon.build.archetype.engine.v2.ast.IfStatement;
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.InputValue;
import io.helidon.build.archetype.engine.v2.ast.InputValues;
import io.helidon.build.archetype.engine.v2.ast.Inputs;
import io.helidon.build.archetype.engine.v2.ast.Invocation;
import io.helidon.build.archetype.engine.v2.ast.ListInput;
import io.helidon.build.archetype.engine.v2.ast.ListInputValue;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.ModelListValue;
import io.helidon.build.archetype.engine.v2.ast.ModelMapValue;
import io.helidon.build.archetype.engine.v2.ast.ModelStringValue;
import io.helidon.build.archetype.engine.v2.ast.ModelValue;
import io.helidon.build.archetype.engine.v2.ast.Option;
import io.helidon.build.archetype.engine.v2.ast.Output;
import io.helidon.build.archetype.engine.v2.ast.Replacement;
import io.helidon.build.archetype.engine.v2.ast.Script;
import io.helidon.build.archetype.engine.v2.ast.Statement;
import io.helidon.build.archetype.engine.v2.ast.BlockStatement;
import io.helidon.build.archetype.engine.v2.ast.Step;
import io.helidon.build.archetype.engine.v2.ast.Template;
import io.helidon.build.archetype.engine.v2.ast.Templates;
import io.helidon.build.archetype.engine.v2.ast.TextInput;
import io.helidon.build.archetype.engine.v2.ast.TextInputValue;
import io.helidon.build.archetype.engine.v2.ast.Transformation;
import io.helidon.build.common.xml.SimpleXMLParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * Script loader.
 */
public class ScriptLoader {

    private static final WeakHashMap<FileSystem, Map<Path, WeakReference<Script>>> CACHE = new WeakHashMap<>();
    private static final Path DEFAULT_PATH = Path.of("script.xml");

    /**
     * Get or load the script at the given path.
     *
     * @param path path
     * @return script
     */
    public static Script load(Path path) {
        //noinspection ConstantConditions
        return CACHE.computeIfAbsent(path.getFileSystem(), fs -> new HashMap<>())
                    .compute(path, (p, r) -> r != null && r.get() == null ? new WeakReference<>(load0(p)) : r)
                    .get();
    }

    static Script load0(Path path) {
        try {
            return load0(java.nio.file.Files.newInputStream(path), path);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static Script load0(InputStream is) {
        return load0(is, DEFAULT_PATH);
    }

    private static Script load0(InputStream is, Path path) {
        try {
            return new ReaderImpl().read(is, path);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static final class ReaderImpl implements SimpleXMLParser.Reader {

        private volatile Path path;
        private volatile SimpleXMLParser parser;
        private volatile LinkedList<Item> stack;
        private volatile Script.Builder scriptBuilder;
        private volatile Item parent;

        private ReaderImpl() {
        }

        Script read(InputStream is, Path path) throws IOException {
            this.path = Objects.requireNonNull(path, "path is null");
            this.stack = new LinkedList<>();
            this.parser = SimpleXMLParser.create(is, this);
            parser.parse();
            return scriptBuilder.build();
        }

        private final class Item {

            final String id;
            final ASTNode.Builder<?, ?> builder;

            Item(String id, ASTNode.Builder<?, ?> builder) {
                this.id = id;
                if (parent != null) {
                    this.builder = builder == null ? parent.builder : builder;
                } else {
                    this.builder = Objects.requireNonNull(builder, "builder is null");
                }
            }
        }

        @Override
        public void startElement(String qName, Map<String, String> attrs) {
            parent = stack.peek();
            if (parent == null) {
                if (!"archetype-script".equals(qName)) {
                    throw new IllegalStateException("Invalid root element '" + qName + "'");
                }
                scriptBuilder = Script.builder()
                                      .path(path)
                                      .position(parser.lineNumber(), parser.charNumber());
                stack.push(new Item(qName, scriptBuilder));
            } else if (!startElement0(qName, attrs)) {
                throw new IllegalStateException(String.format(
                        "Invalid element: %s, line=%s, char=%s",
                        qName, parser.lineNumber(), parser.charNumber()));
            }
        }

        @Override
        public void endElement(String name) {
            stack.pop();
        }

        @Override
        public void elementText(String value) {
            parent = stack.peek();
            if (parent == null) {
                return;
            }
            switch (parent.id) {
                case "help":
                    ((Help.Builder) parent.builder).help(value);
                    break;
                case "context/list/value":
                    ((ListInputValue.Builder) parent.builder).value0(value);
                    break;
                case "context/enum/value":
                    ((EnumInputValue.Builder) parent.builder).value(value);
                    break;
                case "context/boolean":
                    ((BooleanInputValue.Builder) parent.builder).value(parseBoolean(value));
                    break;
                case "context/text":
                    ((TextInputValue.Builder) parent.builder).value(value);
                    break;
                case "output/files/includes/include":
                case "output/templates/includes/include":
                    ((AbstractFiles.Builder<?, ?>) parent.builder).include(value);
                    break;
                case "output/files/excludes/exclude":
                case "output/templates/excludes/exclude":
                    ((AbstractFiles.Builder<?, ?>) parent.builder).exclude(value);
                    break;
                case "output/files/directory":
                case "output/templates/directory":
                    ((AbstractFiles.Builder<?, ?>) parent.builder).directory(value);
                    break;
                case "output/model/value":
                    ((ModelStringValue.Builder) parent.builder).value(value);
                    break;
                default:
            }
        }

        private boolean startElement0(String qName, Map<String, String> attrs) {
            switch (parent.id) {
                case "archetype-script":
                case "step":
                case "option":
                case "input/boolean":
                case "input/text":
                    return codeBlock(qName, attrs);
                case "context":
                    return inputValue(qName, attrs);
                case "input":
                    switch (qName) {
                        case "text":
                        case "boolean":
                        case "enum":
                        case "list":
                            return input(qName, attrs);
                        default:
                            return false;
                    }
                case "input/list":
                case "input/enum":
                    switch (qName) {
                        case "option":
                            return statement(qName,
                                    Option.builder()
                                          .label(attrs.get("label"))
                                          .value(attrs.get("value")));
                        case "help":
                            return statement(qName, Help.builder());
                        default:
                            return false;
                    }
                case "output":
                    return output(qName, attrs);
                case "output/transformation":
                    if (qName.equals("replace")) {
                        return statement(qName,
                                Replacement.builder()
                                           .regex(readRequiredAttribute("regex", qName, attrs))
                                           .replacement(readRequiredAttribute("replacement", qName, attrs)));
                    }
                    return false;
                case "output/model":
                case "output/model/list":
                case "output/model/map":
                    return model(qName, attrs);
                case "output/template":
                    if (qName.equals("model")) {
                        return conditional("output/" + qName, attrs, Model.builder());
                    }
                    return false;
                case "output/templates":
                case "output/files":
                    return files(qName, attrs);
                case "output/templates/includes":
                case "output/templates/excludes":
                case "output/files/includes":
                case "output/files/excludes":
                case "context/list":
                case "context/enum":
                    return wrapper(qName);
                case "exec":
                case "source":
                    // do nothing for attributes-only elements
                    return true;
                default:
                    return false;
            }
        }

        private boolean wrapper(String qName) {
            stack.push(new Item(parent.id + "/" + qName, null));
            return true;
        }

        private boolean invocation(String qName, Map<String, String> attrs) {
            Invocation.Builder builder = Invocation.builder().src(attrs.get("src"));
            switch (qName) {
                case "exec":
                    builder.kind(Invocation.Kind.EXEC);
                    break;
                case "source":
                    builder.kind(Invocation.Kind.SOURCE);
                    break;
                default:
                    return false;
            }
            statement(qName, builder);
            return true;
        }

        private boolean files(String qName, Map<String, String> attrs) {
            switch (qName) {
                case "model":
                    if (parent.id.equals("output/templates")) {
                        return conditional("output/" + qName, attrs, Model.builder());
                    }
                    return false;
                case "directory":
                case "includes":
                case "excludes":
                    return wrapper(qName);
                default:
                    return false;
            }
        }

        private boolean codeBlock(String qName, Map<String, String> attrs) {
            switch (qName) {
                case "context":
                    return statement(qName, InputValues.builder());
                case "exec":
                case "source":
                    return invocation(qName, attrs);
                case "input":
                    return statement(qName, Inputs.builder());
                case "step":
                    if (!parent.id.equals("step")) {
                        return conditional("step", attrs,
                                Step.builder()
                                    .label(attrs.get("label")));
                    }
                    return false;
                case "output":
                    return conditional(qName, attrs, Output.builder());
                case "help":
                    switch (parent.id) {
                        case "step":
                        case "option":
                        case "input/boolean":
                        case "input/text":
                            return statement(qName, Help.builder());
                        default:
                            return false;
                    }
                default:
                    return false;
            }
        }

        private boolean input(String qName, Map<String, String> attrs) {
            Input.Builder<?, ?, ?> builder;
            switch (qName) {
                case "boolean":
                    builder = BooleanInput.builder()
                                          .defaultValue(parseBoolean(attrs.get("default")));
                    break;
                case "list":
                    builder = ListInput.builder()
                                       .defaultValue(parseList(attrs.get("default")));
                    break;
                case "enum":
                    builder = EnumInput.builder()
                                       .defaultValue(attrs.get("default"));
                    break;
                case "text":
                    builder = TextInput.builder()
                                       .defaultValue(attrs.get("default"));
                    break;
                default:
                    return false;
            }
            builder.name(attrs.get("name"))
                   .label(attrs.get("label"))
                   .prompt(attrs.get("prompt"))
                   .position(parser.lineNumber(), parser.charNumber());
            return statement("input/" + qName, builder);
        }

        private boolean inputValue(String qName, Map<String, String> attrs) {
            InputValue.Builder<?, ?, ?> builder;
            switch (qName) {
                case "boolean":
                    builder = BooleanInputValue.builder();
                    break;
                case "list":
                    builder = ListInputValue.builder();
                    break;
                case "enum":
                    builder = EnumInputValue.builder();
                    break;
                case "text":
                    builder = TextInputValue.builder();
                    break;
                default:
                    return false;
            }
            builder.path(attrs.get("path"));
            return statement("context/" + qName, builder);
        }

        private boolean conditional(String qName, Map<String, String> attrs, Statement.Builder<?, ?> then) {
            Statement.Builder<?, ?> builder;
            String ifExpr = attrs.get("if");
            then.position(parser.lineNumber(), parser.charNumber());
            if (ifExpr != null) {
                builder = IfStatement.builder().expression(ifExpr).thenStmt(then);
            } else {
                builder = then;
            }
            return statement(qName, builder);
        }

        private boolean statement(String id, Statement.Builder<? extends Statement, ?> builder) {
            builder.position(parser.lineNumber(), parser.charNumber());
            ASTNode.Builder<?, ?> parentBuilder;
            if (parent.builder instanceof IfStatement.Builder) {
                parentBuilder = ((IfStatement.Builder) parent.builder).thenStmt();
            } else {
                parentBuilder = parent.builder;
            }
            ((BlockStatement.Builder<?, ?>) parentBuilder).statement(builder);
            stack.push(new Item(id, builder));
            return true;
        }

        private boolean model(String qName, Map<String, String> attrs) {
            ModelValue.Builder<?, ?> builder;
            switch (qName) {
                case "value":
                    // TODO file, template, url attrs
                    builder = ModelStringValue.builder();
                    break;
                case "list":
                    if ("output/model".equals(parent.id)
                            || "output/model/list".equals(parent.id)
                            || "output/model/map".equals(parent.id)) {
                        builder = ModelListValue.builder();
                        break;
                    }
                case "map":
                    if ("output/model".equals(parent.id)
                            || "output/model/list".equals(parent.id)) {
                        builder = ModelMapValue.builder();
                        break;
                    }
                default:
                    return false;
            }
            builder.key(attrs.get("key"))
                   .order(parseInt(attrs.get("order"), 100));
            return conditional("output/model/" + qName, attrs, builder);
        }

        private boolean output(String qName, Map<String, String> attrs) {
            Statement.Builder<?, ?> builder;
            switch (qName) {
                case "transformation":
                    builder = Transformation.builder().id(attrs.get("id"));
                    break;
                case "file":
                    builder = File.builder()
                                  .source(readRequiredAttribute("source", qName, attrs))
                                  .target(readRequiredAttribute("target", qName, attrs));
                    break;
                case "template":
                    builder = Template.builder()
                                      .source(readRequiredAttribute("source", qName, attrs))
                                      .target(readRequiredAttribute("target", qName, attrs))
                                      .engine(readRequiredAttribute("engine", qName, attrs));
                    break;
                case "files":
                    builder = Files.builder().transformation(attrs.get("transformations"));
                    break;
                case "templates":
                    builder = Templates.builder()
                                       .transformation(attrs.get("transformations"))
                                       .engine(attrs.get("engine"));
                    break;
                case "model":
                    builder = Model.builder();
                    break;
                default:
                    return false;
            }
            return conditional("output/" + qName, attrs, builder);
        }

        @SuppressWarnings("SameParameterValue")
        private static int parseInt(String value, int defaultValue) {
            return Optional.ofNullable(value).map(Integer::parseInt).orElse(defaultValue);
        }

        private static boolean parseBoolean(String value) {
            return Boolean.parseBoolean(value == null ? "true" : value);
        }

        private static List<String> parseList(String value) {
            return Optional.ofNullable(value)
                           .stream()
                           .flatMap(d -> Arrays.stream(d.split(",")))
                           .collect(toList());
        }
    }
}
