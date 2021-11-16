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
 * Invocations.
 */
public final class Invocation extends Statement {

    private final String src;
    private final Kind kind;

    /**
     * Invocation kind.
     */
    public enum Kind {
        /***
         * Source invocation.
         */
        SOURCE,
        /**
         * Exec invocation.
         */
        EXEC
    }

    private Invocation(Builder builder) {
        super(builder);
        this.src = builder.src;
        this.kind = builder.kind;
    }

    /**
     * Get the script src.
     *
     * @return script source path
     */
    public String src() {
        return src;
    }

    /**
     * Get the kind.
     *
     * @return kind
     */
    public Kind kind() {
        return kind;
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
     * Invocation builder.
     */
    public static final class Builder extends Statement.Builder<Invocation, Builder> {

        private String src;
        private Kind kind;

        private Builder() {
        }

        /**
         * Set the script src.
         *
         * @param script script src
         * @return this builder
         */
        public Builder src(String script) {
            this.src = script;
            return this;
        }

        /**
         * Set the kind.
         *
         * @param kind kind
         * @return this builder
         */
        public Builder kind(Kind kind) {
            this.kind = kind;
            return this;
        }

        @Override
        public Invocation build() {
            return new Invocation(this);
        }
    }
}
