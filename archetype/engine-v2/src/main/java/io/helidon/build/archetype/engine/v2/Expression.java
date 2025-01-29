/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Stack;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.helidon.build.common.LazyValue;
import io.helidon.build.common.Lists;
import io.helidon.build.common.Maps;

import static java.util.Collections.unmodifiableList;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Logical expression.
 */
public final class Expression implements Comparable<Expression> {

    /**
     * True.
     */
    public static final Expression TRUE = new Expression(List.of(Token.TRUE), true);

    /**
     * False.
     */
    public static final Expression FALSE = new Expression(List.of(Token.FALSE), true);

    private static final Map<String, Expression> CACHE = new HashMap<>();
    private static final Map<String, Operator> OPS = Arrays.stream(Operator.values())
            .collect(toMap(op -> op.symbol, Function.identity()));

    static {
        CACHE.put("true", TRUE);
        CACHE.put("false", FALSE);
    }

    private final List<Token> tokens;
    private final LazyValue<String> literal = new LazyValue<>(this::print);
    private final LazyValue<Expression> reduced0 = new LazyValue<>(this::reduce0);
    private final LazyValue<Set<String>> variables = new LazyValue<>(this::variables0);
    private final boolean reduced;

    Expression(String expression) {
        this(parse(expression), false);
    }

    private Expression(List<Token> tokens, boolean reduced) {
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Empty expression");
        }
        this.tokens = unmodifiableList(tokens);
        this.reduced = reduced;
    }

    /**
     * Get or create an expression.
     *
     * @param expression expression
     * @return Expression
     */
    public static Expression create(String expression) {
        return CACHE.computeIfAbsent(expression, Expression::new);
    }

    /**
     * Negate the given expression.
     *
     * @param expr expression
     * @return Expression
     */
    public static Expression not(Expression expr) {
        if (expr == TRUE) {
            return FALSE;
        } else if (expr == FALSE) {
            return TRUE;
        } else {
            return new Expression(Lists.addAll(expr.tokens, Token.NOT), false);
        }
    }

    /**
     * Combine this expression and the given expression with the logical 'and' operator.
     *
     * @param expr expression
     * @return Expression
     */
    public Expression and(Expression expr) {
        if (expr == TRUE || this == FALSE) {
            return this;
        } else if (this == TRUE) {
            return expr;
        } else {
            return new Expression(Lists.addAll(tokens, expr.tokens, Token.AND), false);
        }
    }

    /**
     * Combine this expression and the given expression with the logical 'or' operator.
     *
     * @param expr expression
     * @return Expression
     */
    public Expression or(Expression expr) {
        if (expr == FALSE || this == TRUE) {
            return this;
        } else if (this == FALSE) {
            return expr;
        } else {
            return new Expression(Lists.addAll(tokens, expr.tokens, Token.OR), false);
        }
    }

    /**
     * Get the expression tokens.
     *
     * @return list of tokens
     */
    public List<Token> tokens() {
        return tokens;
    }

    /**
     * Get the variable names in this expression.
     *
     * @return variable names
     */
    public Set<String> variables() {
        return variables.get();
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
     * @param variables variables
     * @return result
     * @throws UnresolvedVariableException if a variable is unresolved
     */
    public boolean eval(Map<String, String> variables) {
        return eval(s -> {
            String v = variables.get(s);
            if (v != null) {
                return Value.dynamic(v);
            }
            return null;
        });
    }

    /**
     * Evaluate this expression.
     *
     * @param resolver variable resolver
     * @return result
     * @throws UnresolvedVariableException if {@code resolver} returns {@code null}
     */
    public boolean eval(Function<String, Value<?>> resolver) {
        Deque<Value<?>> stack = new ArrayDeque<>();
        for (Token token : tokens) {
            Value<?> value;
            if (token.operator != null) {
                Value<?> op1 = stack.pop();
                switch (token.operator) {
                    case NOT:
                        value = Value.of(!op1.getBoolean());
                        break;
                    case SIZEOF:
                        if (op1.type() == Value.Type.LIST) {
                            value = Value.of(op1.getList().size());
                        } else {
                            value = Value.of(op1.getString().length());
                        }
                        break;
                    case AS_INT:
                        value = Value.of(op1.getInt());
                        break;
                    case AS_LIST:
                        value = Value.of(op1.getList());
                        break;
                    case AS_STRING:
                        value = Value.of(op1.getString());
                        break;
                    default:
                        Value<?> op2 = stack.pop();
                        switch (token.operator) {
                            case OR:
                                value = Value.of(op2.asBoolean().orElse(false) || op1.asBoolean().orElse(false));
                                break;
                            case AND:
                                value = Value.of(op2.asBoolean().orElse(false) && op1.asBoolean().orElse(false));
                                break;
                            case EQUAL:
                                value = Value.of(Value.isEqual(op2, op1));
                                break;
                            case NOT_EQUAL:
                                value = Value.of(!Value.isEqual(op2, op1));
                                break;
                            case CONTAINS:
                                if (op1.type() == Value.Type.LIST) {
                                    value = Value.of(new HashSet<>(op2.getList()).containsAll(op1.getList()));
                                } else if (op2.type() == Value.Type.LIST) {
                                    value = Value.of(op2.getList().contains(op1.asString().orElse(null)));
                                } else {
                                    value = Value.of(op1.isPresent()
                                                     && op2.asString().orElse("").contains(op1.getString()));
                                }
                                break;
                            default:
                                throw new IllegalStateException("Unsupported operator: " + token.operator);
                        }
                }
            } else if (token.operand != null) {
                value = token.operand;
            } else if (token.variable != null) {
                value = resolver.apply(token.variable);
                if (value == null) {
                    throw new UnresolvedVariableException(token.variable);
                }
            } else {
                throw new IllegalStateException("Invalid token");
            }
            stack.push(value);
        }
        return stack.pop().asBoolean().get();
    }

    /**
     * Reduce the expression.
     *
     * @return Expression
     */
    public Expression reduce() {
        return reduced ? this : reduced0.get();
    }

    /**
     * Return a sub expression.
     *
     * @param expr expression
     * @return sub expression
     */
    public Expression sub(Expression expr) {
        Map<String, List<Token>> v1 = new TreeMap<>();
        Expression e1 = synthetic(v1);
        if (v1.isEmpty()) {
            return e1.eval() ? TRUE : FALSE;
        }

        Map<String, List<Token>> v2 = new TreeMap<>();
        Expression e2 = expr.synthetic(v2);
        if (v2.isEmpty() || !Lists.allMatch(v2.keySet(), v1::containsKey)) {
            return this;
        }

        // evaluate the truth tables
        BitSet m2 = e2.minterms(v2.keySet());

        // v2 variables first (high bits) then v1 variables
        Map<String, List<Token>> vars = Maps.merge(List.of(v2, v1));
        BitSet m1 = e1.minterms(vars.keySet());

        // create a sub truth table
        BitSet minterms = new BitSet();

        int offset = v1.size() - v2.size();
        int mask = (1 << offset) - 1; // bits exclusive to the 1st table
        int tableSize2 = 1 << v2.size(); // size of the 2nd table
        for (int row1 = m1.nextSetBit(0); row1 >= 0; row1 = m1.nextSetBit(row1 + 1)) {
            int row2 = row1 >> offset;
            if (m2.get(row2)) {
                // make the exclusive bits always true
                // go through the 2nd table as prefixes
                int x = row1 & mask;
                for (int y = 0; y < tableSize2; y++) {
                    minterms.set((y << offset) | x);
                }
            }
            minterms.set(row1);
        }

        if (minterms.isEmpty() || minterms.cardinality() == (1 << vars.size())) {
            // always true
            return TRUE;
        }
        return reduce(vars, minterms);
    }

    /**
     * Get the expression literal.
     *
     * @return expression literal
     */
    public String literal() {
        return literal.get();
    }

    @Override
    public String toString() {
        return "Expression{"
               + "tokens=" + tokens
               + '}';
    }

    @Override
    public int compareTo(Expression o) {
        return Lists.compare(tokens, o.tokens);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Expression)) {
            return false;
        }
        Expression other = (Expression) o;
        return tokens.equals(other.tokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokens);
    }

    // QMC algorithm
    static int[][] reduce(int... minterms) {
        if (minterms.length > 64) {
            // using long to bitmap the minterms indexes
            throw new IllegalArgumentException("Too many minterms");
        }
        TermTable table = new TermTable();

        // sort by bit count
        BitSet positions = new BitSet(); // bitmap of sorted minterms
        int numVars = 32 - Integer.numberOfLeadingZeros(Math.max(minterms[minterms.length - 1], 1));
        for (int x = 0, y = 0; x <= numVars && y < minterms.length; x++) {
            int z = y;
            for (int i = positions.nextClearBit(0); i < minterms.length; i = positions.nextClearBit(i + 1)) {
                int term = minterms[i];
                if (x == Integer.bitCount(term)) {
                    table.terms.add(new Term(term, 0, 1L << i));
                    positions.set(i);
                    y++;
                    if (x == 0) {
                        break;
                    }
                }
            }
            if (y > z) {
                table.groups.add(y);
            }
        }

        // find implicants
        TermTable tmp = new TermTable(); // write-table
        while (true) {
            tmp.clear();
            long bitmap = 0;
            for (int g = 0, i1 = 0; g < table.groups.size(); g++) { // for-each group
                boolean addGroup = false;
                for (int l1 = table.groups.get(g); i1 < l1; i1++) {
                    Term term1 = table.terms.get(i1);
                    boolean merged = false;
                    if (g + 1 < table.groups.size()) { // compare nth and nth+1
                        for (int i2 = l1, l2 = table.groups.get(g + 1); i2 < l2; i2++) {
                            Term term2 = table.terms.get(i2);
                            if (term1.mark == term2.mark) {
                                int delta = term1.bits ^ term2.bits;
                                if (Integer.bitCount(delta) == 1) {
                                    long ids = term1.ids | term2.ids; // merge the ids
                                    if (!tmp.contains(ids)) { // add once
                                        int mark = delta | term1.mark | term2.mark; // merge the marks
                                        int bits = (term1.bits | term2.bits) & ~mark; // merge the bits (exclude the marks)
                                        tmp.terms.add(new Term(bits, mark, ids));
                                        bitmap |= ids;
                                        merged = true;
                                    }
                                }
                            }
                        }
                    }
                    if (!merged && (bitmap & term1.ids) == 0) {
                        // not mergeable and not covered by any other term
                        tmp.terms.add(term1);
                    }
                    addGroup |= merged;
                }
                if (addGroup) {
                    tmp.groups.add(tmp.terms.size());
                }
            }
            if (!tmp.terms.isEmpty()) {
                // swap tables
                TermTable next = tmp;
                tmp = table;
                table = next;
            } else {
                break;
            }
        }

        // prime chart
        long essentials = 0;
        long duplicates = 0;
        for (Term term : table.terms) {
            if ((term.ids & essentials) != 0) {
                duplicates |= (term.ids & essentials); // record used terms
                essentials &= ~duplicates; // remove duplicates from essentials
                essentials |= (term.ids & ~duplicates); // record unused terms
            } else {
                essentials |= term.ids;
            }
        }

        // compute the result as an array of [bits][marks]
        List<int[]> result = new ArrayList<>(table.terms.size());
        for (int i = 0; i < table.terms.size(); i++) {
            Term term = table.terms.get(i);
            if ((term.ids & essentials) != 0) {
                result.add(new int[] {
                        term.bits,
                        term.mark,
                });
            }
        }
        return result.toArray(new int[0][]);
    }

    private static Expression reduce(Map<String, List<Token>> vars, BitSet minterms) {
        List<Token> tokens = new ArrayList<>();
        int[][] terms = reduce(minterms.stream().toArray());
        for (int i = 0; i < terms.length; i++) {
            // associate bits with variables from high to low
            int bit = 1 << vars.size() - 1;
            int y = 0;
            for (List<Token> v : vars.values()) {
                if ((bit & terms[i][1]) == 0) {
                    // bit is not marked
                    tokens.addAll(v);
                    if ((bit & terms[i][0]) == 0) {
                        tokens.add(Token.NOT);
                    }
                    if (y++ > 0) {
                        tokens.add(Token.AND);
                    }
                }
                bit >>>= 1;
            }
            if (i > 0) {
                tokens.add(Token.OR);
            }
        }
        return new Expression(tokens, true);
    }

    /**
     * Unresolved variable error.
     */
    public static final class UnresolvedVariableException extends RuntimeException {
        private final String variable;

        private UnresolvedVariableException(String variable) {
            super("Unresolved variable: " + variable);
            this.variable = variable;
        }

        /**
         * Get the unresolved variable name.
         *
         * @return variable name
         */
        public String variable() {
            return variable;
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

    private String print() {
        Deque<String> stack = new ArrayDeque<>();
        Deque<Integer> ops = new ArrayDeque<>();
        for (Token token : tokens) {
            if (token.operator != null) {
                String op1 = stack.pop();
                int pr1 = ops.pop();
                if (token.operator.precedence >= pr1) {
                    op1 = "(" + op1 + ")";
                }
                if (token.operator == Operator.NOT) {
                    stack.push(token + op1);
                } else {
                    String op2 = stack.pop();
                    int pr2 = ops.pop();
                    if (token.operator.precedence > pr2) {
                        op2 = "(" + op2 + ")";
                    }
                    stack.push(op2 + " " + token + " " + op1);
                }
                ops.push(token.operator.precedence);
            } else {
                stack.push(token.toString());
                ops.push(Integer.MAX_VALUE);
            }
        }
        return stack.peek();
    }

    private Set<String> variables0() {
        Set<String> variables = new HashSet<>();
        for (Token token : tokens) {
            if (token.isVariable()) {
                variables.add(token.variable);
            }
        }
        return variables;
    }

    private Expression reduce0() {
        if (this == TRUE) {
            return TRUE;
        } else if (this == FALSE) {
            return FALSE;
        }

        Map<String, List<Token>> vars = new TreeMap<>();

        // ensure a boolean-only expression and record variables
        Expression expr = synthetic(vars);

        // constant
        int numVars = vars.size();
        if (numVars == 0) {
            return expr.eval() ? TRUE : FALSE;
        }

        // evaluate the truth table
        BitSet minterms = expr.minterms(vars.keySet());

        // always false
        if (minterms.isEmpty()) {
            return FALSE;
        }

        // always true
        if (minterms.cardinality() == (1 << numVars)) {
            return TRUE;
        }

        // QMC resolution
        return reduce(vars, minterms);
    }

    private List<Token> expandVar(String varName, Map<String, List<Token>> vars) {
        List<Token> value = vars.get(varName);
        if (value == null) {
            return List.of(Token.of(varName));
        } else {
            List<Token> tokens = new ArrayList<>();
            for (Token token : value) {
                if (token.variable != null) {
                    tokens.addAll(expandVar(token.variable, vars));
                } else {
                    tokens.add(token);
                }
            }
            return tokens;
        }
    }

    private Expression synthetic(Map<String, List<Token>> vars) {
        Map<String, List<Token>> tempVars = new TreeMap<>();
        Deque<List<Token>> stack = new ArrayDeque<>();
        for (Token token : tokens) {
            if (token.operator != null) {
                List<Token> op1 = stack.pop();
                Token t1 = op1.get(0);
                String s1 = t1.variable != null ? t1.variable : t1.toString();
                String varName;
                switch (token.operator) {
                    case NOT:
                        stack.push(Lists.addAll(op1, token));
                        break;
                    case SIZEOF:
                    case AS_INT:
                    case AS_LIST:
                    case AS_STRING:
                        // t1 is always a variable
                        varName = token.toString() + ' ' + s1;
                        tempVars.putIfAbsent(varName, Lists.addAll(vars.getOrDefault(s1, op1), token));
                        stack.push(List.of(Token.of(varName)));
                        break;
                    case CONTAINS:
                    case EQUAL:
                    case NOT_EQUAL:
                        List<Token> op2 = stack.pop();
                        Token t2 = op2.get(0);
                        if (t1.variable != null || t2.variable != null) {
                            String s2 = t2.variable != null ? t2.variable : t2.toString();
                            op2 = tempVars.getOrDefault(s2, op2);
                            op1 = tempVars.getOrDefault(s1, op1);
                            varName = s2 + ' ' + token + ' ' + s1;
                            tempVars.putIfAbsent(varName, Lists.addAll(op2, op1, token));
                            stack.push(List.of(Token.of(varName)));
                        } else {
                            stack.push(Lists.addAll(op2, op1, token));
                        }
                        break;
                    default:
                        stack.push(Lists.addAll(stack.pop(), op1, token));
                }
            } else {
                stack.push(List.of(token));
            }
        }

        List<Token> expr = new ArrayList<>();
        while (!stack.isEmpty()) {
            for (Token token : stack.pop()) {
                if (token.variable != null) {
                    vars.putIfAbsent(token.variable, expandVar(token.variable, tempVars));
                }
                expr.add(token);
            }
        }
        return new Expression(expr, false);
    }

    private BitSet minterms(Set<String> names) {
        BitSet minterms = new BitSet();

        // evaluate the truth table
        int tableSize = 1 << names.size(); // 2 ^ length
        for (int y = 0; y < tableSize; y++) {

            // associate bits with variables from high to low
            int x = 1 << names.size() - 1;
            Map<String, Value<Boolean>> vars = new HashMap<>();
            for (String varName : names) {
                vars.put(varName, Value.of((y & x) != 0));
                x >>>= 1;
            }

            // record only when successful
            if (eval(vars::get)) {
                minterms.set(y);
            }
        }
        return minterms;
    }

    private static List<Token> parse(String expression) {
        // raw infix tokens
        Spliterator<Symbol> spliterator = spliteratorUnknownSize(new Tokenizer(expression), ORDERED);
        List<Symbol> symbols = StreamSupport.stream(spliterator, false).collect(toList());

        // used for validation
        int stackSize = 0;

        List<Token> tokens = new ArrayList<>();
        Stack<Symbol> stack = new Stack<>();

        // shunting yard, convert infix to rpn
        ListIterator<Symbol> it = symbols.listIterator();
        while (it.hasNext()) {
            int previous = it.previousIndex();
            Symbol symbol = it.next();
            switch (symbol.type) {
                case BINARY_OPERATOR:
                case UNARY_OPERATOR:
                    if (symbol.type != Symbol.Type.UNARY_OPERATOR
                        && previous >= 0 && symbols.get(previous).value.equals("(")) {
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
                case INT:
                case VARIABLE:
                    stackSize += 1 - addToken(symbol, tokens);
                    break;
                case SKIP:
                case COMMENT:
                    break;
                default:
                    throw new IllegalStateException("Unexpected symbol: " + symbol.value);
            }
        }
        while (!stack.isEmpty()) {
            stackSize += 1 - addToken(stack.pop(), tokens);
        }
        if (stackSize != 1) {
            throw new FormatException(String.format("Invalid expression: '%s'", expression));
        }
        return tokens;
    }

    private static int addToken(Symbol symbol, List<Token> tokens) {
        Token token = Token.of(symbol);
        int valence = 0;
        if (token.operator != null) {
            if (tokens.isEmpty()) {
                throw new FormatException("Missing operand");
            }
            Token op1 = tokens.get(tokens.size() - 1);
            switch (token.operator) {
                case NOT:
                    if (op1.operand != null && !(op1.operand.type() == Value.Type.BOOLEAN)) {
                        throw new FormatException("Invalid operand");
                    }
                    valence = 1;
                    break;
                case SIZEOF:
                    valence = 1;
                    break;
                case AS_LIST:
                case AS_INT:
                case AS_STRING:
                    if (op1.variable == null) {
                        throw new FormatException("Invalid operand");
                    }
                    valence = 1;
                    break;
                case EQUAL:
                case NOT_EQUAL:
                case AND:
                case OR:
                case CONTAINS:
                    if (tokens.size() < 2) {
                        throw new FormatException("Missing operand");
                    }
                    valence = 2;
                    break;
                default:
            }
        }
        tokens.add(token);
        return valence;
    }

    /**
     * Expression operator.
     */
    public enum Operator {
        /**
         * Equal operator.
         */
        EQUAL(8, "=="),

        /**
         * Not equal operator.
         */
        NOT_EQUAL(8, "!="),

        /**
         * And operator.
         */
        AND(4, "&&"),

        /**
         * Or operator.
         */
        OR(3, "||"),

        /**
         * Contains operator.
         */
        CONTAINS(9, "contains"),

        /**
         * As-list operator.
         */
        AS_LIST(14, "(list)"),

        /**
         * As-string operator.
         */
        AS_STRING(14, "(string)"),

        /**
         * As-int operator.
         */
        AS_INT(14, "(int)"),

        /**
         * Sizeof operator.
         */
        SIZEOF(14, "sizeof"),

        /**
         * Not operator.
         */
        NOT(13, "!");

        private final int precedence;
        private final String symbol;

        Operator(int precedence, String symbol) {
            this.precedence = precedence;
            this.symbol = symbol;
        }

        /**
         * Get the operator symbol.
         *
         * @return symbol
         */
        public String symbol() {
            return symbol;
        }
    }

    /**
     * Expression token.
     */
    public static final class Token implements Comparable<Token> {

        private static final Pattern ARRAY_PATTERN = Pattern.compile("(?<element>'[^']*')((\\s*,\\s*)|(\\s*]))");
        private static final Pattern VAR_PATTERN = Pattern.compile("^\\$\\{(?<varName>~?[\\w.-]+)}");
        private static final Token TRUE = new Token(null, null, Value.TRUE);
        private static final Token FALSE = new Token(null, null, Value.FALSE);
        private static final Token AND = new Token(Operator.AND, null, null);
        private static final Token OR = new Token(Operator.OR, null, null);
        private static final Token NOT = new Token(Operator.NOT, null, null);
        private static final Token EQUAL = new Token(Operator.EQUAL, null, null);
        private static final Token NOT_EQUAL = new Token(Operator.NOT_EQUAL, null, null);
        private static final Token CONTAINS = new Token(Operator.CONTAINS, null, null);
        private static final Token SIZEOF = new Token(Operator.SIZEOF, null, null);
        private static final Token AS_STRING = new Token(Operator.AS_STRING, null, null);
        private static final Token AS_INT = new Token(Operator.AS_INT, null, null);
        private static final Token AS_LIST = new Token(Operator.AS_LIST, null, null);

        private final Operator operator;
        private final String variable;
        private final Value<?> operand;

        private Token(Operator operator, String variable, Value<?> operand) {
            this.operator = operator;
            this.variable = variable;
            this.operand = operand;
        }

        /**
         * Indicate if this token represents an operator.
         *
         * @return {@code true} if an operator, {@code false} otherwise
         */
        public boolean isOperator() {
            return operator != null;
        }

        /**
         * Indicate if this token represents an operand.
         *
         * @return {@code true} if an operand, {@code false} otherwise
         */
        public boolean isOperand() {
            return operand != null;
        }

        /**
         * Indicate if this token represents a variable.
         *
         * @return {@code true} if a variable, {@code false} otherwise
         */
        public boolean isVariable() {
            return variable != null;
        }

        /**
         * Get the operator.
         *
         * @return Operator
         */
        public Operator operator() {
            if (operator == null) {
                throw new IllegalStateException("Token is not an operator: " + this);
            }
            return operator;
        }

        /**
         * Get the operand.
         *
         * @return Value
         */
        public Value<?> operand() {
            if (operand == null) {
                throw new IllegalStateException("Token is not an operand: " + this);
            }
            return operand;
        }

        /**
         * Get the variable.
         *
         * @return String
         */
        public String variable() {
            if (variable == null) {
                throw new IllegalStateException("Token is not a variable: " + this);
            }
            return variable;
        }

        /**
         * Create a new operator token.
         *
         * @param operator operator
         * @return Token
         */
        public static Token of(Operator operator) {
            switch (operator) {
                case EQUAL:
                    return EQUAL;
                case NOT_EQUAL:
                    return NOT_EQUAL;
                case AND:
                    return AND;
                case OR:
                    return OR;
                case NOT:
                    return NOT;
                case CONTAINS:
                    return CONTAINS;
                case SIZEOF:
                    return SIZEOF;
                case AS_STRING:
                    return AS_STRING;
                case AS_INT:
                    return AS_INT;
                case AS_LIST:
                    return AS_LIST;
                default:
                    throw new IllegalStateException("Unexpected operator: " + operator);
            }
        }

        /**
         * Create a new variable token.
         *
         * @param variable variable
         * @return Token
         */
        public static Token of(String variable) {
            return new Token(null, variable, null);
        }

        /**
         * Create a new operand token.
         *
         * @param value value
         * @return Token
         */
        public static Token of(Value<?> value) {
            return new Token(null, null, value);
        }

        /**
         * Create a new boolean operand token.
         *
         * @param value value
         * @return Token
         */
        public static Token of(boolean value) {
            return value ? TRUE : FALSE;
        }

        @Override
        public int compareTo(Token o) {
            if (operator != null) {
                return o.operator != null ? operator.compareTo(o.operator) : 1;
            }
            if (operand != null) {
                return o.operand != null ? Value.compare(operand, o.operand) : -1;
            }
            if (variable != null) {
                return o.variable != null ? variable.compareTo(o.variable) : -1;
            }
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Token)) {
                return false;
            }
            Token token = (Token) o;
            return operator == token.operator
                   && Objects.equals(variable, token.variable)
                   && Value.isEqual(operand, token.operand);
        }

        @Override
        public int hashCode() {
            return Objects.hash(operator, variable, operand);
        }

        @Override
        public String toString() {
            if (operand != null) {
                switch (operand.type()) {
                    case STRING:
                        return "'" + operand.getString() + "'";
                    case BOOLEAN:
                        return String.valueOf(operand.getBoolean());
                    case INTEGER:
                        return String.valueOf(operand.getInt());
                    case LIST:
                        return "["
                               + operand.getList().stream()
                                       .map(s -> "'" + s + "'")
                                       .collect(Collectors.joining(","))
                               + "]";
                    default:
                        throw new IllegalStateException("Unexpected operand type: " + operand.type());
                }
            } else if (variable != null) {
                return "${" + variable + "}";
            } else if (operator != null) {
                return operator.symbol();
            } else {
                return "?";
            }
        }

        private static List<String> parseArray(String symbol) {
            return ARRAY_PATTERN.matcher(symbol)
                    .results()
                    .map(r -> r.group(1))
                    .map(s -> s.substring(1, s.length() - 1))
                    .collect(toList());
        }

        private static String parseVariable(String symbol) {
            return VAR_PATTERN.matcher(symbol)
                    .results()
                    .map(r -> r.group(1))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Incorrect variable name: " + symbol));
        }

        private static Token of(Symbol symbol) {
            switch (symbol.type) {
                case BINARY_OPERATOR:
                case UNARY_OPERATOR:
                    return Token.of(OPS.get(symbol.value));
                case BOOLEAN:
                    return Token.of(Boolean.parseBoolean(symbol.value));
                case STRING:
                    return Token.of(Value.of(symbol.value.substring(1, symbol.value.length() - 1)));
                case INT:
                    return Token.of(Value.of(Integer.parseInt(symbol.value)));
                case ARRAY:
                    return Token.of(Value.of(parseArray(symbol.value)));
                case VARIABLE:
                    return Token.of(parseVariable(symbol.value));
                case PARENTHESIS:
                    throw new FormatException("Unmatched parenthesis");
                default:
                    throw new IllegalStateException("Unexpected symbol" + symbol.value);
            }
        }
    }

    private static final class Symbol {

        private final Type type;
        private final String value;

        Symbol(Type type, String value) {
            this.type = type;
            this.value = value;
        }

        enum Type {
            SKIP("^\\s+"),
            ARRAY("^\\[[^]\\[]*]"),
            BOOLEAN("^(true|false)"),
            STRING("^['\"][^'\"]*['\"]"),
            INT("^[0-9]+"),
            VARIABLE("^\\$\\{(?<varName>~?[\\w.-]+)}"),
            BINARY_OPERATOR("^(!=|==|\\|\\||&&|contains)"),
            UNARY_OPERATOR("^(!|\\(list\\)|\\(string\\)|\\(int\\)|sizeof)"),
            PARENTHESIS("^[()]"),
            COMMENT("#.*\\R");

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

        private final String line;
        private int cursor;

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
                    return new Symbol(type, value);
                }
            }
            throw new FormatException("Unexpected token: " + current);
        }
    }

    private static final class Term {
        private final int mark;
        private final int bits;
        private final long ids;

        Term(int bits, int mark, long ids) {
            this.bits = bits;
            this.mark = mark;
            this.ids = ids;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Term)) {
                return false;
            }
            return ids == ((Term) o).ids;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(ids);
        }
    }

    private static final class TermTable {
        private final List<Term> terms = new ArrayList<>();
        private final List<Integer> groups = new ArrayList<>();

        void clear() {
            terms.clear();
            groups.clear();
        }

        boolean contains(long ids) {
            int numGroups = groups.size();
            int startIndex = numGroups == 0 ? 0 : groups.get(numGroups - 1);
            int endIndex = numGroups > 1 ? groups.get(numGroups - 2) : terms.size();
            for (int i = startIndex; i < endIndex; i++) {
                if (terms.get(i).ids == ids) {
                    return true;
                }
            }
            return false;
        }
    }
}
