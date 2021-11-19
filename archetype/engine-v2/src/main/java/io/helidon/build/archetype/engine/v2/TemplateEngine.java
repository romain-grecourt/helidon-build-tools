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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Interface for template engine.
 */
public interface TemplateEngine {

    /**
     * Get the name of the template engine.
     *
     * @return name
     */
    String name();

    /**
     * Render a template.
     *
     * @param template     template to render
     * @param templateName name of the template
     * @param charset      charset for the written characters
     * @param target       path to target file to create
     * @param scope        the scope for the template
     */
    void render(InputStream template,
                String templateName,
                Charset charset,
                OutputStream target,
                Object scope);

    /**
     * Get all found template engines.
     *
     * @return list of template engines
     */
    static List<TemplateEngine> allEngines() {
        return ServiceLoader.load(TemplateEngine.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toList());
    }

    /**
     * Find a template engine by its name.
     *
     * @param name name of template engine
     * @return Optional
     */
    static Optional<TemplateEngine> getEngineByName(String name) {
        return allEngines().stream().filter(engine -> engine.name().equals(name)).findFirst();
    }
}
