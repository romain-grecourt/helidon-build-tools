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
 * Files.
 */
public final class File extends AbstractFile {

    private File(Builder builder) {
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
     * File builder.
     */
    public static final class Builder extends AbstractFile.Builder<File, Builder> {

        private Builder() {
        }

        @Override
        public Builder statement(Statement.Builder<? extends Statement, ?> builder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File build() {
            return new File(this);
        }
    }
}
