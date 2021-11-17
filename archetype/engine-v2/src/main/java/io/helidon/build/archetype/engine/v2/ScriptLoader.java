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

import io.helidon.build.archetype.engine.v2.ast.Attributes.InputType;
import io.helidon.build.archetype.engine.v2.ast.Attributes.Replacement;
import io.helidon.build.archetype.engine.v2.ast.Executable;
import io.helidon.build.archetype.engine.v2.ast.Expression;
import io.helidon.build.archetype.engine.v2.ast.Literal;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.IfStatement;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Output;
import io.helidon.build.archetype.engine.v2.ast.Position;
import io.helidon.build.archetype.engine.v2.ast.Script;
import io.helidon.build.archetype.engine.v2.ast.Statement;
import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Value;
import io.helidon.build.common.GenericType;
import io.helidon.build.common.xml.SimpleXMLParser;
import io.helidon.build.common.xml.SimpleXMLParser.XMLReaderException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import static io.helidon.build.archetype.engine.v2.ast.Attributes.INPUT_TYPE;
import static io.helidon.build.archetype.engine.v2.ast.Attributes.INPUT_VALUE;
import static io.helidon.build.archetype.engine.v2.ast.Attributes.INVOCATION_TYPE;
import static io.helidon.build.archetype.engine.v2.ast.Attributes.REPLACEMENT;
import static io.helidon.build.archetype.engine.v2.ast.Attributes.REPLACEMENT_TYPE_INFO;

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
            return load0(Files.newInputStream(path), path);
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

        private Path path;
        private SimpleXMLParser parser;
        private LinkedList<Context> stack;
        private Script.Builder builder;
        private Context context;
        private Position position;
        private String qName;
        private Map<String, String> attrs;

        private ReaderImpl() {
        }

        Script read(InputStream is, Path path) throws IOException {
            this.path = Objects.requireNonNull(path, "path is null");
            this.stack = new LinkedList<>();
            this.parser = SimpleXMLParser.create(is, this);
            parser.parse();
            return builder.build();
        }

        private final class Context {

            final String id;
            final Node.Builder<?, ?> builder;

            Context(String id, Node.Builder<?, ?> builder) {
                this.id = id;
                if (context != null) {
                    this.builder = builder == null ? context.builder : builder;
                } else {
                    this.builder = Objects.requireNonNull(builder, "builder is null");
                }
            }

            @SuppressWarnings("SameParameterValue")
            <T extends Node, U extends Node.Builder<T, U>> U as(GenericType<U> type) {
                if (!type.equals(builder.type())) {
                    throw new XMLReaderException(String.format(
                            "Builder type mismatch, expected=%s, actual=%s, file=%s, position=%s",
                            type, builder.type(), path, position));
                }
                return type.cast(builder);
            }
        }

        @Override
        public void startElement(String qName, Map<String, String> attrs) {
            this.context = stack.peek();
            this.position = new Position(parser.lineNumber(), parser.charNumber());
            this.qName = qName;
            this.attrs = attrs;
            if (context == null) {
                if (!"archetype-script".equals(qName)) {
                    throw new XMLReaderException(String.format(
                            "Invalid root element '%s', file=%s, position=%s",
                            qName, path, position));
                }
                this.builder = newNode(Node.BuilderTypes.SCRIPT);
                stack.push(new Context(qName, this.builder));
            } else if (!processElement()) {
                throw new XMLReaderException(String.format(
                        "Invalid element '%s', file=%s, position=%s",
                        qName, path, position));
            }
        }

        @Override
        public void endElement(String name) {
            stack.pop();
            context = null;
            qName = null;
            attrs = null;
            position = null;
        }

        @Override
        public void elementText(String value) {
            context = stack.peek();
            if (context == null) {
                return;
            }
            switch (context.id) {
                case "help":
                    context.builder.attribute("help", parseLiteral(Value.Types.STRING, value));
                    break;
                case "context/list/value":
                    context.builder.attribute("value", parseLiteral(Value.Types.STRING_LIST, value));
                    break;
                case "context/text":
                case "context/enum/value":
                case "output/model/value":
                    context.builder.attribute("value", parseLiteral(Value.Types.STRING, value));
                    break;
                case "context/boolean":
                    context.builder.attribute("value", parseLiteral(Value.Types.BOOLEAN, value));
                    break;
                case "output/files/includes/include":
                case "output/templates/includes/include":
                    context.builder.attribute("include",
                            (k, v) -> Literal.listAdd(this::newLiteral, v, Value.Types.STRING_LIST, value));
                    break;
                case "output/files/excludes/exclude":
                case "output/templates/excludes/exclude":
                    context.builder.attribute("exclude",
                            (k, v) -> Literal.listAdd(this::newLiteral, v, Value.Types.STRING_LIST, value));
                    break;
                case "output/files/directory":
                case "output/templates/directory":
                    context.builder.attribute("directory", parseLiteral(Value.Types.STRING, value));
                    break;
                default:
            }
        }

        private boolean processElement() {
            switch (context.id) {
                case "archetype-script":
                    context.as(Node.BuilderTypes.SCRIPT)
                           .body(newExecutable(Executable.Kind.SCRIPT));
                case "step":
                case "option":
                case "input/boolean":
                case "input/text":
                    return executable();
                case "context":
                    return inputValue();
                case "input":
                    switch (qName) {
                        case "text":
                        case "boolean":
                        case "enum":
                        case "list":
                            return input();
                        default:
                            return false;
                    }
                case "input/list":
                case "input/enum":
                    switch (qName) {
                        case "option":
                            return statement(qName, newExecutable(Executable.Kind.OPTION));
                        case "help":
                            return noop();
                        default:
                            return false;
                    }
                case "output":
                    return output();
                case "output/transformation":
                    if (qName.equals("replace")) {
                        // TODO improve this
                        Replacement replacement = Replacement.create(
                                readRequiredAttribute("regex", qName, attrs),
                                readRequiredAttribute("replacement", qName, attrs));
                        context.builder.attribute(REPLACEMENT, (k, v) -> Literal.listAdd(this::newLiteral, v,
                                REPLACEMENT_TYPE_INFO, replacement));
                        return true;
                    }
                    return false;
                case "output/model":
                case "output/model/list":
                case "output/model/map":
                    return model();
                case "output/template":
                    if (qName.equals("model")) {
                        return conditional("output/" + qName, newOutput(Output.Kind.MODEL));
                    }
                    return false;
                case "output/templates":
                case "output/files":
                    return files();
                case "output/templates/includes":
                case "output/templates/excludes":
                case "output/files/includes":
                case "output/files/excludes":
                case "context/list":
                case "context/enum":
                    return noop();
                case "exec":
                case "source":
                    // do nothing for attributes-only elements
                    return true;
                default:
                    return false;
            }
        }

        private boolean noop() {
            stack.push(new Context(context.id + "/" + qName, null));
            return true;
        }

        private boolean invocation() {
            Expression.Builder builder = newExpression(Expression.Kind.INVOCATION);
            switch (qName) {
                case "exec":
                case "source":
                    builder.attribute(INVOCATION_TYPE, parseLiteral(Value.Types.STRING, qName));
                    break;
                default:
                    return false;
            }
            statement(qName, builder);
            return true;
        }

        private boolean files() {
            switch (qName) {
                case "model":
                    if (context.id.equals("output/templates")) {
                        return conditional("output/" + qName, newOutput(Output.Kind.MODEL));
                    }
                    return false;
                case "directory":
                case "includes":
                case "excludes":
                    return noop();
                default:
                    return false;
            }
        }

        private boolean executable() {
            switch (qName) {
                case "context":
                    return statement(qName, newBlock(Block.Kind.INPUT_VALUES));
                case "exec":
                case "source":
                    return invocation();
                case "input":
                    return conditional(qName, newBlock(Block.Kind.INPUTS));
                case "step":
                    if (!context.id.equals("step")) {
                        return conditional(qName, newExecutable(Executable.Kind.STEP));
                    }
                    return false;
                case "output":
                    return conditional(qName, newExecutable(Executable.Kind.OUTPUT));
                case "help":
                    switch (context.id) {
                        case "step":
                        case "option":
                        case "input/boolean":
                        case "input/text":
                            return true;
                        default:
                            return false;
                    }
                default:
                    return false;
            }
        }

        private boolean input() {
            InputType inputType = InputType.valueOf(qName.toUpperCase());
            Executable.Builder builder = newExecutable(Executable.Kind.INPUT)
                    .attribute(INPUT_TYPE, inputType.toValue(this::newLiteral));
            return statement("input/" + qName, builder);
        }

        private boolean inputValue() {
            InputType inputType = InputType.valueOf(qName.toUpperCase());
            Expression.Builder builder = newExpression(Expression.Kind.INPUT_VALUE)
                    // TODO improve this
                    .attribute(INPUT_VALUE, parseLiteral(inputType.valueType(), attrs.get("value")))
                    .attribute(INPUT_TYPE, inputType.toValue(this::newLiteral));
            return statement("context/" + qName, builder);
        }

        private boolean conditional(String id, Statement.Builder<?, ?> then) {
            Statement.Builder<?, ?> builder;
            String ifExpr = attrs.get("if");
            if (ifExpr != null) {
                builder = newNode(Node.BuilderTypes.IF).expression(ifExpr).thenStmt(then);
            } else {
                builder = then;
            }
            return statement(id, builder);
        }

        private boolean statement(String id, Statement.Builder<? extends Statement, ?> builder) {
            Node.Builder<?, ?> parentBuilder;
            if (context.builder instanceof IfStatement.Builder) {
                parentBuilder = ((IfStatement.Builder) context.builder).thenStmt();
            } else {
                parentBuilder = context.builder;
            }
            ((Block.Builder<?, ?>) parentBuilder).statement(builder);
            stack.push(new Context(id, builder));
            return true;
        }

        private boolean model() {
            Model.Builder builder;
            switch (qName) {
                case "value":
                    // TODO file, template, url attrs
                    builder = newModel(Model.Kind.VALUE);
                    break;
                case "list":
                    if ("output/model".equals(context.id)
                            || "output/model/list".equals(context.id)
                            || "output/model/map".equals(context.id)) {
                        builder = newModel(Model.Kind.LIST);
                        break;
                    }
                case "map":
                    if ("output/model".equals(context.id)
                            || "output/model/list".equals(context.id)) {
                        builder = newModel(Model.Kind.MAP);
                        break;
                    }
                default:
                    return false;
            }
            return conditional("output/model/" + qName, builder);
        }

        private boolean output() {
            Output.Builder builder = newOutput(Output.Kind.valueOf(qName.toUpperCase()));
            return conditional("output/" + qName, builder);
        }

        private <T extends Node, U extends Node.Builder<T, U>> U newNode(GenericType<U> type) {
            U builder = Node.builder(type).position(position).location(path);
            attrs.forEach((k, v) -> {
                GenericType<?> valueType;
                switch (k) {
                    case "order":
                        valueType = Value.Types.INT;
                        break;
                    case "transformations":
                        valueType = Value.Types.STRING_LIST;
                        break;
                    case "if":
                    case "value":
                        return;
                    default:
                        valueType = Value.Types.STRING;
                }
                builder.attribute(k, parseLiteral(valueType, v));
            });
            return builder;
        }

        @SuppressWarnings("unchecked")
        private Block.Builder<?, ?> newBlock(Block.Kind kind) {
            return newNode(Node.BuilderTypes.BLOCK).blockKind(kind);
        }

        private Executable.Builder newExecutable(Executable.Kind kind) {
            return newNode(Node.BuilderTypes.EXECUTABLE).executableKind(kind);
        }

        private Expression.Builder newExpression(Expression.Kind kind) {
            return newNode(Node.BuilderTypes.EXPRESSION).expressionKind(kind);
        }

        private Output.Builder newOutput(Output.Kind kind) {
            return newNode(Node.BuilderTypes.OUTPUT).outputKind(kind);
        }

        private Model.Builder newModel(Model.Kind kind) {
            return newNode(Node.BuilderTypes.MODEL).modelKind(kind);
        }

        @SuppressWarnings("rawtypes")
        private Literal.Builder newLiteral() {
            return newNode(Node.BuilderTypes.LITERAL);
        }

        @SuppressWarnings("unchecked")
        private <T> Literal newLiteral(GenericType<T> type, T value) {
            return newLiteral().type(type).value(value).build();
        }

        private <T> Literal parseLiteral(GenericType<T> type, String rawValue) {
            return Literal.parse(this::newLiteral, type, rawValue);
        }
    }
}
