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
package io.helidon.build.archetype.engine.v2;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.build.archetype.engine.v2.ast.Value;
import io.helidon.build.archetype.engine.v2.ast.ValueTypes;
import io.helidon.build.common.GenericType;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Context.
 */
public final class Context {

    private static final Map<String, String> NULL_MAP = Collections.emptyMap();
    private static final Path NULL_PATH = Path.of("");

    private final Map<String, Value> defaults = new HashMap<>();
    private final Map<String, ContextValue> values = new HashMap<>();
    private final Deque<Path> directories = new ArrayDeque<>();
    private final Deque<String> inputs = new ArrayDeque<>();

    private Context(Path cwd, Map<String, String> externalValues, Map<String, String> externalDefaults) {
        externalDefaults.forEach((k, v) -> defaults.put(k, new ExternalValue(v)));
        externalValues.forEach((k, v) -> values.put(k, new ExternalValue(v)));
        directories.push(cwd);
    }

    /**
     * Push a new working directory.
     *
     * @param dir directory
     */
    public void pushCwd(Path dir) {
        directories.push(dir.toAbsolutePath());
    }

    /**
     * Pop the current working directory.
     *
     * @throws IllegalStateException if the current cwd is the initial one
     */
    public void popCwd() {
        if (directories.size() == 1) {
            throw new IllegalStateException("Cannot pop the initial working directory");
        }
        directories.pop();
    }

    /**
     * Get the current working directory.
     *
     * @return path
     */
    public Path cwd() {
        return directories.peek();
    }

    /**
     * Get an external default value.
     *
     * @param name input name
     * @return value
     */
    public Value defaultValue(String name) {
        return defaults.get(path(name));
    }

    /**
     * Put a value in the context.
     *
     * @param path  input path
     * @param value value
     */
    public void put(String path, Value value) {
        ContextValue current = values.get(path);
        if (current == null) {
            values.put(path, new ContextValue(value, true));
        } else {
            String fullPath = inputs.stream().map(i -> i + "=" + values.get(i).unwrap()).collect(joining(";"));
            String peek = inputs.peek();
            String name = peek == null ? path : path.substring(peek.length());
            if (current.external) {
                throw new IllegalStateException(String.format(
                        "%s requires %s=%s", fullPath, name, value.unwrap()));
            } else if (current.internal) {
                throw new IllegalStateException(String.format(
                        "Archetype error, internal value '%s' already set", path));
            } else {
                throw new IllegalStateException(String.format(
                        "Archetype error, value '%s' already set", path));
            }
        }
    }

    /**
     * Push the given input name if it already has a value.
     *
     * @param name input name
     * @return {@code true} if the input path was pushed, {@code false} otherwise
     */
    public boolean pushIfPresent(String name) {
        String path = path(name);
        if (values.get(path) != null) {
            inputs.push(path);
            return true;
        }
        return false;
    }

    /**
     * Push a new input value.
     *
     * @param name  input name
     * @param value value
     */
    public void push(String name, Value value) {
        String path = path(name);
        if (value != null) {
            values.put(path, new ContextValue(value, false));
            inputs.push(path);
        } else if (values.get(path) != null) {
            inputs.push(path);
        }
    }

    /**
     * Pop the current input.
     */
    public void pop() {
        inputs.pop();
    }

    /**
     * Lookup a context value
     *
     * @param path input path
     * @return value, {@code null} if not found
     */
    public Value lookup(String path) {
        String current = inputs.peek();
        if (current == null) {
            current = "";
        }
        String key;
        if (path.startsWith("ROOT.")) {
            key = path.substring(5);
        } else {
            int offset = 0;
            int level = 0;
            while (path.startsWith("PARENT.", offset)) {
                offset += 7;
                level++;
            }
            if (offset > 0) {
                path = path.substring(offset);
            } else {
                level = 1;
            }
            int index;
            for (index = current.length() - 1; index >= 0 && level > 0; index--) {
                if (current.charAt(index) == '.') {
                    level--;
                }
            }
            if (index > 0) {
                key = current.substring(0, index + 1) + "." + path;
            } else {
                key = path;
            }
        }
        return values.get(key);
    }

    /**
     * Compute the path for a given input name.
     *
     * @param name name
     * @return input path
     */
    public String path(String name) {
        String path = inputs.peek();
        if (path != null) {
            path += "." + name;
        } else {
            path = name;
        }
        return path;
    }

    /**
     * Create a new context.
     *
     * @return context
     */
    static Context create() {
        return create(NULL_PATH, NULL_MAP, NULL_MAP);
    }

    /**
     * Create a new context.
     *
     * @param cwd initial working directory
     * @return context
     */
    public static Context create(Path cwd) {
        return new Context(cwd, NULL_MAP, NULL_MAP);
    }

    /**
     * Create a new context.
     *
     * @param cwd              initial working directory
     * @param externalValues   external values
     * @param externalDefaults external defaults
     * @return context
     */
    public static Context create(Path cwd, Map<String, String> externalValues, Map<String, String> externalDefaults) {
        return new Context(cwd, externalValues, externalDefaults);
    }

    /**
     * Context value.
     */
    private static class ContextValue extends Value {

        private final boolean internal;
        private final boolean external;

        private ContextValue(Object value, GenericType<?> type) {
            super(value, type);
            internal = false;
            external = true;
        }

        private ContextValue(Value value, boolean internal) {
            super(value.unwrap(), value.type());
            this.internal = internal;
            this.external = true;
        }
    }

    private static final class ExternalValue extends ContextValue {

        private ExternalValue(String value) {
            super(value, ValueTypes.STRING);
        }

        @Override
        public Boolean asBoolean() {
            return Boolean.valueOf((String) value);
        }

        @Override
        public Integer asInt() {
            return Integer.parseInt((String) value);
        }

        @Override
        public List<String> asList() {
            return Arrays.stream(((String) value).split(","))
                         .map(String::trim)
                         .collect(toList());
        }
    }
}
