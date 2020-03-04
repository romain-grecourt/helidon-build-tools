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

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.build.archetype.engine.ArchetypeDescriptor.Choice;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.FileSet;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.FileSets;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.FlowNode;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.Input;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.InputFlow;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.Replacement;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.Transformation;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.Property;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.Select;
import io.helidon.build.archetype.engine.ArchetypeDescriptor.TemplateSets;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.nullValue;

/**
 * Tests {@link ArchetypeDescriptor}.
 */
public class ArchetypeDescriptorTest {

    @Test
    public void testUnmarshall() {
        InputStream is = ArchetypeDescriptorTest.class.getResourceAsStream("helidon-archetype.xml");

        assertThat(is, is(notNullValue()));
        ArchetypeDescriptor desc = ArchetypeDescriptor.read(is);
        assertThat(desc.getName(), is("test"));

        List<Property> properties = desc.getProperties();
        assertThat(properties, is(notNullValue()));
        assertThat(properties.size(), is(7));
        assertThat(properties.stream().map(Property::getId).collect(Collectors.toList()),
                hasItems("gradle", "maven", "groupId", "artifactId", "version", "name", "package"));
        assertThat(properties.stream().map(Property::getDescription).collect(Collectors.toList()),
                hasItems("Gradle based project", "Maven based project", "Project groupId", "Project artifactId", "Project version"
                , "Project name", "Java package name"));

        Map<String, Transformation> transformations = desc.getTransformations().stream()
                .collect(Collectors.toMap(Transformation::getId, (o) -> o));
        assertThat(transformations.size(), is(2));
        assertThat(transformations.keySet(), hasItems("packaged", "mustache"));
        List<Replacement> packaged = transformations.get("packaged").getReplacements();
        assertThat(packaged, is(notNullValue()));
        assertThat(packaged.stream().map(Replacement::getRegex).collect(Collectors.toList()), hasItems("__pkg__", "\\\\."));
        assertThat(packaged.stream().map(Replacement::getReplacement).collect(Collectors.toList()),
                hasItems("${package}", "\\\\/"));
        List<Replacement> mustache = transformations.get("mustache").getReplacements();
        assertThat(mustache, is(notNullValue()));
        assertThat(mustache.stream().map(Replacement::getRegex).collect(Collectors.toList()), hasItems("\\.mustache$"));
        assertThat(mustache.stream().map(Replacement::getReplacement).collect(Collectors.toList()), hasItems(""));

        TemplateSets templateSets = desc.getTemplateSets();
        assertThat(templateSets, is(notNullValue()));
        assertThat(templateSets.getTransformations().stream().map(Transformation::getId).collect(Collectors.toList()),
                hasItems("mustache"));
        assertThat(templateSets.getTemplateSets().size(), is(3));

        FileSet ts1 = templateSets.getTemplateSets().get(0);
        assertThat(ts1.getTransformations().stream().map(Transformation::getId).collect(Collectors.toList()),
                hasItems("packaged"));
        assertThat(ts1.getDirectory(), is("src/main/java"));
        assertThat(ts1.getIncludes(), hasItems("**/*.mustache"));
        assertThat(ts1.getExcludes(), is(nullValue()));
        assertThat(ts1.getIfProperties(), is(nullValue()));
        assertThat(ts1.getUnlessProperties(), is(nullValue()));

        FileSet ts2 = templateSets.getTemplateSets().get(1);
        assertThat(ts2.getTransformations().stream().map(Transformation::getId).collect(Collectors.toList()),
                hasItems("packaged"));
        assertThat(ts2.getDirectory(), is("src/test/java"));
        assertThat(ts2.getIncludes(), hasItems("**/*.mustache"));
        assertThat(ts2.getExcludes(), is(nullValue()));
        assertThat(ts2.getIfProperties(), is(nullValue()));
        assertThat(ts2.getUnlessProperties(), is(nullValue()));

        FileSet ts3 = templateSets.getTemplateSets().get(2);
        assertThat(ts3.getIfProperties().stream().map(Property::getId).collect(Collectors.toList()), hasItems("gradle"));
        assertThat(ts3.getUnlessProperties(), is(nullValue()));
        assertThat(ts3.getTransformations(), is(nullValue()));
        assertThat(ts3.getDirectory(), is("."));
        assertThat(ts3.getIncludes(), hasItems("build.gradle.mustache"));
        assertThat(ts3.getExcludes(), is(nullValue()));

        FileSets fileSets = desc.getFileSets();
        assertThat(fileSets, is(notNullValue()));
        assertThat(fileSets.getTransformations(), is(nullValue()));
        assertThat(fileSets.getFileSets().size(), is(4));
        FileSet fs1 = fileSets.getFileSets().get(0);
        assertThat(fs1.getTransformations().stream().map(Transformation::getId).collect(Collectors.toList()),
                hasItems("packaged"));
        assertThat(fs1.getDirectory(), is("src/main/java"));
        assertThat(fs1.getIncludes(), is(nullValue()));
        assertThat(fs1.getExcludes(), hasItems("**/*.mustache"));
        assertThat(fs1.getIfProperties(), is(nullValue()));
        assertThat(fs1.getUnlessProperties(), is(nullValue()));

        FileSet fs2 = fileSets.getFileSets().get(1);
        assertThat(fs2.getTransformations(), is(nullValue()));
        assertThat(fs2.getDirectory(), is("src/main/resources"));
        assertThat(fs2.getExcludes(), is(nullValue()));
        assertThat(fs2.getIncludes(), hasItems("**/*"));
        assertThat(fs2.getIfProperties(), is(nullValue()));
        assertThat(fs2.getUnlessProperties(), is(nullValue()));

        FileSet fs3 = fileSets.getFileSets().get(2);
        assertThat(fs3.getTransformations().stream().map(Transformation::getId).collect(Collectors.toList()),
                hasItems("packaged"));
        assertThat(fs3.getDirectory(), is("src/test/java"));
        assertThat(fs3.getIncludes(), is(nullValue()));
        assertThat(fs3.getExcludes(), hasItems("**/*.mustache"));
        assertThat(fs3.getIfProperties(), is(nullValue()));
        assertThat(fs3.getUnlessProperties(), is(nullValue()));

        FileSet fs4 = fileSets.getFileSets().get(3);
        assertThat(fs4.getTransformations(), is(nullValue()));
        assertThat(fs4.getDirectory(), is("src/test/resources"));
        assertThat(fs4.getIncludes(), is(nullValue()));
        assertThat(fs4.getExcludes(), hasItems("**/*"));
        assertThat(fs4.getIfProperties(), is(nullValue()));
        assertThat(fs4.getUnlessProperties(), is(nullValue()));

        InputFlow inputFlow = desc.getInputFlow();
        assertThat(inputFlow, is(notNullValue()));
        assertThat(inputFlow.getNodes().size(), is(6));
        FlowNode fn1 = inputFlow.getNodes().get(0);
        assertThat(fn1, is(instanceOf(Select.class)));
        assertThat(((Select) fn1).getId(), is("build"));
        assertThat(((Select) fn1).getText(), is("Select a build system"));
        assertThat(((Select) fn1).getChoices().size(), is(2));
        assertThat(fn1.getIfProperties(), is(nullValue()));
        assertThat(fn1.getUnlessProperties(), is(nullValue()));

        Choice c1 = ((Select) fn1).getChoices().get(0);
        assertThat(c1.getProperty().getId(), is("maven"));
        assertThat(c1.getText(), is("Maven"));
        assertThat(c1.getIfProperties(), is(nullValue()));
        assertThat(c1.getUnlessProperties(), is(nullValue()));

        Choice c2 = ((Select) fn1).getChoices().get(1);
        assertThat(c2.getProperty().getId(), is("gradle"));
        assertThat(c2.getText(), is("Gradle"));
        assertThat(c2.getIfProperties(), is(nullValue()));
        assertThat(c2.getUnlessProperties(), is(nullValue()));

        FlowNode fn2 = inputFlow.getNodes().get(1);
        assertThat(fn2, is(instanceOf(Input.class)));
        assertThat(((Input) fn2).getProperty().getId(), is("groupId"));
        assertThat(((Input) fn2).getId(), is("groupId"));
        assertThat(((Input) fn2).getText(), is("Enter a project groupId"));
        assertThat(((Input) fn2).getDefaultValue(), is(nullValue()));
        assertThat(fn2.getIfProperties().stream().map(Property::getId).collect(Collectors.toList()), hasItems("maven"));
        assertThat(fn2.getUnlessProperties(), is(nullValue()));

        FlowNode fn3 = inputFlow.getNodes().get(2);
        assertThat(fn3, is(instanceOf(Input.class)));
        assertThat(((Input) fn3).getProperty().getId(), is("artifactId"));
        assertThat(((Input) fn3).getId(), is("artifactId"));
        assertThat(((Input) fn3).getText(), is("Enter a project artifactId"));
        assertThat(((Input) fn3).getDefaultValue(), is(nullValue()));
        assertThat(fn3.getIfProperties(), is(nullValue()));
        assertThat(fn3.getUnlessProperties(), is(nullValue()));

        FlowNode fn4 = inputFlow.getNodes().get(3);
        assertThat(fn4, is(instanceOf(Input.class)));
        assertThat(((Input) fn4).getProperty().getId(), is("version"));
        assertThat(((Input) fn4).getId(), is("version"));
        assertThat(((Input) fn4).getText(), is("Enter a project version"));
        assertThat(((Input) fn4).getDefaultValue(), is("1.0-SNAPSHOT"));
        assertThat(fn4.getIfProperties(), is(nullValue()));
        assertThat(fn4.getUnlessProperties(), is(nullValue()));

        FlowNode fn5 = inputFlow.getNodes().get(4);
        assertThat(fn5, is(instanceOf(Input.class)));
        assertThat(((Input) fn5).getProperty().getId(), is("name"));
        assertThat(((Input) fn5).getId(), is("name"));
        assertThat(((Input) fn5).getText(), is("Enter a project name"));
        assertThat(((Input) fn5).getDefaultValue(), is("${artifactId}"));
        assertThat(fn5.getIfProperties(), is(nullValue()));
        assertThat(fn5.getUnlessProperties(), is(nullValue()));

        FlowNode fn6 = inputFlow.getNodes().get(5);
        assertThat(fn6, is(instanceOf(Input.class)));
        assertThat(((Input) fn6).getProperty().getId(), is("package"));
        assertThat(((Input) fn6).getId(), is("package"));
        assertThat(((Input) fn6).getText(), is("Enter a Java package name"));
        assertThat(((Input) fn6).getDefaultValue(), is("${groupId}"));
        assertThat(fn5.getIfProperties(), is(nullValue()));
        assertThat(fn5.getUnlessProperties(), is(nullValue()));
    }
}
