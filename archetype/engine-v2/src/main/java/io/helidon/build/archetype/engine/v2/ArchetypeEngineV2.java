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

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Script;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Map;

/**
 * Archetype engine (v2).
 */
public class ArchetypeEngineV2 {

    private final Path cwd;

    /**
     * Create a new archetype engine.
     *
     * @param fs archetype file system
     */
    public ArchetypeEngineV2(FileSystem fs) {
        this.cwd = fs.getPath("/");
    }

    /**
     * Run the archetype.
     *
     * @param inputResolver    input resolver
     * @param externalValues   external values
     * @param externalDefaults external defaults
     * @param directory        output directory
     */
    public void generate(InputResolver inputResolver,
                         Map<String, String> externalValues,
                         Map<String, String> externalDefaults,
                         Path directory) {

        // TODO entry point should be 'main.xml'
        Script entrypoint = ScriptLoader.load(cwd.resolve("flavor.xml"));
        Block block = entrypoint.body();
        Context context = Context.create(cwd, externalValues, externalDefaults);
        Controller.run(inputResolver, context, block);
        Controller.run(new Generator(block, directory), context, block);
    }
}
