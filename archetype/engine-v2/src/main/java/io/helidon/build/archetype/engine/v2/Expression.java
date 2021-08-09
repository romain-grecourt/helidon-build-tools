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
package io.helidon.build.archetype.engine.v2;

import java.util.*;

public final class Expression {

    // first 7 bits are operators
    static final int OPS_BITMASK = 128;

    static final int NOT = 0;
    static final int EQUAL = 1;
    static final int NOT_EQUAL = 2;
    static final int AND = 3;
    static final int OR = 4;
    static final int CONTAINS = 5;

    static final int LEFT_PAREN = 128;
    static final int RIGHT_PAREN = 129;
    static final int TRUE = 130;
    static final int FALSE = 131;
    static final int NULL = 132;
    static final int STRING = 133;
    static final int VARIABLE = 134;
    static final int UNKNOWN = 135;
    static final int IS_NULL = 136;
    static final int IS_NOT_NULL = 137;

    private static final String MISSING_CLOSING_QUOTE = "Missing closing quote";
    private static final String INVALID_STATE = "Invalid state %s, index=%d";
    private static final String INVALID_CHARACTER = "Invalid character '%c', index=%d";
    private static final String MISSING_OPERATOR = "Missing operator";
    private static final String MISSING_OPERAND = "Missing operand";
    private static final String INVALID_EXPRESSION = "Invalid Expression";
    private static final String NON_BOOLEAN_EXPRESSION = "Non-boolean expression";
    private static final String UNKNOWN_OPERATOR = "Unknown operator '%s'";

    private enum Associativity {
        LEFT,
        RIGHT
    }

    enum Operator {
        NOT(Associativity.RIGHT, 0),
        EQUAL(Associativity.LEFT, 10),
        NOT_EQUAL(Associativity.LEFT, 10),
        AND(Associativity.LEFT, 20),
        OR(Associativity.LEFT, 30),
        CONTAINS(Associativity.LEFT, 40);
        // TODO compound operators IS_NULL, IS_NOT_NULL, NOT_CONTAIN

        final Associativity associativity;
        final int precedence;

        Operator(Associativity associativity, int precedence) {
            this.associativity = associativity;
            this.precedence = precedence;
        }

        int comparePrecedence(Operator operator) {
            return this.precedence - operator.precedence;
        }
    }

    // operators by code
    static Operator[] OPERATORS = new Operator[]{
            Operator.NOT,
            Operator.EQUAL,
            Operator.NOT_EQUAL,
            Operator.AND,
            Operator.OR,
            Operator.CONTAINS
    };

    static final class Token {

        static final Token NOT = new Token("!", Expression.NOT);
        static final Token EQUAL = new Token("==", Expression.EQUAL);
        static final Token NOT_EQUAL = new Token("!=", Expression.NOT_EQUAL);
        static final Token AND = new Token("&&", Expression.AND);
        static final Token OR = new Token("||", Expression.OR);
        static final Token CONTAINS = new Token("contains", Expression.CONTAINS);
        static final Token TRUE = new Token("true", Expression.TRUE);
        static final Token FALSE = new Token("false", Expression.FALSE);
        static final Token NULL = new Token("null", Expression.NULL);
        static final Token LEFT_PAREN = new Token("(", Expression.LEFT_PAREN);
        static final Token RIGHT_PAREN = new Token(")", Expression.RIGHT_PAREN);
        static final Token UNKNOWN = new Token(null, Expression.UNKNOWN);
        static final Token IS_NULL = new Token(null, Expression.IS_NULL);
        static final Token IS_NOT_NULL = new Token(null, Expression.IS_NOT_NULL);

        final String value;
        final int code;

        Token(String value, int code) {
            this.value = value;
            this.code = code;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Token token = (Token) o;
            return code == token.code && Objects.equals(value, token.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, code);
        }
    }

    public static final class ExpressionException extends RuntimeException {
        public ExpressionException(String message, Object... args) {
            super(String.format(message, args));
        }
    }

    enum State {
        DEFAULT,
        NOT,
        EQUAL(Token.EQUAL),
        NOT_EQUAL(Token.NOT_EQUAL),
        AND(Token.AND),
        OR(Token.OR),
        CONTAINS(Token.CONTAINS),
        TRUE(Token.TRUE),
        FALSE(Token.FALSE),
        NULL(Token.NULL),
        STRING_LITERAL,
        STRING_LITERAL_END,
        IDENTIFIER;

        Token token;

        State() {
            this.token = null;
        }

        State(Token token) {
            this.token = token;
        }
    }

    final List<Token> tokens;

    public Expression(String input) {
        this.tokens = compile(input);
    }

    static List<Token> compile(String input) {
        List<Token> tokens = tokenize(input);
        // TODO compounds, normalization
        validate(tokens);
        return shuntingYard(tokens);
        // TODO more validation post shuntingYard-yard
    }

    static List<Token> tokenize(String input) {
        LinkedList<Token> tokens = new LinkedList<>();
        StringBuilder buf = new StringBuilder();

        int code;
        int lastCode = -1;
        int tokenPos = 0;
        State state = State.DEFAULT;
        boolean isUnary = false;

        for (int i = 0, len = input.length(); i < len; i++) {

            char c = input.charAt(i);
            Token token = null;

            switch (state) {
                case DEFAULT:
                    buf.setLength(0);
                    tokenPos = 0;
                    isUnary = false;
                    switch (c) {
                        case '$':
                            state = State.IDENTIFIER;
                            break;
                        case '(':
                            token = Token.LEFT_PAREN;
                            break;
                        case ')':
                            token = Token.RIGHT_PAREN;
                            break;
                        case '!':
                            isUnary = lastCode < 0 || ((lastCode & OPS_BITMASK) == 0) || lastCode == LEFT_PAREN;
                        case '=':
                        case '&':
                        case '|':
                            switch (lastCode) {
                                case NOT_EQUAL:
                                case EQUAL:
                                case AND:
                                case OR:
                                    if (!isUnary) {
                                        throw new ExpressionException(INVALID_CHARACTER, c, i);
                                    }
                                default:
                                    switch (c) {
                                        case '!':
                                            state = State.NOT;
                                            break;
                                        case '=':
                                            state = State.EQUAL;
                                            break;
                                        case '&':
                                            state = State.AND;
                                            break;
                                        case '|':
                                            state = State.OR;
                                            break;
                                    }
                            }
                            break;
                        case 'c':
                            state = State.CONTAINS;
                            break;
                        case 't':
                            state = State.TRUE;
                            break;
                        case 'f':
                            state = State.FALSE;
                            break;
                        case 'n':
                            state = State.NULL;
                            break;
                        case '\'':
                            state = State.STRING_LITERAL;
                            break;
                        default:
                            if (Character.isWhitespace(c)) {
                                continue;
                            }
                            throw new ExpressionException(INVALID_CHARACTER, c, i);
                    }
                    break;
                case NOT:
                    if (isUnary) {
                        token = Token.NOT;
                    }
                    switch (c) {
                        case '!':
                            throw new ExpressionException(INVALID_CHARACTER, c, i);
                        case '=':
                            token = Token.NOT_EQUAL;
                            break;
                        default:
                            i--;
                            state = State.DEFAULT;
                    }
                    break;
                case EQUAL:
                case NOT_EQUAL:
                case AND:
                case OR:
                case CONTAINS:
                case TRUE:
                case FALSE:
                case NULL:
                    if (state.token.value.charAt(++tokenPos) == c) {
                        if (tokenPos + 1 == state.token.value.length()) {
                            token = state.token; // operator matched
                        }
                        // keep matching
                    } else {
                        switch (state) {
                            case EQUAL:
                            case NOT_EQUAL:
                            case AND:
                            case OR:
                                throw new ExpressionException(INVALID_CHARACTER, c, i);
                            default:
                                i -= tokenPos;
                                state = State.IDENTIFIER;
                        }
                    }
                    break;
                case STRING_LITERAL:
                    if (c == '\'') {
                        if (i < len - 1) {
                            state = State.STRING_LITERAL_END;
                        } else {
                            token = new Token(buf.toString(), STRING);
                        }
                    } else {
                        buf.append(c);
                    }
                    break;
                case STRING_LITERAL_END:
                    if (c == '\'') {
                        state = State.STRING_LITERAL;
                        buf.append(c);
                    } else {
                        token = new Token(buf.toString(), STRING);
                        i--;
                    }
                    break;
                case IDENTIFIER:
                    if ((int) c == 45 // -
                            || (int) c == 46 // .
                            || (int) c == 95 // _
                            || ((int) c >= 48 && (int) c <= 57) // A-Z
                            || ((int) c >= 97 && (int) c <= 122)) {  // a-z
                        buf.append(c);
                        if (i == len - 1) {
                            token = new Token(buf.toString(), VARIABLE);
                        }
                    } else {
                        token = new Token(buf.toString(), VARIABLE);
                        i--;
                    }
                    break;
                default:
                    throw new ExpressionException(INVALID_STATE, state, i);
            }

            if (token != null) {
                tokens.add(token);
                state = State.DEFAULT;
                code = token.code;
                lastCode = code;
            }
        }
        if (state == State.STRING_LITERAL) {
            throw new ExpressionException(MISSING_CLOSING_QUOTE);
        } else if (state != State.DEFAULT) {
            throw new ExpressionException(INVALID_EXPRESSION);
        }
        return tokens;
    }

    private static void validate(List<Token> tokens) {
        Token last = null;
        for (Token token : tokens) {
            if (last != null) {
                // If the current token is an operand, then the previous
                // token must be an operator (or STARTING)
                if ((token.code & OPS_BITMASK) != 0) {
                    if ((last.code & OPS_BITMASK) != 0) {
                        throw new ExpressionException(MISSING_OPERATOR);
                    }
                } else {
                    if (last.code == token.code && last.code != LEFT_PAREN && last.code != RIGHT_PAREN) {
                        throw new ExpressionException(MISSING_OPERAND);
                    }
                }
            }
            last = token;
        }
    }

    private static List<Token> shuntingYard(List<Token> tokens) {
        List<Token> output = new LinkedList<>();
        Stack<Token> stack = new Stack<>();
        for (Token token : tokens) {
            if ((token.code & OPS_BITMASK) == 0) {
                while (!stack.isEmpty() && (stack.peek().code & OPS_BITMASK) == 0) {
                    Operator cOp = OPERATORS[token.code];
                    Operator lOp = OPERATORS[stack.peek().code];
                    if ((cOp.associativity == Associativity.LEFT && cOp.comparePrecedence(lOp) <= 0) ||
                            (cOp.associativity == Associativity.RIGHT && cOp.comparePrecedence(lOp) < 0)) {
                        output.add(stack.pop());
                        continue;
                    }
                    break;
                }
                stack.push(token);
            } else if (token.code == LEFT_PAREN) {
                stack.push(token);
            } else if (token.code == RIGHT_PAREN) {
                while (!stack.isEmpty() && stack.peek().code != LEFT_PAREN) {
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

    /**
     * Evaluate this expression.
     *
     * @return result of the evaluation
     */
    public boolean eval() {
        return eval(Collections.emptyMap());
    }

    /**
     * Evaluate this expression.
     *
     * @param variables variables
     * @return result of the evaluation
     */
    public boolean eval(Map<String, String> variables) {
        Stack<Token> stack = new Stack<>();
        Token token;
        Token operand1;
        Token operand2;

        try {
            for (Token item : tokens) {
                token = item;

                // Push operands onto stack
                if ((token.code & OPS_BITMASK) != 0) {
                    if (token.code == VARIABLE) {
                        // Expand identifier
                        String value = variables.get(token.value);
                        if (value == null) {
                            stack.push(Token.UNKNOWN);
                        } else {
                            stack.push(new Token(value, STRING));
                        }
                    } else {
                        // A literal operand
                        stack.push(token);
                    }
                    continue;
                }

                // Handle operator. We know we'll need at least one operand
                // so get it now.
                operand1 = stack.pop();

                // Process operator
                switch (token.code) {

                    // For OR, AND, and NOT we have to handle UNKNOWN.
                    case OR:
                        operand2 = stack.pop();
                        //noinspection DuplicatedCode
                        if (operand1.code == TRUE || operand2.code == TRUE) {
                            stack.push(Token.TRUE);
                        } else if (operand1.code == FALSE && operand2.code == FALSE) {
                            stack.push(Token.FALSE);
                        } else {
                            stack.push(Token.UNKNOWN);
                        }
                        break;
                    case AND:
                        operand2 = stack.pop();
                        //noinspection DuplicatedCode
                        if (operand1.code == TRUE && operand2.code == TRUE) {
                            stack.push(Token.TRUE);
                        } else if (operand1.code == FALSE || operand2.code == FALSE) {
                            stack.push(Token.FALSE);
                        } else {
                            stack.push(Token.UNKNOWN);
                        }
                        break;
                    case NOT:
                        if (operand1.code == TRUE) {
                            stack.push(Token.FALSE);
                        } else if (operand1.code == FALSE) {
                            stack.push(Token.TRUE);
                        } else {
                            stack.push(Token.UNKNOWN);
                        }
                        break;
                    case EQUAL:
                        operand2 = stack.pop();
                        if (operand1.equals(operand2)) {
                            stack.push(Token.TRUE);
                        } else {
                            stack.push(Token.FALSE);
                        }
                        break;
                    case NOT_EQUAL:
                        operand2 = stack.pop();
                        if (operand1.equals(operand2)) {
                            stack.push(Token.FALSE);
                        } else {
                            stack.push(Token.TRUE);
                        }
                        break;
                    case IS_NULL:
                        if (operand1.code == UNKNOWN) {
                            stack.push(Token.TRUE);
                        } else {
                            stack.push(Token.FALSE);
                        }
                        break;
                    case IS_NOT_NULL:
                        if (operand1.code != UNKNOWN) {
                            stack.push(Token.TRUE);
                        } else {
                            stack.push(Token.FALSE);
                        }
                        break;
                    default:
                        throw new ExpressionException(UNKNOWN_OPERATOR, token);
                }
            }

            // All done!
            // The top of the stack better hold a boolean!
            token = stack.pop();

        } catch (EmptyStackException e) {
            throw new ExpressionException(MISSING_OPERAND);
        }

        if (!stack.empty()) {
            throw new ExpressionException(MISSING_OPERATOR);
        }

        switch (token.code) {
            case TRUE:
                return true;
            case FALSE:
            case UNKNOWN:
                return false;
            default:
                throw new ExpressionException(NON_BOOLEAN_EXPRESSION);
        }
    }
}
