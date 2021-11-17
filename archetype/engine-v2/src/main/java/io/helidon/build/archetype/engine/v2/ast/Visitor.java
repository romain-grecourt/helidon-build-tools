package io.helidon.build.archetype.engine.v2.ast;

/**
 * Visitor.
 *
 * @param <A> argument
 * @param <R> type of the returned value
 */
public interface Visitor<A, R> {

    default R visit(Statement stmt, A arg) {

        return null;
    }

    /**
     * Visit a block statement.
     *
     * @param block node
     * @param arg   argument
     * @return visit result
     */
    default R visit(Block block, A arg) {

        return null;
    }

    /**
     * Visit an if statement.
     *
     * @param ifStatement node
     * @param arg         argument
     * @return visit result
     */
    default R visit(IfStatement ifStatement, A arg) {
        return null;
    }

}
