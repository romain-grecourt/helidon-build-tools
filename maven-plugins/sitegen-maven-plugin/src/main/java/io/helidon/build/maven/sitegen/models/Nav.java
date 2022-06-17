/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.build.maven.sitegen.models;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;

import io.helidon.build.maven.sitegen.Config;

import static io.helidon.build.common.Strings.requireValid;

/**
 * Navigation tree.
 */
@SuppressWarnings("unused")
public final class Nav extends SourcePathFilter {

    private final String title;
    private final Glyph glyph;
    private final String href;
    private final String target;
    private final String pathprefix;
    private final List<Nav> items;
    private final boolean root;

    private Nav(Builder builder) {
        super(builder);
        this.title = requireValid(builder.title, "title is invalid!");
        this.glyph = builder.glyph;
        this.href = builder.href;
        this.target = builder.target;
        this.pathprefix = builder.pathprefix;
        this.items = builder.items;
        this.root = builder.parent == null;
    }

    /**
     * Get the title.
     *
     * @return title, may be {@code null}
     */
    public String title() {
        return title;
    }

    /**
     * Get the glyph.
     *
     * @return glyph, may be {@code null}
     */
    public Glyph glyph() {
        return glyph;
    }

    /**
     * Get the href value.
     *
     * @return href value, may be {@code null}
     */
    public String href() {
        return href;
    }

    /**
     * Get the href target.
     *
     * @return href target, may be {@code null}
     */
    public String target() {
        return target;
    }

    /**
     * Get the path prefix.
     *
     * @return path prefix, may be {@code null}
     */
    public String pathprefix() {
        return pathprefix;
    }

    /**
     * Get the nested items.
     *
     * @return list of items
     */
    public List<Nav> items() {
        return items;
    }

    /**
     * Test if this item is a root node.
     *
     * @return {@code true} if root, {@code false} otherwise
     */
    public boolean isRoot() {
        return root;
    }

    @Override
    public Object get(String attr) {
        switch (attr) {
            case "title":
                return title;
            case "glyph":
                return glyph;
            case "href":
                return href;
            case "target":
                return target;
            case "pathprefix":
                return pathprefix;
            case "items":
                return items;
            case "isgroup":
                return !items.isEmpty();
            case "islink":
                return href != null;
            default:
                return super.get(attr);
        }
    }

    /**
     * Create a new instance from config.
     *
     * @param config config
     * @return navigation tree root node
     */
    public static Nav create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create a new builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder(null);
    }

    /**
     * Create a new builder.
     *
     * @param parent parent builder
     * @return builder
     */
    public static Builder builder(Nav.Builder parent) {
        return new Builder(parent);
    }

    /**
     * Builder of {@link Nav}.
     */
    public static final class Builder extends AbstractBuilder<Builder, Nav> {

        private String title;
        private Glyph glyph;
        private String href;
        private String target = "_blank";
        private String pathprefix;
        private final List<Nav> items = new ArrayList<>();
        private final Builder parent;

        private Builder(Builder parent) {
            this.parent = parent;
        }

        /**
         * Get the parent builder.
         *
         * @return parent builder
         */
        public Builder parent() {
            return parent;
        }

        /**
         * Set the title.
         *
         * @param title title
         * @return this builder
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Set the glyph.
         *
         * @param type  glyph type
         * @param value glyph value
         * @return this builder
         */
        public Builder glyph(String type, String value) {
            this.glyph = Glyph.builder()
                              .type(type)
                              .value(value)
                              .build();
            return this;
        }

        /**
         * Set the glyph.
         *
         * @param glyph glyph
         * @return this builder
         */
        public Builder glyph(Glyph glyph) {
            this.glyph = glyph;
            return this;
        }

        /**
         * Set the glyph.
         *
         * @param supplier glyph supplier
         * @return this builder
         */
        public Builder glyph(Supplier<Glyph> supplier) {
            if (supplier != null) {
                this.glyph = supplier.get();
            }
            return this;
        }

        /**
         * Set the href value.
         *
         * @param href href
         * @return this builder
         */
        public Builder href(String href) {
            this.href = href;
            return this;
        }

        /**
         * Set the href target.
         *
         * @param target target
         * @return this builder
         */
        public Builder target(String target) {
            this.target = target;
            return this;
        }

        /**
         * Set the pathprefix.
         *
         * @param pathprefix pathprefix
         * @return this builder
         */
        public Builder pathprefix(String pathprefix) {
            this.pathprefix = pathprefix;
            return this;
        }

        /**
         * Add an item.
         *
         * @param item item
         * @return this builder
         */
        public Builder item(Nav item) {
            if (item != null) {
                this.items.add(item);
            }
            return this;
        }

        /**
         * Add an item.
         *
         * @param supplier item supplier
         * @return this builder
         */
        public Builder item(Supplier<Nav> supplier) {
            if (supplier != null) {
                this.items.add(supplier.get());
            }
            return this;
        }

        /**
         * Apply the specified configuration.
         *
         * @param config config
         * @return this builder
         */
        public Builder config(Config config) {
            Deque<Builder> builders = new ArrayDeque<>();
            Deque<Config> stack = new ArrayDeque<>();
            applyConfig(config);
            builders.push(this);
            stack.push(config);
            Builder parentBuilder = null;
            while (!stack.isEmpty() && !builders.isEmpty()) {
                Config node = stack.peek();
                Builder builder = builders.peek();
                List<Config> items = node.get("items").asNodeList().orElseGet(List::of);
                if (node.containsKey("title") && !items.isEmpty() && builder != parentBuilder) {
                    boolean traversing = false;
                    ListIterator<Config> it = items.listIterator(items.size());
                    while (it.hasPrevious()) {
                        Config item = it.previous();
                        if (item.containsKey("title")) {
                            traversing = true;
                            stack.push(item);
                            builders.push(new Builder(builder));
                        }
                    }
                    if (traversing) {
                        // 1st tree-node pass, or no nested items
                        continue;
                    }
                }
                // leaf-node, or 2nd tree-node pass
                builder.applyConfig(node);
                if (builder.parent != null) {
                    builder.parent.item(builder.build());
                    parentBuilder = builder.parent;
                    builders.pop();
                }
                stack.pop();
            }
            return this;
        }

        private void applyConfig(Config config) {
            title = config.get("title").asString().orElseThrow(() -> new IllegalArgumentException("title is required"));
            glyph = config.get("glyph").asOptional().map(Glyph::create).orElse(null);
            href = config.get("href").asString().orElse(null);
            target = config.get("target").asString().orElse(null);
            pathprefix = config.get("pathprefix").asString().orElse(null);
            config.get("items").asOptional().ifPresent(super::config);
        }

        /**
         * Build this instance.
         *
         * @return new instance
         */
        public Nav build() {
            return new Nav(this);
        }
    }
}
