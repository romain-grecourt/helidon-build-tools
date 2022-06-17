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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.build.maven.sitegen.asciidoctor.AsciidocEngine;
import io.helidon.build.maven.sitegen.freemarker.FreemarkerEngine;

import static io.helidon.build.common.Strings.requireValid;

/**
 * Configuration of {@link FreemarkerEngine} and {@link AsciidocEngine}.
 */
public final class SiteEngine {

    private static final Map<String, SiteEngine> REGISTRY = new HashMap<>();

    private final AsciidocEngine asciidoc;
    private final FreemarkerEngine freemarker;

    private SiteEngine(Builder builder) {
        freemarker = Optional.ofNullable(builder.freemarker)
                             .orElseGet(() -> FreemarkerEngine.create(builder.backend));
        asciidoc = Optional.ofNullable(builder.asciidoc)
                           .orElseGet(() -> AsciidocEngine.create(builder.backend));
    }

    /**
     * Get the asciidoc engine.
     *
     * @return asciidoc engine, never {@code null}
     */
    public AsciidocEngine asciidoc() {
        return asciidoc;
    }

    /**
     * Get the freemarker engine.
     *
     * @return freemarker engine, never {@code null}
     */
    public FreemarkerEngine freemarker() {
        return freemarker;
    }

    /**
     * Register a site engine in the registry with the given backend  name.
     *
     * @param backend the backend name to use as key in the registry
     * @param engine  the engine instance to register
     */
    public static void register(String backend, SiteEngine engine) {
        REGISTRY.put(requireValid(backend, "backend is invalid!"), engine);
    }

    /**
     * Remove the registered for the given backend.
     *
     * @param backend the backend to remove
     */
    public static void deregister(String backend) {
        REGISTRY.remove(requireValid(backend, "backend is invalid!"));
    }

    /**
     * Get a site engine from the registry.
     *
     * @param backend the backend name
     * @return site engine, never {@code null}
     * @throws IllegalArgumentException if site engine is found for the given backend name
     */
    public static SiteEngine get(String backend) {
        SiteEngine siteEngine = REGISTRY.get(backend);
        if (siteEngine == null) {
            throw new IllegalArgumentException("no site engine found for backend: " + backend);
        }
        return siteEngine;
    }

    /**
     * A builder of {@link SiteEngine}.
     */
    public static class Builder implements Supplier<SiteEngine> {

        private FreemarkerEngine freemarker;
        private AsciidocEngine asciidoc;
        private final String backend;

        private Builder(String backend) {
            this.backend = backend;
        }

        /**
         * Set the freemarker engine to use.
         *
         * @param freemarker the freemarker engine
         * @return this builder
         */
        public Builder freemarker(FreemarkerEngine freemarker) {
            this.freemarker = freemarker;
            return this;
        }

        /**
         * Set the asciidoctor engine to use.
         *
         * @param asciidoc asciidoc engine
         * @return this builder
         */
        public Builder asciidoctor(AsciidocEngine asciidoc) {
            this.asciidoc = asciidoc;
            return this;
        }

        /**
         * Set the asciidoctor engine to use.
         *
         * @param supplier asciidoc engine supplier
         * @return this builder
         */
        public Builder asciidoctor(Supplier<AsciidocEngine> supplier) {
            this.asciidoc = supplier.get();
            return this;
        }

        /**
         * Apply the specified configuration.
         *
         * @param config config
         * @return this builder
         */
        public Builder config(Config config) {
            freemarker = config.get("freemarker")
                               .asOptional()
                               .map(c -> FreemarkerEngine.create(backend, c))
                               .orElse(null);
            asciidoc = config.get("asciidoctor")
                             .asOptional()
                             .map(c -> AsciidocEngine.create(backend, c))
                             .orElse(null);
            return this;
        }

        /**
         * Build the instance.
         *
         * @return new instance.
         */
        public SiteEngine build() {
            return new SiteEngine(this);
        }

        @Override
        public SiteEngine get() {
            return build();
        }
    }

    /**
     * Create a new instance from configuration.
     *
     * @param backend backend name
     * @param config  config
     * @return new instance
     */
    public static SiteEngine create(String backend, Config config) {
        return builder(backend).config(config).build();
    }

    /**
     * Create a new instance.
     *
     * @param backend backend name
     * @return new instance
     */
    public static SiteEngine create(String backend) {
        return builder(backend).build();
    }

    /**
     * Create a new builder.
     *
     * @param backend backend name
     * @return new builder
     */
    public static Builder builder(String backend) {
        return new Builder(backend);
    }
}
