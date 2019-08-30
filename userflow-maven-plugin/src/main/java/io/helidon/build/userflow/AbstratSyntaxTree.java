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

/**
 * Expression AST.
 */
public interface AbstratSyntaxTree {

    /**
     * Test if this instance is a logical expression.
     * @return {@code true} if a logical expression, {@code false} otherwise
     */
    default boolean isExpression() {
        return this instanceof LogicalExpression;
    }

    /**
     * Get this instance as a {@link LogicalExpression}.
     * @return Expression
     */
    default LogicalExpression asExpression() {
        return (LogicalExpression) this;
    }

    /**
     * Test if this instance is a value.
     * @return {@code true} if a value, {@code false} otherwise
     */
    default boolean isValue() {
        return this instanceof Value;
    }

    /**
     * Get this instance as a {@link Value}.
     * @return Value
     */
    default Value asValue() {
        return (Value) this;
    }

    /**
     * All node types.
     */
    enum NodeType {
        VARIABLE, LITERAL, AND, OR, XOR, NOT, IS, IS_NOT, EQUAL, NOT_EQUAL;
    }

    /**
     * Get the type of this node.
     *
     * @return NodeType
     */
    NodeType type();

    /**
     * Represents literal text values, either as a variable or constant.
     */
    public static abstract class Value implements AbstratSyntaxTree {

        private final String value;

        /**
         * Create a new value.
         * @param value underlying value
         */
        protected Value(String value) {
            this.value = value;
        }

        /**
         * Test if this value is a variable.
         * @return {@code true} if a variable, {@code false} otherwise
         */
        public boolean isVariable() {
            return this instanceof Variable;
        }

        /**
         * Get this instance as a {@link Variable}.
         * @return Variable
         */
        public Variable asVariable() {
            return (Variable) this;
        }

        /**
         * Test if this instance is a literal.
         * @return {@code true} if a literal, {@code false} otherwise
         */
        public boolean isLiteral() {
            return this instanceof Literal;
        }

        /**
         * Get this instance as a {@link Literal}.
         * @return Literal
         */
        public Literal asLiteral() {
            return (Literal) this;
        }

        /**
         * Get the underlying value.
         * @return String
         */
        public String value() {
            return value;
        }
    }

    /**
     * Variable. E.g. {@code $foo}.
     * The name can be only alpha numerical with {@code -} and {@code _}.
     */
    public static final class Variable extends Value {

        /**
         * Create a new variable.
         * @param value variable name
         */
        Variable(String value) {
            super(value);
        }

        @Override
        public NodeType type() {
            return NodeType.VARIABLE;
        }
    }

    /**
     * Constant value. E.g. {@code "bar"}.
     */
    public static final class Literal extends Value {

        /**
         * Create a new literal.
         * @param value underlying literal value
         */
        Literal(String value) {
            super(value);
        }

        @Override
        public NodeType type() {
            return NodeType.LITERAL;
        }
    }

    /**
     * Operation with one operand.
     */
    public interface UnaryOperation {

        /**
         * Get the right operand
         * @return ExpressionSyntaxTree
         */
        public AbstratSyntaxTree right();
    }

    /**
     * Operation with two operands.
     */
    public interface BinaryOperation {

        /**
         * Get the left operand
         * @return ExpressionSyntaxTree
         */
        public AbstratSyntaxTree left();

        /**
         * Get the right operand
         * @return ExpressionSyntaxTree
         */
        public AbstratSyntaxTree right();
    }

    /**
     * Logical expression of named variables and text literal.
     */
    public static abstract class LogicalExpression implements AbstratSyntaxTree {

        /**
         * Test if this expression has one operand.
         *
         * @return {@code true} if instance accepts one operand, {@code false} otherwise
         */
        public boolean isUnaryOperation() {
            return this instanceof UnaryOperation;
        }

        /**
         * Get this instance as a {@link UnaryOperation}.
         *
         * @return UnaryOperation
         */
        public UnaryOperation asUnaryOperation() {
            return (UnaryOperation) this;
        }

        /**
         * Test if this expression has two operands.
         *
         * @return {@code true} if instance accepts two operands, {@code false} otherwise
         */
        public boolean isBinaryOperation() {
            return this instanceof BinaryOperation;
        }

        /**
         * Get this instance as a {@link BinaryOperation}.
         * @return BinaryOperation
         */
        public BinaryOperation asBinaryOperation() {
            return (BinaryOperation) this;
        }

        /**
         * Test if this instance is a {@code AND} expression.
         * @return {@code true} if a {@code AND} expression, {@code false} otherwise
         */
        public boolean isAnd(){
            return (this instanceof And);
        }

        /**
         * Get this instance as a {@link And} expression.
         * @return And
         */
        public And asAnd() {
            return (And) this;
        }

        /**
         * Test if this instance is a {@code OR} expression.
         * @return {@code true} if a {@code OR} expression, {@code false} otherwise
         */
        public boolean isOr() {
            return (this instanceof Or);
        }

        /**
         * Get this instance as a {@link Or} expression.
         * @return Or
         */
        public Or asOr() {
            return (Or) this;
        }

        /**
         * Test if this instance is a {@code XOR} expression.
         * @return {@code true} if a {@code XOR} expression, {@code false} otherwise
         */
        public boolean isXor() {
            return (this instanceof Xor);
        }

        /**
         * Get this instance as an {@link Xor} expression.
         *
         * @return Xor
         */
        public Xor asXor() {
            return (Xor) this;
        }

        /**
         * Test if this instance is a {@code IS} expression.
         * @return {@code true} if a {@code IS} expression, {@code false} otherwise
         */
        public boolean isIs() {
            return (this instanceof Is);
        }

        /**
         * Get this instance as an {@link Is} expression.
         *
         * @return Is
         */
        public Is asIs() {
            return (Is) this;
        }

        /**
         * Test if this instance is a {@code IS_NOT} expression.
         *
         * @return {@code true} if a {@code IS_NOT} expression, {@code false} otherwise
         */
        public boolean isIsNot() {
            return (this instanceof IsNot);
        }

        /**
         * Get this instance as a {@link IsNot} expression.
         *
         * @return IsNot
         */
        public IsNot asIsNot() {
            return (IsNot) this;
        }

        /**
         * Test if this instance is a {@code EQUAL} expression.
         * @return {@code true} if a {@code EQUAL} expression, {@code false} otherwise
         */
        public boolean isEqual() {
            return (this instanceof Equal);
        }

        /**
         * Get this instance as a {@link Equal} expression.
         *
         * @return Equal
         */
        public Equal asEqual() {
            return (Equal) this;
        }

        /**
         * Test if this instance is a {@code NOT_EQUAL} expression.
         * @return {@code true} if a {@code NOT_EQUAL} expression, {@code false} otherwise
         */
        public boolean isNotEqual() {
            return (this instanceof NotEqual);
        }

        /**
         * Get this instance as a {@link NotEqual} expression.
         *
         * @return NotEqual
         */
        public NotEqual asNotEqual() {
            return (NotEqual) this;
        }

        /**
         * Test if this instance is a {@code NOT} expression.
         * @return {@code true} if an {@code NOT} expression, {@code false} otherwise
         */
        public boolean isNot() {
            return (this instanceof Not);
        }

        /**
         * Get this instance as a {@link Not} expression.
         *
         * @return Not
         */
        public Not asNot() {
            return (Not) this;
        }
    }

    /**
     * This expression represents the logical negation of an expression.
     */
    public final class Not extends LogicalExpression implements UnaryOperation {

        private final LogicalExpression right;

        Not(LogicalExpression expr) {
            this.right = expr;
        }

        @Override
        public LogicalExpression right() {
            return right;
        }

        @Override
        public NodeType type() {
            return NodeType.NOT;
        }
    }

    /**
     * Base class for expression with two operands.
     */
    public static abstract class BinaryConditionalExpression extends LogicalExpression implements BinaryOperation {

        private final LogicalExpression left;
        private final LogicalExpression right;

        BinaryConditionalExpression(LogicalExpression left, LogicalExpression right) {
            this.left = left;
            this.right = right;
        }

        /**
         * Get the left operand.
         * @return Condition
         */
        @Override
        public LogicalExpression left(){
            return left;
        }

        /**
         * Get the right operand.
         * @return Condition
         */
        @Override
        public LogicalExpression right() {
            return right;
        }
    }

    /**
     * This expression represents an equality between two expressions.
     */
    public static final class Is extends BinaryConditionalExpression {

        Is(LogicalExpression left, LogicalExpression right) {
            super(left, right);
        }

        @Override
        public NodeType type() {
            return NodeType.IS;
        }
    }

    /**
     * This expression represents an inequality between two expressions.
     */
    public static final class IsNot extends BinaryConditionalExpression {

        IsNot(LogicalExpression left, LogicalExpression right) {
            super(left, right);
        }

        @Override
        public NodeType type() {
            return NodeType.IS_NOT;
        }
    }

    /**
     * This expression represents a logical AND between two expressions.
     */
    public static final class And extends BinaryConditionalExpression {

        And(LogicalExpression left, LogicalExpression right) {
            super(left, right);
        }

        @Override
        public NodeType type() {
            return NodeType.AND;
        }
    }

    /**
     * This expression represents a logical OR between two expressions.
     */
    public static final class Or extends BinaryConditionalExpression {

        Or(LogicalExpression left, LogicalExpression right) {
            super(left, right);
        }

        @Override
        public NodeType type() {
            return NodeType.OR;
        }
    }

    /**
     * This expression represents a logical OR between two expressions.
     */
    public static final class Xor extends BinaryConditionalExpression {

        Xor(LogicalExpression left, LogicalExpression right) {
            super(left, right);
        }

        @Override
        public NodeType type() {
            return NodeType.XOR;
        }
    }

    /**
     * Base class for value expression with two operands.
     */
    public static abstract class BinaryValueExpression extends LogicalExpression implements BinaryOperation {

        private final Value left;
        private final Value right;

        BinaryValueExpression(Value left, Value right) {
            this.left = left;
            this.right = right;
        }

        /**
         * Get the left value operand.
         * @return Value
         */
        @Override
        public Value left() {
            return left;
        }

        /**
         * Get the right value operand.
         *
         * @return Value
         */
        @Override
        public Value right() {
            return right;
        }
    }

    /**
     * This expression represents the equality of two values.
     */
    public static final class Equal extends BinaryValueExpression {

        Equal(Value left, Value right) {
            super(left, right);
        }

        @Override
        public NodeType type() {
            return NodeType.EQUAL;
        }
    }

    /**
     * This expression represents the inequality of two values.
     */
    public static final class NotEqual extends BinaryValueExpression {

        NotEqual(Value left, Value right) {
            super(left, right);
        }

        @Override
        public NodeType type() {
            return NodeType.NOT_EQUAL;
        }
    }
}
