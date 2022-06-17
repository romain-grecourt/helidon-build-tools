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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import static io.helidon.build.common.FileUtils.pathOf;

/**
 * A helper class to help with class-path resources.
 */
public abstract class Helper {

    /**
     * Load a resource directory as a {@link java.nio.file.Path} instance.
     *
     * @param resourcePath the resource path to load
     * @return the created path instance
     * @throws URISyntaxException    if the resource URL cannot be converted to a =
     *                               URI
     * @throws IOException           if an error occurred during {@link FileSystem}
     *                               creation
     * @throws IllegalStateException if the resource path is not found, or if
     *                               the URI scheme is not <code>jar</code> or <code>file</code>
     */
    public static Path loadResourceDirAsPath(String resourcePath)
            throws URISyntaxException, IOException, IllegalStateException {

        // get classloader resource URL
        URL templatesDirURL = Helper.class.getResource(resourcePath);
        if (templatesDirURL == null) {
            throw new IllegalStateException("resource not found: " + resourcePath);
        }
        // convert URL to Path
        return pathOf(templatesDirURL.toURI());
    }

    /**
     * Verify that a given {@link String} instance is non {@code null} and non-empty.
     *
     * @param str  the instance to check
     * @param name the name of the instance used for the exception message
     * @return supplied instance
     * @throws IllegalArgumentException if the supplied instance is {@code null} or empty
     */
    public static String requireValidString(String str, String name) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException(name + " is null or empty");
        }
        return str;
    }
}
