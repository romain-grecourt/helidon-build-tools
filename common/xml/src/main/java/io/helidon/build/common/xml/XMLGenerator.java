/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.build.common.xml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * XML generator.
 */
@SuppressWarnings("resource")
public class XMLGenerator implements AutoCloseable {

    private static final int ATTRIBUTE_MASK = 1 << 1;
    private static final int VALUE_MASK = 1 << 2;
    private static final int ELEMENT_MASK = 1 << 3;

    private final Writer writer;
    private final boolean pretty;
    private final Deque<String> names = new ArrayDeque<>();
    private String token;
    private int depth;
    private int state;

    /**
     * Create a new instance.
     *
     * @param writer IO writer
     * @param pretty {@code true} for newlines and indentation
     */
    public XMLGenerator(Writer writer, boolean pretty) {
        this.writer = writer;
        this.pretty = pretty;
    }

    /**
     * Append the given element.
     *
     * @param elt element
     */
    public void append(XMLElement elt) {
        elt.visit(new XMLElement.Visitor() {
            @Override
            public void visitElement(XMLElement elt) {
                startElement(elt.name());
                elt.attributes().forEach(XMLGenerator.this::attribute);
            }

            @Override
            public void postVisitElement(XMLElement elt) {
                value(elt.value());
                endElement();
            }
        });
    }

    /**
     * Write the prolog.
     *
     * @return this instance
     * @throws XMLException if the prolog is already set
     */
    public XMLGenerator prolog() {
        if ((state & ELEMENT_MASK) == ELEMENT_MASK) {
            throw new XMLException("Cannot write prolog");
        }
        state |= ELEMENT_MASK;
        return append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    }

    /**
     * Write the start of an element.
     *
     * @param name element name
     * @return this instance
     */
    public XMLGenerator startElement(String name) {
        if ((state & ATTRIBUTE_MASK) == ATTRIBUTE_MASK) {
            append(">");
        }
        if ((state & VALUE_MASK) == 0) {
            indent();
        }
        append("<").append(name);
        state = ELEMENT_MASK | ATTRIBUTE_MASK;
        depth = names.size();
        names.push(name);
        return this;
    }

    /**
     * Write the end of the current element.
     *
     * @return this instance
     * @throws XMLException if there is no element opened
     */
    public XMLGenerator endElement() {
        if (names.isEmpty()) {
            throw new XMLException("No element opened");
        }
        String name = names.pop();
        if (depth == names.size()) {
            if ((state & ATTRIBUTE_MASK) == ATTRIBUTE_MASK) {
                state = ELEMENT_MASK;
                return append("/>");
            }
        } else if ((state & VALUE_MASK) == 0) {
            indent();
        }
        state = ELEMENT_MASK;
        append("</").append(name).append(">");
        return this;
    }

    /**
     * Write an attribute.
     *
     * @param name  attribute name
     * @param value attribute value
     * @return this instance
     */
    public XMLGenerator attribute(String name, Object value) {
        return value != null ? attribute(name, String.valueOf(value)) : this;
    }

    /**
     * Write an attribute.
     *
     * @param name  attribute name
     * @param value attribute value
     * @return this instance
     */
    public XMLGenerator attribute(String name, String value) {
        if (value == null) {
            return this;
        }
        return append(" ").append(encode(name)).append("=\"").append(encode(value)).append("\"");
    }

    /**
     * Write an element value.
     *
     * @param value value
     * @return this instance
     */
    public XMLGenerator value(Object value) {
        return value != null ? value(String.valueOf(value)) : this;
    }

    /**
     * Write an element value.
     *
     * @param value value
     * @return this instance
     */
    public XMLGenerator value(String value) {
        if (value == null || value.isEmpty()) {
            return this;
        }
        if ((state & VALUE_MASK) == VALUE_MASK) {
            throw new XMLException("value already set");
        }
        state |= VALUE_MASK;
        if ((state & ATTRIBUTE_MASK) == ATTRIBUTE_MASK) {
            append(">");
            state ^= ATTRIBUTE_MASK;
        }
        return append(encode(value));
    }

    /**
     * Write an element CDATA value.
     *
     * @param value value
     * @return this instance
     */
    public XMLGenerator cdata(String value) {
        if (value == null) {
            return this;
        }
        if ((state & VALUE_MASK) == VALUE_MASK) {
            throw new XMLException("value already set");
        }
        state |= VALUE_MASK;
        if ((state & ATTRIBUTE_MASK) == ATTRIBUTE_MASK) {
            append(">");
            state ^= ATTRIBUTE_MASK;
        }
        return append("<![CDATA[").append(value).append("]]>");
    }

    @Override
    public void close() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Replace the known unescaped XML entities with their escaped form.
     *
     * @param str input string
     * @return new string with replaced values
     */
    public static String encode(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private XMLGenerator append(String str) {
        try {
            writer.append(str);
            token = str;
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void indent() {
        if (pretty) {
            if (token != null && !token.endsWith("\n")) {
                append("\n");
            }
            if (!names.isEmpty()) {
                for (int i = 0; i < names.size(); i++) {
                    append("    ");
                }
            }
        }
    }
}
