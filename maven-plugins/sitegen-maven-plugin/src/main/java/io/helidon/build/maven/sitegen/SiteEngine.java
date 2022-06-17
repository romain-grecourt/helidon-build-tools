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
import java.util.function.Supplier;

import io.helidon.build.maven.sitegen.asciidoctor.AsciidocEngine;
import io.helidon.build.maven.sitegen.freemarker.FreemarkerEngine;

import static io.helidon.build.maven.sitegen.Helper.requireValidString;

/**
 * Configuration of {@link FreemarkerEngine} and {@link AsciidocEngine}.
 */
public final class SiteEngine {

    private static final Map<String, SiteEngine> REGISTRY = new HashMap<>();

    private final AsciidocEngine asciidoc;
    private final FreemarkerEngine freemarker;

    private SiteEngine(Builder builder) {
        if (builder.freemarker != null) {
            this.freemarker = builder.freemarker;
        } else {
            this.freemarker = FreemarkerEngine.create();
        }
        if (builder.asciidoc != null) {
            this.asciidoc = builder.asciidoc;
        } else {
            this.asciidoc = AsciidocEngine.create();
        }
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
        REGISTRY.put(requireValidString(backend, "backend"), engine);
    }

    /**
     * Remove the registered for the given backend.
     *
     * @param backend the backend to remove
     */
    public static void deregister(String backend) {
        REGISTRY.remove(requireValidString(backend, "backend"));
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
            freemarker = config.get("freemarker").asOptional().map(FreemarkerEngine::create).orElse(null);
            asciidoc = config.get("asciidoctor").asOptional().map(AsciidocEngine::create).orElse(null);
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
     * @param config config
     * @return new instance
     */
    public static SiteEngine create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create a new instance.
     *
     * @return new instance
     */
    public static SiteEngine create() {
        return builder().build();
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
