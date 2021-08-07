/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.build.archetype.engine.v2.expression;

import java.util.Stack;

import static io.helidon.build.archetype.engine.v2.expression.BinaryOperators.*;
import static io.helidon.build.archetype.engine.v2.expression.UnaryOperators.*;

public final class ExpressionParser {

    private static final String NO_LEFT_OPERAND = "No left operand found for operator '%s' at index: %d";
    private static final String INVALID_LEFT_OPERAND = "Invalid left operand found for operator '%s' at index: %d";
    private static final String UNEXPECTED_EOL = "Cannot parse, end of line";
    private static final String UNEXPECTED_CHAR = "Invalid '%c' character found at index: %d, expecting :'%c'";
    private static final String INVALID_STATE = "Cannot parse value, invalid state";
    private static final String NO_RIGHT_OPERAND_FOUND = "No right operand found for operator '%s' at index: ";
    private static final String EMPTY_INVALID_VARIABLE = "Invalid empty variable at index: %s";
    private static final String INVALID_CHAR = "Invalid '%c' character found at index: %d";
    private static final String UNMATCHED_PARENTHESIS = "Unmatched ')' found at index: %s";

    private enum State {
        CONDITION, VARIABLE, LITERAL
    }

    private final String line;
    private final Stack<InfixOperation> stack;
    private State state;
    private StringBuilder value;
    private int index;

    ExpressionParser(String line) {
        this.line = line;
        this.state = State.CONDITION;
        this.index = 0;
        this.value = null;
        this.stack = new Stack<>();
    }

    private static final class InfixOperation {

        final int index;
        Operator operator;
        Expression node;

        InfixOperation(int index) {
            this.index = index;
        }

        void resolve(Expression operand) {
            if (operator == null) {
                return;
            }
            Expression expr;
            if (NOT.equals(operator)) {
                expr = new UnaryExpression(NOT, operand);
            } else if (AND.equals(operator)) {
                expr = new BinaryExpression(AND, node, operand);
            } else if (OR.equals(operator)) {
                expr = new BinaryExpression(OR, node, operand);
            } else if (XOR.equals(operator)) {
                expr = new BinaryExpression(XOR, node, operand);
            } else if (EQUAL.equals(operator)) {
                expr = new BinaryExpression(EQUAL, node, operand);
            } else if (NOT_EQUAL.equals(operator)) {
                expr = new BinaryExpression(NOT_EQUAL, node, operand);
            } else {
                throw new IllegalStateException("Unknown operator: " + operator);
            }
            operator = null; // resolved
            node = expr;
        }
    }

    private Expression lastOperand(Operator op) {
        InfixOperation operation = stack.peek();
        if (operation.node == null) {
            throw new ParserException(NO_LEFT_OPERAND, op, index);
        }
        return operation.node;
    }

    private void checkOperand(Operator op) {
        Expression operand = lastOperand(op);
        switch (operand.expressionKind()) {
            case VALUE:
            case VARIABLE:
                throw new ParserException(INVALID_LEFT_OPERAND, op, index);
            default:
        }
    }

    private void checkEOL() {
        if (index + 1 >= line.length()) {
            throw new ParserException(UNEXPECTED_EOL);
        }
    }

    private void checkNextChar(char op) {
        checkEOL();
        char c = line.charAt(++index);
        if (c != op) {
            throw new ParserException(UNEXPECTED_CHAR, c, index, op);
        }
    }

    private void checkValueState() {
        if (value == null) {
            throw new ParserException(INVALID_STATE);
        }
    }

    private void invalidChar(char c) {
        throw new ParserException(INVALID_CHAR, c, index);
    }

    private void appendValue(char c) {
        checkValueState();
        value.append(c);
    }

    private void finalizeVariable() {
        if (state == State.VARIABLE) {
            InfixOperation operation = stack.peek();
            String name = value.toString();
            if (name.isEmpty()) {
                throw new ParserException(EMPTY_INVALID_VARIABLE, operation.index);
            }
            Variable variable = new Variable(value.toString());
            if (operation.node == null) {
                operation.node = variable;
            } else if (operation.node.expressionKind() == ExpressionKind.VALUE) {
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

    private void checkOperatorNotSet(char c) {
        if (stack.peek().operator != null) {
            invalidChar(c);
        }
    }

    private static boolean isValidVarChar(char c) {
        return (int) c == 45 // -
                || (int) c == 46 // .
                || (int) c == 95 // _
                || ((int) c >= 48 && (int) c <= 57) // A-Z
                || ((int) c >= 97 && (int) c <= 122); // a-z
    }

    private InfixOperation resolveOp() {
        InfixOperation op = stack.pop();
        if (op.operator != null) {
            throw new ParserException(NO_RIGHT_OPERAND_FOUND, op.operator, op.index);
        }
        if (stack.isEmpty()) {
            return op;
        }
        InfixOperation next = stack.peek();
        if (next.operator == null && next.node == null) {
            next.node = op.node;
        } else {
            next.resolve(op.node);
        }
        return next;
    }

    private void processOperator(char c, BinaryOperator op, char o) {
        if (state != State.LITERAL) {
            finalizeVariable();
            checkOperatorNotSet(c);
            checkNextChar(o);
            checkOperand(op);
            stack.peek().operator = op;
        } else {
            appendValue(c);
        }
    }

    Expression parse() {
        int brackets = 0;
        stack.push(new InfixOperation(index));
        for (; index < line.length(); index++) {
            char c = line.charAt(index);
            switch (c) {
                case ('('):
                    if (state != State.LITERAL) {
                        brackets++;
                        stack.push(new InfixOperation(index));
                    } else {
                        appendValue(c);
                    }
                    break;
                case (')'):
                    if (state != State.LITERAL) {
                        finalizeVariable();
                        int size = stack.size();
                        if (--brackets < 0 || size <= 1) {
                            throw new ParserException(UNMATCHED_PARENTHESIS, index);
                        }
                        resolveOp();
                    } else {
                        appendValue(c);
                    }
                    break;
                case (' '):
                    if (state != State.LITERAL) {
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
                            invalidChar(c);
                        }
                    } else {
                        appendValue(c);
                    }
                    break;
                case ('\''):
                    if (state == State.LITERAL) {
                        checkValueState();
                        InfixOperation operation = stack.peek();
                        Literal<?> literal = Literal.of(value.toString());
                        if (operation.node == null) {
                            operation.node = literal;
                        } else {
                            operation.resolve(literal);
                        }
                        value = null;
                        state = State.CONDITION;
                        break;
                    } else if (value == null) {
                        value = new StringBuilder();
                        state = State.LITERAL;
                        break;
                    } else if (state == State.CONDITION) {
                        invalidChar(c);
                    }
                case ('\\'):
                    if (state == State.LITERAL) {
                        checkEOL();
                        char next = line.charAt(index + 1);
                        if ('\'' == next) {
                            c = '\'';
                            index++;
                        }
                    }
                    appendValue(c);
                    break;
                case ('='):
                    if (state != State.LITERAL) {
                        finalizeVariable();
                        checkOperatorNotSet(c);
                        checkNextChar('=');
                        if (lastOperand(EQUAL).expressionKind() == ExpressionKind.VALUE) {
                            stack.peek().operator = EQUAL;
                        } else {
//                            ??
//                            stack.peek().operator = Operator.IS;
                        }
                    } else {
                        appendValue(c);
                    }
                    break;
                case ('^'):
                    processOperator(c, XOR, '^');
                    break;
                case ('&'):
                    processOperator(c, AND, '&');
                    break;
                case ('|'):
                    processOperator(c, OR, '|');
                    break;
                case ('!'):
                    if (state != State.LITERAL) {
                        finalizeVariable();
                        checkEOL();
                        char next = line.charAt(index + 1);
                        switch (next) {
                            case '=':
                                checkOperatorNotSet(c);
                                index++;
                                switch (lastOperand(NOT_EQUAL).expressionKind()) {
                                    case VALUE:
                                    case VARIABLE:
                                        stack.peek().operator = NOT_EQUAL;
                                    default:
                                        // stack.peek().operator = IS_NOT;
                                }
                                break;
                            case '(':
                                InfixOperation operation = new InfixOperation(index);
                                operation.operator = NOT;
                                stack.push(operation);
                                break;
                            default:
                                invalidChar(c);
                        }
                    } else {
                        appendValue(c);
                    }
                    break;
                default:
                    if ((state == State.VARIABLE && !isValidVarChar(c))
                            || state == State.CONDITION) {
                        invalidChar(c);
                    }
                    appendValue(c);
            }
        }
        if (brackets != 0) {
            throw new ParserException("Unmatched '(' found");
        }
        finalizeVariable();
        while (stack.size() >= 1) {
            InfixOperation operation = resolveOp();
            if (stack.isEmpty()) {
                return operation.node;
            }
        }
        throw new ParserException("Invalid state");
    }
}
