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

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.common.Strings;
import io.helidon.build.common.test.utils.TestFiles;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link Generator}.
 */
public class GeneratorTest {

    @Test
    public void testFile() throws IOException {
        Path outputDir = generate("generator/file.xml");
        Path expected = outputDir.resolve("file2.txt");
        assertThat(Files.exists(expected), is(true));
        assertThat(readFile(expected), is("foo\n"));
    }

    @Test
    public void testTemplate() throws IOException {
        Path outputDir = generate("generator/template.xml");
        Path expected = outputDir.resolve("template1.txt");
        assertThat(Files.exists(expected), is(true));
        assertThat(readFile(expected), is("bar\n"));
    }

    // TODO test files
    // TODO test templates
    // TODO test transformations
    // TODO test transformation not found

    private static Path generate(String path) {
        Path target = TestFiles.targetDir(GeneratorTest.class);
        Path testResources = target.resolve("test-classes");
        Path scriptPath = testResources.resolve(path);
        String dirName = scriptPath.getFileName().toString().replaceAll(".xml", "");
        Path outputDir = target.resolve("generator-ut/" + dirName);
        Block block = ScriptLoader.load(scriptPath).body();
        Context context = Context.create(scriptPath.getParent());
        Walker.walk(new VisitorAdapter<>(new Generator(block, outputDir)), block, context);
        return outputDir;
    }

    private static String readFile(Path file) throws IOException {
        return Strings.normalizeNewLines(Files.readString(file));
    }
}
