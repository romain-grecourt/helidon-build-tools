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

import java.util.function.Function;

import static io.helidon.build.archetype.engine.v2.expression.ValueKind.*;

final class Literal<T> implements Value {

    private final ValueKind kind;
    private final T value;

    private Literal(ValueKind kind, T value) {
        this.kind = kind;
        this.value = value;
    }

    @Override
    public ValueKind valueKind() {
        return kind;
    }

    public T get() {
        return value;
    }

    static Literal<Boolean> of(boolean value) {
        return new Literal<>(BOOLEAN, value);
    }

    static Literal<String> of(String value) {
        return new Literal<>(STRING, value);
    }

    @Override
    public Value eval(Function<String, String> resolver) throws EvaluationException {
        return this;
    }

    @Override
    public ExpressionKind expressionKind() {
        return ExpressionKind.VALUE;
    }
}
