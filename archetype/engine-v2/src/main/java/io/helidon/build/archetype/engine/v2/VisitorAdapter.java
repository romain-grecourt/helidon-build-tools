package io.helidon.build.archetype.engine.v2;

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Output;

class VisitorAdapter<A> implements Node.Visitor<A>, Block.Visitor<A> {

    private final Output.Visitor<A> outputVisitor;
    private final Model.Visitor<A> modelVisitor;
    private final Input.Visitor<A> inputVisitor;

    VisitorAdapter(Output.Visitor<A> outputVisitor, Model.Visitor<A> modelVisitor, Input.Visitor<A> inputVisitor) {
        this.outputVisitor = outputVisitor;
        this.modelVisitor = modelVisitor;
        this.inputVisitor = inputVisitor;
    }

    @Override
    public VisitResult visitBlock(Block block, A arg) {
        return block.accept((Block.Visitor) this, arg);
    }

    @Override
    public VisitResult postVisitBlock(Block block, A arg) {
        return block.acceptAfter((Block.Visitor) this, arg);
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
