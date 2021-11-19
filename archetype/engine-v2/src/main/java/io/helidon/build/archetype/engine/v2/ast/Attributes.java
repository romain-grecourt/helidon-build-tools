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

import io.helidon.build.common.GenericType;

import static io.helidon.build.archetype.engine.v2.ast.ValueTypes.STRING;
import static io.helidon.build.archetype.engine.v2.ast.ValueTypes.STRING_LIST;

/**
 * Attributes.
 */
public enum Attributes {

    /**
     * Id.
     */
    ID(STRING_LIST),

    /**
     * Path.
     */
    PATH(STRING),

    /**
     * Src.
     */
    SRC(STRING),

    /**
     * Directory.
     */
    DIRECTORY(STRING),

    /**
     * Name.
     */
    NAME(STRING),

    /**
     * Label.
     */
    LABEL(STRING),

    /**
     * Help.
     */
    HELP(STRING),

    /**
     * Order.
     */
    ORDER(STRING),

    /**
     * Engine.
     */
    ENGINE(STRING),

    /**
     * Replacement.
     */
    TRANSFORMATIONS(STRING_LIST),

    /**
     * Replacement.
     */
    REPLACEMENT(STRING),

    /**
     * Regex.
     */
    REGEX(STRING),

    /**
     * Includes.
     */
    INCLUDES(STRING_LIST),

    /**
     * Excludes.
     */
    EXCLUDES(STRING_LIST),

    /**
     * Value.
     */
    VALUE(null);

    private final GenericType<?> type;

    Attributes(GenericType<?> type) {
        this.type = type;
    }

    /**
     * Get the value type.
     *
     * @return type
     */
    public GenericType<?> valueType() {
        return type;
    }

    /**
     * Get an attribute.
     *
     * @param node node
     * @return value
     */
    public Value get(Node node) {
        Value value = node.attributes().get(this);
        if (value == null) {
            throw new IllegalStateException(String.format(
                    "Unable to get attribute '%s', file=%s, position=%s",
                    this, node.location(), node.position()));
        }
        return value;
    }

    /**
     * Get an attribute.
     *
     * @param node node
     * @return value
     */
    public <T> T get(Node node, GenericType<T> type) {
        Value value = node.attributes().get(this);
        if (value == null) {
            throw new IllegalStateException(String.format(
                    "Unable to get attribute '%s', file=%s, position=%s",
                    this, node.location(), node.position()));
        }
        return value.as(type);
    }
}
