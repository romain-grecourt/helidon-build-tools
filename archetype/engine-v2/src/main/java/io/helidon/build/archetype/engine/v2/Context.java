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
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import io.helidon.build.archetype.engine.v2.ast.BooleanInput;
import io.helidon.build.archetype.engine.v2.ast.EnumInput;
import io.helidon.build.archetype.engine.v2.ast.ListInput;
import io.helidon.build.archetype.engine.v2.ast.TextInput;
import io.helidon.build.archetype.engine.v2.prompter.Prompter;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

/**
 * Context node.
 */
interface Context {

    /**
     * Get the context value.
     *
     * @return optional of context value
     */
    Optional<ContextValue<?>> value();

    /**
     * Get the unresolved outputs
     *
     * @return unresolved outputs
     */
    UnresolvedOutputs outputs();

    /**
     * Push a new working directory
     *
     * @param dir relative directory resolved against the current working directory
     * @return new context node
     */
    Context pushd(Path dir);

    /**
     * Pop the current working directory and restore the previous one.
     *
     * @return previous context node
     */
    Context popd();

    /**
     * Get the current working directory for this context node.
     *
     * @return path
     */
    Path cwd();

    /**
     * Resolve a context value in the current context node.
     *
     * @param input    input to be resolved
     * @param prompter prompted to resolve the value
     * @return created child node containing the resolved value
     */
    Context resolve(TextInput input, Prompter prompter);

    /**
     * Resolve a context value in the current context node.
     *
     * @param input    input to be resolved
     * @param prompter prompted to resolve the value
     * @return created child node containing the resolved value
     */
    Context resolve(BooleanInput input, Prompter prompter);

    /**
     * Resolve a context value in the current context node.
     *
     * @param input    input to be resolved
     * @param prompter prompted to resolve the value
     * @return created child node containing the resolved value
     */
    Context resolve(EnumInput input, Prompter prompter);

    /**
     * Resolve a context value in the current context node.
     *
     * @param input    input to be resolved
     * @param prompter prompted to resolve the value
     * @return created child node containing the resolved value
     */
    Context resolve(ListInput input, Prompter prompter);

    /**
     * Lookup a context node by input path.
     *
     * @param inputPath input path
     * @return found context node
     */
    Context lookup(String inputPath);

    /**
     * Lookup a context node by input path.
     *
     * @param inputPath input path
     * @param prefix    input path prefix
     * @return found context node
     */
    Context lookup(String inputPath, String prefix);

    /**
     * Create a new root context node.
     *
     * @param cwd current working directory
     * @return created context node
     */
    static Context create(Path cwd) {
        return new ContextNode(null, null, new UnresolvedOutputs()).pushd(cwd);
    }

    /**
     * Context node.
     */
    final class ContextNode implements Context {

        private final ContextNode parent;
        private final String name;
        private final List<ContextNode> children;
        private final Deque<WorkDirRef> workDirRefs;
        private final UnresolvedOutputs outputs;
        private ContextValue<?> value;

        private ContextNode(ContextNode parent, String name, UnresolvedOutputs outputs) {
            this.parent = parent;
            this.name = name;
            this.outputs = outputs;
            this.children = new LinkedList<>();
            this.workDirRefs = new LinkedList<>();
        }

        @Override
        public UnresolvedOutputs outputs() {
            return outputs;
        }

        @Override
        public Optional<ContextValue<?>> value() {
            return Optional.ofNullable(value);
        }

        @Override
        public Context pushd(Path dir) {
            Objects.requireNonNull(dir, "dir is null");
            Path cwd = workDirRefs.isEmpty() ? dir : workDirRefs.peek().cwd().resolve(dir);
            WorkDirRef ref = new WorkDirRef(this, cwd);
            workDirRefs.push(ref);
            return ref;
        }

        @Override
        public Context popd() {
            return workDirRefs.pop();
        }

        @Override
        public Path cwd() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Context resolve(TextInput input, Prompter prompter) {
            ContextNode ctx = addChild(input.name());
            ctx.value = new TextContextValue(prompter.prompt(input));
            return ctx;
        }

        @Override
        public Context resolve(BooleanInput input, Prompter prompter) {
            ContextNode ctx = addChild(input.name());
            ctx.value = new BooleanContextValue(prompter.prompt(input));
            return ctx;
        }

        @Override
        public Context resolve(EnumInput input, Prompter prompter) {
            ContextNode ctx = addChild(input.name());
            ctx.value = new EnumContextValue(prompter.prompt(input));
            return ctx;
        }

        @Override
        public Context resolve(ListInput input, Prompter prompter) {
            ContextNode ctx = addChild(input.name());
            ctx.value = new ListContextValue(prompter.prompt(input));
            return ctx;
        }

        private ContextNode addChild(String name) {
            ContextNode node = new ContextNode(this, name, outputs);
            this.children.add(node);
            return node;
        }

        @Override
        public Context lookup(String inputPath) {
            String[] segments = inputPath.split("\\.");
            if (segments[0].equals("ROOT")) {
                segments = Arrays.copyOfRange(segments, 1, segments.length);
                if (this.parent == null) {
                    return lookup(segments);
                }
                return lookup(concat(parent.inputPath().toArray(String[]::new), segments));
            }
            if (segments[0].equals("PARENT")) {
                segments = Arrays.copyOfRange(segments, 1, segments.length);
                if (parent == null) {
                    throw new InvalidInputPathException(segments, 1);
                }
                return lookup(concat(segments, parent.name));
            }
            return lookup(segments);
        }

        @Override
        public Context lookup(String inputPath, String prefix) {
            String[] segments = inputPath.split("\\.");
            if (segments[0].equals("ROOT") || segments[0].equals("PARENT")) {
                throw new InvalidInputPathException(segments, 1);
            }
            if (prefix == null) {
                return lookup(segments);
            }
            String[] prefixSegments = prefix.split("\\.");
            int prefixIndex = 0;
            int index = 0;
            int limitPrefix = prefixSegments.length;
            int limitPath = segments.length;
            if (!prefixSegments[0].equals(segments[0])) {
                return lookup(concat(prefixSegments, segments));
            }
            while (prefixIndex < limitPrefix && index < limitPath) {
                if (!segments[index].equals(prefixSegments[prefixIndex]) && prefixIndex != 0) {
                    throw new InvalidInputPathException(segments);
                }
                prefixIndex++;
                index++;
            }
            return lookup(segments);
        }

        private List<String> inputPath() {
            ContextNode node = this;
            LinkedList<String> path = new LinkedList<>();
            while (node.parent != null) {
                path.addFirst(node.name);
                node = node.parent;
            }
            return path;
        }

        private Context lookup(String[] segments) {
            int index = 0;
            int childIndex = 0;
            boolean found = false;

            ContextNode node = this;
            if (!segments[index++].equals(node.name)) {
                throw new InvalidInputPathException(segments, 1);
            }

            while (index < segments.length) {
                while (childIndex < node.children.size()) {
                    if (segments[index].equals(node.children.get(childIndex).name)) {
                        node = node.children.get(childIndex);
                        found = true;
                        break;
                    }
                    childIndex++;
                }
                childIndex = 0;
                if (!found || (node.children.isEmpty() && index < segments.length - 1)) {
                    throw new InvalidInputPathException(segments, index + 1);
                }
                found = false;
                index++;
            }
            return node;
        }

        private static String[] concat(String[] segments1, String... segments2) {
            return Stream.concat(stream(segments1), stream(segments2)).toArray(String[]::new);
        }
    }

    /**
     * Working directory reference.
     */
    final class WorkDirRef implements Context {

        private final Context delegate;
        private final Path cwd;

        private WorkDirRef(Context delegate, Path cwd) {
            this.delegate = delegate;
            this.cwd = cwd;
        }

        @Override
        public UnresolvedOutputs outputs() {
            return delegate.outputs();
        }

        @Override
        public Optional<ContextValue<?>> value() {
            return delegate.value();
        }

        @Override
        public Context pushd(Path dir) {
            return delegate.pushd(dir);
        }

        @Override
        public Context popd() {
            return delegate.popd();
        }

        @Override
        public Path cwd() {
            return cwd;
        }

        @Override
        public Context resolve(TextInput input, Prompter prompter) {
            return delegate.resolve(input, prompter);
        }

        @Override
        public Context resolve(BooleanInput input, Prompter prompter) {
            return delegate.resolve(input, prompter);
        }

        @Override
        public Context resolve(EnumInput input, Prompter prompter) {
            return delegate.resolve(input, prompter);
        }

        @Override
        public Context resolve(ListInput input, Prompter prompter) {
            return delegate.resolve(input, prompter);
        }

        @Override
        public Context lookup(String inputPath) {
            return delegate.lookup(inputPath);
        }

        @Override
        public Context lookup(String inputPath, String prefix) {
            return delegate.lookup(inputPath, prefix);
        }
    }

    /**
     * Context value.
     *
     * @param <T> value type
     */
    abstract class ContextValue<T> {

        private final boolean external;
        private final boolean readOnly;
        private final T value;

        /**
         * Create a new context value.
         *
         * @param external external flag (i.e. passed via env)
         * @param readOnly read-only flag (i.e. passed via env, or set via input value directive)
         * @param value    actual value
         */
        protected ContextValue(boolean external, boolean readOnly, T value) {
            this.external = external;
            this.readOnly = readOnly;
            this.value = Objects.requireNonNull(value, "value is null");
        }

        /**
         * Get the value.
         *
         * @return value
         */
        T value() {
            return value;
        }

        /**
         * Is the value external.
         *
         * @return {@code true} if external, {@code false} otherwise
         */
        boolean external() {
            return external;
        }

        /**
         * Is the value read-only.
         *
         * @return {@code true} if external, {@code false} otherwise
         */
        boolean readOnly() {
            return readOnly;
        }
    }

    /**
     * Text context value.
     */
    final class TextContextValue extends ContextValue<String> {

        private TextContextValue(boolean external, boolean readOnly, String value) {
            super(external, readOnly, value);
        }

        private TextContextValue(String value) {
            this(false, false, value);
        }
    }

    /**
     * Boolean context value.
     */
    final class BooleanContextValue extends ContextValue<Boolean> {

        private BooleanContextValue(boolean external, boolean readOnly, Boolean value) {
            super(external, readOnly, value);
        }

        private BooleanContextValue(Boolean value) {
            this(false, false, value);
        }
    }

    /**
     * Enum context value.
     */
    final class EnumContextValue extends ContextValue<String> {

        private EnumContextValue(boolean external, boolean readOnly, String value) {
            super(external, readOnly, value);
        }

        private EnumContextValue(String value) {
            this(false, false, value);
        }
    }

    /**
     * List context value.
     */
    final class ListContextValue extends ContextValue<List<String>> {

        private ListContextValue(boolean external, boolean readOnly, List<String> value) {
            super(external, readOnly, value);
        }

        private ListContextValue(List<String> value) {
            this(false, false, value);
        }
    }

    /**
     * Invalid input path exception.
     */
    final class InvalidInputPathException extends RuntimeException {

        private InvalidInputPathException(String[] segments) {
            super(String.join(".", segments));
        }

        private InvalidInputPathException(String[] segments, int limit) {
            super(stream(segments).limit(limit).collect(joining(".")));
        }
    }
}
