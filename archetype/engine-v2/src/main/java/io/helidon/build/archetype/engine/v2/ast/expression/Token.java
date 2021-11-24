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

package io.helidon.build.archetype.engine.v2.ast.expression;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Parsing token used.
 */
final class Token {

    private final Type type;
    private final String value;

    /**
     * Create a new token.
     *
     * @param type  Type of the token.
     * @param value Value of the token.
     */
    Token(Type type, String value) {
        this.type = type;
        this.value = value;
    }

    /**
     * Get type of the token.
     *
     * @return Type
     */
    public Type type() {
        return type;
    }

    /**
     * Get value of the token.
     *
     * @return String value.
     */
    public String value() {
        return value;
    }

    /**
     * Supported types of the token.
     */
    public enum Type {

        /**
         * Token that can be skipped (whitespaces for example).
         */
        SKIP("^\\s+"),

        /**
         * Array.
         */
        ARRAY("^\\[[^]\\[]*]"),

        /**
         * Boolean literal.
         */
        BOOLEAN("^(true|false)"),

        /**
         * String literal.
         */
        STRING("^['\"][^'\"]*['\"]"),

        /**
         * Variable.
         */
        VARIABLE("^\\$\\{(?<varName>[\\w.-]+)}"),

        /**
         * Equality operator.
         */
        EQUALITY_OPERATOR("^(!=|==)"),

        /**
         * Binary logical operator.
         */
        BINARY_LOGICAL_OPERATOR("^(\\|\\||&&)"),

        /**
         * Unary logical operator.
         */
        UNARY_LOGICAL_OPERATOR("^[!]"),

        /**
         * Contains operator.
         */
        CONTAINS_OPERATOR("^contains"),

        /**
         * Parenthesis.
         */
        PARENTHESIS("^[()]");

        private final Pattern pattern;

        Type(String regex) {
            this.pattern = Pattern.compile(regex);
        }

        /**
         * Get the pattern.
         *
         * @return pattern
         */
        public Pattern pattern() {
            return pattern;
        }
    }
}
