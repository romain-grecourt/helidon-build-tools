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

import java.util.List;
import java.util.function.BiFunction;

/**
 * Attributes.
 */
public enum Attributes {

    /**
     * Input type.
     *
     * @see InputType
     */
    INPUT_TYPE,

    /**
     * Input value.
     */
    INPUT_VALUE,

    /**
     * Input name.
     */
    INPUT_NAME,

    /**
     * Invocation type.
     *
     * @see InvocationType
     */
    INVOCATION_TYPE,

    /**
     * Replacement
     */
    REPLACEMENT;

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

    // TODO parse?

    /**
     * Type info for {@code INPUT_TYPE}.
     */
    public static final GenericType<InputType> INPUT_TYPE_INFO = GenericType.create(InputType.class);

    /**
     * Type info for {@code INVOCATION_TYPE}.
     */
    public static final GenericType<InvocationType> INVOCATION_TYPE_INFO = GenericType.create(InvocationType.class);

    /**
     * Type info for {@code REPLACEMENT}.
     */
    public static final GenericType<List<Replacement>> REPLACEMENT_TYPE_INFO = new GenericType<>() {
    };

    /**
     * Input type.
     */
    public enum InputType {

        /**
         * Text.
         */
        TEXT(Value.Types.STRING),

        /**
         * Boolean.
         */
        BOOLEAN(Value.Types.BOOLEAN),

        /**
         * Enum.
         */
        ENUM(Value.Types.STRING),

        /**
         * List.
         */
        LIST(Value.Types.STRING_LIST);

        private final GenericType<?> type;

        InputType(GenericType<?> type) {
            this.type = type;
        }

        /**
         * Create a value.
         *
         * @param function function
         * @return value
         */
        public Value toValue(BiFunction<GenericType<InputType>, InputType, Value> function) {
            return function.apply(INPUT_TYPE_INFO, this);
        }

        /**
         * Get the value type.
         *
         * @return type
         */
        public GenericType<?> valueType() {
            return type;
        }
    }

    /**
     * Invocation type.
     */
    public enum InvocationType {

        /**
         * Exec.
         */
        EXEC,

        /**
         * Source.
         */
        SOURCE
    }

    /**
     * Replacement.
     */
    public static final class Replacement {

        private final String regexp;
        private final String replace;

        private Replacement(String regexp, String replace) {
            this.regexp = regexp;
            this.replace = replace;
        }

        /**
         * Get the regexp.
         *
         * @return regexp
         */
        public String regexp() {
            return regexp;
        }

        /**
         * Get the replacement.
         *
         * @return replacement
         */
        public String replace() {
            return replace;
        }

        /**
         * Create a new replacement.
         *
         * @param regexp  regexp
         * @param replace replace
         * @return replacement
         */
        public static Replacement create(String regexp, String replace) {
            return new Replacement(regexp, replace);
        }
    }
}
