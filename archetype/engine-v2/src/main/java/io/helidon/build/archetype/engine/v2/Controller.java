package io.helidon.build.archetype.engine.v2;

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Condition;
import io.helidon.build.archetype.engine.v2.ast.Node;

public class Controller implements Node.Visitor<Context>, Block.Visitor<Void, Void> {

    // TODO polish the mustache model visitor

    @Override
    public Node.VisitResult visitCondition(Condition condition, Context ctx) {
        if (condition.expression().eval(ctx::lookup)) {
            return Node.VisitResult.CONTINUE;
        }
        return Node.VisitResult.SKIP_SUBTREE;
    }

    @Override
    public Node.VisitResult preVisitBlock(Block block, Context ctx) {
        block.accept((Block.Visitor<Void, Void>) this, null);
        return Node.VisitResult.CONTINUE;
    }
}
