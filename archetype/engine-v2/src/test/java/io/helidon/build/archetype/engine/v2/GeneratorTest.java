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
import java.util.function.Consumer;

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Value;
import io.helidon.build.common.Strings;
import io.helidon.build.common.test.utils.TestFiles;

import org.junit.jupiter.api.Test;

import static io.helidon.build.archetype.engine.v2.Controller.generateOutput;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link Generator}.
 */
class GeneratorTest {

    @Test
    void testFile() throws IOException {
        Path outputDir = generate("generator/file.xml");
        Path expected = outputDir.resolve("file2.txt");
        assertThat(Files.exists(expected), is(true));
        assertThat(readFile(expected), is("foo\n"));
    }

    @Test
    void testTemplate() throws IOException {
        Path outputDir = generate("generator/template.xml");
        Path expected = outputDir.resolve("template1.txt");
        assertThat(Files.exists(expected), is(true));
        assertThat(readFile(expected), is("bar\n"));
    }

    @Test
    void testFiles() throws IOException {
        Path outputDir = generate("generator/files.xml");
        Path expected1 = outputDir.resolve("dir1/file1.xml");
        assertThat(Files.exists(expected1), is(true));
        assertThat(readFile(expected1), is("<foo/>\n"));
        Path expected2 = outputDir.resolve("dir1/file2.xml");
        assertThat(Files.exists(expected2), is(true));
        assertThat(readFile(expected2), is("<bar/>\n"));
    }

    @Test
    void testTemplates() throws IOException {
        Path outputDir = generate("generator/templates.xml");
        Path expected1 = outputDir.resolve("dir2/file1.txt");
        assertThat(Files.exists(expected1), is(true));
        assertThat(readFile(expected1), is("red\n"));
        Path expected2 = outputDir.resolve("dir2/file2.txt");
        assertThat(Files.exists(expected2), is(true));
        assertThat(readFile(expected2), is("circle\n"));
    }

    @Test
    void testTransformation() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> generate("generator/transformation.xml"));
        assertThat(ex.getMessage(), is("Unresolved transformation: t1"));
    }

    @Test
    void testReplacement() throws IOException {
        Path outputDir = generate("generator/replacement.xml",
                ctx -> ctx.put("package", Value.create("com.example")));
        Path expected = outputDir.resolve("com/example/file1.txt");
        assertThat(Files.exists(expected), is(true));
        assertThat(readFile(expected), is("foo\n"));
    }

    private static Path generate(String path) {
        return generate(path, ctx -> {});
    }

    private static Path generate(String path, Consumer<Context> initializer) {
        Path target = TestFiles.targetDir(GeneratorTest.class);
        Path testResources = target.resolve("test-classes");
        Path scriptPath = testResources.resolve(path);
        String dirname = scriptPath.getFileName().toString().replaceAll(".xml", "");
        Path outputDir = target.resolve("generator-ut/" + dirname);
        Context context = Context.create(scriptPath.getParent());
        initializer.accept(context);
        Block block = ScriptLoader.load(scriptPath).body();
        generateOutput(new InputResolver(), block, context, outputDir);
        return outputDir;
    }

    private static String readFile(Path file) throws IOException {
        return Strings.normalizeNewLines(Files.readString(file));
    }
}
