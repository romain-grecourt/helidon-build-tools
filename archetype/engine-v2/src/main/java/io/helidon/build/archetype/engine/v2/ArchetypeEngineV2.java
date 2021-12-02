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

import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Script;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Map;

/**
 * Archetype engine (v2).
 */
public class ArchetypeEngineV2 {

    private final Path cwd;
    private final InputResolver inputResolver;

    /**
     * Create a new archetype engine.
     *
     * @param fs            archetype file system
     * @param inputResolver input resolver
     */
    public ArchetypeEngineV2(FileSystem fs, InputResolver inputResolver) {
        this.cwd = fs.getPath("/");
        this.inputResolver = inputResolver;
    }

    /**
     * Run the archetype.
     *
     * @param env       initial context properties
     * @param directory output directory
     */
    public void generate(Map<String, String> env, Path directory) {
        Script entrypoint = ScriptLoader.load(cwd.resolve("flavor.xml"));
        Context context = Context.create(cwd, env);
        Controller.run(inputResolver, context, entrypoint.body());
        Controller.run(new OutputGenerator(directory), context, entrypoint.body());
    }
}
