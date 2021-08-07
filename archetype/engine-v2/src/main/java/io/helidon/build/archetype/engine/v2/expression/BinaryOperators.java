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
package io.helidon.build.archetype.engine.v2.expression;

import java.util.Arrays;

import static io.helidon.build.archetype.engine.v2.expression.ValueKind.*;

enum BinaryOperators implements BinaryOperator {

    EQUAL() {
        @Override
        public boolean evaluate(Value left, Value right) {
            switch (left.valueKind()) {
                case STRING_ARRAY:
                    return Arrays.equals(left.as(String[].class), right.as(String[].class));
                case STRING:
                case BOOLEAN:
                    return left.equals(right);
                default:
                    throw new EvaluationException(INVALID_TYPE, left.valueKind(), this);
            }
        }
    },
    NOT_EQUAL() {
        @Override
        public boolean evaluate(Value left, Value right) {
            switch (left.valueKind()) {
                case STRING_ARRAY:
                    return !Arrays.equals(left.as(String[].class), right.as(String[].class));
                case STRING:
                case BOOLEAN:
                    return !left.equals(right);
                default:
                    throw new EvaluationException(INVALID_TYPE, left.valueKind(), this);
            }
        }
    },
    AND() {
        @Override
        public boolean evaluate(Value left, Value right) {
            if (left.valueKind() == BOOLEAN) {
                return left.as(Boolean.class) && right.as(Boolean.class);
            }
            throw new EvaluationException(INVALID_TYPE, left.valueKind(), this);
        }
    },
    OR() {
        @Override
        public boolean evaluate(Value left, Value right) {
            if (left.valueKind() == BOOLEAN) {
                return left.as(Boolean.class) || right.as(Boolean.class);
            }
            throw new EvaluationException(INVALID_TYPE, left.valueKind(), this);
        }
    },
    XOR() {
        @Override
        public boolean evaluate(Value left, Value right) {
            if (left.valueKind() == BOOLEAN) {
                return left.as(Boolean.class) ^ right.as(Boolean.class);
            }
            throw new EvaluationException(INVALID_TYPE, left.valueKind(), this);
        }
    },
    CONTAINS() {
        @Override
        public boolean evaluate(Value left, Value right) {
            if (left.valueKind() == STRING_ARRAY) {
                return Arrays.asList(left.as(String[].class)).contains(right.as(String.class));
            }
            throw new EvaluationException(INVALID_TYPE, left.valueKind(), this);
        }
    };

    private static final String INVALID_TYPE = "Invalid value type: %s, operator: %s";
}
