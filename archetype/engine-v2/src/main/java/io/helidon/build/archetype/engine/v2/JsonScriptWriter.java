/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.build.archetype.engine.v2.Node.Kind;

/**
 * JSON writer.
 */
public final class JsonScriptWriter implements Script.Writer {

    private final Writer writer;
    private final boolean pretty;
    private final Map<Expression, String> expressions = new LinkedHashMap<>();

    /**
     * Create a new instance.
     *
     * @param writer writer
     * @param pretty pretty
     */
    public JsonScriptWriter(Writer writer, boolean pretty) {
        this.writer = writer;
        this.pretty = pretty;
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void writeScript(Node scriptNode) {
        expressions.clear();
        Map<String, Object> root = new LinkedHashMap<>();

        for (Node node : scriptNode.traverse(Kind.CONDITION::equals)) {
            expressions.computeIfAbsent(node.expression(), k -> String.valueOf(expressions.size() + 1));
        }

        if (!expressions.isEmpty()) {
            Map<String, Object> tokens = new LinkedHashMap<>();
            expressions.forEach((e, id) -> tokens.put(id, tokens(e)));
            root.put("expressions", tokens);
        }

        if (!scriptNode.script().methods().isEmpty()) {
            Map<String, Object> methods = new LinkedHashMap<>();
            scriptNode.script().methods().forEach((name, method) -> methods.put(name, directives(method)));
            root.put("methods", methods);
        }

        List<Map<String, Object>> children = directives(scriptNode);
        if (!children.isEmpty()) {
            root.put("children", children);
        }

        try {
            new JsonRenderer(writer, pretty).write(root);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private List<Map<String, Object>> directives(Node block) {
        return directives(block, null, new AtomicInteger());
    }

    private List<Map<String, Object>> directives(Node parent, String conditionId, AtomicInteger stepId) {
        List<Map<String, Object>> directives = new ArrayList<>();
        for (Node child : parent.children()) {
            if (child.kind() == Kind.CONDITION) {
                directives.addAll(directives(child, expressions.get(child.expression()), stepId));
                continue;
            }
            Map<String, Object> directive = new LinkedHashMap<>();
            directive.put("kind", child.kind().token());
            if (child.kind() == Kind.STEP) {
                directive.put("id", String.valueOf(stepId.incrementAndGet()));
            }
            child.attributes().forEach((key, value) -> {
                if (key.equals("default") && child.kind() == Kind.INPUT_BOOLEAN) {
                    directive.put(key, scalar(booleanDefault(value)));
                } else {
                    directive.put(key, scalar(value));
                }
            });
            if (conditionId != null) {
                directive.put("if", conditionId);
            }
            Object value = nodeValue(child);
            if (value != AbsentValue.INSTANCE) {
                directive.put("value", value);
            }
            List<Map<String, Object>> children = directives(child, null, stepId);
            if (!children.isEmpty()) {
                directive.put("children", children);
            }
            directives.add(directive);
        }
        return directives;
    }

    private Value<?> booleanDefault(Value<?> value) {
        Value<Boolean> booleanValue = value.asBoolean();
        return booleanValue.isPresent() ? booleanValue : value;
    }

    private List<Map<String, Object>> tokens(Expression expression) {
        List<Map<String, Object>> tokens = new ArrayList<>();
        for (Expression.Token token : expression.tokens()) {
            Map<String, Object> value = new LinkedHashMap<>();
            if (token.isOperator()) {
                value.put("kind", "operator");
                value.put("value", token.operator().symbol());
            } else if (token.isVariable()) {
                value.put("kind", "variable");
                value.put("value", token.variable());
            } else if (token.isOperand()) {
                value.put("kind", "literal");
                value.put("value", scalar(token.operand()));
            }
            tokens.add(value);
        }
        return tokens;
    }

    private Object nodeValue(Node node) {
        switch (node.kind()) {
            case PRESET_BOOLEAN:
            case VARIABLE_BOOLEAN:
                return scalar(node.value().asBoolean().or(() -> Value.FALSE));
            case PRESET_ENUM:
            case VARIABLE_ENUM:
            case PRESET_TEXT:
            case VARIABLE_TEXT:
                return scalar(node.value().asString().or(() -> Value.EMPTY_STRING));
            case PRESET_LIST:
            case VARIABLE_LIST:
                return scalar(node.value().asList().or(() -> Value.EMPTY_LIST));
            default:
                return node.value().isPresent() ? scalar(node.value()) : AbsentValue.INSTANCE;
        }
    }

    private Object scalar(Value<?> value) {
        switch (value.type()) {
            case DYNAMIC:
            case STRING:
                return value.getString();
            case INTEGER:
                return value.getInt();
            case BOOLEAN:
                return value.getBoolean();
            case LIST:
                return List.copyOf(value.getList());
            default:
                return null;
        }
    }

    private enum AbsentValue {
        INSTANCE
    }

    private static final class JsonRenderer {
        private static final String INDENT = "    ";

        private final Writer writer;
        private final boolean pretty;

        JsonRenderer(Writer writer, boolean pretty) {
            this.writer = writer;
            this.pretty = pretty;
        }

        void write(Object value) throws IOException {
            write(value, 0);
            if (pretty) {
                writer.write(System.lineSeparator());
            }
        }

        @SuppressWarnings("unchecked")
        void write(Object value, int depth) throws IOException {
            if (value == null) {
                writer.write("null");
            } else if (value instanceof String) {
                string((String) value);
            } else if (value instanceof Number || value instanceof java.lang.Boolean) {
                writer.write(String.valueOf(value));
            } else if (value instanceof Map<?, ?>) {
                object((Map<String, Object>) value, depth);
            } else if (value instanceof List<?>) {
                array((List<Object>) value, depth);
            } else {
                throw new IllegalStateException("Unsupported JSON value: " + value.getClass());
            }
        }

        void object(Map<String, Object> object, int depth) throws IOException {
            writer.write("{");
            if (object.isEmpty()) {
                writer.write("}");
                return;
            }
            newline();
            int index = 0;
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                indent(depth + 1);
                string(entry.getKey());
                writer.write(pretty ? ": " : ":");
                write(entry.getValue(), depth + 1);
                if (++index < object.size()) {
                    writer.write(",");
                }
                newline();
            }
            indent(depth);
            writer.write("}");
        }

        void array(List<Object> array, int depth) throws IOException {
            writer.write("[");
            if (array.isEmpty()) {
                writer.write("]");
                return;
            }
            newline();
            for (int i = 0; i < array.size(); i++) {
                indent(depth + 1);
                write(array.get(i), depth + 1);
                if (i + 1 < array.size()) {
                    writer.write(",");
                }
                newline();
            }
            indent(depth);
            writer.write("]");
        }

        void string(String value) throws IOException {
            writer.write("\"");
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\\':
                        writer.write("\\\\");
                        break;
                    case '"':
                        writer.write("\\\"");
                        break;
                    case '\b':
                        writer.write("\\b");
                        break;
                    case '\f':
                        writer.write("\\f");
                        break;
                    case '\n':
                        writer.write("\\n");
                        break;
                    case '\r':
                        writer.write("\\r");
                        break;
                    case '\t':
                        writer.write("\\t");
                        break;
                    default:
                        if (c < 0x20) {
                            writer.write(String.format("\\u%04x", (int) c));
                        } else {
                            writer.write(c);
                        }
                }
            }
            writer.write("\"");
        }

        void newline() throws IOException {
            if (pretty) {
                writer.write(System.lineSeparator());
            }
        }

        void indent(int depth) throws IOException {
            if (!pretty) {
                return;
            }
            for (int i = 0; i < depth; i++) {
                writer.write(INDENT);
            }
        }
    }
}
