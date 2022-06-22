/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.build.maven.sitegen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.build.common.SourcePath;
import io.helidon.build.maven.sitegen.asciidoctor.AsciidocPageRenderer;
import io.helidon.build.maven.sitegen.freemarker.FreemarkerEngine;
import io.helidon.build.maven.sitegen.freemarker.TemplateSession;
import io.helidon.build.maven.sitegen.models.Nav;
import io.helidon.build.maven.sitegen.models.Page;

import static io.helidon.build.common.FileUtils.resourceAsPath;
import static io.helidon.build.common.Strings.requireValid;
import static io.helidon.build.maven.sitegen.RenderingContext.copyResources;
import static io.helidon.build.maven.sitegen.RenderingContext.filterPages;
import static io.helidon.build.maven.sitegen.asciidoctor.AsciidocPageRenderer.ADOC_EXT;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * A backend implementation for Vuetify.
 *
 * @see <a href="https://vuetifyjs.com">https://vuetifyjs.com</a>
 */
public class VuetifyBackend extends Backend {

    /**
     * The Vuetify backend name.
     */
    public static final String BACKEND_NAME = "vuetify";

    private final Map<String, PageRenderer> renderers;
    private final Nav nav;
    private final Map<String, String> theme;
    private final Path staticFiles;
    private final String home;
    private final List<String> releases;

    private VuetifyBackend(Builder builder) {
        super(BACKEND_NAME);
        theme = builder.theme;
        nav = builder.nav;
        home = requireValid(builder.home, "home is invalid!");
        releases = builder.releases;
        renderers = Map.of(ADOC_EXT, AsciidocPageRenderer.create(BACKEND_NAME));
        staticFiles = resourceAsPath("/files/vuetify", VuetifyBackend.class);
    }

    /**
     * Get the navigation tree root.
     *
     * @return navigation tree root or {@code null} if not set
     */
    public Nav nav() {
        return nav;
    }

    /**
     * Get the theme.
     *
     * @return map, never {@code null}
     */
    public Map<String, String> theme() {
        return theme;
    }

    /**
     * Get the home.
     *
     * @return home or {@code null} if not set
     */
    public String home() {
        return home;
    }

    /**
     * Get the releases.
     *
     * @return list, never {@code null}
     */
    public List<String> releases() {
        return releases;
    }

    @Override
    public Map<String, PageRenderer> renderers() {
        return renderers;
    }

    @Override
    public void generate(RenderingContext ctx) {
        Path pagesDir = ctx.outputDir().resolve("pages");
        try {
            Files.createDirectories(pagesDir);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        // render all pages
        ctx.processPages(pagesDir, "js");

        // copy declared assets
        ctx.copyStaticAssets();

        TemplateSession session = ctx.templateSession();

        Page home = ctx.pages().get(new SourcePath(this.home).asString(false));
        if (home == null) {
            throw new IllegalStateException("unable to get home page");
        }

        Collection<Page> pages = ctx.pages().values();

        // resolve navigation
        Nav resolvedNav;
        Set<String> navRoutes;
        if (nav != null) {
            resolvedNav = resolveNav(pages);
            navRoutes = resolvedNav.items()
                                   .stream()
                                   .flatMap(n -> n.items().isEmpty() ? Stream.of(n) : n.items().stream()) // depth:1
                                   .flatMap(n -> n.items().isEmpty() ? Stream.of(n) : n.items().stream()) // depth:2
                                   .flatMap(n -> n.items().isEmpty() ? Stream.of(n) : n.items().stream()) // depth:3
                                   .map(Nav::to)
                                   .filter(Objects::nonNull)
                                   .collect(toSet());
        } else {
            resolvedNav = null;
            navRoutes = Set.of();
        }

        Map<String, Page> pagesByRoute = pages.stream()
                                              .map(p -> Map.entry(p.target(), p))
                                              .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        // resolve all routes
        List<String> allRoutes = new ArrayList<>(navRoutes);
        allRoutes.add(home.target());
        allRoutes.addAll(pagesByRoute.keySet());

        // TODO navRoutes must follow the declaration order
        allRoutes = allRoutes.stream()
                             .sorted()
                             .distinct()
                             .collect(toList());

        Map<String, String> allBindings = session.vueBindings().bindings();

        Map<String, Object> model = new HashMap<>();
        model.put("searchEntries", session.searchIndex().entries());
        model.put("navRoutes", navRoutes);
        model.put("allRoutes", allRoutes);
        model.put("customLayoutEntries", session.customLayouts().mappings());
        model.put("pages", pagesByRoute);
        model.put("metadata", home.metadata());
        model.put("nav", resolvedNav);
        model.put("header", ctx.site().header());
        model.put("theme", theme);
        model.put("home", home);
        model.put("releases", releases);
        model.put("bindings", allBindings);

        FreemarkerEngine freemarker = ctx.site().engine().freemarker();

        // custom bindings
        for (Page page : pages) {
            String bindings = allBindings.get(page.source());
            if (bindings != null) {
                Map<String, Object> bindingsModel = new HashMap<>(model);
                bindingsModel.put("bindings", bindings);
                bindingsModel.put("page", page);
                String path = "pages/" + page.target() + "_custom.js";
                freemarker.renderFile("custom_bindings", path, bindingsModel, ctx);
            }
        }

        // render search-index.js
        freemarker.renderFile("search_index", "main/search-index.json", model, ctx);

        // render index.html
        freemarker.renderFile("index", "index.html", model, ctx);

        // render main/config.js
        freemarker.renderFile("config", "main/config.js", model, ctx);

        // copy vuetify resources
        copyResources(staticFiles, ctx.outputDir());
    }

    private Nav resolveNav(Collection<Page> pages) {
        Deque<Nav.Builder> builders = new ArrayDeque<>();
        Deque<Nav> stack = new ArrayDeque<>();
        stack.push(nav);
        builders.push(Nav.builder());
        Nav.Builder parent = null;
        while (!stack.isEmpty() && !builders.isEmpty()) {
            Nav node = stack.peek();
            Nav.Builder builder = builders.peek();
            List<Nav> items = node.items();
            if (parent != builder && !items.isEmpty()) {
                // 1st tree-node pass
                ListIterator<Nav> it = items.listIterator(items.size());
                while (it.hasPrevious()) {
                    Nav item = it.previous();
                    builders.push(Nav.builder(builder));
                    stack.push(item);
                }
            } else {
                // leaf-node, or 2nd tree-node pass
                Nav.Builder nextParent = builder.parent();
                List<Nav.Builder> itemBuilders = new ArrayList<>();
                if (node.includes().isEmpty()) {
                    itemBuilders.add(builder.title(node.title())
                                            .to(node.to())
                                            .href(node.href())
                                            .pathprefix(node.pathprefix())
                                            .includes(node.includes())
                                            .excludes(node.excludes())
                                            .glyph(node.glyph()));
                } else {
                    // resolve item
                    for (Page page : filterPages(pages, node.includes(), node.excludes())) {
                        itemBuilders.add(Nav.builder(nextParent)
                                            .title(page.metadata().title())
                                            .to(page.target()));
                    }
                }
                if (nextParent != null) {
                    for (Nav.Builder itemBuilder : itemBuilders) {
                        nextParent.item(itemBuilder.build());
                        nextParent.maxDepth(itemBuilder.maxDepth());
                    }
                    parent = nextParent;
                    builders.pop();
                }
                stack.pop();
            }
        }
        return builders.pop().build();
    }

    /**
     * A builder of {@link VuetifyBackend}.
     */
    @SuppressWarnings("unused")
    public static class Builder implements Supplier<VuetifyBackend> {

        private final Map<String, String> theme = new HashMap<>();
        private Nav nav;
        private String home;
        private final List<String> releases = new ArrayList<>();

        /**
         * Set the theme.
         *
         * @param theme a map containing theme options
         * @return this builder
         */
        public Builder theme(Map<String, String> theme) {
            if (theme != null) {
                this.theme.putAll(theme);
            }
            return this;
        }

        /**
         * Set the navigation.
         *
         * @param nav nav
         * @return this builder
         */
        public Builder nav(Nav nav) {
            this.nav = nav;
            return this;
        }

        /**
         * Set the navigation.
         *
         * @param supplier navigation supplier
         * @return this builder
         */
        public Builder nav(Supplier<Nav> supplier) {
            this.nav = supplier.get();
            return this;
        }

        /**
         * Set the home.
         *
         * @param home source path of the home page document
         * @return this builder
         */
        public Builder home(String home) {
            this.home = home;
            return this;
        }

        /**
         * Set the releases.
         *
         * @param releases releases
         * @return this builder
         */
        public Builder releases(List<String> releases) {
            if (releases != null) {
                this.releases.addAll(releases);
            }
            return this;
        }

        /**
         * Set the releases.
         *
         * @param releases releases
         * @return this builder
         */
        public Builder releases(String... releases) {
            if (releases != null) {
                this.releases.addAll(Arrays.asList(releases));
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
            theme.putAll(config.get("theme")
                               .asMap(String.class)
                               .orElseGet(Map::of));
            home = config.get("homePage")
                         .asString()
                         .orElse(null);
            releases.addAll(config.get("releases")
                                  .asList(String.class)
                                  .orElseGet(List::of));
            nav = config.get("navigation")
                        .asOptional()
                        .map(Nav::create)
                        .orElse(null);
            return this;
        }

        /**
         * Build the instance.
         *
         * @return new instance
         */
        public VuetifyBackend build() {
            return new VuetifyBackend(this);
        }

        @Override
        public VuetifyBackend get() {
            return build();
        }

    }

    /**
     * Create a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }
}
