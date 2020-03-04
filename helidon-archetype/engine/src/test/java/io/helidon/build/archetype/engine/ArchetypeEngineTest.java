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

import io.helidon.build.archetype.engine.ArchetypeDescriptor.FileSet;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.Property;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.Replacement;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.Transformation;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
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
        Transformation t1 = new Transformation();
        LinkedList<Replacement> t1r = new LinkedList<>();
        Replacement t1r1 = new Replacement();
        t1r1.setRegex("\\.mustache$");
        t1r1.setReplacement("");
        t1r.add(t1r1);
        t1.setReplacements(t1r);
        transformations.add(t1);
        Transformation t2 = new Transformation();
        LinkedList<Replacement> t2r = new LinkedList<>();
        Replacement t2r1 = new Replacement();
        t2r1.setRegex("__pkg__");
        t2r1.setReplacement("com.example.myapp");
        t2r.add(t2r1);
        Replacement t2r2 = new Replacement();
        t2r2.setRegex("(?!\\.java)\\.");
        t2r2.setReplacement("\\/");
        t2r.add(t2r2);
        t2.setReplacements(t2r);
        transformations.add(t2);
        assertThat(ArchetypeEngine.transform("src/main/java/__pkg__/Main.java.mustache", transformations),
                is("src/main/java/com/example/myapp/Main.java"));
    }

    @Test
    public void testEvaluateConditional() {
        Properties props2 = new Properties();
        props2.put("prop1", "true");
        props2.put("prop2", "true");

        Properties props1 = new Properties();
        props1.put("prop1", "true");

        FileSet fset1 = new FileSet();
        Property prop1 = new Property();
        prop1.setId("prop1");
        fset1.setIfProperties(List.of(prop1));
        assertThat(ArchetypeEngine.evaluateConditional(fset1, props1), is(true));
        assertThat(ArchetypeEngine.evaluateConditional(fset1, new Properties()), is(false));

        FileSet fset2 = new FileSet();
        fset2.setUnlessProperties(List.of(prop1));
        assertThat(ArchetypeEngine.evaluateConditional(fset2, props1), is(false));
        assertThat(ArchetypeEngine.evaluateConditional(fset2, new Properties()), is(true));

        Property prop2 = new Property();
        prop2.setId("prop2");
        FileSet fset3 = new FileSet();
        fset3.setIfProperties(List.of(prop1, prop2));
        assertThat(ArchetypeEngine.evaluateConditional(fset3, props2), is(true));
        assertThat(ArchetypeEngine.evaluateConditional(fset3, props1), is(false));
        assertThat(ArchetypeEngine.evaluateConditional(fset3, new Properties()), is(false));

        FileSet fset4 = new FileSet();
        fset4.setIfProperties(List.of(prop1));
        fset4.setUnlessProperties(List.of(prop2));
        assertThat(ArchetypeEngine.evaluateConditional(fset4, props2), is(false));
        assertThat(ArchetypeEngine.evaluateConditional(fset4, new Properties()), is(false));
    }
}
