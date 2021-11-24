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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

import io.helidon.build.archetype.engine.v2.ast.Value;
import io.helidon.build.common.GenericType;

/**
 * Context.
 */
public final class Context {

    private final Map<String, ContextValue> values = new HashMap<>();
    private final LinkedList<Path> directories = new LinkedList<>();
    private final LinkedList<String> paths = new LinkedList<>();

    private Context(Path cwd) {
        directories.push(cwd);
    }

    /**
     * Push a new working directory.
     *
     * @param dir directory
     */
    public void pushDir(Path dir) {
        directories.push(cwd().resolve(dir).toAbsolutePath());
    }

    /**
     * Pop the current working directory.
     */
    public void popDir() {
        directories.pop();
    }

    /**
     * Get the current working directory.
     *
     * @return path
     */
    public Path cwd() {
        Path cwd = directories.peek();
        if (cwd == null) {
            throw new IllegalStateException("No current working directory");
        }
        return cwd;
    }

    /**
     * Push a new value.
     *
     * @param name  input name
     * @param value value
     * @throws IllegalArgumentException if the key is invalid
     */
    public void pushInput(String name, Value value) {
        if (value == null) {
            return;
        }
        if (name.indexOf('.') >= 0) {
            throw new IllegalArgumentException("Invalid name: " + name);
        }
        String path;
        if (paths.isEmpty()) {
            path = name;
        } else {
            path = paths.peek() + "." + name;
        }
        values.put(path, new ContextValue(value, false, false));
        paths.push(path);
    }

    /**
     * Pop the current input.
     */
    public void popInput() {
        paths.pop();
    }

    /**
     * Lookup a context value
     *
     * @param path input path
     * @return found context node
     */
    public ContextValue lookup(String path) {
        String current = paths.peek();
        if (current == null) {
            return null;
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
     * Create a new context.
     *
     * @param cwd initial working directory
     * @return context
     */
    public static Context create(Path cwd) {
        return new Context(cwd);
    }

    /**
     * Context value.
     */
    public static final class ContextValue implements Value {

        private final boolean external;
        private final boolean readOnly;
        private final Value value;

        private ContextValue(Value value, boolean external, boolean readOnly) {
            this.value = Objects.requireNonNull(value, "value is null");
            this.external = external;
            this.readOnly = readOnly;
        }

        /**
         * Is the value external.
         *
         * @return {@code true} if external, {@code false} otherwise
         */
        public boolean external() {
            return external;
        }

        /**
         * Is the value read-only.
         *
         * @return {@code true} if external, {@code false} otherwise
         */
        public boolean readOnly() {
            return readOnly;
        }

        @Override
        public GenericType<?> type() {
            return value.type();
        }

        @Override
        public <T> T as(GenericType<T> type) {
            return value.as(type);
        }
    }
}
