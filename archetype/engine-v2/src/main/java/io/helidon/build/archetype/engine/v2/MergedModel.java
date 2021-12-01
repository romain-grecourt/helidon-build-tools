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
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.Output;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Merged model.
 */
abstract class MergedModel {

    protected final MergedModel parent;
    protected final String key;
    protected final int order;

    private MergedModel(MergedModel parent, String key, int order) {
        this.parent = parent;
        this.key = key;
        this.order = order;
    }

    /**
     * Get a model node by key.
     *
     * @param key key
     * @return model
     */
    MergedModel get(String key) {
        return this.key != null && this.key.equals(key) ? this : null;
    }

    /**
     * Get this node as a string.
     *
     * @return string
     */
    String asString() {
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
     * Resolve the merged model for the given block.
     *
     * @param block   block
     * @param context context
     * @return model
     */
    static MergedModel resolve(Block block, Context context) {
        Resolver resolver = new Resolver();
        Walker.walk(resolver, block, context);
        return resolver.head;
    }

    private static class List extends MergedModel implements Iterable<MergedModel> {

        final java.util.List<MergedModel> value = new LinkedList<>();

        private List(MergedModel parent, String key, int order) {
            super(parent, key, order);
        }

        @Override
        @SuppressWarnings("NullableProblems")
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

    private static class Map extends MergedModel {

        final java.util.Map<String, MergedModel> value = new HashMap<>();

        private Map(MergedModel parent, String key, int order) {
            super(parent, key, order);
        }

        @Override
        MergedModel get(String key) {
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

    private static class Value extends MergedModel {

        final String value;

        private Value(MergedModel parent, String key, int order, String value) {
            super(parent, key, order);
            this.value = value;
        }

        @Override
        String asString() {
            return value;
        }
    }

    private static class Resolver extends Controller implements Output.Visitor<Void>, Model.Visitor<Void> {

        MergedModel head = new Map(null, null, 0);

        @Override
        public void visitList(Model.List list, Void arg) {
            head = head.add(new List(head, list.key(), list.order()));
        }

        @Override
        public void visitMap(Model.Map map, Void arg) {
            head = head.add(new Map(head, map.key(), map.order()));
        }

        @Override
        public void visitValue(Model.Value value, Void arg) {
            head = head.add(new Value(head, value.key(), value.order(), value.value()));
        }

        @Override
        public void visitModel(Model model, Void arg) {
            model.accept((Model.Visitor<Void>) this, arg);
        }

        @Override
        public void visitOutput(Output output, Void arg) {
            output.accept((Output.Visitor<Void>) this, arg);
        }

        @Override
        public Node.VisitResult postVisitBlock(Block block, Context arg) {
            if (block instanceof Model && block.blockKind() != Block.Kind.MODEL) {
                if (block instanceof Model.List) {
                    head.sort();
                }
                head = head.parent;
            }
            return Node.VisitResult.CONTINUE;
        }
    }
}
