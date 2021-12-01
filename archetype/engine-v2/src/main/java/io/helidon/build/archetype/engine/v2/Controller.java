package io.helidon.build.archetype.engine.v2;

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Condition;
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Output;

class Controller extends VisitorAdapter {

    private final Context context;

    private Controller(Output.Visitor outputVisitor,
                       Model.Visitor modelVisitor,
                       Input.Visitor inputVisitor,
                       Context context) {

        super(outputVisitor, modelVisitor, inputVisitor);
        this.context = context;
    }

    @Override
    public VisitResult visitCondition(Condition condition) {
        if (condition.expression().eval(context::lookup)) {
            return VisitResult.CONTINUE;
        }
        return VisitResult.SKIP_SUBTREE;
    }

    static void run(Model.Visitor visitor, Context context, Block block) {
        Controller controller = new Controller(null, visitor, null, context);
        Walker.walk(controller, block);
    }

    static void run(Output.Visitor visitor, Context context, Block block) {
        Controller controller = new Controller(visitor, null, null, context);
        Walker.walk(controller, block);
    }

    static void run(Input.Visitor visitor, Context context, Block block) {
        Controller controller = new Controller(null, null, visitor, context);
        Walker.walk(controller, block);
    }
}
