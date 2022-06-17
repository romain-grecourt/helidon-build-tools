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

import java.util.Map;

import io.helidon.build.maven.sitegen.asciidoctor.AsciidocPageRenderer;

import static io.helidon.build.maven.sitegen.asciidoctor.AsciidocPageRenderer.ADOC_EXT;

/**
 * A basic backend implementation.
 */
public class BasicBackend extends Backend {

    /**
     * The basic backend name.
     */
    public static final String BACKEND_NAME = "basic";

    private final Map<String, PageRenderer> pageRenderers;

    private BasicBackend() {
        super(BACKEND_NAME);
        this.pageRenderers = Map.of(ADOC_EXT, AsciidocPageRenderer.create(BACKEND_NAME));
    }

    @Override
    public Map<String, PageRenderer> renderers() {
        return pageRenderers;
    }

    @Override
    public void generate(RenderingContext ctx) {
        ctx.processPages(ctx.outputDir(), "html");
        ctx.copyStaticAssets();
    }

    /**
     * Create a new instance.
     *
     * @return new instance
     */
    public static BasicBackend create() {
        return new BasicBackend();
    }
}
