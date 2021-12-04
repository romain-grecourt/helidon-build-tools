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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import io.helidon.build.common.test.utils.TestFiles;

import org.junit.jupiter.api.Test;

import static io.helidon.build.archetype.engine.v2.TestHelper.engine;
import static io.helidon.build.archetype.engine.v2.TestHelper.readFile;
import static io.helidon.build.common.test.utils.TestFiles.pathOf;
import static java.nio.file.Files.isDirectory;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link ArchetypeEngineV2}.
 */
class ArchetypeEngineV2Test {

    private static Path projectDir(Path parentDirectory, String projectName) {
        Path newProjectDir = parentDirectory.resolve(projectName);
        for (int i = 1; Files.exists(newProjectDir); i++) {
            newProjectDir = parentDirectory.resolve(projectName + "-" + i);
        }
        return newProjectDir;
    }

    @Test
    void generateSkipOptional() throws IOException {

        Map<String, String> externalValues = Map.of(
                "flavor", "se",
                "flavor.base", "bare",
                "build-system", "maven");
        Path directory = TestFiles.targetDir(ArchetypeEngineV2Test.class).resolve("e2e");

        ArchetypeEngineV2 engine = engine("e2e");
        Path outputDir = engine.generate(new Batch(), externalValues, Map.of(), n -> projectDir(directory, n));
        assertThat(Files.exists(outputDir), is(true));

        assertThat(Files.walk(outputDir)
                        .filter(p -> !isDirectory(p))
                        .map((p) -> pathOf(outputDir.relativize(p)))
                        .sorted()
                        .collect(toList()),
                is(List.of(
                        ".helidon",
                        "README.md",
                        "pom.xml",
                        "src/main/java/io/helidon/examples/bare/se/GreetService.java",
                        "src/main/java/io/helidon/examples/bare/se/Main.java",
                        "src/main/java/io/helidon/examples/bare/se/package-info.java",
                        "src/main/resources/META-INF/native-image/reflect-config.json",
                        "src/main/resources/application.yaml",
                        "src/main/resources/logging.properties",
                        "src/test/java/io/helidon/examples/bare/se/MainTest.java"
                )));

        String mainTest = readFile(outputDir.resolve("src/test/java/io/helidon/examples/bare/se/MainTest.java"));
        assertThat(mainTest, containsString("package io.helidon.examples.bare.se;"));
        assertThat(mainTest, containsString("public class MainTest {"));

        String loggingProperties = readFile(outputDir.resolve("src/main/resources/logging.properties"));
        assertThat(loggingProperties, containsString("handlers=io.helidon.common.HelidonConsoleHandler"));

        String applicationYaml = readFile(outputDir.resolve("src/main/resources/application.yaml"));
        assertThat(applicationYaml, containsString("greeting: \"Hello\""));

        String packageInfo = readFile(outputDir.resolve("src/main/java/io/helidon/examples/bare/se/package-info.java"));
        assertThat(packageInfo, containsString("package io.helidon.examples.bare.se;"));

        String mainClass = readFile(outputDir.resolve("src/main/java/io/helidon/examples/bare/se/Main.java"));
        assertThat(mainClass, containsString("package io.helidon.examples.bare.se;"));

        String greetService = readFile(outputDir.resolve("src/main/java/io/helidon/examples/bare/se/GreetService.java"));
        assertThat(greetService, containsString("package io.helidon.examples.bare.se;"));

        String pom = readFile(outputDir.resolve("pom.xml"));
        assertThat(pom, containsString("<groupId>io.helidon.applications</groupId>"));
        assertThat(pom, containsString("<artifactId>helidon-bare-se</artifactId>"));
        assertThat(pom, containsString("<mainClass>io.helidon.examples.bare.se.Main</mainClass>"));
        assertThat(pom, containsString("<groupId>org.junit.jupiter</groupId>"));
        assertThat(pom, containsString("<scope>test</scope>"));
        assertThat(pom, containsString("<artifactId>maven-dependency-plugin</artifactId>"));
        assertThat(pom, containsString("<id>copy-libs</id>"));

        String readme = readFile(outputDir.resolve("README.md"));
        assertThat(readme, containsString("Helidon SE Bare"));
        assertThat(readme, containsString("java -jar target/helidon-bare-se.jar"));
        assertThat(readme, containsString("## Exercise the application"));

        String helidonFile = readFile(outputDir.resolve(".helidon"));
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEE MMM dd");
        ZonedDateTime now = ZonedDateTime.now();
        assertThat(helidonFile, containsString(dtf.format(now)));
        assertThat(helidonFile, containsString("project.directory=" + outputDir));
    }
}
