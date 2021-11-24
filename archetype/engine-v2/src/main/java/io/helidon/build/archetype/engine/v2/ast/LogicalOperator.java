package io.helidon.build.archetype.engine.v2.ast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Logical operator.
 */
enum LogicalOperator {

    /**
     * Equality operator.
     */
    EQUAL(8, "==") {
        @Override
        Boolean evaluate(Expression... operands) {
            checkOperands(2, operands);
            return true;
//            checkLiteralTypesEquality(literals[0], literals[1]);
//            return literals[0].value().equals(literals[1].value());
        }
    },

    /**
     * Inequality operator.
     */
    NOT_EQUAL(8, "!=") {
        @Override
        Boolean evaluate(Expression... operands) {
            checkOperands(2, operands);
            return true;
//            checkLiteralTypesEquality(literals[0], literals[1]);
//            return !literals[0].value().equals(literals[1].value());
        }
    },
    /**
     * Logical AND operator.
     */
    AND(4, "&&") {
        @Override
        Boolean evaluate(Expression... operands) {
            checkOperands(2, operands);
            return true;
//            checkLiteralTypesEquality(literals[0], literals[1]);
//            checkLiteralTypeEquality(literals[0], io.helidon.build.archetype.engine.v2.expression.Literal.Type.BOOLEAN);
//            return Boolean.logicalAnd(
//                    (Boolean) literals[0].value(),
//                    (Boolean) literals[1].value()
//            );
        }
    },
    /**
     * Logical OR operator.
     */
    OR(3, "||") {
        @Override
        Boolean evaluate(Expression... operands) {
            checkOperands(2, operands);
            return true;
//            checkLiteralTypesEquality(literals[0], literals[1]);
//            checkLiteralTypeEquality(literals[0], io.helidon.build.archetype.engine.v2.expression.Literal.Type.BOOLEAN);
//            return Boolean.logicalOr(
//                    (Boolean) literals[0].value(),
//                    (Boolean) literals[1].value()
//            );
        }
    },
    /**
     * {@code contains} operator.
     */
    CONTAINS(9, "contains") {
        @Override
        @SuppressWarnings("unchecked")
        Boolean evaluate(Expression... operands) {
            checkOperands(2, operands);
            return true;
//            checkLiteralTypeEquality(literals[0], io.helidon.build.archetype.engine.v2.expression.Literal.Type.ARRAY);
//            checkLiteralTypeEquality(literals[1], io.helidon.build.archetype.engine.v2.expression.Literal.Type.STRING);
//            return ((List<String>) literals[0].value()).contains(literals[1].value().toString());
        }
    },
    /**
     * Logical NOT operator.
     */
    NOT(13, "!") {
        @Override
        Boolean evaluate(Expression... operands) {
            checkOperands(1, operands);
            return true;
//            if (!operands[0].type().permittedForUnaryLogicalExpression()) {
//                throw new ParserException(String.format(
//                        "Operation '%s' cannot be performed on literals. "
//                                + "The literal %s must have the type %s.",
//                        "!",
//                        operands[0],
//                        operands[0].type()
//                ));
//            }
//            return !(Boolean) operands[0].value();
        }
    };

//    /**
//     * Compare type of the operands throw {@code ParserException} if they are not equal.
//     *
//     * @param left Literal
//     * @param type Literal.Type for comparison
//     */
//    void checkLiteralTypeEquality(Expression left, Expression type) {
//        if (!left.type().equals(type)) {
//            throw new ParserException(String.format(
//                    "Operation '%s' cannot be performed on literals. "
//                            + "The literal %s must have the type %s.",
//                    operator,
//                    left,
//                    type
//            ));
//        }
//    }

    /**
     * Compare types of the literals and throw {@code ParserException} if they are not equal.
     *
     * @param left  Literal
     * @param right Literal
     */
    void checkLiteralTypesEquality(Expression left, Expression right) {
//        if (!left.type().equals(right.type())) {
//            throw new ParserException(String.format(
//                    "Operation '%s' cannot be performed on literals. "
//                            + "The left literal %s and the right literal %s must be of the same type.",
//                    operator,
//                    left,
//                    right
//            ));
//        }
    }

    /**
     * Check operands count.
     *
     * @param count    expected count
     * @param operands operands
     */
    void checkOperands(int count, Expression... operands) {
        if (operands.length != count) {
            throw new UnsupportedOperationException(String.format(
                    "Operation %s with %d operand is not supported.",
                    operator,
                    operands.length));
        }
    }

    private final int precedence;
    private final String operator;

    LogicalOperator(int precedence, String operator) {
        this.precedence = precedence;
        this.operator = operator;
    }

    private static final Map<String, LogicalOperator> OPERATOR_MAP;

    static {
        OPERATOR_MAP = new HashMap<>();
        for (LogicalOperator op : values()) {
            OPERATOR_MAP.put(op.operator, op);
        }
    }

    /**
     * Get an operator by its string representation.
     *
     * @param operator string representation of the logical operator. E.g. {@code "!="}.
     * @return LogicalOperator
     */
    public static LogicalOperator find(String operator) {
        return OPERATOR_MAP.get(operator);
    }

    /**
     * Get the precedence of the operator.
     * Operators with higher precedence are evaluated before operators with relatively lower precedence.
     *
     * @return precedence
     */
    public int priority() {
        return precedence;
    }

    /**
     * Get the string representation of the operator.
     *
     * @return string representation of the operator. E.g. {@code "!="}
     */
    public String operator() {
        return operator;
    }

    /**
     * Evaluate this operation for the given operands.
     *
     * @param operands operands
     * @return Boolean
     */
    abstract Boolean evaluate(Expression... operands);
}
