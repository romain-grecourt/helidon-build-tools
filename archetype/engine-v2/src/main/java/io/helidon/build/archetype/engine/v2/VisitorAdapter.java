package io.helidon.build.archetype.engine.v2;

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Output;

class VisitorAdapter implements Node.Visitor, Block.Visitor {

    private final Output.Visitor outputVisitor;
    private final Model.Visitor modelVisitor;
    private final Input.Visitor inputVisitor;

    VisitorAdapter(Output.Visitor outputVisitor, Model.Visitor modelVisitor, Input.Visitor inputVisitor) {
        this.outputVisitor = outputVisitor;
        this.modelVisitor = modelVisitor;
        this.inputVisitor = inputVisitor;
    }

    @Override
    public VisitResult visitBlock(Block block) {
        return block.accept((Block.Visitor) this);
    }

    @Override
    public VisitResult postVisitBlock(Block block) {
        return block.acceptAfter((Block.Visitor) this);
    }

    @Override
    public VisitResult visitInput(Input input) {
        if (inputVisitor != null) {
            return input.accept(inputVisitor);
        }
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult postVisitInput(Input input) {
        if (inputVisitor != null) {
            return input.acceptAfter(inputVisitor);
        }
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult visitOutput(Output output) {
        if (outputVisitor != null) {
            return output.accept(outputVisitor);
        }
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult postVisitOutput(Output output) {
        if (outputVisitor != null) {
            return output.acceptAfter(outputVisitor);
        }
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult visitModel(Model model) {
        if (modelVisitor != null) {
            return model.accept(modelVisitor);
        }
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult postVisitModel(Model model) {
        if (modelVisitor != null) {
            return model.acceptAfter(modelVisitor);
        }
        return VisitResult.CONTINUE;
    }
}
