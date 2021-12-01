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

import java.nio.file.Path;
import java.util.Objects;

/**
 * Model block.
 */
public class Model extends Output {

    private Model(Model.Builder builder) {
        super(builder);
    }

    /**
     * Model visitor.
     *
     * @param <A> argument type
     */
    public interface Visitor<A> {

        /**
         * Visit a list model.
         *
         * @param list list
         * @param arg  argument
         */
        default void visitList(List list, A arg) {
        }

        /**
         * Visit a map model.
         *
         * @param map map
         * @param arg argument
         */
        default void visitMap(Map map, A arg) {
        }

        /**
         * Visit a value.
         *
         * @param value value
         * @param arg   argument
         */
        default void visitValue(Value value, A arg) {
        }
    }

    /**
     * Visit this model.
     *
     * @param visitor visitor
     * @param arg     argument
     * @param <A>     argument type
     */
    public <A> void accept(Visitor<A> visitor, A arg) {
    }

    @Override
    public <A> void accept(Output.Visitor<A> visitor, A arg) {
        visitor.visitModel(this, arg);
    }

    /**
     * List model.
     */
    public static final class List extends MergeableModel {

        private List(Model.Builder builder) {
            super(builder);
        }

        @Override
        public <A> void accept(Model.Visitor<A> visitor, A arg) {
            visitor.visitList(this, arg);
        }
    }

    /**
     * Mergeable model.
     */
    public static abstract class MergeableModel extends Model {

        private final String key;
        private final int order;

        private MergeableModel(Model.Builder builder) {
            super(builder);
            String rawOrder = builder.attributes.get("order");
            this.order = rawOrder != null ? Integer.parseInt(rawOrder) : 100;
            this.key = builder.attributes.get("key");
        }

        /**
         * Get the key.
         *
         * @return key
         */
        public String key() {
            return key;
        }

        /**
         * Get the order.
         *
         * @return order
         */
        public int order() {
            return order;
        }
    }

    /**
     * Map model.
     */
    public static final class Map extends MergeableModel {

        private Map(Model.Builder builder) {
            super(builder);
        }

        @Override
        public <A> void accept(Model.Visitor<A> visitor, A arg) {
            visitor.visitMap(this, arg);
        }
    }

    /**
     * Model value.
     */
    public static final class Value extends MergeableModel {

        private final String value;

        private Value(Model.Builder builder) {
            super(builder);
            this.value = Objects.requireNonNull(builder.value, "value");
        }

        /**
         * Get the value.
         *
         * @return value
         */
        public String value() {
            return value;
        }

        @Override
        public <A> void accept(Model.Visitor<A> visitor, A arg) {
            visitor.visitValue(this, arg);
        }
    }

    /**
     * Create a new model block builder.
     *
     * @param scriptPath script path
     * @param position   position
     * @param kind       block kind
     * @return builder
     */
    public static Builder builder(Path scriptPath, Position position, Kind kind) {
        return new Builder(scriptPath, position, kind);
    }

    /**
     * Model block builder.
     */
    public static class Builder extends Output.Builder {

        private String value;

        private Builder(Path scriptPath, Position position, Kind kind) {
            super(scriptPath, position, kind);
        }

        @Override
        public Block.Builder value(String value) {
            this.value = value;
            return this;
        }

        @Override
        protected Block doBuild() {
            statements.replaceAll(b -> {
                if (b.kind == Statement.Kind.NOOP) {
                    return new Builder(b.scriptPath, b.position, Kind.VALUE)
                            .value(((Noop.Builder) b).value)
                            .attributes(b.attributes);
                }
                return b;
            });
            switch (kind) {
                case MODEL:
                    return new Model(this);
                case MAP:
                    return new Map(this);
                case LIST:
                    return new List(this);
                case VALUE:
                    return new Value(this);
                default:
                    throw new IllegalArgumentException("Unknown model block kind: " + kind);
            }
        }
    }
}
