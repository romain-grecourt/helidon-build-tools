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
import java.util.function.Function;

/**
 * Archetype engine (v2).
 */
public class ArchetypeEngineV2 {

    private final Path cwd;
    private final Input.Visitor inputResolver;
    private final Context context;

    /**
     * Create a new archetype engine.
     *
     * @param fs       archetype file system
     * @param resolver input resolver factory
     * @param env      initial context environment
     */
    public ArchetypeEngineV2(FileSystem fs, Function<Context, Input.Visitor> resolver, Map<String, String> env) {
        cwd = fs.getPath("/");
        context = Context.create(cwd, env);
        inputResolver = resolver.apply(context);
    }

    /**
     * Run the archetype.
     *
     * @param directory output directory
     */
    public void generate(Path directory) {
        Script entrypoint = ScriptLoader.load(cwd.resolve("flavor.xml"));
        Controller.run(inputResolver, context, entrypoint.body());
        Controller.run(new OutputGenerator(directory), context, entrypoint.body());
    }
}
