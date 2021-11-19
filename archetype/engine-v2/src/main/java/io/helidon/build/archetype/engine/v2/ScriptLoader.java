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
import io.helidon.build.archetype.engine.v2.ast.Condition;
import io.helidon.build.archetype.engine.v2.ast.Invocation;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.Noop;
import io.helidon.build.archetype.engine.v2.ast.Position;
import io.helidon.build.archetype.engine.v2.ast.Preset;
import io.helidon.build.archetype.engine.v2.ast.Script;
import io.helidon.build.archetype.engine.v2.ast.Statement;
import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.ValueTypes;
import io.helidon.build.common.xml.SimpleXMLParser;
import io.helidon.build.common.xml.SimpleXMLParser.XMLReaderException;

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
        return CACHE.computeIfAbsent(path.getFileSystem(), fs -> new HashMap<>())
                    .compute(path, (p, r) -> r == null || r.get() == null ? new WeakReference<>(load0(p)) : r)
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
        PRESET,
        INPUT,
        EXECUTABLE,
        BLOCK
    }

    private static final class Context {

        final State state;
        final Node.Builder<?, ?> builder;
        final Position position;
        final String qName;
        final Map<String, String> attrs;

        Context(State state, Node.Builder<?, ?> builder, ReaderImpl reader) {
            this.state = state;
            this.builder = builder;
            this.position = reader.position;
            this.qName = reader.qName;
            this.attrs = reader.attrs;
        }
    }

    private static final class ReaderImpl implements SimpleXMLParser.Reader {

        Path location;
        Position position;
        SimpleXMLParser parser;
        String qName;
        Map<String, String> attrs;
        LinkedList<Context> stack;
        Context ctx;
        Script.Builder script;

        ReaderImpl() {
        }

        Script read(InputStream is, Path path) throws IOException {
            location = Objects.requireNonNull(path, "path is null");
            stack = new LinkedList<>();
            parser = SimpleXMLParser.create(is, this);
            parser.parse();
            if (script == null) {
                throw new IllegalStateException("Unable to create script");
            }
            return script.build();
        }

        @Override
        public void startElement(String qName, Map<String, String> attrs) {
            this.qName = qName;
            this.attrs = attrs;
            position = Position.of(parser.lineNumber(), parser.charNumber());
            ctx = stack.peek();
            if (ctx == null) {
                if (!"archetype-script".equals(qName)) {
                    throw new XMLReaderException(String.format(
                            "Invalid root element '%s'. {file=%s, position=%s}",
                            qName, location, position));
                }
                script = Script.builder(location, position);
                stack.push(new Context(State.EXECUTABLE, script, this));
            } else {
                try {
                    processElement();
                } catch (IllegalArgumentException ex) {
                    throw new XMLReaderException(String.format(
                            "Invalid element '%s'. { file=%s, position=%s }",
                            qName, location, position), ex);
                } catch (Throwable ex) {
                    throw new XMLReaderException(String.format(
                            "An unexpected error occurred. { file=%s, position=%s }",
                            location, position), ex);
                }
            }
        }

        @Override
        public void elementText(String value) {
            ctx = stack.peek();
            if (ctx == null) {
                return;
            }
            processText(value);
        }

        @Override
        public void endElement(String name) {
            stack.pop();
        }

        void processText(String value) {
            switch (ctx.state) {
                case BLOCK:
                    switch (ctx.qName) {
                        case "help":
                            ctx.builder.parseAttribute(Attributes.HELP, value);
                            break;
                        case "include":
                            ctx.builder.attributeListAdd(Attributes.INCLUDES, value);
                            break;
                        case "exclude":
                            ctx.builder.attributeListAdd(Attributes.EXCLUDES, value);
                            break;
                        case "directory":
                            ctx.builder.parseAttribute(Attributes.DIRECTORY, value);
                        default:
                    }
                    break;
                case PRESET:
                    switch (ctx.qName) {
                        case "text":
                        case "enum":
                        case "boolean":
                            ctx.builder.parseAttribute(Attributes.VALUE, presetKind().valueType(), value);
                            break;
                        case "value":
                            ctx.builder.attributeListAdd(Attributes.VALUE, value);
                            break;
                        default:
                    }
                    break;
                default:
            }
        }

        void processElement() {
            switch (qName) {
                case "directory":
                case "includes":
                case "include":
                case "excludes":
                case "exclude":
                case "help":
                case "value":
                    skip();
                    return;
                case "replace":
                    statement(ctx.state, Noop.builder(location, position));
                    return;
                default:
            }
            switch (ctx.state) {
                case EXECUTABLE:
                    switch (qName) {
                        case "exec":
                        case "source":
                            statement(ctx.state, Invocation.builder(location, position, invocationKind()));
                            break;
                        default:
                            processBlock();
                    }
                    break;
                case BLOCK:
                    processBlock();
                    break;
                case INPUT:
                    processInput();
                    break;
                case PRESET:
                    statement(State.PRESET, Preset.builder(location, position, presetKind()));
                    break;
                default:
                    throw new XMLReaderException(String.format(
                            "Invalid state: %s. { element=%s }", ctx.state, qName));
            }
        }

        void processInput() {
            State nextState;
            Block.Kind blockKind = blockKind();
            Block.Builder builder = Block.builder(location, ctx.position, blockKind);
            switch (blockKind) {
                case BOOLEAN:
                case TEXT:
                case OPTION:
                    builder.parseAttribute(Attributes.VALUE, ValueTypes.STRING, attrs);
                    nextState = State.EXECUTABLE;
                    break;
                case LIST:
                case ENUM:
                    nextState = State.INPUT;
                    break;
                default:
                    throw new XMLReaderException(String.format(
                            "Invalid input block: %s. { element=%s }", blockKind, qName));
            }
            statement(nextState, builder);
        }

        void processBlock() {
            State nextState;
            Block.Kind blockKind = blockKind();
            switch (blockKind) {
                case SCRIPT:
                case OPTION:
                case STEP:
                    nextState = State.EXECUTABLE;
                    break;
                case INPUTS:
                    nextState = State.INPUT;
                    break;
                case PRESETS:
                    nextState = State.PRESET;
                    break;
                default:
                    nextState = State.BLOCK;
            }
            statement(nextState, Block.builder(location, position, blockKind));
        }

        void statement(State nextState, Statement.Builder<? extends Statement, ?> stmt) {
            stmt.parseAttributes(attrs);
            String ifExpr = attrs.get("if");
            if (ifExpr != null) {
                ctx.builder.statement(Condition.builder(location, position).expression(ifExpr).then(stmt));
            } else {
                ctx.builder.statement(stmt);
            }
            stack.push(new Context(nextState, stmt, this));
        }

        void skip() {
            stack.push(new Context(ctx.state, ctx.builder, this));
        }

        Preset.Kind presetKind() {
            return Preset.Kind.valueOf(qName.toUpperCase());
        }

        Block.Kind blockKind() {
            return Block.Kind.valueOf(qName.toUpperCase());
        }

        Invocation.Kind invocationKind() {
            return Invocation.Kind.valueOf(qName.toUpperCase());
        }
    }
}
