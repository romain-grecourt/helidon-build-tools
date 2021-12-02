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

package io.helidon.build.archetype.engine.v2.spi;

import io.helidon.build.archetype.engine.v2.Context;
import io.helidon.build.archetype.engine.v2.ast.Block;

import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;

import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Template support provider.
 */
public interface TemplateSupportProvider {

    /**
     * Get the name of the template support.
     *
     * @return name
     */
    String name();

    /**
     * Instantiate the template support.
     *
     * @param block   block
     * @param context context
     * @return template support
     */
    TemplateSupport create(Block block, Context context);

    /**
     * Find a provider by its name.
     *
     * @param name name of provider
     * @return Optional
     */
    static TemplateSupportProvider providerByName(String name) {
        TemplateSupportProvider provider = Cache.ENTRIES.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("Unresolved template support provider: " + name);
        }
        return provider;
    }

    /**
     * Provider cache.
     */
    class Cache {
        static final Map<String, TemplateSupportProvider> ENTRIES =
                ServiceLoader.load(TemplateSupportProvider.class)
                             .stream()
                             .map(ServiceLoader.Provider::get)
                             .collect(toUnmodifiableMap(TemplateSupportProvider::name, Function.identity()));
    }
}
