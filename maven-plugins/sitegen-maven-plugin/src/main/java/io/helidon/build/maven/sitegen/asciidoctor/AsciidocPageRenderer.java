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

package io.helidon.build.maven.sitegen.asciidoctor;

import java.nio.file.Path;
import java.util.Map;

import io.helidon.build.maven.sitegen.Config;
import io.helidon.build.maven.sitegen.PageRenderer;
import io.helidon.build.maven.sitegen.RenderingContext;
import io.helidon.build.maven.sitegen.SiteEngine;
import io.helidon.build.maven.sitegen.models.Page;

import static io.helidon.build.common.FileUtils.requireDirectory;
import static io.helidon.build.common.Strings.requireValid;
import static io.helidon.build.maven.sitegen.models.Page.Metadata;
import static java.util.Objects.requireNonNull;

/**
 * Implementation of a {@link PageRenderer} for asciidoc documents.
 */
public final class AsciidocPageRenderer implements PageRenderer {

    /**
     * Constant for the asciidoc file extension.
     */
    public static final String ADOC_EXT = "adoc";

    private final String backendName;

    private AsciidocPageRenderer(String backendName) {
        this.backendName = backendName;
    }

    @Override
    public void process(Page page, RenderingContext ctx, Path pagesDir, String ext) {
        requireNonNull(page, "page is null!");
        requireNonNull(ctx, "ctx is null!");
        requireValid(ext, "ext is invalid!");
        SiteEngine siteEngine = SiteEngine.get(backendName);
        Path target = requireDirectory(pagesDir).resolve(page.target()).resolve("." + ext);
        siteEngine.asciidoc().render(page, ctx, target, Map.of("page", page, "pages", ctx.pages()));
    }

    @Override
    public Metadata readMetadata(Path source) {
        requireNonNull(source, "source is null!");
        SiteEngine siteEngine = SiteEngine.get(backendName);
        Map<String, Object> docHeader = siteEngine.asciidoc().readDocumentHeader(source);
        return Metadata.create(Config.create(docHeader));
    }

    /**
     * Create a new instance.
     *
     * @param backendName backend name
     * @return new instance
     */
    public static AsciidocPageRenderer create(String backendName) {
        return new AsciidocPageRenderer(backendName);
    }
}
