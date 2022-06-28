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
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.Patch;
import io.helidon.build.maven.sitegen.asciidoctor.AsciidocEngine;
import io.helidon.build.maven.sitegen.models.Header;
import io.helidon.build.maven.sitegen.models.Nav;
import io.helidon.build.maven.sitegen.models.PageFilter;
import io.helidon.build.maven.sitegen.models.StaticAsset;

import io.helidon.build.maven.sitegen.models.WebResource;
import io.helidon.build.maven.sitegen.models.WebResource.Location;
import org.junit.jupiter.api.Test;

import static io.helidon.build.common.test.utils.TestFiles.targetDir;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@link VuetifyBackend}.
 */
public class VuetifyBackendTest {

    @Test
    public void testVuetify1() throws Exception {
        Path targetDir = targetDir(VuetifyBackendTest.class);
        Path sourceDir = targetDir.resolve("test-classes/vuetify1");
        Path outputDir = targetDir.resolve("vuetify/testvuetify1");

        Site.builder()
            .page(PageFilter.builder().includes("**/*.adoc"))
            .asset(StaticAsset.builder().includes("images/sunset.jpg").target("/"))
            .asset(StaticAsset.builder().includes("css/*.css").target("/"))
            .header(Header.builder().stylesheet(WebResource.builder().location(Location.Type.PATH, "css/styles.css")))
            .backend(VuetifyBackend.builder()
                                   .home("home.adoc")
                                   .releases("1.0")
                                   .nav(Nav.builder()
                                           .title("Pet Project doc")
                                           .glyph("icon", "import_contacts")
                                           .item(Nav.builder()
                                                    .item(Nav.builder()
                                                             .title("Cool Stuff")
                                                             .item(Nav.builder()
                                                                      .title("What is it about?")
                                                                      .glyph("icon", "weekend")
                                                                      .pathprefix("/about")
                                                                      .includes("about/*.adoc"))
                                                             .item(Nav.builder()
                                                                      .title("Getting started")
                                                                      .glyph("icon", "play_circle_outline")
                                                                      .pathprefix("/getting-started")
                                                                      .includes("getting-started/*.adoc")))
                                                    .item(Nav.builder()
                                                             .title("Boring Stuff")
                                                             .item(Nav.builder()
                                                                      .title("Let's code!")
                                                                      .glyph("icon", "code")
                                                                      .pathprefix("/lets-code")
                                                                      .includes("lets-code/*.adoc"))
                                                             .item(Nav.builder()
                                                                      .title("Play time!")
                                                                      .glyph("icon", "home")
                                                                      .to("playtime"))))
                                           .item(Nav.builder()
                                                    .title("Additional Resources"))
                                           .item(Nav.builder()
                                                    .title("Javadocs")
                                                    .glyph("icon", "info")
                                                    .href("https://docs.oracle.com/javase/8/docs/api/"))))
            .build()
            .generate(sourceDir, outputDir);

        Path index = outputDir.resolve("index.html");
        assertThat(Files.exists(index), is(true));

        Path actualConfig = outputDir.resolve("main/config.js");
        assertThat(Files.exists(actualConfig), is(true));
        assertRendering(actualConfig, sourceDir.resolve("expected-config"));

        Path home = outputDir.resolve("pages/home.js");
        assertThat(Files.exists(home), is(true));

        assertThat(Files.readAllLines(home)
                        .stream()
                        .anyMatch(line -> line.contains("to an anchor<br>")), is(true));
    }

    @Test
    public void testVuetify2() {
        Path targetDir = targetDir(VuetifyBackendTest.class);
        Path sourceDir = targetDir.resolve("test-classes/vuetify2");
        Path outputDir = targetDir.resolve("vuetify/testvuetify2");
        Site.builder()
            .page(PageFilter.builder().includes("**/*.adoc"))
            .backend(VuetifyBackend.builder().home("home.adoc"))
            .engine(SiteEngine.builder("vuetify")
                              .asciidoctor(AsciidocEngine.builder("vuetify")
                                                         .library("asciidoctor-diagram")
                                                         .attribute("plantumlconfig", "_plantuml-config.txt")))
            .build()
            .generate(sourceDir, outputDir);

        Path index = outputDir.resolve("index.html");
        assertThat(Files.exists(index), is(true));

        Path config = outputDir.resolve("main/config.js");
        assertThat(Files.exists(config), is(true));

        Path home = outputDir.resolve("pages/home.js");
        assertThat(Files.exists(home), is(true));

        Path homeCustom = outputDir.resolve("pages/home_custom.js");
        assertThat(Files.exists(homeCustom), is(true));
    }

    private static void assertRendering(Path actual, Path expected) throws IOException, DiffException {
        Patch<String> patch = DiffUtils.diff(
                Files.readAllLines(expected),
                Files.readAllLines(actual));
        if (patch.getDeltas().size() > 0) {
            fail("rendered file " + actual.toAbsolutePath() + " differs from expected: " + patch);
        }
    }
}
