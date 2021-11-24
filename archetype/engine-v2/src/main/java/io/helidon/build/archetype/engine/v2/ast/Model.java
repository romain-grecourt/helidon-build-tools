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
     * @param <R> result type
     * @param <A> argument type
     */
    public interface Visitor<R, A> {

        /**
         * Visit a model block.
         *
         * @param model model
         * @param arg   argument
         * @return visit result
         */
        @SuppressWarnings("unused")
        default R visitModel(Model model, A arg) {
            return null;
        }

        /**
         * Visit a list model.
         *
         * @param list list
         * @param arg  argument
         * @return visit result
         */
        default R visitList(List list, A arg) {
            return visitModel(list, arg);
        }

        /**
         * Visit a map model.
         *
         * @param map map
         * @param arg argument
         * @return visit result
         */
        default R visitMap(Map map, A arg) {
            return visitModel(map, arg);
        }

        /**
         * Visit a value.
         *
         * @param value value
         * @param arg   argument
         * @return visit result
         */
        default R visitValue(Value value, A arg) {
            return visitModel(value, arg);
        }
    }

    /**
     * Visit this model.
     *
     * @param visitor visitor
     * @param arg     argument
     * @param <R>     result type
     * @param <A>     argument type
     * @return visit result
     */
    public <R, A> R accept(Visitor<R, A> visitor, A arg) {
        return visitor.visitModel(this, arg);
    }

    @Override
    public <R, A> R accept(Output.Visitor<R, A> visitor, A arg) {
        return visitor.visitModel(this, arg);
    }

    /**
     * List model.
     */
    public static final class List extends MergeableModel {


        private List(Model.Builder builder) {
            super(builder);
        }

        @Override
        public <R, A> R accept(Model.Visitor<R, A> visitor, A arg) {
            return visitor.visitList(this, arg);
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
            this.order = builder.parseAttribute(ValueTypes.INT, "order", 100).asInt();
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
        public <R, A> R accept(Model.Visitor<R, A> visitor, A arg) {
            return visitor.visitMap(this, arg);
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
        public <R, A> R accept(Model.Visitor<R, A> visitor, A arg) {
            return visitor.visitValue(this, arg);
        }
    }

    /**
     * Create a new model block builder.
     *
     * @param location location
     * @param position position
     * @param kind     block kind
     * @return builder
     */
    public static Builder builder(Path location, Position position, Kind kind) {
        return new Builder(location, position, kind);
    }

    /**
     * Model block builder.
     */
    public static class Builder extends Output.Builder {

        private String value;

        private Builder(Path location, Position position, Kind kind) {
            super(location, position, kind);
        }

        @Override
        public Block.Builder value(String value) {
            this.value = value;
            return this;
        }

        @Override
        protected Block build0() {
            statements.replaceAll(b -> {
                if (b.kind == Statement.Kind.NOOP) {
                    return new Builder(b.location, b.position, Kind.VALUE)
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
