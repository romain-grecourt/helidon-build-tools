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
public abstract class Model extends Block {

    private Model(Model.Builder builder) {
        super(builder);
    }

    /**
     * Model visitor.
     */
    public interface Visitor {

        /**
         * Visit a list model.
         *
         * @param list list
         * @return result
         */
        default VisitResult visitList(List list) {
            return visitAny(list);
        }

        /**
         * Visit a list after traversing the nested statements.
         *
         * @param list list
         * @return result
         */
        default VisitResult postVisitList(List list) {
            return postVisitAny(list);
        }

        /**
         * Visit a map model.
         *
         * @param map map
         * @return result
         */
        default VisitResult visitMap(Map map) {
            return visitAny(map);
        }

        /**
         * Visit a map after traversing the nested statements.
         *
         * @param map map
         * @return result
         */
        default VisitResult postVisitMap(Map map) {
            return postVisitAny(map);
        }

        /**
         * Visit a value.
         *
         * @param value value
         * @return result
         */
        default VisitResult visitValue(Value value) {
            return visitAny(value);
        }

        /**
         * Visit any model.
         *
         * @param model model
         * @return result
         */
        @SuppressWarnings("unused")
        default VisitResult visitAny(Model model) {
            return VisitResult.CONTINUE;
        }

        /**
         * Visit any model after traversing the nested statements.
         *
         * @param model model
         * @return result
         */
        @SuppressWarnings("unused")
        default VisitResult postVisitAny(Model model) {
            return VisitResult.CONTINUE;
        }
    }

    /**
     * Visit this model.
     *
     * @param visitor visitor
     * @return result
     */
    public abstract VisitResult accept(Visitor visitor);

    /**
     * Visit this model after traversing the nested statements.
     *
     * @param visitor visitor
     * @return result
     */
    public abstract VisitResult acceptAfter(Visitor visitor);

    @Override
    public VisitResult accept(Block.Visitor visitor) {
        return visitor.visitModel(this);
    }

    @Override
    public VisitResult acceptAfter(Block.Visitor visitor) {
        return visitor.postVisitModel(this);
    }

    /**
     * List model.
     */
    public static final class List extends MergeableModel {

        private List(Model.Builder builder) {
            super(builder);
        }

        @Override
        public VisitResult accept(Model.Visitor visitor) {
            return visitor.visitList(this);
        }

        @Override
        public VisitResult acceptAfter(Model.Visitor visitor) {
            return visitor.postVisitList(this);
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
        public VisitResult accept(Model.Visitor visitor) {
            return visitor.visitMap(this);
        }

        @Override
        public VisitResult acceptAfter(Model.Visitor visitor) {
            return visitor.postVisitMap(this);
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
        public VisitResult accept(Model.Visitor visitor) {
            return visitor.visitValue(this);
        }

        @Override
        public VisitResult acceptAfter(Model.Visitor visitor) {
            return VisitResult.CONTINUE;
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
    public static class Builder extends Block.Builder {

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
                if (b instanceof Noop.Builder) {
                    return new Builder(b.scriptPath, b.position, Kind.VALUE)
                            .value(((Noop.Builder) b).value)
                            .attributes(b.attributes);
                }
                return b;
            });
            switch (kind) {
                case MAP:
                    return new Map(this);
                case LIST:
                    return new List(this);
                case VALUE:
                    return new Value(this);
                default:
                    throw new IllegalArgumentException("Unknown model block: " + kind);
            }
        }
    }
}
