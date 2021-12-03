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

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Condition;
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Output;
import io.helidon.build.archetype.engine.v2.ast.Preset;

import static io.helidon.build.archetype.engine.v2.MergedModel.resolveModel;

/**
 * Controller.
 */
final class Controller extends VisitorAdapter<Context> {

    // TODO unit test

    /**
     * Create a new controller instance.
     *
     * @param inputVisitor input visitor
     * @param modelVisitor model visitor
     */
    Controller(Input.Visitor<Context> inputVisitor, Model.Visitor<Context> modelVisitor) {
        super(inputVisitor, null, modelVisitor);
    }

    /**
     * Create a new controller instance.
     *
     * @param inputVisitor  input visitor
     * @param outputVisitor output visitor
     */
    Controller(Input.Visitor<Context> inputVisitor, Output.Visitor<Context> outputVisitor) {
        super(inputVisitor, outputVisitor, null);
    }

    /**
     * Create a new controller instance.
     *
     * @param inputVisitor input visitor
     */
    Controller(Input.Visitor<Context> inputVisitor) {
        super(inputVisitor, null, null);
    }

    @Override
    public VisitResult visitPreset(Preset preset, Context ctx) {
        ctx.put(preset.path(), preset.value());
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult visitBlock(Block block, Context ctx) {
        if (block.blockKind() == Block.Kind.CD) {
            ctx.pushCwd(block.scriptPath().getParent());
            return VisitResult.CONTINUE;
        }
        return super.visitBlock(block, ctx);
    }

    @Override
    public VisitResult postVisitBlock(Block block, Context ctx) {
        if (block.blockKind() == Block.Kind.CD) {
            ctx.popCwd();
            return VisitResult.CONTINUE;
        }
        return super.postVisitBlock(block, ctx);
    }

    @Override
    public VisitResult visitCondition(Condition condition, Context ctx) {
        if (condition.expression().eval(ctx::lookup)) {
            return VisitResult.CONTINUE;
        }
        return VisitResult.SKIP_SUBTREE;
    }

    /**
     * Resolve inputs.
     *
     * @param inputResolver input resolver
     * @param block         block
     * @param context       context
     */
    static void resolveInputs(InputResolver inputResolver, Block block, Context context) {
        Walker.walk(new Controller(inputResolver), block, context);
    }

    /**
     * Generate output.
     *
     * @param inputResolver input resolver
     * @param block         block
     * @param context       context
     * @param directory     directory
     */
    static void generateOutput(InputResolver inputResolver, Block block, Context context, Path directory) {
        Generator generator = new Generator(block, directory, b -> resolveModel(inputResolver, b, context));
        Walker.walk(new Controller(inputResolver, generator), block, context);
    }
}
