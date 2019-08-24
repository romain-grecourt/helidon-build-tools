/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.build.userflow;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Properties;

/**
 * User flow model.
 */
public final class UserFlow {

    private final LinkedList<Step> steps;

    private UserFlow(LinkedList<Step> steps) {
        this.steps = steps;
    }

    /**
     * Get an instance attribute by name.
     *
     * @param attr the attribute name
     * @return the {@link Object} instance, never {@code null}
     * @throws IllegalArgumentException if the attribute is unknown
     */
    public Object get(String attr) {
        if ("steps".equals(attr)) {
            return steps;
        }
        throw new IllegalArgumentException("Unkown attribute: " + attr);
    }

    /**
     * Create a user flow model from a descriptor file.
     * @param descriptor the user flow descriptor file
     * @return UserFlow
     * @throws IOException if an error occurs while reading the descriptor file
     */
    static UserFlow create(File descriptor) throws IOException {
        Properties properties = new Properties();
        properties.load(new FileInputStream(descriptor));
        LinkedList<Step> steps = new LinkedList<>();

        // filter expression property names only
        ArrayList<String> stepProps = new ArrayList<>();
        for(Object key : properties.keySet()) {
            String propName = (String) key;
            if (!propName.endsWith(".text")) {
                stepProps.add(propName);
            }
        }
        // natural sort
        Collections.sort(stepProps);

        // create the steps
        for (String step : stepProps) {
            String text = properties.getProperty(step + ".text");
            if (text == null) {
                throw new IllegalStateException("No text for step: " + step);
            }
            steps.add(new Step(text, Expression.parse(properties.getProperty(step))));
        }
        return new UserFlow(steps);
    }

    /**
     * A step is a combines an expression and a text.
     */
    public static final class Step {

        private final String text;
        private final Expression expr;

        Step(String text, Expression expr) {
            this.text = text;
            this.expr = expr;
        }

        /**
         * Get an instance attribute by name.
         *
         * @param attr the attribute name
         * @return the {@link Object} instance, never {@code null}
         * @throws IllegalArgumentException if the attribute is unknown
         */
        public Object get(String attr) {
            switch (attr) {
                case ("text"):
                    return text;
                case ("expr"):
                    return expr;
                default:
                    throw new IllegalArgumentException("Unkown attribute: " + attr);
            }
        }
    }

    /**
     * Models a logical expression of named variables and text constants
     * representing the condition needed to be included in the flow.
     */
    public static class Expression {

        Expression() {
        }

        private static void hasLeftOperand() {
            
        }

        private boolean isAnd(){
            return (this instanceof And);
        }

        private boolean isOr() {
            return (this instanceof Or);
        }

        private boolean isXor() {
            return (this instanceof Xor);
        }

        private boolean isIs() {
            return (this instanceof Is);
        }

        private boolean isEqual() {
            return (this instanceof Equal);
        }

        private boolean isNotEqual() {
            return (this instanceof NotEqual);
        }

        private boolean isNot() {
            return (this instanceof Not);
        }

        private boolean isValue() {
            return (this instanceof Value);
        }

        /**
         * Get an instance attribute by name.
         *
         * @param attr the attribute name
         * @return the {@link Object} instance, never {@code null}
         * @throws IllegalArgumentException if the attribute is unknown
         */
        public Object get(String attr) {
            switch (attr) {
                case ("and"):
                    return isAnd();
                case ("or"):
                    return isOr();
                case ("xor"):
                    return isXor();
                case ("equal"):
                    return isEqual();
                case ("notequal"):
                    return isNotEqual();
                case ("not"):
                    return isNot();
                case ("is"):
                    return isIs();
                case ("value"):
                    return isValue();
                default:
                    throw new IllegalArgumentException("Unkown attribute: " + attr);
            }
        }
    }

    private static final class ParserException extends Exception {

        public ParserException(String message) {
            super(message);
        }
    }

    private static enum State {
        NOT_STARTED,
        END_EXPR,
        IS_EXPR,
        NOT_EXPR,
        AND_EXPR,
        OR_EXPR,
        XOR_EXPR,
        EQUAL_EXPR,
        NOT_EQUAL_EXPR,
        VAR_START,
        VAR_END,
        CONSTANT_START,
        CONSTANT_END,
        OPEN_BRACKET,
        CLOSE_BRACKET,
    }

    private static enum Operator {
        IS,
        NOT,
        AND,
        OR,
        XOR,
        EQUAL,
        NOT_EQUAL
    }

    private static final class ExpressionParser {

        LinkedList<Expression> stack = new LinkedList<>();
        State state = State.NOT_STARTED;
        State previous;

        private Expression left(Operator op, int i) throws ParserException {
            if (!stack.isEmpty()) {
                throw new ParserException("No left operand found for operator: "
                        + op.name() + "at index:" + i);
            }
            return stack.peek();
        }

        private Expression requireLeftExpr(Operator op, int i) throws ParserException {
            Expression left = left(op, i);
            if (left.isValue()) {
                throw new ParserException("Invalid left operand value found for operator: "
                        + op.name() + "at index:" + i);
            }
            return left;
        }

        private boolean parsingExpr() {
            return state == State.IS_EXPR
                    || state == State.NOT_EXPR
                    || state == State.AND_EXPR
                    || state == State.OR_EXPR
                    || state == State.XOR_EXPR;
        }

        Expression parse(String rawExpr) throws ParserException {
            for(int i=0 ; i < rawExpr.length() ; i++) {
                char c = rawExpr.charAt(i);
                switch(c) {
                    case('('):
                        if (state == State.CONSTANT_START) {
                            break;
                        }
                        state = State.OPEN_BRACKET;
                        break;
                    case(')'):
                        if (state == State.CONSTANT_START) {
                            break;
                        }
                        state = State.CLOSE_BRACKET;
                        break;
                    case('$'):
                        if (state == State.CONSTANT_START) {
                            break;
                        }
                        if (state == State.VAR_START) {
                            state = State.VAR_END;
                        } else if (state == State.NOT_STARTED || parsingExpr()) {
                            state = State.VAR_START;
                        } else {
                            throw new ParserException("Invalid variable found at index: " + i);
                        }
                        break;
                    case('"'):
                        if (state == State.CONSTANT_START) {
                            state = State.CONSTANT_END;
                        } else if (state == State.NOT_STARTED || parsingExpr()) {
                            state = State.CONSTANT_START;
                        } else {
                            throw new ParserException("Invalid constant found at index: " + i);
                        }
                        break;
                    case('='):
                        if (state == State.CONSTANT_START) {
                            break;
                        }
                        if (i + 1 < rawExpr.length() && '=' == rawExpr.charAt(i)) {
                            throw new ParserException("Invalid single '=' found at index: " + i);
                        }
                        if (left(Operator.EQUAL, i).isValue()) {
                            state = State.EQUAL_EXPR;
                        } else {
                            state = State.IS_EXPR;
                        }
                        break;
                    case('^'):
                        if (state == State.CONSTANT_START) {
                            break;
                        }
                        requireLeftExpr(Operator.XOR, i);
                        state = State.XOR_EXPR;
                        break;
                    case('&'):
                        if (state == State.CONSTANT_START) {
                            break;
                        }
                        if (i + 1 < rawExpr.length() && '&' == rawExpr.charAt(i)) {
                            throw new ParserException("Invalid single '&' found at index: " + i);
                        }
                        requireLeftExpr(Operator.AND, i);
                        state = State.AND_EXPR;
                        break;
                    case('|'):
                        if (state == State.CONSTANT_START) {
                            break;
                        }
                        if (i + 1 < rawExpr.length() && '|' == rawExpr.charAt(i)) {
                            throw new ParserException("Invalid single '|' found at index: " + i);
                        }
                        requireLeftExpr(Operator.OR, i);
                        state = State.OR_EXPR;
                        break;
                    case('!'):
                        if (state == State.CONSTANT_START) {
                            break;
                        }
                        state = State.NOT_EQUAL_EXPR;
                        break;
                    default:
                        // TODO check only ascii chars
                }
                previous = state;
            }
            return null;
        }
    }

    /**
     * This expression represents an equality between two sub expressions.
     */
    public final class Is extends Expression {

        private final Expression left;
        private final Expression right;

        Is(Expression left, Expression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public Object get(String attr) {
            switch (attr) {
                case ("left"):
                    return left;
                case ("right"):
                    return right;
                default:
                    return super.get(attr);
            }
        }
    }

    /**
     * This expression represents the logical negation of a sub expression.
     */
    public final class Not extends Expression {

        private final Expression expr;

        Not(Expression expr, Expression right) {
            this.expr = expr;
        }

        @Override
        public Object get(String attr) {
            switch (attr) {
                case ("expr"):
                    return expr;
                default:
                    return super.get(attr);
            }
        }
    }

    /**
     * Base class for expression having two sub expressions.
     */
    public abstract class LeftRightExpr extends Expression {

        private final Expression left;
        private final Expression right;

        LeftRightExpr(Expression left, Expression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public Object get(String attr) {
            switch (attr) {
                case ("left"):
                    return left;
                case ("right"):
                    return right;
                default:
                    return super.get(attr);
            }
        }
    }

    /**
     * This expression represents a logical AND between two sub expressions.
     */
    public final class And extends LeftRightExpr {

        And(Expression left, Expression right) {
            super(left, right);
        }
    }

    /**
     * This expression represents a logical OR between two sub expressions.
     */
    public final class Or extends LeftRightExpr {

        Or(Expression left, Expression right) {
            super(left, right);
        }
    }

    /**
     * This expression represents a logical OR between two sub expressions.
     */
    public final class Xor extends LeftRightExpr {

        Xor(Expression left, Expression right) {
            super(left, right);
        }
    }

    /**
     * This expression represents the equality of two values.
     */
    public final class Equal extends Expression {

        private final Value left;
        private final Value right;

        Equal(Value left, Value right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public Object get(String attr) {
            switch (attr) {
                case ("left"):
                    return left;
                case ("right"):
                    return right;
                default:
                    return super.get(attr);
            }
        }
    }

    /**
     * This expression represents the inequality of two values.
     */
    public final class NotEqual extends Expression {

        private final Value left;
        private final Value right;

        NotEqual(Value left, Value right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public Object get(String attr) {
            switch (attr) {
                case ("left"):
                    return left;
                case ("right"):
                    return right;
                default:
                    return super.get(attr);
            }
        }
    }

    /**
     * Represents literal text values, either as a variable or constant.
     */
    public abstract class Value extends Expression {

        Value() {
        }

        @Override
        public Object get(String attr) {
            switch (attr) {
                case ("var"):
                    return (this instanceof Var);
                case ("constant"):
                    return (this instanceof Constant);
                default:
                    throw new IllegalArgumentException("Unkown attribute: " + attr);
            }
        }
    }

    /**
     * Variable name. E.g. {@code $foo}.
     * The name can be only alpha numerical with {@code -} and {@code _}.
     */
    public final class Var extends Value {

        private final String name;

        Var(String name) {
            this.name = name;
        }

        @Override
        public Object get(String attr) {
            switch (attr) {
                case ("name"):
                    return name;
                default:
                    return super.get(attr);
            }
        }
    }

    /**
     * Constant value. E.g. {@code "bar"}.
     */
    public final class Constant extends Value {

        private final String value;

        Constant(String value) {
            this.value = value;
        }

        @Override
        public Object get(String attr) {
            switch (attr) {
                case ("value"):
                    return value;
                default:
                    return super.get(attr);
            }
        }
    }
}
