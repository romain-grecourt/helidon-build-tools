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

/**
 * Step.
 */
public class Step extends BlockStatement {

    private final String label;
    private final String help;

    private Step(Builder builder) {
        super(builder);
        this.label = builder.label;
        this.help = builder.help;
    }

    /**
     * Get the label.
     *
     * @return label
     */
    public String label() {
        return label;
    }

    /**
     * Get the help
     *
     * @return help
     */
    public String help() {
        return help;
    }

    @Override
    public <A, R> R accept(Visitor<A, R> visitor, A arg) {
        return visitor.visit(this, arg);
    }

    /**
     * Create a new builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Step builder.
     */
    public static final class Builder extends BlockStatement.Builder<Step, Builder> {

        private String label;
        private String help;

        private Builder() {
        }

        /**
         * Set the label.
         *
         * @param label label
         * @return this builder
         */
        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /**
         * Set the help.
         *
         * @param help help
         * @return this builder
         */
        public Builder help(String help) {
            this.help = help;
            return this;
        }

        @Override
        public Step build() {
            return new Step(this);
        }
    }
}
