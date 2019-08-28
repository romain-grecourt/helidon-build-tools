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

import io.helidon.build.userflow.ExpressionSyntaxTree.And;
import io.helidon.build.userflow.ExpressionSyntaxTree.Literal;
import io.helidon.build.userflow.ExpressionSyntaxTree.Equal;
import io.helidon.build.userflow.ExpressionSyntaxTree.Is;
import io.helidon.build.userflow.ExpressionSyntaxTree.IsNot;
import io.helidon.build.userflow.ExpressionSyntaxTree.Not;
import io.helidon.build.userflow.ExpressionSyntaxTree.NotEqual;
import io.helidon.build.userflow.ExpressionSyntaxTree.Or;
import io.helidon.build.userflow.ExpressionSyntaxTree.Variable;
import io.helidon.build.userflow.ExpressionSyntaxTree.Xor;

/**
 * Simple expression supporting logical operators with text literal and variables. The logical operators have no precedence, the
 * order is the declared order, parenthesis must be used to express a different order.
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
    }

    /**
     * Parsing states.
     */
    private static enum State {
        CONDITION, VARIABLE, CONSTANT;
    }

    /**
     * The condition operators.
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
                    return "^";
                default:
                    throw new IllegalStateException("Unkown operator");
            }
        }
    }

    /**
     * Internal scope represents a partially constructed tree.
     * I.e one operand and one operator.
     */
    private static final class Scope {

        final int index;
        Operator operator;
        ExpressionSyntaxTree node;

        Scope(int index) {
            this.index = index;
        }
    }

    private final String rawExpr;
    private final ExpressionSyntaxTree tree;
    private final Stack<Scope> stack;
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
     * @return ExpressionSyntaxTree
     */
    public ExpressionSyntaxTree tree() {
        return tree;
    }

    /**
     * Return the node of the current scope.
     * @param op operator used to customize the exception thrown
     * @return ExpressionSyntaxTree
     * @throws ParserException if node is {@code null}
     */
    private ExpressionSyntaxTree lastOperand(Operator op) throws ParserException {
        ExpressionSyntaxTree node = stack.peek().node;
        if (node == null) {
            throw new ParserException(String.format(
                    "No left operand found for operator '%s' at index: %d",
                    op, index));
        }
        return node;
    }

    /**
     * Enforce that the last operand is a {@code Condition}.
     * @param op operator used to customize the exception thrown
     * @throws ParserException if there is no left operand or if it is a {@code Value}
     */
    private void checkLeftcondition(Operator op) throws ParserException {
        ExpressionSyntaxTree operand = lastOperand(op);
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
    private void checkEndOfExpression() throws ParserException {
        if (index + 1 >= rawExpr.length()) {
            throw new ParserException("Cannot parse, end of expression");
        }
    }

    /**
     * Enforce the next character.
     * @param op the next expected character
     * @throws ParserException if the next character is not the expected one
     */
    private void checkNextOperatorChar(char op) throws ParserException {
        checkEndOfExpression();
        char c = rawExpr.charAt(++index);
        if (c != op) {
            throw new ParserException(String.format(
                    "Invalid character '%c' found at index: %d, expecting :'%c'",
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
     * Finalize the given scope with a 'right' operand.
     * @param scope the scope
     * @param right right operand
     * @return Condition
     */
    private void finalizeScope(Scope scope, ExpressionSyntaxTree right) {
        ExpressionSyntaxTree newNode;
        switch (scope.operator) {
            case NOT:
                newNode = new Not(right.asCondition());
                break;
            case IS:
                newNode = new Is(scope.node.asCondition(), right.asCondition());
                break;
            case IS_NOT:
                newNode = new IsNot(scope.node.asCondition(), right.asCondition());
                break;
            case AND:
                newNode = new And(scope.node.asCondition(), right.asCondition());
                break;
            case OR:
                newNode = new Or(scope.node.asCondition(), right.asCondition());
                break;
            case XOR:
                newNode = new Xor(scope.node.asCondition(), right.asCondition());
                break;
            case EQUAL:
                newNode = new Equal(scope.node.asValue(), right.asValue());
                break;
            case NOT_EQUAL:
                newNode = new NotEqual(scope.node.asValue(), right.asValue());
                break;
            default:
                throw new IllegalStateException("Unkown operator: " + scope.operator);
        }
        scope.operator = null; // marks a scope as finalized
        scope.node = newNode;
    }

    /**
     * Process a variable.
     * @throws ParserException if the resulting variable name is empty
     */
    private void processVariable() throws ParserException {
        if (state == State.VARIABLE) {
            Scope scope = stack.peek();
            String name = value.toString();
            if (name.isEmpty()) {
                throw new ParserException("Invalid empty variable at index: " + scope.index);
            }
            Variable variable = new Variable(value.toString());
            if (scope.node == null) {
                scope.node = variable;
            } else if (scope.node.isValue()) {
                finalizeScope(scope, variable);
            } else {
                scope = new Scope(index);
                scope.node = variable;
                stack.push(scope);
            }
            value = null;
            state = State.CONDITION;
        }
    }

    /**
     * Test if a given character is valid for a variable name.
     * @param c character to test
     * @return {@code true} if valid, {@code false} otherwise
     */
    private boolean isValidVariableCharacter(char c) {
        return (int) c == 45 // -
                || (int) c == 46 // .
                || (int) c == 95 // _
                || ((int) c >= 48 && (int) c <= 57) // A-Z
                || ((int) c >= 97 && (int) c <= 122); // a-z
    }

    /**
     * Merge the next scopes if there is more than one, or return the one scope.
     * @return Scope
     * @throws ParserException if the first scope isn't finalized
     */
    private Scope merge() throws ParserException {
        Scope right = stack.pop();
        if (right.operator != null) {
            throw new ParserException(String.format(
                    "No right operand found for operator '%s' at index: ",
                    right.operator, right.index));
        }
        if (stack.isEmpty()) {
            return right;
        }
        Scope left = stack.peek();
        if (left.operator == null && left.node == null) {
            left.node = right.node;
        } else {
            finalizeScope(left, right.node);
        }
        return left;
    }

    /**
     * Parse the expression.
     * @return Condition
     * @throws ParserException if an error occurs during parsing
     */
    ExpressionSyntaxTree parse() throws ParserException {
        int brackets = 0;
        stack.push(new Scope(index));
        for (; index < rawExpr.length(); index++) {
            char c = rawExpr.charAt(index);
            switch (c) {
                case ('('):
                    if (state != State.CONSTANT) {
                        brackets++;
                        stack.push(new Scope(index));
                    } else {
                        appendValue(c);
                    }
                    break;
                case (')'):
                    if (state != State.CONSTANT) {
                        processVariable();
                        int size = stack.size();
                        if (--brackets < 0 || size <= 1) {
                            throw new ParserException("Unmatched ')' found at index: " + index);
                        }
                        merge();
                    } else {
                        appendValue(c);
                    }
                    break;
                case (' '):
                    if (state != State.CONSTANT) {
                        processVariable();
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
                        Scope scope = stack.peek();
                        Literal constant = new Literal(value.toString());
                        if (scope.node == null) {
                            scope.node = constant;
                        } else {
                            finalizeScope(scope, constant);
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
                        checkEndOfExpression();
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
                        processVariable();
                        if (stack.peek().operator == Operator.EQUAL) {
                            invalidCharacter(c);
                        }
                        checkNextOperatorChar('=');
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
                    if (state != State.CONSTANT) {
                        processVariable();
                        if (stack.peek().operator == Operator.XOR) {
                            invalidCharacter(c);
                        }
                        checkLeftcondition(Operator.XOR);
                        stack.peek().operator = Operator.XOR;
                    } else {
                        appendValue(c);
                    }
                    break;
                case ('&'):
                    if (state != State.CONSTANT) {
                        processVariable();
                        if (stack.peek().operator == Operator.AND) {
                            invalidCharacter(c);
                        }
                        checkNextOperatorChar('&');
                        checkLeftcondition(Operator.AND);
                        stack.peek().operator = Operator.AND;
                    } else {
                        appendValue(c);
                    }
                    break;
                case ('|'):
                    if (state != State.CONSTANT) {
                        processVariable();
                        if (stack.peek().operator == Operator.OR) {
                            invalidCharacter(c);
                        }
                        checkNextOperatorChar('|');
                        checkLeftcondition(Operator.OR);
                        stack.peek().operator = Operator.OR;
                    } else {
                        appendValue(c);
                    }
                    break;
                case ('!'):
                    if (state != State.CONSTANT) {
                        processVariable();
                        checkEndOfExpression();
                        char next = rawExpr.charAt(index + 1);
                        switch (next) {
                            case '=':
                                index++;
                                if (lastOperand(Operator.NOT_EQUAL).isValue()) {
                                    stack.peek().operator = Operator.NOT_EQUAL;
                                } else {
                                    stack.peek().operator = Operator.IS_NOT;
                                }
                                break;
                            case '(':
                                Scope scope = new Scope(index);
                                scope.operator = Operator.NOT;
                                stack.push(scope);
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
        processVariable();
        while(stack.size() >= 1) {
            Scope root = merge();
            if (stack.isEmpty()) {
                return root.node;
            }
        }
        throw new ParserException("Invalid state");
    }
}
