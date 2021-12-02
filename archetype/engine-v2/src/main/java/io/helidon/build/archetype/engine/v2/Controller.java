package io.helidon.build.archetype.engine.v2;

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Condition;
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Output;

class Controller extends VisitorAdapter<Context> {

    private Controller(Output.Visitor<Context> outputVisitor,
                       Model.Visitor<Context> modelVisitor,
                       Input.Visitor<Context> inputVisitor) {

        super(outputVisitor, modelVisitor, inputVisitor);
    }

    @Override
    public VisitResult visitCondition(Condition condition, Context ctx) {
        if (condition.expression().eval(ctx::lookup)) {
            return VisitResult.CONTINUE;
        }
        return VisitResult.SKIP_SUBTREE;
    }

    static void run(Model.Visitor<Context> visitor, Context context, Block block) {
        Controller controller = new Controller(null, visitor, null);
        Walker.walk(controller, block, context);
    }

    static void run(Output.Visitor<Context> visitor, Context context, Block block) {
        Controller controller = new Controller(visitor, null, null);
        Walker.walk(controller, block, context);
    }

    static void run(Input.Visitor<Context> visitor, Context context, Block block) {
        Controller controller = new Controller(null, null, visitor);
        Walker.walk(controller, block, context);
    }
}
