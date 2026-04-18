/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.archetype.engine.v2.Value.Type;

final class ScriptIndexer implements Node.Visitor {
    private final Map<String, Set<Node>> declaredValues = new HashMap<>();
    private final Map<String, Set<Node>> definedRefs = new HashMap<>();
    private final Map<String, Type> refTypes = new HashMap<>();
    private final Set<String> textInputRefs = new HashSet<>();

    private final Scope rootScope;
    private final Deque<Scope> scopes = new ArrayDeque<>();
    private int nextId;

    ScriptIndexer(Scope rootScope) {
        this.rootScope = rootScope;
    }

    void index(Node node) {
        scopes.push(rootScope);
        node.visit(this);
        scopes.pop();
    }

    Set<Node> declaredValues(String key) {
        return declaredValues.getOrDefault(key, Set.of());
    }

    Set<Node> definedRefs(String key) {
        return definedRefs.getOrDefault(key, Set.of());
    }

    Type refType(String key) {
        return refTypes.getOrDefault(key, Type.EMPTY);
    }

    boolean textInputRef(String key) {
        return textInputRefs.contains(key);
    }

    @Override
    public boolean visit(Node node) {
        Scope currentScope = scopes.peek();
        node.id(nextId++);
        Scope childScope = currentScope;
        switch (node.kind()) {
            case INPUT_BOOLEAN:
            case INPUT_ENUM:
            case INPUT_LIST:
            case INPUT_TEXT:
                childScope = currentScope.getOrCreate(node);
                definedRefs.computeIfAbsent(childScope.key(), k -> new LinkedHashSet<>()).add(node);
                refTypes.putIfAbsent(childScope.key(), node.kind().valueType());
                if (node.kind() == Node.Kind.INPUT_TEXT) {
                    textInputRefs.add(childScope.key());
                }
                break;
            case PRESET_BOOLEAN:
            case PRESET_ENUM:
            case PRESET_LIST:
            case PRESET_TEXT:
            case VARIABLE_BOOLEAN:
            case VARIABLE_ENUM:
            case VARIABLE_LIST:
            case VARIABLE_TEXT:
                String key = currentScope.definitionKey(node.attribute("path").getString());
                definedRefs.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(node);
                declaredValues.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(node);
                refTypes.putIfAbsent(key, node.kind().valueType());
                break;
            case INPUT_OPTION:
            default:
                break;
        }
        scopes.push(childScope);
        return true;
    }

    @Override
    public void postVisit(Node node) {
        scopes.pop();
    }
}
