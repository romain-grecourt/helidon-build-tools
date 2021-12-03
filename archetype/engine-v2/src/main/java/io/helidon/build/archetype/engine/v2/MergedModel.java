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
package io.helidon.build.archetype.engine.v2;

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Merged model.
 */
public abstract class MergedModel {

    protected final MergedModel parent;
    protected final String key;
    protected final int order;

    private MergedModel(MergedModel parent, String key, int order) {
        this.parent = parent;
        this.key = key;
        this.order = order;
    }

    /**
     * Resolve the model for the given block.
     *
     * @param inputResolver input resolver
     * @param block         block
     * @param context       context
     * @return model
     */
    public static MergedModel resolveModel(InputResolver inputResolver, Block block, Context context) {
        ModelResolver modelResolver = new ModelResolver();
        Walker.walk(new Controller(inputResolver, modelResolver), block, context);
        return modelResolver.head;
    }

    /**
     * Resolve the model for the given block.
     *
     * @param block   block
     * @param context context
     * @return model
     */
    public static MergedModel resolveModel(Block block, Context context) {
        return resolveModel(new InputResolver(), block, context);
    }

    /**
     * Get a model node by key.
     *
     * @param key key
     * @return model
     */
    public MergedModel get(String key) {
        return this.key != null && this.key.equals(key) ? this : null;
    }

    /**
     * Get this node as a string.
     *
     * @return string
     */
    public String asString() {
        throw new UnsupportedOperationException();
    }

    /**
     * Sort the nested values.
     */
    protected void sort() {
        throw new UnsupportedOperationException();
    }

    /**
     * Merge the given node.
     *
     * @param node node
     * @return the merged node
     */
    protected MergedModel add(MergedModel node) {
        throw new UnsupportedOperationException();
    }

    /**
     * List node.
     */
    public static class List extends MergedModel implements Iterable<MergedModel> {

        private final java.util.List<MergedModel> value = new LinkedList<>();

        /**
         * Create a new instance.
         *
         * @param parent parent
         * @param key    key
         * @param order  order
         */
        List(MergedModel parent, String key, int order) {
            super(parent, key, order);
        }

        @Override
        public Iterator<MergedModel> iterator() {
            return value.iterator();
        }

        @Override
        protected MergedModel add(MergedModel node) {
            value.add(node);
            return node;
        }

        @Override
        protected void sort() {
            value.sort((c1, c2) -> Integer.compare(c2.order, c1.order));
        }
    }

    /**
     * Map node.
     */
    public static class Map extends MergedModel {

        private final java.util.Map<String, MergedModel> value = new HashMap<>();

        /**
         * Create a new instance.
         *
         * @param parent parent
         * @param key    key
         * @param order  order
         */
        Map(MergedModel parent, String key, int order) {
            super(parent, key, order);
        }

        @Override
        public MergedModel get(String key) {
            return value.get(key);
        }

        @Override
        protected MergedModel add(MergedModel node) {
            if (node.key == null) {
                throw new IllegalArgumentException("Cannot add a model with no key to a map");
            }
            return value.compute(node.key, (k, v) -> {
                if (v == null) {
                    return node;
                }
                if (v instanceof List && node instanceof List) {
                    ((List) v).value.addAll(((List) node).value);
                    return v;
                }
                return v.order < node.order ? node : v;
            });
        }
    }

    /**
     * Value node.
     */
    public static class Value extends MergedModel {

        private final String value;

        /**
         * Create a new instance.
         *
         * @param parent parent
         * @param key    key
         * @param order  order
         * @param value  value
         */
        Value(MergedModel parent, String key, int order, String value) {
            super(parent, key, order);
            this.value = value;
        }

        @Override
        public String asString() {
            return value;
        }
    }

    private static final class ModelResolver implements Model.Visitor<Context> {

        MergedModel head = new Map(null, null, 0);

        @Override
        public VisitResult visitList(Model.List list, Context ctx) {
            head = head.add(new List(head, list.key(), list.order()));
            return VisitResult.CONTINUE;
        }

        @Override
        public VisitResult visitMap(Model.Map map, Context ctx) {
            head = head.add(new Map(head, map.key(), map.order()));
            return VisitResult.CONTINUE;
        }

        @Override
        public VisitResult visitValue(Model.Value value, Context ctx) {
            // value is a leaf-node, thus we are not updating the head
            head.add(new Value(head, value.key(), value.order(), value.value()));
            return VisitResult.CONTINUE;
        }

        @Override
        public VisitResult postVisitList(Model.List list, Context ctx) {
            head.sort();
            return postVisitAny(list, ctx);
        }

        @Override
        public VisitResult postVisitAny(Model model, Context ctx) {
            head = head.parent;
            return VisitResult.CONTINUE;
        }
    }
}
