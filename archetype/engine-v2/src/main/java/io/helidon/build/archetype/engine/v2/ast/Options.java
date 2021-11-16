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
 * Options input.
 *
 * @param <T> input default value type
 */
public abstract class Options<T> extends Input<T> {

    /**
     * Create a new options input.
     *
     * @param builder builder
     */
    protected Options(Builder<?, T, ?> builder) {
        super(builder);
    }

    /**
     * Options builder.
     *
     * @param <T> sub-type
     * @param <U> default value type
     * @param <V> builder sub-type
     */
    @SuppressWarnings("unchecked")
    public static abstract class Builder<T extends Options<U>, U, V extends Builder<T, U, V>>
            extends Input.Builder<T, U, V> {
    }
}
