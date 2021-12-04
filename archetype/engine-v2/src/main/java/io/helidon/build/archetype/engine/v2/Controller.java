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
import io.helidon.build.archetype.engine.v2.ast.Condition;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Output;
import io.helidon.build.archetype.engine.v2.ast.Preset;

import java.util.Objects;

/**
 * Controller.
 * Context aware visitor adapter with convenience methods to perform full AST traversal with complete flow control.
 * Always uses an implementation {@link InputResolver} in order to control the flow of input nodes.
 */
final class Controller extends VisitorAdapter<Context> {

    private Controller(InputResolver inputResolver,
                       Output.Visitor<Context> outputVisitor,
                       Model.Visitor<Context> modelVisitor) {

        super(inputResolver, outputVisitor, modelVisitor);
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
     * Walk.
     *
     * @param inputResolver input resolver
     * @param block         block, must be non {@code null}
     * @param context       context, must be non {@code null}
     * @throws NullPointerException if context or block is {@code null}
     */
    static void walk(InputResolver inputResolver, Block block, Context context) {
        walk(inputResolver, null, null, block, context);
    }

    /**
     * Walk.
     *
     * @param inputResolver input resolver
     * @param outputVisitor output visitor
     * @param block         block, must be non {@code null}
     * @param context       context, must be non {@code null}
     * @throws NullPointerException if context or block is {@code null}
     */
    static void walk(InputResolver inputResolver, Output.Visitor<Context> outputVisitor, Block block, Context context) {
        walk(inputResolver, outputVisitor, null, block, context);
    }

    /**
     * Walk.
     *
     * @param inputResolver input resolver
     * @param modelVisitor  model visitor
     * @param block         block, must be non {@code null}
     * @param context       context, must be non {@code null}
     * @throws NullPointerException if context or block is {@code null}
     */
    static void walk(InputResolver inputResolver, Model.Visitor<Context> modelVisitor, Block block, Context context) {
        walk(inputResolver, null, modelVisitor, block, context);
    }

    /**
     * Walk using a {@link Batch} input resolver.
     *
     * @param block   block, must be non {@code null}
     * @param context context, must be non {@code null}
     * @throws NullPointerException if context or block is {@code null}
     */
    static void walk(Block block, Context context) {
        walk(new Batch(), null, null, block, context);
    }

    /**
     * Walk using a {@link Batch} input resolver.
     *
     * @param outputVisitor output visitor
     * @param block         block, must be non {@code null}
     * @param context       context, must be non {@code null}
     * @throws NullPointerException if context or block is {@code null}
     */
    static void walk(Output.Visitor<Context> outputVisitor, Block block, Context context) {
        walk(new Batch(), outputVisitor, null, block, context);
    }

    /**
     * Walk using a {@link Batch} input resolver.
     *
     * @param modelVisitor model visitor
     * @param block        block, must be non {@code null}
     * @param context      context, must be non {@code null}
     * @throws NullPointerException if context or block is {@code null}
     */
    static void walk(Model.Visitor<Context> modelVisitor, Block block, Context context) {
        walk(new Batch(), null, modelVisitor, block, context);
    }

    /**
     * Walk.
     *
     * @param inputResolver input resolver
     * @param outputVisitor output visitor
     * @param modelVisitor  model visitor
     * @param block         block, must be non {@code null}
     * @param context       context, must be non {@code null}
     * @throws NullPointerException if context or block is {@code null}
     */
    static void walk(InputResolver inputResolver,
                     Output.Visitor<Context> outputVisitor,
                     Model.Visitor<Context> modelVisitor,
                     Block block,
                     Context context) {

        Objects.requireNonNull(context, "context is null");
        Controller controller = new Controller(inputResolver, outputVisitor, modelVisitor);
        Walker.walk(controller, block, context, context::cwd);
    }
}
