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

package io.helidon.build.archetype.engine.v2.ast;

import io.helidon.build.common.GenericType;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Stack;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Logical expression.
 */
public final class Expression {

    private static final Map<String, Expression> CACHE = new HashMap<>();
    private static final Map<String, Operator> OPS = Arrays.stream(Operator.values())
                                                           .collect(toMap(op -> op.symbol, Function.identity()));

    private final List<Token> tokens;

    /**
     * Get or create an expression.
     *
     * @param expression expression
     * @return script
     */
    public static Expression create(String expression) {
        return CACHE.computeIfAbsent(expression, Expression::parse);
    }

    private Expression(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * Evaluate this expression.
     *
     * @return result
     */
    public boolean eval() {
        return eval(s -> null);
    }

    /**
     * Evaluate this expression.
     *
     * @param resolver variable resolver
     * @return result
     */
    public boolean eval(Function<String, Value> resolver) {
        Deque<Value> stack = new ArrayDeque<>();
        for (Token token : tokens) {
            Value value;
            if (token.operator != null) {
                boolean result;
                Value operand1 = stack.pop();
                if (token.operator == Operator.NOT) {
                    result = !operand1.asBoolean();
                } else {
                    Value operand2 = stack.pop();
                    switch (token.operator) {
                        case OR:
                            result = operand2.asBoolean() || operand1.asBoolean();
                            break;
                        case AND:
                            result = operand2.asBoolean() && operand1.asBoolean();
                            break;
                        case EQUAL:
                            result = operand2.unwrap().equals(operand1.unwrap());
                            break;
                        case NOT_EQUAL:
                            result = !operand2.unwrap().equals(operand1.unwrap());
                            break;
                        case CONTAINS:
                            result = operand2.asList().contains(operand1.asString());
                            break;
                        default:
                            throw new IllegalStateException("Unsupported operator: " + token.operator);
                    }
                }
                value = Value.create(result);
            } else if (token.operand != null) {
                value = token.operand;
            } else if (token.variable != null) {
                value = resolver.apply(token.variable);
                if (value == null) {
                    throw new UnresolvedVariableException("Unresolved variable: " + token.variable);
                }
            } else {
                throw new IllegalStateException("Invalid token");
            }
            stack.push(value);
        }
        return stack.pop().asBoolean();
    }

    /**
     * Unresolved variable error.
     */
    public static final class UnresolvedVariableException extends RuntimeException {

        private UnresolvedVariableException(String message) {
            super(message);
        }
    }

    /**
     * Expression formatting error.
     */
    public static final class FormatException extends RuntimeException {

        private FormatException(String message) {
            super(message);
        }
    }

    /**
     * Parse the given expression.
     *
     * @param expression expression
     * @return logical expression
     */
    public static Expression parse(String expression) {

        // raw infix tokens
        Spliterator<Symbol> spliterator = spliteratorUnknownSize(new Tokenizer(expression), ORDERED);
        List<Symbol> symbols = StreamSupport.stream(spliterator, false).collect(toList());

        // used for validation
        int stackSize = 0;

        List<Token> tokens = new LinkedList<>();
        Stack<Symbol> stack = new Stack<>();

        // shunting yard, convert infix to rpn
        ListIterator<Symbol> it = symbols.listIterator();
        while (it.hasNext()) {
            int previous = it.previousIndex();
            Symbol symbol = it.next();
            switch (symbol.type) {
                case BINARY_LOGICAL_OPERATOR:
                case UNARY_LOGICAL_OPERATOR:
                case EQUALITY_OPERATOR:
                case CONTAINS_OPERATOR:
                    if (previous >= 0 && symbols.get(previous).value.equals("(")) {
                        throw new FormatException("Invalid parenthesis");
                    }
                    while (!stack.isEmpty() && OPS.containsKey(stack.peek().value)) {
                        Operator currentOp = OPS.get(symbol.value);
                        Operator leftOp = OPS.get(stack.peek().value);
                        if ((leftOp.precedence >= currentOp.precedence)) {
                            stackSize += 1 - addToken(stack.pop(), tokens);
                            continue;
                        }
                        break;
                    }
                    stack.push(symbol);
                    break;
                case PARENTHESIS:
                    if ("(".equals(symbol.value)) {
                        stack.push(symbol);
                    } else if (")".equals(symbol.value)) {
                        while (!stack.isEmpty() && !stack.peek().value.equals("(")) {
                            stackSize += 1 - addToken(stack.pop(), tokens);
                        }
                        if (stack.isEmpty()) {
                            throw new FormatException("Unmatched parenthesis");
                        }
                        stack.pop();
                    } else {
                        throw new IllegalStateException("Unexpected symbol: " + symbol.value);
                    }
                    break;
                case BOOLEAN:
                case STRING:
                case ARRAY:
                case VARIABLE:
                    stackSize += 1 - addToken(symbol, tokens);
                    break;
                default:
                    throw new IllegalStateException("Unexpected symbol: " + symbol.value);
            }
        }
        while (!stack.isEmpty()) {
            stackSize += 1 - addToken(stack.pop(), tokens);
        }
        if (stackSize != 1) {
            throw new FormatException(String.format("Invalid expression: { %s }", expression));
        }
        return new Expression(tokens);
    }

    private static int addToken(Symbol symbol, List<Token> tokens) {
        Token token = Token.create(symbol);
        int valence;
        if (token.operator != null) {
            if (tokens.isEmpty()) {
                throw new FormatException("Missing operand");
            }
            Token op1 = tokens.get(tokens.size() - 1);
            if (token.operator == Operator.NOT) {
                if (op1.operand != null) {
                    GenericType<?> type = op1.operand.type();
                    if (type != null && type != ValueTypes.BOOLEAN) {
                        throw new FormatException("Invalid operand");
                    }
                }
                valence = 1;
            } else {
                if (tokens.size() < 2) {
                    throw new FormatException("Missing operand");
                }
                valence = 2;
            }
        } else {
            valence = 0;
        }
        tokens.add(token);
        return valence;
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

    private static final class Token {

        static final Pattern ARRAY_PATTERN = Pattern.compile("(?<element>'[^']*')((\\s*,\\s*)|(\\s*]))");
        static final Pattern VAR_PATTERN = Pattern.compile("^\\$\\{(?<varName>[\\w.-]+)}");

        final Operator operator;
        final String variable;
        final Value operand;

        Token(Operator operator, String variable, Value operand) {
            this.operator = operator;
            this.variable = variable;
            this.operand = operand;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder().append("Token{ ");
            if (operator != null) {
                sb.append(operator);
            } else if (operand != null) {
                sb.append(operand);
            } else {
                sb.append("${").append(variable).append("}");
            }
            return sb.append(" }").toString();
        }

        static List<String> parseArray(String symbol) {
            return ARRAY_PATTERN.matcher(symbol)
                                .results()
                                .map(r -> r.group(1))
                                .map(s -> s.substring(1, s.length() - 1))
                                .collect(toList());
        }

        static String parseVariable(String symbol) {
            return VAR_PATTERN.matcher(symbol)
                              .results()
                              .map(r -> r.group(1))
                              .findFirst()
                              .orElseThrow(() -> new IllegalArgumentException(
                                      "Incorrect variable name: " + symbol));
        }

        static Token create(Symbol symbol) {
            switch (symbol.type) {
                case BINARY_LOGICAL_OPERATOR:
                case UNARY_LOGICAL_OPERATOR:
                case EQUALITY_OPERATOR:
                case CONTAINS_OPERATOR:
                    return new Token(OPS.get(symbol.value), null, null);
                case BOOLEAN:
                    return new Token(null, null, Value.create(Boolean.parseBoolean(symbol.value)));
                case STRING:
                    return new Token(null, null, Value.create(symbol.value.substring(1, symbol.value.length() - 1)));
                case ARRAY:
                    return new Token(null, null, Value.create(parseArray(symbol.value)));
                case VARIABLE:
                    return new Token(null, parseVariable(symbol.value), null);
                case PARENTHESIS:
                    throw new FormatException("Unmatched parenthesis");
                default:
                    throw new IllegalStateException("Unexpected symbol" + symbol.value);
            }
        }
    }

    private static final class Symbol {

        final Type type;
        final String value;

        Symbol(Type type, String value) {
            this.type = type;
            this.value = value;
        }

        enum Type {
            SKIP("^\\s+"),
            ARRAY("^\\[[^]\\[]*]"),
            BOOLEAN("^(true|false)"),
            STRING("^['\"][^'\"]*['\"]"),
            VARIABLE("^\\$\\{(?<varName>[\\w.-]+)}"),
            EQUALITY_OPERATOR("^(!=|==)"),
            BINARY_LOGICAL_OPERATOR("^(\\|\\||&&)"),
            UNARY_LOGICAL_OPERATOR("^[!]"),
            CONTAINS_OPERATOR("^contains"),
            PARENTHESIS("^[()]");

            private final Pattern pattern;

            Type(String regex) {
                this.pattern = Pattern.compile(regex);
            }
        }

        @Override
        public String toString() {
            return "Symbol{ " + value + " }";
        }
    }

    private static final class Tokenizer implements Iterator<Symbol> {

        final String line;
        int cursor;

        Tokenizer(String line) {
            this.line = line;
            this.cursor = 0;
        }

        @Override
        public boolean hasNext() {
            return cursor < line.length();
        }

        @Override
        public Symbol next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String current = line.substring(cursor);
            for (Symbol.Type type : Symbol.Type.values()) {
                Matcher matcher = type.pattern.matcher(current);
                if (matcher.find()) {
                    String value = matcher.group();
                    cursor += value.length();
                    if (type == Symbol.Type.SKIP) {
                        return next();
                    }
                    return new Symbol(type, value);
                }
            }
            throw new FormatException("Unexpected token - " + current);
        }
    }
}
