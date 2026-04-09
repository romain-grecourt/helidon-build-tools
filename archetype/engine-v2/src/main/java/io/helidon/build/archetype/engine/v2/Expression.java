/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import java.util.Comparator;
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
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.helidon.build.common.BitSets;
import io.helidon.build.common.LazyValue;
import io.helidon.build.common.Lists;

import static java.util.Collections.unmodifiableList;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Logical expression.
 */
public final class Expression implements Comparable<Expression> {
    private static final int FOLD_CONSTANTS_MAX_TOKENS = 16;
    private static final int QMC_MAX_TERMS = 1 << 16;
    private static final int QMC_MAX_VARIABLES = 12;
    private static final long QMC_MAX_MERGE_PAIRS = 500_000L;
    private static final long QMC_MAX_MERGE_WORK = 500_000L;
    private static final long QMC_MAX_TABLE_BYTES = 64L * 1024 * 1024;
    private static final long QMC_TERM_OVERHEAD_BYTES = 96L;

    /**
     * True.
     */
    public static final Expression TRUE = new Expression(List.of(Token.TRUE), true);

    /**
     * False.
     */
    public static final Expression FALSE = new Expression(List.of(Token.FALSE), true);

    private static final Map<String, Expression> CACHE1 = new HashMap<>();
    private static final Map<List<Token>, Expression> CACHE2 = new HashMap<>();
    private static final Map<String, List<Token>> CACHE3 = new HashMap<>();
    private static final Map<String, Operator> OPS = Arrays.stream(Operator.values())
            .flatMap(op -> Arrays.stream(op.symbols).map(s -> Map.entry(s, op)))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    static {
        CACHE1.put("true", TRUE);
        CACHE1.put("false", FALSE);
    }

    private final List<Token> tokens;
    private final LazyValue<String> literal = new LazyValue<>(this::print);
    private final LazyValue<Expression> reduced0 = new LazyValue<>(this::reduce0);
    private final LazyValue<Set<String>> variables = new LazyValue<>(this::variables0);
    private final boolean reduced;

    Expression(String expression) {
        this(parse(expression), false);
    }

    Expression(List<Token> tokens, boolean reduced) {
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
        return CACHE1.computeIfAbsent(expression, Expression::new);
    }

    static List<Token> parseTokens(String expression) {
        Expression cached = CACHE1.get(expression);
        return cached != null ? cached.tokens : CACHE3.computeIfAbsent(expression, e -> unmodifiableList(parse(e)));
    }

    /**
     * Combine this expression and the given expression with the logical 'and' operator.
     *
     * @param expr expression
     * @return Expression
     */
    public Expression and(Expression expr) {
        if (expr == null || expr == TRUE) {
            return this;
        } else if (expr == FALSE || this == FALSE) {
            return FALSE;
        } else if (this == TRUE) {
            return expr;
        } else if (equals(expr)) {
            return this;
        } else {
            return new Expression(Lists.concatView(tokens, expr.tokens, Token.AND), false);
        }
    }

    /**
     * Combine this expression and the given expression with the logical 'or' operator.
     *
     * @param expr expression
     * @return Expression
     */
    public Expression or(Expression expr) {
        if (expr == null || expr == FALSE) {
            return this;
        } else if (expr == TRUE || this == TRUE) {
            return TRUE;
        } else if (this == FALSE) {
            return expr;
        } else if (equals(expr)) {
            return this;
        } else {
            return new Expression(Lists.concatView(tokens, expr.tokens, Token.OR), false);
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

    boolean variableCountAtMost(int maxVariables) {
        if (maxVariables < 0) {
            return false;
        }
        Set<String> names = new HashSet<>();
        for (Token token : tokens) {
            if (token.isVariable() && names.add(token.variable) && names.size() > maxVariables) {
                return false;
            }
        }
        return true;
    }

    /**
     * Map this expression.
     *
     * @param mapper mapper
     * @return Expression
     */
    public Expression map(UnaryOperator<Token> mapper) {
        return new Expression(Lists.map(tokens, mapper), false);
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
     * @throws UnresolvedVariableException if {@code resolver} returns {@code null}
     */
    public boolean eval(Function<String, Value<?>> resolver) {
        Deque<Value<?>> stack = new ArrayDeque<>();
        for (Token token : tokens) {
            Value<?> value;
            if (token.operator != null) {
                Value<?> op1 = stack.pop();
                value = token.operator.valence == 1
                        ? apply(token.operator, op1)
                        : apply(token.operator, stack.pop(), op1);
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
     * Reduce the expression under the given assumptions.
     *
     * @param truth assumptions that describe the reachable cases
     * @return Expression
     */
    Expression reduce(Expression truth) {
        if (truth == TRUE) {
            return reduced ? this : reduced0.get();
        } else if (truth == FALSE) {
            return FALSE;
        }
        try {
            return reduce1(truth);
        } catch (QmcLimitException e) {
            // preserve semantics by skipping QMC simplification when it exceeds the guard
            return this;
        }
    }

    Expression foldConstants() {
        if (this == TRUE || this == FALSE) {
            return this;
        }
        if (tokens.size() > FOLD_CONSTANTS_MAX_TOKENS) {
            return hasVariables() ? this : constantExpression();
        }
        Expression folded = foldSmallConstants();
        if (folded == this || folded.hasVariables()) {
            return folded;
        }
        try {
            return folded.eval() ? TRUE : FALSE;
        } catch (RuntimeException e) {
            return folded;
        }
    }

    /**
     * Inline variables.
     *
     * @param resolver resolver
     * @return Expression
     */
    public Expression inline(Function<String, Value<?>> resolver) {
        List<Token> inlined = new ArrayList<>();
        for (Token token : tokens) {
            if (token.variable != null) {
                Value<?> value = resolver.apply(token.variable);
                if (value != null && value.isPresent()) {
                    inlined.add(Token.of(value));
                    continue;
                }
            }
            inlined.add(token);
        }
        return new Expression(inlined, false).foldConstants();
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof Expression)) {
            return false;
        }
        Expression other = (Expression) o;
        if (tokens.size() != other.tokens.size()) {
            return false;
        }
        if (tokens == other.tokens) {
            return true;
        }
        if (hashCode() != other.hashCode()) {
            return false;
        }
        return tokens.equals(other.tokens);
    }

    @Override
    public int hashCode() {
        return tokens.hashCode();
    }

    // QMC algorithm
    static int[][] reduce(BitSet minterms, BitSet dontCares) {
        if (minterms.intersects(dontCares)) {
            throw new IllegalArgumentException("minterms and dontCares must be disjoint");
        }
        BitSet terms = BitSets.or(BitSets.copyOf(minterms), dontCares);
        TermTable table = TermTable.init(terms);

        // find implicants
        TermTable tmp = new TermTable(); // write-table
        long mergeWork = 0;
        while (true) {
            tmp.clear();
            BitSet bitmap = new BitSet();
            for (int g = 0, i1 = 0; g < table.groups.size(); g++) { // for-each group
                boolean addGroup = false;
                for (int l1 = table.groups.get(g); i1 < l1; i1++) {
                    Term term1 = table.terms.get(i1);
                    boolean merged = false;
                    if (g + 1 < table.groups.size()) { // compare nth and nth+1
                        int l2 = table.groups.get(g + 1);
                        mergeWork += l2 - l1;
                        if (mergeWork > QMC_MAX_MERGE_WORK) {
                            throw new QmcLimitException("QMC merge work limit exceeded");
                        }
                        for (int i2 = l1; i2 < l2; i2++) {
                            Term term2 = table.terms.get(i2);
                            if (term1.mark == term2.mark) {
                                int delta = term1.bits ^ term2.bits;
                                if (Integer.bitCount(delta) == 1) {
                                    BitSet ids = BitSets.or(BitSets.copyOf(term1.ids), term2.ids); // merge the ids
                                    if (!tmp.contains(ids)) { // add once
                                        int mark = delta | term1.mark | term2.mark; // merge the marks
                                        int bits = (term1.bits | term2.bits) & ~mark; // merge the bits (exclude the marks)
                                        tmp.add(bits, mark, ids);
                                        bitmap.or(ids);
                                        merged = true;
                                    }
                                }
                            }
                        }
                    }
                    if (!merged && !BitSets.containsAll(term1.ids, bitmap)) {
                        // not mergeable and not covered by any other term
                        tmp.add(term1.bits, term1.mark, term1.ids);
                        bitmap.or(term1.ids);
                        if (!tmp.groups.isEmpty()) {
                            tmp.groups.set(tmp.groups.size() - 1, tmp.terms.size());
                        }
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

        // select terms
        PrimeChart chart = PrimeChart.init(table, minterms, dontCares);
        List<Term> selected = chart.select();
        selected.sort(Comparator.comparingInt(term -> term.order));

        // compute the result as an array of [bits][marks]
        List<int[]> result = new ArrayList<>(selected.size());
        for (Term term : selected) {
            result.add(new int[] {
                    term.bits,
                    term.mark
            });
        }
        return result.toArray(new int[0][]);
    }

    private static Expression reduce(SyntheticVars vars, BitSet minterms, BitSet dontCares) {
        int[][] terms = reduce(minterms, dontCares);
        Expression dnf = new Expression(dnf(vars, terms), true);
        if (terms.length < 2) {
            return dnf;
        }
        FactorNode factored = FactorNode.or(FactorNode.terms(vars, terms));
        Expression candidate = new Expression(factored.tokens(), true);
        return !candidate.equals(dnf) && candidate.tokens.size() < dnf.tokens.size() ? candidate : dnf;
    }

    private static List<Token> dnf(SyntheticVars vars, int[][] terms) {
        List<Token> tokens = new ArrayList<>();
        for (int i = 0; i < terms.length; i++) {
            // associate bits with variables from high to low
            int bit = 1 << vars.vars.size() - 1;
            int y = 0;
            for (SyntheticVar v : vars.vars.values()) {
                if ((bit & terms[i][1]) == 0) {
                    // bit is not marked
                    tokens.addAll((bit & terms[i][0]) == 0 ? Token.negate(v.tokens) : v.tokens);
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
        return tokens;
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
                if (token.operator.valence == 1) {
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

    private boolean hasVariables() {
        for (Token token : tokens) {
            if (token.isVariable()) {
                return true;
            }
        }
        return false;
    }

    private Expression constantExpression() {
        try {
            return eval() ? TRUE : FALSE;
        } catch (RuntimeException e) {
            return this;
        }
    }

    private Expression foldSmallConstants() {
        Deque<FoldPart> stack = new ArrayDeque<>();
        boolean changed = false;
        for (Token token : tokens) {
            FoldPart next;
            if (token.operator == null) {
                next = FoldPart.of(token);
            } else {
                FoldPart op1 = stack.pop();
                next = token.operator.valence == 1
                        ? foldUnary(token.operator, op1)
                        : foldBinary(token.operator, stack.pop(), op1);
            }
            changed |= next.changed();
            stack.push(next);
        }
        FoldPart result = stack.pop();
        return !changed && result.tokens().equals(tokens) ? this : result.expression();
    }

    private FoldPart foldUnary(Operator operator, FoldPart op1) {
        if (op1.constant() != null) {
            try {
                return FoldPart.constant(apply(operator, op1.constant()));
            } catch (RuntimeException e) {
                // keep the original expression shape when constant folding
                // cannot evaluate a typed operation safely
                return FoldPart.expression(Lists.appendView(op1.tokens(), Token.of(operator)), op1.changed());
            }
        }
        return FoldPart.expression(Lists.appendView(op1.tokens(), Token.of(operator)), op1.changed());
    }

    private FoldPart foldBinary(Operator operator, FoldPart left, FoldPart right) {
        if (left.constant() != null && right.constant() != null) {
            try {
                return FoldPart.constant(apply(operator, left.constant(), right.constant()));
            } catch (RuntimeException e) {
                // keep the original expression shape when constant folding
                // cannot evaluate a typed operation safely
                return FoldPart.expression(Lists.concatView(left.tokens(), right.tokens(), Token.of(operator)),
                        left.changed() || right.changed());
            }
        }
        if (operator == Operator.AND || operator == Operator.OR) {
            FoldPart simplified = simplifyLogical(operator, left, right);
            if (simplified != null) {
                return simplified;
            }
        }
        return FoldPart.expression(Lists.concatView(left.tokens(), right.tokens(), Token.of(operator)),
                left.changed() || right.changed());
    }

    private FoldPart simplifyLogical(Operator operator, FoldPart left, FoldPart right) {
        if (left.constant() != null) {
            return simplifyLogical(operator, left.constant(), right);
        }
        if (right.constant() != null) {
            return simplifyLogical(operator, right.constant(), left);
        }
        return null;
    }

    private FoldPart simplifyLogical(Operator operator, Value<?> constant, FoldPart other) {
        boolean value = constant.asBoolean().orElse(false);
        if (operator == Operator.AND) {
            return value ? other.withChanged() : FoldPart.constant(Value.FALSE);
        }
        return value ? FoldPart.constant(Value.TRUE) : other.withChanged();
    }

    private static Value<?> apply(Operator operator, Value<?> op1) {
        switch (operator) {
            case NOT:
                return Value.of(!op1.getBoolean());
            case SIZEOF:
                return op1.type() == Value.Type.LIST
                        ? Value.of(op1.getList().size())
                        : Value.of(op1.getString().length());
            case AS_INT:
                return Value.of(op1.getInt());
            case AS_LIST:
                return Value.of(op1.getList());
            case AS_STRING:
                return Value.of(op1.getString());
            default:
                throw new IllegalStateException("Unsupported unary operator: " + operator);
        }
    }

    private static Value<?> apply(Operator operator, Value<?> op2, Value<?> op1) {
        switch (operator) {
            case OR:
                return Value.of(op2.asBoolean().orElse(false) || op1.asBoolean().orElse(false));
            case AND:
                return Value.of(op2.asBoolean().orElse(false) && op1.asBoolean().orElse(false));
            case EQUAL:
                return Value.of(Value.isEqual(op2, op1));
            case NOT_EQUAL:
                return Value.of(!Value.isEqual(op2, op1));
            case GREATER_THAN:
                return Value.of(op2.getInt() > op1.getInt());
            case GREATER_OR_EQUAL:
                return Value.of(op2.getInt() >= op1.getInt());
            case LOWER_THAN:
                return Value.of(op2.getInt() < op1.getInt());
            case LOWER_OR_EQUAL:
                return Value.of(op2.getInt() <= op1.getInt());
            case CONTAINS:
                if (op1.type() == Value.Type.LIST) {
                    return Value.of(new HashSet<>(op2.getList()).containsAll(op1.getList()));
                }
                if (op2.type() == Value.Type.LIST) {
                    return Value.of(op2.getList().contains(op1.asString().orElse(null)));
                }
                return Value.of(op1.isPresent() && op2.asString().orElse("").contains(op1.getString()));
            default:
                throw new IllegalStateException("Unsupported binary operator: " + operator);
        }
    }

    private static final class FoldPart {
        private final Value<?> constant;
        private final List<Token> tokens;
        private final boolean changed;

        private FoldPart(Value<?> constant, List<Token> tokens, boolean changed) {
            this.constant = constant;
            this.tokens = tokens;
            this.changed = changed;
        }

        static FoldPart of(Token token) {
            return new FoldPart(token.operand, List.of(token), false);
        }

        static FoldPart constant(Value<?> value) {
            return new FoldPart(value, List.of(Token.of(value)), true);
        }

        static FoldPart expression(List<Token> tokens, boolean changed) {
            return new FoldPart(null, tokens, changed);
        }

        Value<?> constant() {
            return constant;
        }

        List<Token> tokens() {
            return tokens;
        }

        boolean changed() {
            return changed;
        }

        FoldPart withChanged() {
            return !changed ? new FoldPart(constant, tokens, true) : this;
        }

        Expression expression() {
            if (constant != null && constant.type() == Value.Type.BOOLEAN) {
                return constant.getBoolean() ? TRUE : FALSE;
            }
            return new Expression(tokens, false);
        }
    }

    private Expression reduce0() {
        if (this == TRUE) {
            return TRUE;
        } else if (this == FALSE) {
            return FALSE;
        } else {
            return CACHE2.computeIfAbsent(tokens, l -> {
                try {
                    return reduce1(TRUE);
                } catch (QmcLimitException e) {
                    // preserve semantics by skipping QMC simplification when it exceeds the guard
                    return this;
                }
            });
        }
    }

    private Expression reduce1(Expression truth) {
        SyntheticVars vars = new SyntheticVars(new TreeMap<>());

        // ensure a boolean-only expression and record variables
        Expression expr = synthetic(vars);
        Expression truthExpr = truth.synthetic(vars);

        // constant
        int numVars = vars.vars.size();
        if (numVars == 0) {
            return truthExpr.eval() && expr.eval() ? TRUE : FALSE;
        }
        if (numVars > QMC_MAX_VARIABLES) {
            throw new QmcLimitException("QMC variable limit exceeded");
        }

        // evaluate the truth table
        int tableSize = 1 << numVars;
        BitSet truthTerms = truthExpr.minterms(vars);
        if (truthTerms.isEmpty()) {
            return FALSE;
        }

        // synthetic minterms
        BitSet minterms = expr.minterms(vars);

        // ignore terms from mutually exclusive synthetic equalities
        // and terms ruled out by the truth expression
        BitSet excludes = BitSets.or(vars.exclusiveDontCares(), BitSets.not(truthTerms, tableSize));

        // filter minterms
        minterms.and(truthTerms); // reachable terms satisfied by the expression
        minterms.andNot(excludes); // reachable terms that still matter

        // always false
        if (minterms.isEmpty()) {
            return FALSE;
        }

        // always true
        if (minterms.cardinality() == tableSize - excludes.cardinality()) {
            return TRUE;
        }

        // QMC resolution
        return reduce(vars, minterms, excludes);
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

    private Expression synthetic(SyntheticVars vars) {
        // substitute non-logical operations that use variables with synthetic variables
        // to produce an expression containing only logical operations
        Map<String, List<Token>> tempVars = new TreeMap<>();
        Deque<List<Token>> stack = new ArrayDeque<>();
        for (Token token : tokens) {
            if (token.operator != null) {
                List<Token> op1 = stack.pop();
                Token t1 = op1.get(0);
                String s1 = t1.variable != null ? t1.variable : t1.signature();
                    String varName;
                    switch (token.operator) {
                        case NOT:
                            stack.push(Lists.appendView(op1, token));
                            break;
                        case SIZEOF:
                        case AS_INT:
                    case AS_LIST:
                        case AS_STRING:
                            // t1 is always a variable (enforced in parse)
                            varName = token.operator.symbol() + ' ' + s1;
                            SyntheticVar syntheticVar = vars.vars.get(s1);
                            tempVars.putIfAbsent(varName, Lists.appendView(
                                    syntheticVar != null ? syntheticVar.tokens : op1,
                                    token));
                            stack.push(List.of(Token.of(varName)));
                            break;
                    case CONTAINS:
                    case EQUAL:
                    case NOT_EQUAL:
                    case GREATER_THAN:
                    case GREATER_OR_EQUAL:
                    case LOWER_THAN:
                    case LOWER_OR_EQUAL:
                        List<Token> op2 = stack.pop();
                        Token t2 = op2.get(0);
                        if (t1 == Token.TRUE && t2.variable != null) {
                            stack.push(List.of(t2));
                        } else if (t1 == Token.FALSE && t2.variable != null) {
                            stack.push(List.of(t2, Token.NOT));
                        } else if (t2 == Token.TRUE && t1.variable != null) {
                            stack.push(List.of(t1));
                        } else if (t2 == Token.FALSE && t1.variable != null) {
                            stack.push(List.of(t1, Token.NOT));
                        } else if (t1.variable != null || t2.variable != null) {
                            String s2 = t2.variable != null ? t2.variable : t2.signature();
                            op2 = tempVars.getOrDefault(s2, op2);
                            op1 = tempVars.getOrDefault(s1, op1);
                            List<Token> next = new ArrayList<>();
                            if (token == Token.NOT_EQUAL) {
                                // normalize NOT_EQUAL into NOT + EQUAL
                                // to avoid creating different variables
                                token = Token.EQUAL;
                                next.add(Token.NOT);
                            }
                            varName = s2 + ' ' + token + ' ' + s1;
                            tempVars.putIfAbsent(varName, Lists.concatView(op2, op1, token));
                            stack.push(Lists.addAll(next, 0, Token.of(varName)));
                        } else {
                            stack.push(Lists.concatView(op2, op1, token));
                        }
                        break;
                        default:
                            stack.push(Lists.concatView(stack.pop(), op1, token));
                    }
                } else {
                    stack.push(List.of(token));
            }
        }

        List<Token> expr = new ArrayList<>();
        while (!stack.isEmpty()) {
            for (Token token : stack.pop()) {
                if (token.variable != null) {
                    List<Token> expansion = expandVar(token.variable, tempVars);
                    vars.put(token.variable, expansion);
                }
                expr.add(token);
            }
        }
        return new Expression(expr, false);
    }

    private BitSet minterms(SyntheticVars sVars) {
        BitSet minterms = new BitSet();

        // evaluate the truth table
        int tableSize = 1 << sVars.vars.size(); // 2 ^ length
        for (int y = 0; y < tableSize; y++) {

            // associate bits with variables from high to low
            int x = 1 << sVars.vars.size() - 1;
            Map<String, Value<Boolean>> vars = new HashMap<>();
            for (String varName : sVars.vars.keySet()) {
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
        Spliterator<RawToken> spliterator = spliteratorUnknownSize(new Lexer(expression), ORDERED);
        List<RawToken> rawTokens = StreamSupport.stream(spliterator, false).collect(toList());

        // used for validation
        int stackSize = 0;

        List<Token> tokens = new ArrayList<>();
        Stack<RawToken> stack = new Stack<>();

        // shunting yard, convert infix to rpn
        ListIterator<RawToken> it = rawTokens.listIterator();
        while (it.hasNext()) {
            int previous = it.previousIndex();
            RawToken rawToken = it.next();
            switch (rawToken.kind) {
                case BINARY_OPERATOR:
                case UNARY_OPERATOR:
                    if (rawToken.kind != RawToken.Kind.UNARY_OPERATOR
                        && previous >= 0 && rawTokens.get(previous).text.equals("(")) {
                        throw new FormatException("Invalid parenthesis");
                    }
                    while (!stack.isEmpty() && OPS.containsKey(stack.peek().text)) {
                        Operator currentOp = OPS.get(rawToken.text);
                        Operator leftOp = OPS.get(stack.peek().text);
                        if ((leftOp.precedence >= currentOp.precedence)) {
                            stackSize += 1 - addToken(stack.pop(), tokens);
                            continue;
                        }
                        break;
                    }
                    stack.push(rawToken);
                    break;
                case PARENTHESIS:
                    if ("(".equals(rawToken.text)) {
                        stack.push(rawToken);
                    } else if (")".equals(rawToken.text)) {
                        while (!stack.isEmpty() && !stack.peek().text.equals("(")) {
                            stackSize += 1 - addToken(stack.pop(), tokens);
                        }
                        if (stack.isEmpty()) {
                            throw new FormatException("Unmatched parenthesis");
                        }
                        stack.pop();
                    } else {
                        throw new IllegalStateException("Unexpected raw token: " + rawToken.text);
                    }
                    break;
                case BOOLEAN:
                case STRING:
                case ARRAY:
                case INT:
                case VARIABLE:
                    stackSize += 1 - addToken(rawToken, tokens);
                    break;
                case SKIP:
                case COMMENT:
                    break;
                default:
                    throw new IllegalStateException("Unexpected raw token: " + rawToken.text);
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

    private static int addToken(RawToken rawToken, List<Token> tokens) {
        Token token = Token.of(rawToken);
        int valence = 0;
        if (token.operator != null) {
            if (tokens.size() < token.operator.valence) {
                throw new FormatException("Missing operand(s)");
            }
            valence = token.operator.valence;
            Token op1 = tokens.get(tokens.size() - 1);
            switch (token.operator) {
                case NOT:
                    if (op1.operand != null && !(op1.operand.type() == Value.Type.BOOLEAN)) {
                        throw new FormatException("Invalid operand");
                    }
                    break;
                case AS_LIST:
                case AS_INT:
                case AS_STRING:
                    if (op1.variable == null) {
                        throw new FormatException("Invalid operand");
                    }
                    break;
                default:
            }
        }
        tokens.add(token);
        return valence;
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

    /**
     * QMC reduction limit exceeded.
     */
    public static final class QmcLimitException extends IllegalStateException {

        private QmcLimitException(String message) {
            super(message);
        }
    }

    /**
     * Expression operator.
     */
    public enum Operator {
        /**
         * Equal operator.
         */
        EQUAL(8, 2, "=="),

        /**
         * Not equal operator.
         */
        NOT_EQUAL(8, 2, "!="),

        /**
         * And operator.
         */
        AND(4, 2, "&&", "AND"),

        /**
         * Or operator.
         */
        OR(3, 2, "||", "OR"),

        /**
         * Greater than operator.
         */
        GREATER_THAN(10, 2, ">"),

        /**
         * Greater or equal operator.
         */
        GREATER_OR_EQUAL(10, 2, ">="),

        /**
         * Lower than operator.
         */
        LOWER_THAN(10, 2, "<"),

        /**
         * Lower or equal operator.
         */
        LOWER_OR_EQUAL(10, 2, "<="),

        /**
         * Contains operator.
         */
        CONTAINS(9, 2, "contains"),

        /**
         * As-list operator.
         */
        AS_LIST(14, 1, "(list)"),

        /**
         * As-string operator.
         */
        AS_STRING(14, 1, "(string)"),

        /**
         * As-int operator.
         */
        AS_INT(14, 1, "(int)"),

        /**
         * Sizeof operator.
         */
        SIZEOF(14, 1, "sizeof"),

        /**
         * Not operator.
         */
        NOT(13, 1, "!", "NOT");

        private final int precedence;
        private final int valence;
        private final String[] symbols;

        Operator(int precedence, int valence, String... symbols) {
            this.precedence = precedence;
            this.valence = valence;
            this.symbols = symbols;
        }

        /**
         * Get the operator symbol.
         *
         * @return symbol
         */
        public String symbol() {
            return symbols[0];
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
        private static final Token GREATER_THAN = new Token(Operator.GREATER_THAN, null, null);
        private static final Token GREATER_OR_EQUAL = new Token(Operator.GREATER_OR_EQUAL, null, null);
        private static final Token LOWER_THAN = new Token(Operator.LOWER_THAN, null, null);
        private static final Token LOWER_OR_EQUAL = new Token(Operator.LOWER_OR_EQUAL, null, null);
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
                case GREATER_THAN:
                    return GREATER_THAN;
                case GREATER_OR_EQUAL:
                    return GREATER_OR_EQUAL;
                case LOWER_THAN:
                    return LOWER_THAN;
                case LOWER_OR_EQUAL:
                    return LOWER_OR_EQUAL;
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
            int kind = operand != null ? 0 : variable != null ? 1 : operator != null ? 2 : 3;
            int otherKind = o.operand != null ? 0 : o.variable != null ? 1 : o.operator != null ? 2 : 3;
            if (kind != otherKind) {
                return Integer.compare(kind, otherKind);
            }
            switch (kind) {
                case 0:
                    return Value.compare(operand, o.operand);
                case 1:
                    return variable.compareTo(o.variable);
                case 2:
                    return operator.compareTo(o.operator);
                default:
                    return 0;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Token)) {
                return false;
            }
            Token token = (Token) o;
            return operator == token.operator
                   && Objects.equals(variable, token.variable)
                   && Value.isStrictEqual(operand, token.operand);
        }

        @Override
        public int hashCode() {
            return Objects.hash(operator, variable, Value.hash(operand));
        }

        @Override
        public String toString() {
            if (operand != null) {
                switch (operand.type()) {
                    case STRING:
                        return quoted(operand.getString());
                    case BOOLEAN:
                        return String.valueOf(operand.getBoolean());
                    case INTEGER:
                        return String.valueOf(operand.getInt());
                    case LIST:
                        return "["
                               + operand.getList().stream()
                                       .map(Token::quoted)
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

        static List<Token> negate(List<Token> tokens) {
            List<Token> result = new ArrayList<>(tokens);
            int index = result.size() - 1;
            Token last = result.get(index);
            if (last == EQUAL) {
                result.set(index, NOT_EQUAL);
            } else if (last == NOT_EQUAL) {
                result.set(index, EQUAL);
            } else {
                result.add(NOT);
            }
            return result;
        }

        String signature() {
            if (operand != null) {
                switch (operand.type()) {
                    case DYNAMIC:
                        return "<dynamic>" + quoted(operand.getString());
                    case STRING:
                        return "<string>" + quoted(operand.getString());
                    case BOOLEAN:
                        return "<boolean>" + operand.getBoolean();
                    case INTEGER:
                        return "<integer>" + operand.getInt();
                    case LIST:
                        return "<list>["
                               + operand.getList().stream()
                                       .map(Token::quoted)
                                       .collect(Collectors.joining(","))
                               + "]";
                    default:
                        throw new IllegalStateException("Unexpected operand type: " + operand.type());
                }
            }
            throw new IllegalStateException("Expected operand token");
        }

        static String quoted(String value) {
            return "'" + value + "'";
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

        static Token of(RawToken token) {
            switch (token.kind) {
                case BINARY_OPERATOR:
                case UNARY_OPERATOR:
                    return Token.of(OPS.get(token.text));
                case BOOLEAN:
                    return Token.of(Boolean.parseBoolean(token.text));
                case STRING:
                    return Token.of(Value.of(token.text.substring(1, token.text.length() - 1)));
                case INT:
                    return Token.of(Value.of(Integer.parseInt(token.text)));
                case ARRAY:
                    return Token.of(Value.of(parseArray(token.text)));
                case VARIABLE:
                    return Token.of(parseVariable(token.text));
                case PARENTHESIS:
                    throw new FormatException("Unmatched parenthesis");
                default:
                    throw new IllegalStateException("Unexpected raw token: " + token.text);
            }
        }
    }

    private static final class RawToken {

        private final Kind kind;
        private final String text;

        RawToken(Kind kind, String text) {
            this.kind = kind;
            this.text = text;
        }

        enum Kind {
            SKIP("^\\s+"),
            ARRAY("^\\[[^]\\[]*]"),
            BOOLEAN("^(true|false)"),
            STRING("^['\"][^'\"]*['\"]"),
            INT("^\\-?[0-9]+"),
            VARIABLE("^\\$\\{(?<varName>~?[\\w.-]+)}"),
            BINARY_OPERATOR("^([<>=!]=|[<>]|\\|\\||OR|&&|AND|contains)"),
            UNARY_OPERATOR("^(!|NOT|\\(list\\)|\\(string\\)|\\(int\\)|sizeof)"),
            PARENTHESIS("^[()]"),
            COMMENT("#.*\\R");

            private final Pattern pattern;

            Kind(String regex) {
                this.pattern = Pattern.compile(regex);
            }
        }

        @Override
        public String toString() {
            return "RawToken{ " + text + " }";
        }
    }

    private static final class Lexer implements Iterator<RawToken> {

        private final String line;
        private int cursor;

        Lexer(String line) {
            this.line = line;
            this.cursor = 0;
        }

        @Override
        public boolean hasNext() {
            return cursor < line.length();
        }

        @Override
        public RawToken next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String current = line.substring(cursor);
            for (RawToken.Kind kind : RawToken.Kind.values()) {
                Matcher matcher = kind.pattern.matcher(current);
                if (matcher.find()) {
                    String text = matcher.group();
                    cursor += text.length();
                    return new RawToken(kind, text);
                }
            }
            throw new FormatException("Unexpected token: " + current);
        }
    }

    private static final class Term {
        private final int mark;
        private final int bits;
        private final BitSet ids;
        private final int order;

        Term(int bits, int mark, BitSet ids, int order) {
            this.bits = bits;
            this.mark = mark;
            this.ids = ids;
            this.order = order;
        }

        boolean prefer(Term other) {
            int marks = Integer.bitCount(mark);
            int otherMarks = Integer.bitCount(other.mark);
            if (marks != otherMarks) {
                return marks > otherMarks;
            }
            int bits = Integer.compare(this.bits, other.bits);
            return bits < 0 || (bits == 0 && mark < other.mark);
        }

    }

    private static final class TermTable {
        private final List<Term> terms = new ArrayList<>();
        private final List<Integer> groups = new ArrayList<>();
        private long memoryCost;

        static TermTable init(BitSet terms) {
            TermTable table = new TermTable();
            int termCount = terms.cardinality();
            int numVars = 32 - Integer.numberOfLeadingZeros(Math.max(terms.length() - 1, 1));
            if (termCount > QMC_MAX_TERMS) {
                throw new QmcLimitException("QMC initial term limit exceeded");
            }
            for (int x = 0, y = 0; x <= numVars && y < termCount; x++) {
                int z = y;
                for (int i = 0, term = terms.nextSetBit(0); term >= 0; term = terms.nextSetBit(term + 1), i++) {
                    if (x == Integer.bitCount(term)) {
                        table.terms.add(new Term(term, 0, BitSets.of(i), table.terms.size()));
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
            long mergePairs = 0;
            for (int i = 0, start = 0; i + 1 < table.groups.size(); i++) {
                int mid = table.groups.get(i);
                int end = table.groups.get(i + 1);
                mergePairs += (long) (mid - start) * (end - mid);
                start = mid;
            }
            if (mergePairs > QMC_MAX_MERGE_PAIRS) {
                throw new QmcLimitException("QMC merge pair limit exceeded");
            }
            return table;
        }

        void clear() {
            terms.clear();
            groups.clear();
            memoryCost = 0;
        }

        void add(int bits, int mark, BitSet ids) {
            long nextMemoryCost = memoryCost + QMC_TERM_OVERHEAD_BYTES + (long) ids.size() / Byte.SIZE;
            if (nextMemoryCost > QMC_MAX_TABLE_BYTES) {
                throw new QmcLimitException("QMC implicant table memory limit exceeded");
            }
            terms.add(new Term(bits, mark, ids, terms.size()));
            memoryCost = nextMemoryCost;
        }

        boolean contains(BitSet ids) {
            int numGroups = groups.size();
            int startIndex = numGroups == 0 ? 0 : groups.get(numGroups - 1);
            int endIndex = numGroups > 1 ? groups.get(numGroups - 2) : terms.size();
            for (int i = startIndex; i < endIndex; i++) {
                if (terms.get(i).ids.equals(ids)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class PrimeChart {
        private final List<Term> terms;
        private final BitSet essentials;
        private final int cardinality;

        private PrimeChart(List<Term> terms, BitSet essentials, int cardinality) {
            this.terms = terms;
            this.essentials = essentials;
            this.cardinality = cardinality;
        }

        static PrimeChart init(TermTable table, BitSet minterms, BitSet dontCares) {
            BitSet terms = BitSets.or(BitSets.copyOf(minterms), dontCares);
            BitSet excludes = BitSets.indicesOf(terms, dontCares);
            BitSet essentials = new BitSet();
            BitSet coverage = new BitSet();
            List<Term> chart = new ArrayList<>(table.terms.size());
            for (Term term : table.terms) {
                BitSet ids = BitSets.reindex(term.ids, excludes); // reindex without dontCares
                if (!ids.isEmpty()) {
                    chart.add(new Term(term.bits, term.mark, ids, term.order));
                    BitSet shared = BitSets.and(BitSets.copyOf(ids), coverage); // record reused terms
                    essentials.andNot(shared); // remove terms now covered more than once
                    essentials.or(BitSets.andNot(BitSets.copyOf(ids), coverage)); // record terms first seen here
                    coverage.or(ids); // record all covered terms
                }
            }
            int cardinality = minterms.cardinality();
            if (coverage.cardinality() != cardinality) {
                throw new IllegalStateException("Prime chart does not cover all terms");
            }
            return new PrimeChart(chart, essentials, cardinality);
        }

        List<Term> select() {
            List<Term> selected = new ArrayList<>(terms.size());
            List<Term> remaining = new ArrayList<>(terms.size());
            BitSet uncovered = BitSets.not(essentials, cardinality);
            for (Term term : terms) {
                if (term.ids.intersects(essentials)) {
                    selected.add(term);
                    uncovered.andNot(term.ids);
                } else {
                    remaining.add(term);
                }
            }
            while (!uncovered.isEmpty()) {
                Term best = select(remaining, uncovered);
                if (best == null) {
                    throw new IllegalStateException("Prime chart does not cover all terms");
                }
                selected.add(best);
                uncovered.andNot(best.ids);
            }
            return selected;
        }

        static Term select(List<Term> terms, BitSet ids) {
            Term best = null;
            int bestCoverage = 0;
            for (Term term : terms) {
                int coverage = BitSets.intersectCount(term.ids, ids);
                if (coverage > 0) {
                    if (best == null) {
                        best = term;
                        bestCoverage = coverage;
                    } else if (coverage > bestCoverage) {
                        best = term;
                        bestCoverage = coverage;
                    } else if (coverage == bestCoverage && term.prefer(best)) {
                        best = term;
                    }
                }
            }
            return best;
        }
    }

    private static final class SyntheticVars {
        private final Map<String, SyntheticVar> vars;

        SyntheticVars(Map<String, SyntheticVar> vars) {
            this.vars = vars;
        }

        List<int[]> exclusiveMasks() {
            if (vars.size() < 2) {
                return List.of();
            }
            int bit = 1 << vars.size() - 1;
            Map<List<Token>, Map<List<Token>, Integer>> variableExpressions = new HashMap<>();
            for (Map.Entry<String, SyntheticVar> entry : vars.entrySet()) {
                SyntheticVar value = entry.getValue();
                if (!value.variableExpr.isEmpty()) {
                    variableExpressions.computeIfAbsent(value.variableExpr, k -> new HashMap<>())
                            .merge(value.valueExpr, bit, (v1, v2) -> v1 | v2);
                }
                bit >>>= 1;
            }
            List<int[]> result = new ArrayList<>();
            for (Map<List<Token>, Integer> values : variableExpressions.values()) {
                if (values.size() > 1) {
                    result.add(values.values().stream()
                            .mapToInt(Integer::intValue)
                            .toArray());
                }
            }
            return result;
        }

        BitSet exclusiveDontCares() {
            List<int[]> groups = exclusiveMasks();
            if (groups.isEmpty()) {
                return new BitSet();
            }
            BitSet result = new BitSet();
            int tableSize = 1 << vars.size();
            for (int y = 0; y < tableSize; y++) {
                for (int[] masks : groups) {
                    int count = 0;
                    for (int mask : masks) {
                        if ((y & mask) != 0 && ++count > 1) {
                            result.set(y);
                            break;
                        }
                    }
                    if (result.get(y)) {
                        break;
                    }
                }
            }
            return result;
        }

        void put(String name, List<Token> tokens) {
            vars.putIfAbsent(name, new SyntheticVar(tokens));
        }
    }

    private static final class SyntheticVar {
        private final List<Token> tokens;
        private final List<Token> variableExpr; // variable side of the equality
        private final List<Token> valueExpr; // value side of the equality

        SyntheticVar(List<Token> tokens) {
            this.tokens = List.copyOf(tokens);
            if (tokens.isEmpty() || tokens.get(tokens.size() - 1) != Token.EQUAL) {
                this.variableExpr = List.of();
                this.valueExpr = List.of();
                return;
            }
            List<Token> right = expression(tokens, tokens.size() - 1);
            List<Token> left = expression(tokens, tokens.size() - 1 - right.size());
            boolean leftVar = left.stream().anyMatch(Token::isVariable);
            boolean rightVar = right.stream().anyMatch(Token::isVariable);
            if (leftVar != rightVar) {
                this.variableExpr = leftVar ? left : right;
                this.valueExpr = leftVar ? right : left;
            } else {
                this.variableExpr = List.of();
                this.valueExpr = List.of();
            }
        }

        static List<Token> expression(List<Token> tokens, int endIndex) {
            for (int i = endIndex - 1, depth = 1; i >= 0; i--) {
                Token token = tokens.get(i);
                if (token.operator != null) {
                    depth += token.operator.valence - 1;
                } else {
                    depth--;
                }
                if (depth == 0) {
                    return tokens.subList(i, endIndex);
                }
            }
            throw new IllegalStateException("Invalid postfix expression");
        }
    }

    private static final class FactorNode implements Comparable<FactorNode> {
        private enum Type {
            TRUE, FALSE, LITERAL, COMPOSITE
        }

        private static final FactorNode TRUE = new FactorNode(Type.TRUE, 0, false, List.of(), null, List.of());
        private static final FactorNode FALSE = new FactorNode(Type.FALSE, 0, false, List.of(), null, List.of());

        private final Type type;
        private final int bit; // truth table bit
        private final boolean negated;
        private final List<Token> literalTokens;
        private final Operator operator;
        private final List<FactorNode> children;
        private final LazyValue<List<Token>> tokens = new LazyValue<>(this::tokens0);

        private FactorNode(Type type, int bit, boolean negated, List<Token> literalTokens,
                           Operator operator, List<FactorNode> children) {
            this.type = type;
            this.bit = bit;
            this.negated = negated;
            this.literalTokens = literalTokens;
            this.operator = operator;
            this.children = children;
        }

        List<Token> tokens() {
            return tokens.get();
        }

        @Override
        public int compareTo(FactorNode other) {
            int cmp = Integer.compare(tokens().size(), other.tokens().size());
            return cmp != 0 ? cmp : Lists.compare(tokens(), other.tokens());
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FactorNode)) {
                return false;
            }
            FactorNode other = (FactorNode) o;
            return type == other.type
                   && bit == other.bit
                   && negated == other.negated
                   && literalTokens.equals(other.literalTokens)
                   && operator == other.operator
                   && children.equals(other.children);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, bit, negated, literalTokens, operator, children);
        }

        List<Token> tokens0() {
            switch (type) {
                case TRUE:
                    return List.of(Token.TRUE);
                case FALSE:
                    return List.of(Token.FALSE);
                case LITERAL:
                    return negated ? Token.negate(literalTokens) : literalTokens;
                case COMPOSITE:
                    List<Token> tokens = new ArrayList<>();
                    for (int i = 0; i < children.size(); i++) {
                        tokens.addAll(children.get(i).tokens());
                        if (i > 0) {
                            tokens.add(Token.of(operator));
                        }
                    }
                    return tokens;
                default:
                    throw new IllegalStateException("Unexpected factor node type: " + type);
            }
        }

        static List<FactorNode> terms(SyntheticVars vars, int[][] terms) {
            List<SyntheticVar> values = new ArrayList<>(vars.vars.values());
            List<FactorNode> result = new ArrayList<>(terms.length);
            for (int[] term : terms) {
                int bit = 1 << vars.vars.size() - 1;
                List<FactorNode> factors = new ArrayList<>();
                for (SyntheticVar value : values) {
                    if ((bit & term[1]) == 0) {
                        factors.add(literal(bit, (bit & term[0]) == 0, value.tokens));
                    }
                    bit >>>= 1;
                }
                result.add(and(factors));
            }
            return result;
        }

        static FactorNode literal(int bit, boolean negated, List<Token> tokens) {
            return new FactorNode(Type.LITERAL, bit, negated, tokens, null, List.of());
        }

        static FactorNode composite(Operator operator, List<FactorNode> children) {
            return new FactorNode(Type.COMPOSITE, 0, false, List.of(), operator, children);
        }

        static FactorNode and(List<FactorNode> children) {
            return node(Operator.AND, children);
        }

        static FactorNode or(List<FactorNode> children) {
            return node(Operator.OR, children);
        }

        static FactorNode node(Operator operator, List<FactorNode> rawChildren) {
            List<FactorNode> children = new ArrayList<>();
            FactorNode annihilator = operator == Operator.OR ? TRUE : FALSE;
            FactorNode identity = operator == Operator.OR ? FALSE : TRUE;
            for (FactorNode child : rawChildren) {
                if (child == annihilator) {
                    return annihilator;
                }
                if (child != identity) {
                    if (child.type == Type.COMPOSITE && child.operator == operator) {
                        children.addAll(child.children);
                    } else {
                        children.add(child);
                    }
                }
            }
            if (children.isEmpty()) {
                return identity;
            }
            children.sort(FactorNode::compareTo);
            children = Lists.unique(children);
            FactorNode complement = complement(operator, children);
            if (complement != null) {
                return complement;
            }
            children = absorb(operator, children);
            if (children.isEmpty()) {
                return identity;
            }
            if (children.size() == 1) {
                return children.get(0);
            }
            FactorNode direct = composite(operator, children);
            if (operator == Operator.OR) {
                return factor(direct, children);
            }
            return direct;
        }

        static FactorNode complement(Operator operator, List<FactorNode> children) {
            Map<Integer, Boolean> states = new HashMap<>();
            for (FactorNode child : children) {
                if (child.type != Type.LITERAL) {
                    continue;
                }
                Boolean previous = states.putIfAbsent(child.bit, child.negated);
                if (previous != null && previous != child.negated) {
                    return operator == Operator.OR ? TRUE : FALSE;
                }
            }
            return null;
        }

        static List<FactorNode> absorb(Operator operator, List<FactorNode> children) {
            List<List<FactorNode>> factors = new ArrayList<>(children.size());
            for (FactorNode child : children) {
                factors.add(factors(operator, child));
            }
            BitSet removed = new BitSet(children.size());
            for (int i = 0; i < children.size(); i++) {
                if (!removed.get(i)) {
                    for (int j = 0; j < children.size(); j++) {
                        if (i != j && !removed.get(j) && Lists.containsAll(factors.get(j), factors.get(i))) {
                            removed.set(j);
                        }
                    }
                }
            }
            if (removed.isEmpty()) {
                return children;
            }
            List<FactorNode> result = new ArrayList<>(children.size() - removed.cardinality());
            for (int i = 0; i < children.size(); i++) {
                if (!removed.get(i)) {
                    result.add(children.get(i));
                }
            }
            return result;
        }

        static FactorNode factor(FactorNode direct, List<FactorNode> children) {
            List<List<FactorNode>> factors = new ArrayList<>(children.size());
            for (FactorNode child : children) {
                factors.add(factors(Operator.OR, child));
            }
            Set<List<FactorNode>> candidates = new HashSet<>();
            FactorNode best = direct;
            for (int i = 0; i < factors.size(); i++) {
                for (int j = i + 1; j < factors.size(); j++) {
                    List<FactorNode> common = Lists.intersect(factors.get(i), factors.get(j));
                    if (!common.isEmpty() && candidates.add(common)) {
                        List<FactorNode> grouped = new ArrayList<>();
                        List<FactorNode> groupedFactors = new ArrayList<>();
                        List<FactorNode> remaining = new ArrayList<>();
                        for (int k = 0; k < children.size(); k++) {
                            if (Lists.containsAll(factors.get(k), common)) {
                                grouped.add(children.get(k));
                                groupedFactors.add(and(Lists.subtract(factors.get(k), common)));
                            } else {
                                remaining.add(children.get(k));
                            }
                        }
                        if (grouped.size() >= 2) {
                            FactorNode remainders = or(groupedFactors);
                            List<FactorNode> nextFactors = new ArrayList<>(common.size() + 1);
                            nextFactors.addAll(common);
                            if (remainders != TRUE) {
                                nextFactors.add(remainders);
                            }
                            remaining.add(and(nextFactors));
                            FactorNode candidate = or(remaining);
                            if (!candidate.equals(best) && candidate.tokens().size() < best.tokens().size()) {
                                best = candidate;
                            }
                        }
                    }
                }
            }
            return best;
        }

        static List<FactorNode> factors(Operator operator, FactorNode child) {
            Operator nested = operator == Operator.OR ? Operator.AND : Operator.OR;
            if (child.type == Type.COMPOSITE && child.operator == nested) {
                return child.children;
            }
            return List.of(child);
        }
    }
}
