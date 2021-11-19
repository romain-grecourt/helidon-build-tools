package io.helidon.build.archetype.engine.v2.ast;

/**
 * Visitor.
 *
 * @param <T> argument
 */
public interface Visitor<T> {

    /**
     * Visit a script.
     *
     * @param script script
     * @param arg    argument
     * @return visit result
     */
    default VisitResult visitScript(Script script, T arg) {
        return VisitResult.CONTINUE;
    }

    /**
     * Visit a condition.
     *
     * @param condition condition
     * @param arg       argument
     * @return visit result
     */
    default VisitResult visitCondition(Condition condition, T arg) {
        return VisitResult.CONTINUE;
    }

    /**
     * Visit an invocation.
     *
     * @param invocation invocation
     * @param arg        argument
     * @return visit result
     */
    default VisitResult visitInvocation(Invocation invocation, T arg) {
        return VisitResult.CONTINUE;
    }

    /**
     * Visit a preset.
     *
     * @param preset preset
     * @param arg    argument
     * @return visit result
     */
    default VisitResult visitPreset(Preset preset, T arg) {
        return VisitResult.CONTINUE;
    }

    /**
     * Visit a block.
     *
     * @param block block
     * @param arg   argument
     * @return visit result
     */
    default VisitResult visitBlock(Block block, T arg) {
        return VisitResult.CONTINUE;
    }

    /**
     * Visit a noop.
     *
     * @param noop noop
     * @param arg  argument
     * @return visit result
     */
    default VisitResult visitNoop(Noop noop, T arg) {
        return VisitResult.CONTINUE;
    }

    /**
     * Visit result.
     */
    enum VisitResult {

        /**
         * Continue.
         */
        CONTINUE,

        /**
         * Terminate.
         */
        TERMINATE,

        /**
         * Continue without visiting the children.
         */
        SKIP_SUBTREE,

        /**
         * Continue without visiting the siblings.
         */
        SKIP_SIBLINGS
    }
}
