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

import java.util.Objects;
import java.util.function.Function;

final class Variable implements Value {

    private final String name;

    Variable(String name) {
        this.name = Objects.requireNonNull(name, "variable name is null");
    }

    String name() {
        return name;
    }

    @Override
    public Value eval(Function<String, String> resolver) throws EvaluationException {
        String value = resolver.apply(name);
        if (value == null) {
            throw new EvaluationException("Unresolved variable: %s", name);
        }
        return Literal.of(value);
    }

    @Override
    public ExpressionKind expressionKind() {
        return ExpressionKind.VARIABLE;
    }

    @Override
    public ValueKind valueKind() {
        return ValueKind.VARIABLE;
    }

    @Override
    public Object get() {
        return null;
    }
}
