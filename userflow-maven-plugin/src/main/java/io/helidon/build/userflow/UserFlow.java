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

import io.helidon.build.userflow.Expression.ParserException;
import io.helidon.build.userflow.ExpressionSyntaxTree.Condition;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Properties;

/**
 * User flow model.
 */
public final class UserFlow {

    private final LinkedList<Step> steps;

    private UserFlow(LinkedList<Step> steps) {
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
            steps.add(new Step(text, new Expression(properties.getProperty(step))));
        }
        return new UserFlow(steps);
    }

    /**
     * A step is a combines an expression and a text.
     */
    public static final class Step {

        private final String text;
        private final Expression expr;

        Step(String text, Expression expr) {
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
                case ("text"):
                    return text;
                case ("expr"):
                    return expr;
                default:
                    throw new IllegalArgumentException("Unkown attribute: " + attr);
            }
        }
    }
}
