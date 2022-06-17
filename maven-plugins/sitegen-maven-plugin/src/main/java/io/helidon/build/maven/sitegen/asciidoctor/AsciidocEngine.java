/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.build.maven.sitegen.asciidoctor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.helidon.build.maven.sitegen.Config;
import io.helidon.build.maven.sitegen.models.Page;
import io.helidon.build.maven.sitegen.RenderingContext;
import io.helidon.build.maven.sitegen.RenderingException;
import io.helidon.build.maven.sitegen.Site;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.AttributesBuilder;
import org.asciidoctor.Options;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.SafeMode;
import org.asciidoctor.ast.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import static io.helidon.build.maven.sitegen.Helper.requireNonNull;
import static io.helidon.build.maven.sitegen.Helper.requireValidString;
import static io.helidon.build.maven.sitegen.Helper.requireValidFile;
import static io.helidon.build.maven.sitegen.Helper.relativePath;

/**
 * A facade over Asciidoctorj.
 */
public class AsciidocEngine {

    /**
     * Constant for the default images directory.
     */
    public static final String DEFAULT_IMAGESDIR = "./images";

    private static final Logger LOGGER = LoggerFactory.getLogger(AsciidocEngine.class);
    private static volatile Asciidoctor asciidoctorInstance = null;

    private final String backend;
    private final List<String> libraries;
    private final Map<String, Object> attributes;
    private final String imagesdir;
    private final Asciidoctor asciidoctor;

    private AsciidocEngine(Builder builder) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        this.backend = requireValidString(Site.THREAD_LOCAL.get(), "backend");
        this.attributes = builder.attributes;
        this.libraries = builder.libraries;
        this.imagesdir = builder.imagesDir;
        if (asciidoctorInstance == null) {
            asciidoctorInstance = Asciidoctor.Factory.create();
        }
        this.asciidoctor = asciidoctorInstance;
        AsciidocExtensionRegistry.create(backend).register(asciidoctor);
    }

    /**
     * Unregister asciidoctor extensions.
     */
    public void unregister() {
        this.asciidoctor.unregisterAllExtensions();
    }

    /**
     * Get the asciidoctor libraries in use.
     *
     * @return list library name, never {@code null}
     */
    public List<String> libraries() {
        return libraries;
    }

    /**
     * Get the attributes.
     *
     * @return attributes, never {@code null}
     */
    public Map<String, Object> attributes() {
        return attributes;
    }

    /**
     * Get the image directory.
     *
     * @return image directory
     */
    public String imagesDir() {
        return imagesdir;
    }

    /**
     * Read a document's header.
     *
     * @param source the document to read the header from
     * @return the header as {@code Map<String, Object>}, never {@code null}
     */
    public Map<String, Object> readDocumentHeader(Path source) {
        requireValidFile(source, "source");
        OptionsBuilder optionsBuilder =
                Options.builder()
                       .attributes(Attributes.builder().attributes(attributes).build())
                       .safe(SafeMode.UNSAFE)
                       .headerFooter(false)
                       .eruby("")
                       .baseDir(source.getParent().toFile())
                       .option("parse_header_only", true);
        if (backend != null) {
            optionsBuilder.backend(this.backend);
        }
        Document doc = asciidoctor.loadFile(source.toFile(), optionsBuilder.build());
        Map<String, Object> headerMap = new HashMap<>();
        String h1 = parseSection0Title(source);
        if (h1 != null) {
            headerMap.put("h1", h1);
        }
        headerMap.putAll(doc.getAttributes());
        return headerMap;
    }

    /**
     * Render the document represented by the given {@link Page} instance.
     *
     * @param page            the {@link Page} instance representing the document to render
     * @param ctx             the context representing this site processing invocation
     * @param target          the file to create as a result of the rendering
     * @param extraAttributes extra asciidoctor attributes, can be {@code null}
     */
    @SuppressWarnings("GrazieInspection")
    public void render(Page page, RenderingContext ctx, Path target, Map<String, Object> extraAttributes) {
        requireNonNull(page, "page");
        requireNonNull(ctx, "ctx");

        asciidoctor.requireLibraries(libraries);
        if (extraAttributes == null) {
            extraAttributes = Collections.emptyMap();
        }

        Path outputDir = ctx.outputDir();
        Path sourceDir = ctx.sourceDir();
        Path source = sourceDir.resolve(page.sourcePath());
        requireValidFile(source, "source");

        // set attributes
        AttributesBuilder attrsBuilder =
                Attributes.builder()
                          .attributes(extraAttributes)
                          .attributes(attributes)
                          .skipFrontMatter(true)
                          .experimental(true);

        // set imagesDir and imagesoutdir
        // imagesoutdir is needed by asciidoctorj-diagram
        if (imagesdir != null) {
            attrsBuilder.imagesDir(imagesdir);
            attrsBuilder.attribute("imagesoutdir", outputDir.resolve(imagesdir).toString());
        }

        // set outdir attribute to relative to outputDir from source
        // needed by asciidoctorj-diagram
        String outDir = relativePath(source, outputDir);
        attrsBuilder.attribute("outdir", outDir);

        // set options
        // set backend name
        // use unsafe mode
        // set headerFooter to false to use embedded transform
        // basedir needed by asciidoctorj-diagram
        OptionsBuilder optionsBuilder =
                Options.builder()
                       .attributes(attrsBuilder.build())
                       .safe(SafeMode.UNSAFE)
                       .headerFooter(false)
                       .eruby("")
                       .baseDir(source.getParent().toFile())
                       .option("parse", false);
        if (backend != null) {
            optionsBuilder.backend(this.backend);
        }
        LOGGER.info("rendering {} to {}", source, target);
        Document document = asciidoctor.loadFile(source.toFile(), optionsBuilder.build());
        document.setAttribute("templateSession", ctx.templateSession(), true);
        String output = document.convert();
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, output);
        } catch (IOException ex) {
            throw new RenderingException(ex.getMessage(), ex);
        }
    }

    /**
     * A builder of {@link AsciidocEngine}.
     */
    @SuppressWarnings("unused")
    public static class Builder implements Supplier<AsciidocEngine> {

        private final List<String> libraries = new ArrayList<>();
        private final Map<String, Object> attributes = new HashMap<>();
        private String imagesDir = DEFAULT_IMAGESDIR;

        /**
         * Add libraries.
         *
         * @param libraries asciidoctor library names
         * @return this builder
         */
        public Builder libraries(List<String> libraries) {
            if (libraries != null) {
                this.libraries.addAll(libraries);
            }
            return this;
        }

        /**
         * Add a library.
         *
         * @param library asciidoctor library name
         * @return this builder
         */
        public Builder library(String library) {
            if (library != null) {
                this.libraries.add(library);
            }
            return this;
        }

        /**
         * Add attributes.
         *
         * @param attributes asciidoctor attributes
         * @return this builder
         */
        public Builder attributes(Map<String, Object> attributes) {
            if (attributes != null) {
                this.attributes.putAll(attributes);
            }
            return this;
        }

        /**
         * Add an attribute.
         *
         * @param key   attribute key
         * @param value attribute value
         * @return this builder
         */
        public Builder attribute(String key, Object value) {
            if (key != null && value != null) {
                this.attributes.put(key, value);
            }
            return this;
        }

        /**
         * Set the image directory.
         *
         * @param imagesDir the asciidoctor {@code "imagesdir"} option
         * @return this builder
         */
        public Builder imagesDir(String imagesDir) {
            this.imagesDir = imagesDir;
            return this;
        }

        /**
         * Apply the specified configuration.
         *
         * @param config config
         * @return this builder
         */
        public Builder config(Config config) {
            libraries.addAll(config.get("libraries")
                                   .asList(String.class)
                                   .orElseGet(List::of));

            attributes.putAll(config.get("attributes")
                                    .detach()
                                    .asMap()
                                    .orElseGet(Map::of));

            imagesDir = config.get("imagesdir")
                              .asString()
                              .orElse(null);
            return this;
        }

        /**
         * Build the new instance.
         *
         * @return new instance.
         */
        public AsciidocEngine build() {
            return new AsciidocEngine(this);
        }

        @Override
        public AsciidocEngine get() {
            return build();
        }
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config config
     * @return new instance
     */
    public static AsciidocEngine create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create a new instance.
     *
     * @return new instance
     */
    public static AsciidocEngine create() {
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

    private static String parseSection0Title(Path source) {
        try {
            for (String line : Files.readAllLines(source)) {
                // level0
                if (line.startsWith("= ")) {
                    return line.substring(2).trim();
                }
                // level1, abort...
                if (line.startsWith("== ")) {
                    break;
                }
            }
        } catch (IOException ex) {
            LOGGER.error("Error while parsing section0 title of " + source, ex);
        }
        return null;
    }
}
