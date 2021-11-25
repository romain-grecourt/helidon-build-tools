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

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

/**
 * Archetype engine (version 2).
 */
public class ArchetypeEngineV2 {

    private final Path entryPoint;
    private final Input.Visitor<Void, Context> prompter;
    private final Map<String, String> env;

    public ArchetypeEngineV2(Path entryPoint, Input.Visitor<Void, Context> prompter, Map<String, String> env) {
        this.entryPoint = entryPoint;
        this.prompter = prompter;
        // TODO init context with entry point and env
        this.env = env;
    }

    /**
     * Run the archetype.
     *
     * @param directory output directory
     */
    public void generate(File directory) {
        Context ctx = evalInput();
//        Output output = evalOutput(ctx);
        // TODO generate (get code from OutputGenerator)
    }

    private Context evalInput() {
        // TODO populate context from env
        Context ctx = Context.create(entryPoint.getParent());
        Script script = ScriptLoader.load(entryPoint);
//        script.accept(new InputInterpreter(prompter, batch), ctx);
        return ctx;
    }

//    private Output evalOutput(Context ctx) {
        // TODO just do this as another full path on the tree
        // i.e remove unresolvedOutput from the context
        // the 2nd pass will need to accumulate the global model, list of file and templates
        // and do the rendering at the end

        // TODO sub-class output builder since the fields are private and have no accessor
        // we need to mutate the underlying data to merge while visiting
//        Output.Builder builder = Output.builder();
//        ctx.outputs().accept(new OutputInterpreter(), builder);
//        return builder.build();
//        return null;
//    }
}
