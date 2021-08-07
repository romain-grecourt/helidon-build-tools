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

import java.util.HashMap;
import java.util.Map;

public enum ValueKind {

    BOOLEAN(Boolean.class),
    STRING(String.class),
    STRING_ARRAY(String[].class),
    VARIABLE(null);

    private static final Map<Class<?>, ValueKind> TYPES;
    private final Class<?> type;

    ValueKind(Class<?> type) {
        this.type = type;
    }

    static {
        TYPES = new HashMap<>();
        for (ValueKind type : ValueKind.values()) {
            if (type.type != null) {
                TYPES.put(type.type, type);
            }
        }
    }

    static ValueKind valueOf(Class<?> type) {
        ValueKind kind = TYPES.get(type);
        if (kind == null) {
            throw new IllegalArgumentException("Unsupported type: " + type);
        }
        return kind;
    }
}
