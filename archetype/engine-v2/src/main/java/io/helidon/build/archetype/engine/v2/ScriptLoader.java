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

import io.helidon.build.archetype.engine.v2.ast.Attributes;
import io.helidon.build.archetype.engine.v2.ast.Data;
import io.helidon.build.archetype.engine.v2.ast.Executable;
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Invocation;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Output;
import io.helidon.build.archetype.engine.v2.ast.Position;
import io.helidon.build.archetype.engine.v2.ast.Script;
import io.helidon.build.archetype.engine.v2.ast.Statement;
import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.ValueTypes;
import io.helidon.build.common.Pair;
import io.helidon.build.common.xml.SimpleXMLParser;
import io.helidon.build.common.xml.SimpleXMLParser.XMLReaderException;

import static io.helidon.build.archetype.engine.v2.ast.NodeFactory.newBlock;
import static io.helidon.build.archetype.engine.v2.ast.NodeFactory.newData;
import static io.helidon.build.archetype.engine.v2.ast.NodeFactory.newExecutable;
import static io.helidon.build.archetype.engine.v2.ast.NodeFactory.newIf;
import static io.helidon.build.archetype.engine.v2.ast.NodeFactory.newInput;
import static io.helidon.build.archetype.engine.v2.ast.NodeFactory.newInvocation;
import static io.helidon.build.archetype.engine.v2.ast.NodeFactory.newModel;
import static io.helidon.build.archetype.engine.v2.ast.NodeFactory.newOutput;
import static io.helidon.build.archetype.engine.v2.ast.NodeFactory.newPreset;
import static io.helidon.build.archetype.engine.v2.ast.NodeFactory.newScript;

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

    private enum State {
        SCRIPT,
        PRESETS,
        PRESET,
        INPUTS,
        INPUT,
        OPTION,
        HELP,
        INCLUDES,
        EXCLUDES,
        DIRECTORY,
        INVOCATION,
        OUTPUT,
        TEMPLATES,
        FILES,
        TEMPLATE,
        TRANSFORMATION,
        REPLACE,
        MODEL
    }

    private static final class ReaderImpl implements SimpleXMLParser.Reader {

        Path location;
        Position position;
        SimpleXMLParser parser;
        String qName;
        Map<String, String> attrs;
        LinkedList<Pair<State, Node.Builder<?, ?>>> stack;
        Script.Builder script;
        State state;
        Node.Builder<?, ?> builder;

        ReaderImpl() {
        }

        Script read(InputStream is, Path path) throws IOException {
            this.location = Objects.requireNonNull(path, "path is null");
            this.stack = new LinkedList<>();
            this.parser = SimpleXMLParser.create(is, this);
            parser.parse();
            if (script == null) {
                throw new IllegalStateException("Unable to create script");
            }
            return script.build();
        }

        @Override
        public void startElement(String qName, Map<String, String> attrs) {
            this.position = Position.of(parser.lineNumber(), parser.charNumber());
            this.qName = qName;
            this.attrs = attrs;
            Pair<State, Node.Builder<?, ?>> ctx = stack.peek();
            if (ctx == null) {
                if (!"archetype-script".equals(qName)) {
                    throw new XMLReaderException(String.format(
                            "Invalid root element '%s', file=%s, position=%s",
                            qName, location, position));
                }
                script = newScript(location, position);
                stack.push(Pair.of(State.SCRIPT, script));
            } else {
                state = ctx.left();
                builder = ctx.right();
                boolean invalidElement;
                Throwable cause = null;
                try {
                    invalidElement = !processElement();
                } catch (IllegalArgumentException | XMLReaderException ex) {
                    invalidElement = true;
                    cause = ex;
                } catch (Throwable ex) {
                    invalidElement = false;
                    cause = ex;
                }
                if (invalidElement) {
                    throw new XMLReaderException(String.format(
                            "Invalid element '%s', file=%s, position=%s", qName, location, position), cause);
                } else if (cause != null) {
                    throw new XMLReaderException(String.format(
                            "Unexpected error, file=%s, position=%s", location, position), cause);
                }
            }
        }

        @Override
        public void elementText(String value) {
            Pair<State, Node.Builder<?, ?>> ctx = stack.peek();
            if (ctx == null) {
                return;
            }
            state = ctx.left();
            builder = ctx.right();
            processText(value);
        }

        @Override
        public void endElement(String name) {
            stack.pop();
        }

        boolean processElement() {
            switch (state) {
                case SCRIPT:
                case INPUT:
                    return executable();
                case PRESETS:
                    return statement(State.PRESET, newPreset(location, position, inputKind()));
                case INPUTS:
                    return statement(State.INPUT, newInput(location, position, inputKind()));
                case OPTION:
                    return option();
                case OUTPUT:
                    return statement(nextState(), newOutput(location, position, outputKind()));
                case TRANSFORMATION:
                    return statement(State.REPLACE, newData(location, position, Data.Kind.REPLACE));
                case MODEL:
                    return statement(state, newModel(location, position, modelKind()));
                case TEMPLATE:
                    return statement(state, newOutput(location, position, Output.Kind.MODEL));
                case TEMPLATES:
                case FILES:
                    return files();
                case INCLUDES:
                case EXCLUDES:
                case INVOCATION:
                case PRESET:
                    return repeat();
                default:
                    throw new XMLReaderException();
            }
        }

        void processText(String value) {
            switch (state) {
                case HELP:
                    builder.parseAttribute(Attributes.HELP, ValueTypes.STRING, value);
                    break;
                case PRESET:
                    switch (qName) {
                        case "boolean":
                            builder.parseAttribute(Attributes.VALUE, ValueTypes.BOOLEAN, value);
                            break;
                        case "text":
                        case "enum":
                            builder.parseAttribute(Attributes.VALUE, ValueTypes.STRING, value);
                            break;
                        case "value":
                            builder.attributeListAdd(Attributes.VALUE, ValueTypes.STRING_LIST, value);
                            break;
                        default:
                    }
                    break;
                case INCLUDES:
                    builder.attributeListAdd(Attributes.INCLUDES, ValueTypes.STRING_LIST, value);
                    break;
                case EXCLUDES:
                    builder.attributeListAdd(Attributes.EXCLUDES, ValueTypes.STRING_LIST, value);
                    break;
                case DIRECTORY:
                    builder.parseAttribute(Attributes.DIRECTORY, ValueTypes.STRING, value);
                    break;
                default:
            }
        }

        boolean option() {
            switch (qName) {
                case "option":
                    return statement(State.SCRIPT, newExecutable(location, position, Executable.Kind.OPTION));
                case "help":
                    return repeat();
                default:
                    throw new XMLReaderException();
            }
        }

        boolean files() {
            switch (qName) {
                case "model":
                    if (state == State.TEMPLATES) {
                        return statement(State.MODEL, newBlock(location, position, Block.Kind.MODEL));
                    }
                case "directory":
                case "includes":
                case "excludes":
                    return next();
                default:
                    throw new XMLReaderException();
            }
        }

        boolean executable() {
            switch (qName) {
                case "option":
                    if (state == State.INPUT) {
                        return statement(State.OPTION, newExecutable(location, position, Executable.Kind.OPTION));
                    }
                    return false;
                case "presets":
                    return statement(State.PRESETS, newBlock(location, position, Block.Kind.PRESETS));
                case "exec":
                    return statement(State.INVOCATION, newInvocation(location, position, Invocation.Kind.EXEC));
                case "source":
                    return statement(State.INVOCATION, newInvocation(location, position, Invocation.Kind.SOURCE));
                case "inputs":
                    return statement(State.INPUTS, newBlock(location, position, Block.Kind.INPUTS));
                case "step":
                    return statement(State.SCRIPT, newExecutable(location, position, Executable.Kind.STEP));
                case "output":
                    return statement(State.OUTPUT, newBlock(location, position, Block.Kind.OUTPUT));
                case "help":
                    return repeat();
                default:
                    throw new XMLReaderException();
            }
        }

        State nextState() {
            return State.valueOf(qName.toUpperCase());
        }

        Model.Kind modelKind() {
            return Model.Kind.valueOf(qName.toUpperCase());
        }

        Output.Kind outputKind() {
            return Output.Kind.valueOf(qName.toUpperCase());
        }

        Input.Kind inputKind() {
            return Input.Kind.valueOf(qName.toUpperCase());
        }

        boolean statement(State nextState, Statement.Builder<? extends Statement, ?> stmt) {
            stmt.parseAttributes(attrs);
            String ifExpr = attrs.get("if");
            if (ifExpr != null) {
                builder.statement(newIf(location, position).expression(ifExpr).thenStmt(stmt));
            } else {
                builder.statement(stmt);
            }
            stack.push(Pair.of(nextState, stmt));
            return true;
        }

        boolean next() {
            stack.push(Pair.of(nextState(), builder));
            return true;
        }

        boolean repeat() {
            stack.push(stack.peek());
            return true;
        }
    }
}
