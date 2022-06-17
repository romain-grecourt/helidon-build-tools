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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import freemarker.cache.FileTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test helper.
 */
public abstract class TestHelper {

    public static final String SOURCE_DIR_PREFIX = "src/test/resources/";

    /**
     * Get the base directory path of the project.
     *
     * @return base directory path
     */
    static String getBasedirPath() {
        String basedirPath = System.getProperty("basedir");
        if (basedirPath == null) {
            basedirPath = Path.of("").toAbsolutePath().toString();
        }
        return basedirPath.replace("\\", "/");
    }

    /**
     * Get a file in the project.
     *
     * @param path a relative path within the project directory
     * @return the corresponding for the given path
     */
    public static Path getFile(String path) {
        return Path.of(getBasedirPath(), path);
    }

    /**
     * Render the expected template and compare it with the given actual file.
     * The actual file must exist and be identical to the rendered template,
     * otherwise assert errors will be thrown.
     *
     * @param outputDir   the output directory where to render the expected template
     * @param expectedTpl the template used for comparing the actual file
     * @param actual      the rendered file to be compared
     * @throws Exception if an error occurred
     */
    public static void assertRendering(Path outputDir, Path expectedTpl, Path actual) throws Exception {

        assertThat(Files.exists(actual), is(true));

        // render expected
        FileTemplateLoader ftl = new FileTemplateLoader(expectedTpl.getParent().toFile());
        Configuration config = new Configuration(Configuration.VERSION_2_3_23);
        config.setTemplateLoader(ftl);
        Template template = config.getTemplate(expectedTpl.getFileName().toString());
        Path expected = outputDir.resolve("expected_" + actual.getFileName());
        Map<String, Object> model = new HashMap<>();
        model.put("basedir", getBasedirPath());
        template.process(model, Files.newBufferedWriter(expected));

        // diff expected and rendered
        List<String> expectedLines = Files.readAllLines(expected);
        List<String> actualLines = Files.readAllLines(actual);

        // compare expected and rendered
        Patch<String> patch = DiffUtils.diff(expectedLines, actualLines);
        if (patch.getDeltas().size() > 0) {
            fail("rendered file " + actual.toAbsolutePath() + " differs from expected: " + patch);
        }
    }
}
