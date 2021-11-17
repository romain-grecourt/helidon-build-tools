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

import io.helidon.build.archetype.engine.v2.ast.Output;
import io.helidon.build.archetype.engine.v2.ast.Script;
import io.helidon.build.archetype.engine.v2.prompter.Prompter;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

/**
 * Archetype engine (version 2).
 */
public class ArchetypeEngineV2 {

    private final Path entryPoint;
    private final Prompter prompter;
    private final Map<String, String> env;
    private final boolean batch;

    public ArchetypeEngineV2(Path entryPoint, Prompter prompter, Map<String, String> env, boolean batch) {
        this.entryPoint = entryPoint;
        this.prompter = prompter;
        this.env = env;
        this.batch = batch;
    }

    /**
     * Run the archetype.
     *
     * @param directory output directory
     */
    public void generate(File directory) {
        Context ctx = evalInput();
        Output output = evalOutput(ctx);

        // TODO generate (get code from OutputGenerator)
    }

    private Context evalInput() {
        // TODO populate context from env
        Context ctx = Context.create(entryPoint.getParent());
        Script script = ScriptLoader.load(entryPoint);
        script.accept(new InputInterpreter(prompter, batch), ctx);
        return ctx;
    }

    private Output evalOutput(Context ctx) {
        // TODO sub-class output builder since the fields are private and have no accessor
        // we need to mutate the underlying data to merge while visiting
//        Output.Builder builder = Output.builder();
//        ctx.outputs().accept(new OutputInterpreter(), builder);
//        return builder.build();
        return null;
    }
}
