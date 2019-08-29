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

import io.helidon.build.userflow.Expression.ParserException;
import io.helidon.build.userflow.ExpressionSyntaxTree.ConditionalExpression;
import io.helidon.build.userflow.ExpressionSyntaxTree.Value;

/**
 * User flow model.
 */
public final class UserFlow {

    private final LinkedList<Step> steps;

    /**
     * Create a new user flow instance.
     * @param steps user flow steps
     */
    UserFlow(LinkedList<Step> steps) {
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
    static UserFlow create(File descriptor) throws IOException, ParserException {
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
            steps.add(new Step(step, text, new Expression(properties.getProperty(step))));
        }
        return new UserFlow(steps);
    }

    /**
     * A step is a combines an expression and a text.
     */
    public static final class Step {

        private final String name;
        private final String text;
        private final Expression expr;

        Step(String name, String text, Expression expr) {
            this.name=  name;
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
                case ("name"):
                    return name;
                case ("text"):
                    return text;
                case ("expr"):
                    return new ExpressionModel(expr.tree());
                default:
                    throw new IllegalArgumentException("Unkown attribute: " + attr);
            }
        }
    }

    /**
     * Expression template model.
     */
    public static final class ExpressionModel {

        private final ExpressionSyntaxTree node;

        /**
         * Create a new expression model.
         * @param node syntax tree node
         */
        ExpressionModel(ExpressionSyntaxTree node) {
            this.node = node;
        }

        private Object getExpressionAttribute(String attr) {
            ConditionalExpression expression = node.asExpression();
            switch (attr) {
                case ("type"):
                    return expression.type().name();
                case ("isAnd"):
                    return expression.isAnd();
                case ("asAnd"):
                    return new ExpressionModel(expression.asAnd());
                case ("isIs"):
                    return expression.isIs();
                case ("asIs"):
                    return new ExpressionModel(expression.asIs());
                case ("isIsNot"):
                    return expression.isIsNot();
                case ("asIsNot"):
                    return new ExpressionModel(expression.asIsNot());
                case ("isOr"):
                    return expression.isOr();
                case ("asOr"):
                    return new ExpressionModel(expression.asOr());
                case ("isXor"):
                    return expression.isXor();
                case ("asXor"):
                    return new ExpressionModel(expression.asXor());
                case ("isNot"):
                    return expression.isNot();
                case ("asNot"):
                    return new ExpressionModel(expression.asNot());
                case ("isNotEqual"):
                    return expression.isNotEqual();
                case ("asNotEqual"):
                    return new ExpressionModel(expression.asNotEqual());
                case ("isEqual"):
                    return expression.isEqual();
                case ("asEqual"):
                    return new ExpressionModel(expression.asEqual());
                case ("isBinaryOperation"):
                    return expression.isBinaryOperation();
                case ("isUnaryOperation"):
                    return expression.isUnaryOperation();
                case ("right"):
                    if (expression.isBinaryOperation()) {
                        return new ExpressionModel(expression.asBinaryOperation().right());
                    } else {
                        return new ExpressionModel(expression.asBinaryOperation().right());
                    }
                case ("left"):
                    if (expression.isBinaryOperation()) {
                        return new ExpressionModel(expression.asBinaryOperation().left());
                    } else {
                        throw new IllegalArgumentException("Not a binary operation: " + expression.type());
                    }
                default:
                    throw new IllegalArgumentException("Unkown attribute: " + attr);
            }
        }

        private Object getValueAttribute(String attr) {
            Value value = node.asValue();
            switch (attr) {
                case ("type"):
                    return value.type().name();
                case ("isVariable"):
                    return value.isVariable();
                case ("asVariable"):
                    return new ExpressionModel(value.asVariable());
                case ("isLiteral"):
                    return value.isLiteral();
                case ("asLiteral"):
                    return new ExpressionModel(value.asLiteral());
                case ("value"):
                    return value.value();
                default:
                    throw new IllegalArgumentException("Unkown attribute: " + attr);
            }
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
                case ("isExpression"):
                    return node.isExpression();
                case ("asExpression"):
                    return new ExpressionModel(node.asExpression());
                default:
                    if (node.isExpression()) {
                        return getExpressionAttribute(attr);
                    } else if (node.isValue()) {
                        return getValueAttribute(attr);
                    } else {
                        throw new IllegalArgumentException("Unkown node type");
                    }
            }
        }
    }
}
