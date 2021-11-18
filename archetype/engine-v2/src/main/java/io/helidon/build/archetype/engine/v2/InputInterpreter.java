package io.helidon.build.archetype.engine.v2;

import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.prompter.Prompter;

/**
 * Input interpreter.
 */
class InputInterpreter implements Node.Visitor<Context, Void> {

    private final Prompter prompter;
    private final boolean batch;

    InputInterpreter(Prompter prompter, boolean batch) {
        this.prompter = prompter;
        this.batch = batch;
    }

//    @Override
//    public Void visit(Script script, Context ctx) {
//        script.accept(this, ctx);
//        return null;
//    }
//
//    @Override
//    public Void visit(Block block, Context ctx) {
//        block.statements().forEach(stmt -> stmt.accept(this, ctx));
//        return null;
//    }
//
//    @Override
//    public Void visit(Step step, Context ctx) {
//        step.accept(this, ctx);
//        return null;
//    }
//
//    @Override
//    public Void visit(Inputs inputs, Context ctx) {
//        inputs.statements().forEach(stmt -> stmt.accept(this, ctx));
//        return null;
//    }
//
//    @Override
//    public Void visit(TextInput input, Context ctx) {
//        input.statements().forEach(stmt -> stmt.accept(this, ctx));
//        return null;
//    }
//
//    @Override
//    public Void visit(BooleanInput input, Context ctx) {
//        input.statements().forEach(stmt -> stmt.accept(this, ctx));
//        return null;
//    }
//
//    @Override
//    public Void visit(EnumInput input, Context ctx) {
//        input.statements().forEach(stmt -> stmt.accept(this, ctx));
//        return null;
//    }
//
//    @Override
//    public Void visit(ListInput input, Context ctx) {
//        input.statements().forEach(stmt -> stmt.accept(this, ctx));
//        return null;
//    }
//
//    @Override
//    public Void visit(Invocation invocation, Context ctx) {
//        Script script = ScriptLoader.load(ctx.cwd().resolve(invocation.src()));
//        switch (invocation.invocationKind()) {
//            case EXEC:
//                script.accept(this, ctx.pushd(script.path().getParent()));
//                ctx.popd();
//            case SOURCE:
//                script.accept(this, ctx);
//            default:
//                // do nothing
//        }
//        return null;
//    }
}
