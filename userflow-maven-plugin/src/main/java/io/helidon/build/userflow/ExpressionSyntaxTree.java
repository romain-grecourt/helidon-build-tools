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
 * Expression syntax tree.
 */
public interface ExpressionSyntaxTree {

    /**
     * Test if this instance is an condition.
     * @return {@code true} if an condition, {@code false} otherwise
     */
    default boolean isCondition() {
        return this instanceof Condition;
    }

    /**
     * Get this instance as an condition.
     * @return Expression
     */
    default Condition asCondition() {
        return (Condition) this;
    }

    /**
     * Test if this instance is a value.
     * @return {@code true} if a value, {@code false} otherwise
     */
    default boolean isValue() {
        return this instanceof Value;
    }

    /**
     * Get this instance as a value.
     * @return Value
     */
    default Value asValue() {
        return (Value) this;
    }

    /**
     * Represents literal text values, either as a variable or constant.
     */
    public static abstract class Value implements ExpressionSyntaxTree {

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
         * Get this instance as a variable.
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
         * Get this instance as a literal.
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
    }

    /**
     * Logical expression of named variables and text literal.
     */
    public static abstract class Condition implements ExpressionSyntaxTree {

        /**
         * Test if this instance is an {@code AND} condition.
         * @return {@code true} if an {@code AND} condition, {@code false} otherwise
         */
        public boolean isAnd(){
            return (this instanceof And);
        }

        /**
         * Get this instance as an {@code AND} condition.
         * @return And
         */
        public And asAnd() {
            return (And) this;
        }

        /**
         * Test if this instance is an {@code OR} condition.
         * @return {@code true} if an {@code OR} condition, {@code false} otherwise
         */
        public boolean isOr() {
            return (this instanceof Or);
        }

        /**
         * Get this instance as an {@code OR} condition.
         * @return Or
         */
        public Or asOr() {
            return (Or) this;
        }

        /**
         * Test if this instance is an {@code XOR} condition.
         * @return {@code true} if an {@code XOR} condition, {@code false} otherwise
         */
        public boolean isXor() {
            return (this instanceof Xor);
        }

        /**
         * Get this instance as an {@code XOR} condition.
         *
         * @return Xor
         */
        public Xor asXor() {
            return (Xor) this;
        }

        /**
         * Test if this instance is an {@code IS} condition.
         * @return {@code true} if an {@code IS} condition, {@code false} otherwise
         */
        public boolean isIs() {
            return (this instanceof Is);
        }

        /**
         * Get this instance as an {@code IS} condition.
         *
         * @return Is
         */
        public Is asIs() {
            return (Is) this;
        }

        /**
         * Test if this instance is an {@code IS_NOT} condition.
         *
         * @return {@code true} if an {@code IS_NOT} condition, {@code false} otherwise
         */
        public boolean isIsNot() {
            return (this instanceof IsNot);
        }

        /**
         * Get this instance as an {@code IS_NOT} condition.
         *
         * @return IsNot
         */
        public IsNot asIsNot() {
            return (IsNot) this;
        }

        /**
         * Test if this instance is an {@code EQUAL} condition.
         * @return {@code true} if an {@code EQUAL} condition, {@code false} otherwise
         */
        public boolean isEqual() {
            return (this instanceof Equal);
        }

        /**
         * Get this instance as an {@code EQUAL} condition.
         *
         * @return Equal
         */
        public Equal asEqual() {
            return (Equal) this;
        }

        /**
         * Test if this instance is an {@code NOT_EQUAL} condition.
         * @return {@code true} if an {@code NOT_EQUAL} condition, {@code false} otherwise
         */
        public boolean isNotEqual() {
            return (this instanceof NotEqual);
        }

        /**
         * Get this instance as an {@code NOT_EQUAL} condition.
         *
         * @return NotEqual
         */
        public NotEqual asNotEqual() {
            return (NotEqual) this;
        }

        /**
         * Test if this instance is an {@code NOT} condition.
         * @return {@code true} if an {@code NOT} condition, {@code false} otherwise
         */
        public boolean isNot() {
            return (this instanceof Not);
        }

        /**
         * Get this instance as an {@code NOT} condition.
         *
         * @return Not
         */
        public Not asNot() {
            return (Not) this;
        }
    }

    /**
     * This condition represents the logical negation of a sub condition.
     */
    public final class Not extends Condition {

        private final Condition right;

        Not(Condition expr) {
            this.right = expr;
        }

        /**
         * Get the right condition operand.
         * @return Condition
         */
        public Condition right() {
            return right;
        }
    }

    /**
     * Base class for conditions having two sub conditions.
     */
    public static abstract class LeftRightCondition extends Condition {

        private final Condition left;
        private final Condition right;

        LeftRightCondition(Condition left, Condition right) {
            this.left = left;
            this.right = right;
        }

        /**
         * Get the left condition operand.
         * @return Condition
         */
        public Condition left(){
            return left;
        }

        /**
         * Get the right condition operand.
         * @return Condition
         */
        public Condition right() {
            return right;
        }
    }

    /**
     * This condition represents an equality between two sub conditions.
     */
    public static final class Is extends LeftRightCondition {

        Is(Condition left, Condition right) {
            super(left, right);
        }
    }

    /**
     * This condition represents an inequality between two sub conditions.
     */
    public static final class IsNot extends LeftRightCondition {

        IsNot(Condition left, Condition right) {
            super(left, right);
        }
    }

    /**
     * This condition represents a logical AND between two sub conditions.
     */
    public static final class And extends LeftRightCondition {

        And(Condition left, Condition right) {
            super(left, right);
        }
    }

    /**
     * This condition represents a logical OR between two sub conditions.
     */
    public static final class Or extends LeftRightCondition {

        Or(Condition left, Condition right) {
            super(left, right);
        }
    }

    /**
     * This condition represents a logical OR between two sub conditions.
     */
    public static final class Xor extends LeftRightCondition {

        Xor(Condition left, Condition right) {
            super(left, right);
        }
    }

    /**
     * This condition represents the equality of two values.
     */
    public static final class Equal extends Condition {

        private final Value left;
        private final Value right;

        Equal(Value left, Value right) {
            this.right = right;
            this.left = left;
        }

        /**
         * Get the left value operand.
         * @return Value
         */
        public Value left() {
            return left;
        }

        /**
         * Get the right value operand.
         *
         * @return Value
         */
        public Value right() {
            return right;
        }
    }

    /**
     * This condition represents the inequality of two values.
     */
    public static final class NotEqual extends Condition {

        private final Value left;
        private final Value right;

        NotEqual(Value left, Value right) {
            this.right = right;
            this.left = left;
        }

        /**
         * Get the left value operand.
         * @return Value
         */
        public Value left() {
            return left;
        }

        /**
         * Get the right value operand.
         *
         * @return Value
         */
        public Value right() {
            return right;
        }
    }
}
