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

import java.nio.file.Path;

import io.helidon.build.maven.sitegen.models.PageFilter;
import org.junit.jupiter.api.Test;

import static io.helidon.build.maven.sitegen.TestHelper.SOURCE_DIR_PREFIX;
import static io.helidon.build.maven.sitegen.TestHelper.assertRendering;
import static io.helidon.build.maven.sitegen.TestHelper.getFile;

/**
 * Tests {@link BasicBackend}.
 */
public class BasicBackendTest {

    private static final Path OUTPUT_DIR = getFile("target/basic-backend-test");

    @Test
    public void testBasic1() throws Exception {
        Path sourceDir = getFile(SOURCE_DIR_PREFIX + "testbasic1");

        Site.builder()
            .page(PageFilter.builder().includes("**/*.adoc").excludes("**/_*"))
            .build()
            .generate(sourceDir, OUTPUT_DIR);

        assertRendering(OUTPUT_DIR,
                sourceDir.resolve("_expected.ftl"),
                OUTPUT_DIR.resolve("basic.html"));
    }

    @Test
    public void testBasic2() throws Exception {
        Path sourceDir = getFile(SOURCE_DIR_PREFIX + "testbasic2");

        Site.builder()
            .page(PageFilter.builder().includes("**/*.adoc").excludes("**/_*"))
            .build()
            .generate(sourceDir, OUTPUT_DIR);

        assertRendering(OUTPUT_DIR,
                sourceDir.resolve("_expected.ftl"),
                OUTPUT_DIR.resolve("example-manual.html"));
    }

    @Test
    public void testBasic3() throws Exception {
        Path sourceDir = getFile(SOURCE_DIR_PREFIX + "testbasic3");

        Site.builder()
            .page(PageFilter.builder().includes("**/*.adoc").excludes("**/_*"))
            .build()
            .generate(sourceDir, OUTPUT_DIR);

        assertRendering(OUTPUT_DIR,
                sourceDir.resolve("_expected.ftl"),
                OUTPUT_DIR.resolve("passthrough.html"));
    }
}
