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
import java.util.Objects;

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Condition;
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Output;
import io.helidon.build.archetype.engine.v2.ast.Preset;

/**
 * Controller.
 */
class Controller {

    private final InputResolver inputResolver;
    private final Block block;
    private final Context context;

    // TODO unit test

    private Controller(InputResolver inputResolver, Block block, Context context) {
        this.inputResolver = Objects.requireNonNull(inputResolver, "inputResolver is null");
        this.block = block;
        this.context = context;
    }

    /**
     * Create a new controller.
     *
     * @param inputResolver input resolver
     * @param block         block
     * @param context       context
     * @return controller
     */
    static Controller create(InputResolver inputResolver, Block block, Context context) {
        return new Controller(inputResolver, block, context);
    }

    /**
     * Create a new controller.
     *
     * @param block   block
     * @param context context
     * @return controller
     */
    static Controller create(Block block, Context context) {
        return new Controller(new InputResolver(), block, context);
    }

    /**
     * Resolve the inputs.
     */
    void resolveInputs() {
        Walker.walk(new VisitorImpl(inputResolver, null, null), block, context);
    }

    /**
     * Generate.
     *
     * @param outputDir output directory
     */
    void generate(Path outputDir) {
        Generator generator = new Generator(block, outputDir, this::resolveModel);
        Walker.walk(new VisitorImpl(inputResolver, generator, null), block, context);
    }

    private MergedModel resolveModel(Block block) {
        ModelResolver modelResolver = new ModelResolver();
        Walker.walk(new VisitorImpl(inputResolver, null, modelResolver), block, context);
        return modelResolver.model();
    }

    private static final class VisitorImpl extends VisitorAdapter<Context> {

        VisitorImpl(Input.Visitor<Context> inputVisitor,
                    Output.Visitor<Context> outputVisitor,
                    Model.Visitor<Context> modelVisitor) {

            super(inputVisitor, outputVisitor, modelVisitor);
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
    }
}
