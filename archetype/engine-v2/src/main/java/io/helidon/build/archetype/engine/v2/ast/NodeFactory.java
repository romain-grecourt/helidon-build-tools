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

import java.nio.file.Path;

/**
 * Node factory.
 */
public final class NodeFactory {

    /**
     * Create a new block builder.
     *
     * @param location location
     * @param position position
     * @param kind     kind
     * @return builder
     */
    public static Block.Builder<?, ?> newBlock(Path location, Position position, Block.Kind kind) {
        return new Block.Builder<>(location, position, kind);
    }

    /**
     * Create a new preset builder.
     *
     * @param location location
     * @param position position
     * @param kind     kind
     * @return builder
     */
    public static Preset.Builder newPreset(Path location, Position position, Input.Kind kind) {
        return new Preset.Builder(location, position, kind);
    }

    /**
     * Create a new input builder.
     *
     * @param location location
     * @param position position
     * @param kind     kind
     * @return builder
     */
    public static Input.Builder newInput(Path location, Position position, Input.Kind kind) {
        return new Input.Builder(location, position, kind);
    }

    /**
     * Create a new executable builder.
     *
     * @param location location
     * @param position position
     * @param kind     kind
     * @return builder
     */
    public static Executable.Builder newExecutable(Path location, Position position, Executable.Kind kind) {
        return new Executable.Builder(location, position, kind);
    }

    /**
     * Create a new invocation builder.
     *
     * @param location location
     * @param position position
     * @param kind     kind
     * @return builder
     */
    public static Invocation.Builder newInvocation(Path location, Position position, Invocation.Kind kind) {
        return new Invocation.Builder(location, position, kind);
    }

    /**
     * Create a new output builder.
     *
     * @param location location
     * @param position position
     * @param kind     kind
     * @return builder
     */
    public static Output.Builder newOutput(Path location, Position position, Output.Kind kind) {
        return new Output.Builder(location, position, kind);
    }

    /**
     * Create a new model builder.
     *
     * @param location location
     * @param position position
     * @param kind     kind
     * @return builder
     */
    public static Model.Builder newModel(Path location, Position position, Model.Kind kind) {
        return new Model.Builder(location, position, kind);
    }

    /**
     * Create a new data builder.
     *
     * @param location location
     * @param position position
     * @param kind     kind
     * @return builder
     */
    public static Data.Builder newData(Path location, Position position, Data.Kind kind) {
        return new Data.Builder(location, position, kind);
    }

    /**
     * Create a new if statement builder.
     *
     * @param location location
     * @param position position
     * @return builder
     */
    public static IfStatement.Builder newIf(Path location, Position position) {
        return new IfStatement.Builder(location, position);
    }

    /**
     * Create a new script builder.
     *
     * @param location location
     * @param position position
     * @return builder
     */
    public static Script.Builder newScript(Path location, Position position) {
        return new Script.Builder(location, position);
    }

    private NodeFactory() {
    }
}
