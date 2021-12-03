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
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Output;

/**
 * Visitor adapter.
 *
 * @param <A> visitor argument type
 */
class VisitorAdapter<A> implements Node.Visitor<A>, Block.Visitor<A> {

    private Input.Visitor<A> inputVisitor;
    private Output.Visitor<A> outputVisitor;
    private Model.Visitor<A> modelVisitor;

    /**
     * Create a new adapter for the given input visitor.
     *
     * @param inputVisitor  input visitor
     * @param outputVisitor output visitor
     * @param modelVisitor  model visitor
     */
    VisitorAdapter(Input.Visitor<A> inputVisitor, Output.Visitor<A> outputVisitor, Model.Visitor<A> modelVisitor) {
        this.inputVisitor = inputVisitor;
        this.outputVisitor = outputVisitor;
        this.modelVisitor = modelVisitor;
    }

    /**
     * Set the input visitor.
     *
     * @param inputVisitor visitor
     */
    protected void inputVisitor(Input.Visitor<A> inputVisitor) {
        this.inputVisitor = inputVisitor;
    }

    /**
     * Set the output visitor.
     *
     * @param outputVisitor visitor
     */
    protected void outputVisitor(Output.Visitor<A> outputVisitor) {
        this.outputVisitor = outputVisitor;
    }

    /**
     * Set the model visitor.
     *
     * @param modelVisitor visitor
     */
    protected void modelVisitor(Model.Visitor<A> modelVisitor) {
        this.modelVisitor = modelVisitor;
    }

    @Override
    public VisitResult visitBlock(Block block, A arg) {
        return block.accept((Block.Visitor<A>) this, arg);
    }

    @Override
    public VisitResult postVisitBlock(Block block, A arg) {
        return block.acceptAfter((Block.Visitor<A>) this, arg);
    }

    @Override
    public VisitResult visitInput(Input input, A arg) {
        if (inputVisitor != null) {
            return input.accept(inputVisitor, arg);
        }
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult postVisitInput(Input input, A arg) {
        if (inputVisitor != null) {
            return input.acceptAfter(inputVisitor, arg);
        }
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult visitOutput(Output output, A arg) {
        if (outputVisitor != null) {
            return output.accept(outputVisitor, arg);
        }
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult postVisitOutput(Output output, A arg) {
        if (outputVisitor != null) {
            return output.acceptAfter(outputVisitor, arg);
        }
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult visitModel(Model model, A arg) {
        if (modelVisitor != null) {
            return model.accept(modelVisitor, arg);
        }
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult postVisitModel(Model model, A arg) {
        if (modelVisitor != null) {
            return model.acceptAfter(modelVisitor, arg);
        }
        return VisitResult.CONTINUE;
    }
}
