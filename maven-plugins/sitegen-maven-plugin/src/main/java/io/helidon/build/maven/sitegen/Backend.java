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

import io.helidon.build.common.Strings;

/**
 * Backend base class.
 */
public abstract class Backend implements Model {

    private final String name;

    /**
     * Create a new backend instance.
     *
     * @param name the name of the backend
     */
    protected Backend(String name) {
        this.name = Strings.requireValid(name, "name");
    }

    /**
     * Get the backend name.
     *
     * @return the backend name
     */
    public String name() {
        return name;
    }

    /**
     * Generate.
     *
     * @param ctx rendering context
     */
    public abstract void generate(RenderingContext ctx);

    /**
     * Get the renderers.
     *
     * @return map of renderers keyed by extensions, never {@code null}
     */
    public abstract Map<String, PageRenderer> renderers();

    /**
     * Get a renderer for the given file extension.
     *
     * @param ext the file extension
     * @return the renderer associated with the extension
     * @throws IllegalArgumentException if no renderer is found for the extension
     */
    public PageRenderer renderer(String ext) {
        PageRenderer renderer = renderers().get(ext);
        if (renderer == null) {
            throw new IllegalArgumentException("no renderer found for extension: " + ext);
        }
        return renderer;
    }

    @Override
    public Object get(String attr) {
        throw new IllegalArgumentException("Unknown attribute: " + attr);
    }
}
