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

import java.util.List;
import java.util.ServiceLoader;

import static io.helidon.build.maven.sitegen.Helper.requireValidString;

/**
 * Backend SPI.
 */
public interface BackendProvider {

    /**
     * Create a new instance from configuration.
     *
     * @param name   the backend name
     * @param config configuration
     * @return the created backend if the name matches, {@code null} otherwise
     */
    Backend create(String name, Config config);

    /**
     * The built-in backends.
     */
    List<BackendProvider> BUILTINS = List.of(new BasicBackendProvider(), new VuetifyBackendProvider());

    /**
     * Get a backend from configuration.
     *
     * @param config config
     * @return the backend instance
     * @throws IllegalArgumentException if the configured backend is not found
     */
    static Backend get(Config config) {
        String name = config.get("name").asString().orElse(null);
        Config backendConfig = config.get("config")
                                     .asOptional()
                                     .map(bc -> bc.parent()
                                                  .get(bc.asString()
                                                         .orElseThrow(IllegalStateException::new)))
                                     .orElse(null);
        return get(name, backendConfig);
    }

    /**
     * Get a backend by its name.
     *
     * @param name   backend name
     * @param config config
     * @return the backend instance
     * @throws IllegalArgumentException if the specified backend is not found
     */
    static Backend get(String name, Config config) {
        requireValidString(name, "name");
        for (BackendProvider provider : BUILTINS) {
            Backend backend = provider.create(name, config);
            if (backend != null) {
                return backend;
            }
        }
        for (BackendProvider provider : ServiceLoader.load(BackendProvider.class)) {
            Backend backend = provider.create(name, config);
            if (backend != null) {
                return backend;
            }
        }
        throw new IllegalArgumentException("backend: " + name + "not found");
    }
}
