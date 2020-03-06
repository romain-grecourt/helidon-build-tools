/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.build.archetype.engine;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.stream.Collectors;

import io.helidon.build.archetype.engine.ArchetypeDescriptor.FileSet;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.Property;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.Replacement;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.Transformation;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test {@link ArchetypeEngine}.
 */
public class ArchetypeEngineTest {

    @Test
    public void testResolveProperties() {
        Properties props = new Properties();
        props.put("foo", "bar");
        props.put("bar", "foo");
        assertThat(ArchetypeEngine.resolveProperties("${foo}", props), is("bar"));
        assertThat(ArchetypeEngine.resolveProperties("${xxx}", props), is(""));
        assertThat(ArchetypeEngine.resolveProperties("-${foo}-", props), is("-bar-"));
        assertThat(ArchetypeEngine.resolveProperties("$${foo}}", props), is("$bar}"));
        assertThat(ArchetypeEngine.resolveProperties("${foo}-${bar}", props), is("bar-foo"));
        assertThat(ArchetypeEngine.resolveProperties("foo", props), is("foo"));
        assertThat(ArchetypeEngine.resolveProperties("$foo", props), is("$foo"));
        assertThat(ArchetypeEngine.resolveProperties("${foo", props), is("${foo"));
        assertThat(ArchetypeEngine.resolveProperties("${ foo}", props), is(""));
        assertThat(ArchetypeEngine.resolveProperties("${foo }", props), is(""));
    }

    @Test
    public void testTransform() {
        LinkedList<Transformation> transformations = new LinkedList<>();
        Transformation t1 = new Transformation("mustache");
        t1.replacements().add(new Replacement("\\.mustache$", ""));
        transformations.add(t1);
        Transformation t2 = new Transformation("packaged");
        t2.replacements().add(new Replacement("__pkg__", "com.example.myapp"));
        t2.replacements().add(new Replacement("(?!\\.java)\\.", "\\/"));
        transformations.add(t2);
        assertThat(ArchetypeEngine.transform("src/main/java/__pkg__/Main.java.mustache", transformations, new Properties()),
                is("src/main/java/com/example/myapp/Main.java"));
    }

    @Test
    public void testEvaluateConditional() {
        Properties props2 = new Properties();
        props2.put("prop1", "true");
        props2.put("prop2", "true");

        Properties props1 = new Properties();
        props1.put("prop1", "true");

        Property prop1 = new Property("prop1", "Prop 1");
        FileSet fset1 = new FileSet(List.of(), List.of(prop1), List.of());
        assertThat(ArchetypeEngine.evaluateConditional(fset1, props1), is(true));
        assertThat(ArchetypeEngine.evaluateConditional(fset1, new Properties()), is(false));

        FileSet fset2 = new FileSet(List.of(), List.of(), List.of(prop1));
        assertThat(ArchetypeEngine.evaluateConditional(fset2, props1), is(false));
        assertThat(ArchetypeEngine.evaluateConditional(fset2, new Properties()), is(true));

        Property prop2 = new Property("prop2", "Prop 2");
        FileSet fset3 = new FileSet(List.of(), List.of(prop1, prop2), List.of());
        assertThat(ArchetypeEngine.evaluateConditional(fset3, props2), is(true));
        assertThat(ArchetypeEngine.evaluateConditional(fset3, props1), is(false));
        assertThat(ArchetypeEngine.evaluateConditional(fset3, new Properties()), is(false));

        FileSet fset4 = new FileSet(List.of(), List.of(prop1), List.of(prop2));
        assertThat(ArchetypeEngine.evaluateConditional(fset4, props2), is(false));
        assertThat(ArchetypeEngine.evaluateConditional(fset4, new Properties()), is(false));
    }

    @Test
    public void testGenerate() throws IOException {
        Properties props = new Properties();
        props.put("groupId", "com.example");
        props.put("artifactId", "my-project");
        props.put("version", "1.0-SNAPSHOT");
        props.put("name", "my super project");
        props.put("package", "com.example.myproject");
        props.put("maven", "true");
        File targetDir = new File(new File("").getAbsolutePath(), "target");
        File outputDir = new File(targetDir, "test-project");
        Path outputDirPath = outputDir.toPath();
        if (Files.exists(outputDirPath)) {
            Files.walk(outputDirPath)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
        assertThat(Files.exists(outputDirPath), is(false));
        new ArchetypeEngine(ArchetypeEngineTest.class.getClassLoader(), props).generate(outputDir);
        assertThat(Files.exists(outputDirPath), is(true));
        assertThat(Files.walk(outputDirPath)
                .filter(p -> !Files.isDirectory(p))
                .map((p) -> outputDirPath.relativize(p).toString())
                .collect(Collectors.toList()),
                is(List.of("pom.xml", "src/main/java/com/example/myproject/Main.java")));

        InputStream is = ArchetypeEngineTest.class.getClassLoader().getResourceAsStream("META-INF/test.properties");
        assertThat(is, is(not(nullValue())));
        Properties testProps = new Properties();
        testProps.load(is);

        String pomBase64 = testProps.getProperty("pom.xml");
        assertThat(pomBase64, is(not(nullValue())));
        assertThat(new String(Files.readAllBytes(outputDirPath.resolve("pom.xml")), StandardCharsets.UTF_8),
                is (new String(Base64.getDecoder().decode(pomBase64), StandardCharsets.UTF_8)));

        String mainBase64 = testProps.getProperty("main.java");
        assertThat(mainBase64, is(not(nullValue())));
        assertThat(new String(Files.readAllBytes(outputDirPath.resolve("src/main/java/com/example/myproject/Main.java")),
                StandardCharsets.UTF_8),
                is (new String(Base64.getDecoder().decode(mainBase64), StandardCharsets.UTF_8)));
    }
}
