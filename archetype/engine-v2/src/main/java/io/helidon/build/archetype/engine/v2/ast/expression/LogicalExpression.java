package io.helidon.build.archetype.engine.v2.ast.expression;

import io.helidon.build.archetype.engine.v2.ast.Expression;
import io.helidon.build.archetype.engine.v2.ast.Position;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.Stack;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toMap;

public class LogicalExpression extends Expression {

    // contains a list of operands and operator

    /**
     * Create a new logical expression.
     *
     * @param builder builder
     */
    protected LogicalExpression(Builder builder) {
        super(builder);
    }

    @Override
    public <A> VisitResult accept(Visitor<A> visitor, A arg) {
        // TODO
        return null;
    }

    /**
     * Create a new builder.
     *
     * @param location location
     * @param position position
     * @return builder
     */
    public static Builder builder(Path location, Position position) {
        return new Builder(location, position);
    }

    /**
     * Logical expression builder.
     */
    public static final class Builder extends Expression.Builder<LogicalExpression, Builder> {

        private Builder(Path location, Position position) {
            super(location, position, Kind.LOGICAL);
        }

        @Override
        protected LogicalExpression build0() {
            return new LogicalExpression(this);
        }
    }


    private enum Operator {
        EQUAL(8, "=="),
        NOT_EQUAL(8, "!="),
        AND(4, "&&"),
        OR(3, "||"),
        CONTAINS(9, "contains"),
        NOT(13, "!");

        final int precedence;
        final String symbol;

        Operator(int precedence, String symbol) {
            this.precedence = precedence;
            this.symbol = symbol;
        }
    }

    final static Map<String, Operator> OPS = Arrays.stream(Operator.values())
                                                   .collect(toMap(op -> op.symbol, Function.identity()));

    public static List<Token> shuntingYard(List<Token> tokens) {
        List<Token> output = new LinkedList<>();
        Stack<Token> stack = new Stack<>();
        for (Token token : tokens) {
            if (OPS.containsKey(token.value())) {
                while (!stack.isEmpty() && OPS.containsKey(stack.peek())) {
                    Operator currentOp = OPS.get(token);
                    Operator leftOp = OPS.get(stack.peek());
                    if ((currentOp.precedence - leftOp.precedence <= 0)) {
                        output.add(stack.pop());
                        continue;
                    }
                    break;
                }
                stack.push(token);
            } else if ("(".equals(token)) {
                stack.push(token);
            } else if (")".equals(token)) {
                while (!stack.isEmpty() && !stack.peek().equals("(")) {
                    output.add(stack.pop());
                }
                stack.pop();
            } else {
                output.add(token);
            }
        }
        while (!stack.isEmpty()) {
            output.add(stack.pop());
        }
        return output;
    }

    private static class Parser {

        private void parse(String expr) {
            List<Token> infix = StreamSupport.stream(spliteratorUnknownSize(new Tokenizer(expr), ORDERED), false)
                                               .collect(Collectors.toList());
            List<Token> rpn = shuntingYard(infix);

            // TODO rename Token to RawToken
            // TODO create Token (union type between operator and operand)
            // abstract class Token {
            //   enum Type {
            //      OPERATOR,
            //      OPERAND
            //   }
            //
            //  abstract Type type();
            //
            //  static class OperatorToken extends Token {
            //     Operator operator;
            //     Type type() { return Type.OPERATOR };
            //  }
            //  static class OperandToken extends Token {
            //     Expression operand;
            //     Type type() { return Type.OPERAND };
            //  }
            // }
            // TODO map rpn to Token (the new one)
            // TODO model variable as a value
            // TODO expose a method to evaluate with a function as parameter to resolve variables (decouple context)
        }
    }
}
