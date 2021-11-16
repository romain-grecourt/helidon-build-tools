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

import java.util.LinkedList;
import java.util.List;

/**
 * List input value.
 */
public final class ListInputValue extends InputValue<List<String>> {

    private ListInputValue(Builder builder) {
        super(builder);
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
     * List input value builder.
     */
    public static final class Builder extends InputValue.Builder<ListInputValue, List<String>, Builder> {

        private Builder() {
            super(new LinkedList<>());
        }

        /**
         * Add a value.
         *
         * @param value value
         * @return this builder
         */
        public Builder value0(String value) {
            super.value.add(value);
            return this;
        }

        @Override
        public ListInputValue build() {
            return new ListInputValue(this);
        }
    }
}
