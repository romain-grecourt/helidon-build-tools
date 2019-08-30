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

import java.util.Stack;

import io.helidon.build.userflow.AbstratSyntaxTree.And;
import io.helidon.build.userflow.AbstratSyntaxTree.Literal;
import io.helidon.build.userflow.AbstratSyntaxTree.Equal;
import io.helidon.build.userflow.AbstratSyntaxTree.Is;
import io.helidon.build.userflow.AbstratSyntaxTree.IsNot;
import io.helidon.build.userflow.AbstratSyntaxTree.Not;
import io.helidon.build.userflow.AbstratSyntaxTree.NotEqual;
import io.helidon.build.userflow.AbstratSyntaxTree.Or;
import io.helidon.build.userflow.AbstratSyntaxTree.Variable;
import io.helidon.build.userflow.AbstratSyntaxTree.Xor;

/**
 * Simple expression supporting logical operators with text literal and variables. The logical operators have no precedence, the
 * order is the declared order, parenthesis must be used to express a different order. The expression is parsed into a syntax tree
 * that can be accessed using {@link #tree()}.
 */
public final class Expression {

    /**
     * Parser exception.
     */
    public static final class ParserException extends Exception {

        /**
         * Create a new exception.
         * @param message error message
         */
        private ParserException(String message) {
            super(message);
        }

        /**
         * Create a new exception.
         * @param message error message
         */
        private ParserException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Parsing states.
     */
    private static enum State {
        CONDITION, VARIABLE, CONSTANT;
    }

    /**
     * The conditional expression operators.
     */
    private static enum Operator {

        IS, IS_NOT, NOT, AND, OR, XOR, EQUAL, NOT_EQUAL;

        @Override
        public String toString() {
            switch(this) {
                case NOT:
                    return "!";
                case IS:
                case EQUAL:
                    return "==";
                case IS_NOT:
                case NOT_EQUAL:
                    return "!=";
                case AND:
                    return "&&";
                case OR:
                    return "||";
                case XOR:
                    return "^^";
                default:
                    throw new IllegalStateException("Unkown operator");
            }
        }
    }

    /**
     * Infix operation. An unresolved operation has a non {@code null} operator, and a node representing the {@code left}
     * operand. If the operator takes a single operand, the node will be {@code null}. An operation is resolved with
     * {@link #resolve(AbstractSyntaxTree)}, by passing a {@code right} operand. A resolved operation has a {@code null}
     * operator and a node of type {@link LogicalExpression} that represents the operation with the proper operand(s).
     */
    private static final class InfixOperation {

        final int index;
        Operator operator;
        AbstratSyntaxTree node;

        /**
         * Create a new operation.
         * @param index source index
         */
        InfixOperation(int index) {
            this.index = index;
        }

        /**
         * Complete this operation with the given operand.
         *
         * @param right right operand
         */
        void resolve(AbstratSyntaxTree right) throws ParserException {
            AbstratSyntaxTree newNode;
            try {
                switch (operator) {
                    case NOT:
                        newNode = new Not(right.asExpression());
                        break;
                    case IS:
                        newNode = new Is(node.asExpression(), right.asExpression());
                        break;
                    case IS_NOT:
                        newNode = new IsNot(node.asExpression(), right.asExpression());
                        break;
                    case AND:
                        newNode = new And(node.asExpression(), right.asExpression());
                        break;
                    case OR:
                        newNode = new Or(node.asExpression(), right.asExpression());
                        break;
                    case XOR:
                        newNode = new Xor(node.asExpression(), right.asExpression());
                        break;
                    case EQUAL:
                        newNode = new Equal(node.asValue(), right.asValue());
                        break;
                    case NOT_EQUAL:
                        newNode = new NotEqual(node.asValue(), right.asValue());
                        break;
                    default:
                        throw new IllegalStateException("Unkown operator: " + operator);
                }
            } catch (ClassCastException ex) {
                throw new ParserException("Internal parser error", ex);
            }
            operator = null; // marks the operation as resolved
            node = newNode;
        }
    }

    private final String rawExpr;
    private final AbstratSyntaxTree tree;
    private final Stack<InfixOperation> stack;
    private State state;
    private StringBuilder value;
    private int index;

    /**
     * Create a new expression.
     * @param rawExpr the string representation of the expression
     * @throws ParserException if a parsing error occurs
     */
    public Expression(String rawExpr) throws ParserException {
        this.rawExpr = rawExpr;
        this.state = State.CONDITION;
        this.index = 0;
        this.value = null;
        this.stack = new Stack<>();
        this.tree = parse();
    }

    /**
     * Get the syntax tree.
     * @return AbstractSyntaxTree
     */
    public AbstratSyntaxTree tree() {
        return tree;
    }

    /**
     * Return the node of the current operation.
     * @param op operator used to customize the exception thrown
     * @return AbstractSyntaxTree
     * @throws ParserException if node is {@code null}
     */
    private AbstratSyntaxTree lastOperand(Operator op) throws ParserException {
        InfixOperation operation = stack.peek();
        if (operation.node == null) {
            throw new ParserException(String.format(
                    "No left operand found for operator '%s' at index: %d",
                    op, index));
        }
        return operation.node;
    }

    /**
     * Enforce that the left operand is an expression.
     * @param op operator used to customize the exception thrown
     * @throws ParserException if there is no left operand or if it is a {@code Value}
     */
    private void checkOperandExpression(Operator op) throws ParserException {
        AbstratSyntaxTree operand = lastOperand(op);
        if (operand.isValue()) {
            throw new ParserException(String.format(
                    "Invalid left operand found for operator '%s' at index: %d",
                    op, index));
        }
    }

    /**
     * Enforce that there are more characters to parse.
     * @throws ParserException if there are no more characters to parse
     */
    private void checkNotEndOfExpression() throws ParserException {
        if (index + 1 >= rawExpr.length()) {
            throw new ParserException("Cannot parse, end of expression");
        }
    }

    /**
     * Enforce the next character.
     * @param op the next expected character
     * @throws ParserException if the next character is not the expected one
     */
    private void checkNextCharacter(char op) throws ParserException {
        checkNotEndOfExpression();
        char c = rawExpr.charAt(++index);
        if (c != op) {
            throw new ParserException(String.format(
                    "Invalid '%c' character found at index: %d, expecting :'%c'",
                    c, index, op));
        }
    }

    /**
     * Enforce the state is valid to parse a value.
     * @throws ParserException if the state is not valid to parse a value
     */
    private void checkValueState() throws ParserException {
        if (value == null) {
            throw new ParserException("Cannot parse value, invalid state");
        }
    }

    /**
     * Throw an exception to indicate an invalid character.
     * @param c the invalid character
     * @throws ParserException to describe the error
     */
    private void invalidCharacter(char c) throws ParserException {
        throw new ParserException(String.format(
                "Invalid '%c' character found at index: %d",
                c, index));
    }

    /**
     * Append the given character to the current value.
     * @param c character to append
     * @throws ParserException if the current value is {@code null}
     */
    private void appendValue(char c) throws ParserException {
        checkValueState();
        value.append(c);
    }

    /**
     * Finalize a variable.
     * @throws ParserException if the resulting variable name is empty
     */
    private void finalizeVariable() throws ParserException {
        if (state == State.VARIABLE) {
            InfixOperation operation = stack.peek();
            String name = value.toString();
            if (name.isEmpty()) {
                throw new ParserException("Invalid empty variable at index: " + operation.index);
            }
            Variable variable = new Variable(value.toString());
            if (operation.node == null) {
                operation.node = variable;
            } else if (operation.node.isValue()) {
                operation.resolve(variable);
            } else {
                operation = new InfixOperation(index);
                operation.node = variable;
                stack.push(operation);
            }
            value = null;
            state = State.CONDITION;
        }
    }

    /**
     * Check that the operator is not set in the current operation.
     * @param op operator
     * @throws ParserException if the operator of the current operation is already set
     */
    private void checkOperatorNotSet(char c) throws ParserException {
        if (stack.peek().operator != null) {
            invalidCharacter(c);
        }
    }

    /**
     * Test if a given character is valid for a variable name.
     * @param c character to test
     * @return {@code true} if valid, {@code false} otherwise
     */
    private static boolean isValidVariableCharacter(char c) {
        return (int) c == 45 // -
                || (int) c == 46 // .
                || (int) c == 95 // _
                || ((int) c >= 48 && (int) c <= 57) // A-Z
                || ((int) c >= 97 && (int) c <= 122); // a-z
    }

    /**
     * Resolve the next operations if there is more than one, or return the one operation.
     * @return InfixOperation
     * @throws ParserException if the first operation is not completed
     */
    private InfixOperation resolveOperation() throws ParserException {
        InfixOperation current = stack.pop();
        if (current.operator != null) {
            throw new ParserException(String.format(
                    "No right operand found for operator '%s' at index: ",
                    current.operator, current.index));
        }
        if (stack.isEmpty()) {
            return current;
        }
        InfixOperation next = stack.peek();
        if (next.operator == null && next.node == null) {
            next.node = current.node;
        } else {
            next.resolve(current.node);
        }
        return next;
    }

    /**
     * Process a binary operator.
     * @param c the current character
     * @param op the operator
     * @param o the next expected character
     * @throws ParserException if an error occurs
     */
    private void processBinaryOperator(char c, Operator op, char o) throws ParserException {
        if (state != State.CONSTANT) {
            finalizeVariable();
            checkOperatorNotSet(c);
            checkNextCharacter(o);
            checkOperandExpression(op);
            stack.peek().operator = op;
        } else {
            appendValue(c);
        }
    }

    /**
     * Parse the expression.
     * @return Condition
     * @throws ParserException if an error occurs during parsing
     */
    AbstratSyntaxTree parse() throws ParserException {
        int brackets = 0;
        stack.push(new InfixOperation(index));
        for (; index < rawExpr.length(); index++) {
            char c = rawExpr.charAt(index);
            switch (c) {
                case ('('):
                    if (state != State.CONSTANT) {
                        brackets++;
                        stack.push(new InfixOperation(index));
                    } else {
                        appendValue(c);
                    }
                    break;
                case (')'):
                    if (state != State.CONSTANT) {
                        finalizeVariable();
                        int size = stack.size();
                        if (--brackets < 0 || size <= 1) {
                            throw new ParserException("Unmatched ')' found at index: " + index);
                        }
                        resolveOperation();
                    } else {
                        appendValue(c);
                    }
                    break;
                case (' '):
                    if (state != State.CONSTANT) {
                        finalizeVariable();
                    } else {
                        appendValue(c);
                    }
                    break;
                case ('$'):
                    if (state == State.CONDITION) {
                        if (value == null) {
                            value = new StringBuilder();
                            state = State.VARIABLE;
                        } else {
                            invalidCharacter(c);
                        }
                    } else {
                        appendValue(c);
                    }
                    break;
                case ('"'):
                    if (state == State.CONSTANT) {
                        checkValueState();
                        InfixOperation operation = stack.peek();
                        Literal constant = new Literal(value.toString());
                        if (operation.node == null) {
                            operation.node = constant;
                        } else {
                            operation.resolve(constant);
                        }
                        value = null;
                        state = State.CONDITION;
                        break;
                    } else if (value == null) {
                        value = new StringBuilder();
                        state = State.CONSTANT;
                        break;
                    } else if (state == State.CONDITION) {
                        invalidCharacter(c);
                    }
                case('\\'):
                    if (state == State.CONSTANT) {
                        checkNotEndOfExpression();
                        char next = rawExpr.charAt(index + 1);
                        if ('"' == next) {
                            c = '"';
                            index++;
                        }
                    }
                    appendValue(c);
                    break;
                case ('='):
                    if (state != State.CONSTANT) {
                        finalizeVariable();
                        checkOperatorNotSet(c);
                        checkNextCharacter('=');
                        if (lastOperand(Operator.EQUAL).isValue()) {
                            stack.peek().operator = Operator.EQUAL;
                        } else {
                            stack.peek().operator = Operator.IS;
                        }
                    } else {
                        appendValue(c);
                    }
                    break;
                case ('^'):
                    processBinaryOperator(c, Operator.XOR, '^');
                    break;
                case ('&'):
                    processBinaryOperator(c, Operator.AND, '&');
                    break;
                case ('|'):
                    processBinaryOperator(c, Operator.OR, '|');
                    break;
                case ('!'):
                    if (state != State.CONSTANT) {
                        finalizeVariable();
                        checkNotEndOfExpression();
                        char next = rawExpr.charAt(index + 1);
                        switch (next) {
                            case '=':
                                checkOperatorNotSet(c);
                                index++;
                                if (lastOperand(Operator.NOT_EQUAL).isValue()) {
                                    stack.peek().operator = Operator.NOT_EQUAL;
                                } else {
                                    stack.peek().operator = Operator.IS_NOT;
                                }
                                break;
                            case '(':
                                InfixOperation operation = new InfixOperation(index);
                                operation.operator = Operator.NOT;
                                stack.push(operation);
                                break;
                            default:
                                invalidCharacter(c);
                        }
                    } else {
                        appendValue(c);
                    }
                    break;
                default:
                    if ((state == State.VARIABLE && !isValidVariableCharacter(c))
                            || state == State.CONDITION) {
                        invalidCharacter(c);
                    }
                    appendValue(c);
            }
        }
        if (brackets != 0) {
            throw new ParserException("Unmatched '(' found");
        }
        finalizeVariable();
        while(stack.size() >= 1) {
            InfixOperation operation = resolveOperation();
            if (stack.isEmpty()) {
                return operation.node;
            }
        }
        throw new ParserException("Invalid state");
    }
}
