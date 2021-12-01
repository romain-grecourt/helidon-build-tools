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

import com.github.mustachejava.MustacheException;
import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link MustacheSupport}.
 */
public class MustacheSupportTest {

    @Test
    void testSimpleValue() {
        Block model = model(value("foo", "bar"));
        assertThat(render("{{foo}}", model), is("bar"));
    }

    @Test
    void testDottedKeyValue() {
        Block model = model(value("foo.bar", "foobar"));
        assertThat(render("{{foo.bar}}", model), is("foobar"));
    }

    @Test
    void testSimpleList() {
        Block model = model(list("data", value("bar1"), value("bar2")));
        assertThat(render("{{#data}}{{.}},{{/data}}", model), is("bar1,bar2,"));
    }

    @Test
    void testSimpleMap() {
        Block model = model(map("data", value("shape", "circle"), value("color", "red")));
        assertThat(render("{{#data}}{{shape}}:{{color}}{{/data}}", model), is("circle:red"));
    }

    @Test
    void testListOfMap() {
        Block model = model(list("data",
                map(value("name", "bar"), value("id", "1")),
                map(value("name", "foo"), value("id", "2"))));
        assertThat(render("{{#data}}{{name}}={{id}},{{/data}}", model), is("bar=1,foo=2,"));
    }

    @Test
    void testListOfListOfMap() {
        Block model = model(list("data",
                list(
                        map(value("name", "bar"), value("id", "1")),
                        map(value("name", "foo"), value("id", "2"))),
                list(
                        map(value("name", "bob"), value("id", "3")),
                        map(value("name", "alice"), value("id", "4")))));

        String rendered = render("{{#data}}{{#.}}{{name}}={{id}},{{/.}}{{/data}}", model);
        assertThat(rendered, is("bar=1,foo=2,bob=3,alice=4,"));
    }

    @Test
    void testListOfListOfListOfMap() {
        Block model = model(list("data",
                list(
                        list(
                                map(value("name", "bar"), value("id", "1")),
                                map(value("name", "foo"), value("id", "2"))),
                        list(
                                map(value("name", "bob"), value("id", "3")),
                                map(value("name", "alice"), value("id", "4")))),
                list(
                        list(
                                map(value("name", "roger"), value("id", "5")),
                                map(value("name", "joe"), value("id", "6"))),
                        list(
                                map(value("name", "john"), value("id", "7")),
                                map(value("name", "jack"), value("id", "8"))))));

        String rendered = render("{{#data}}{{#.}}{{#.}}{{name}}={{id}},{{/.}}{{/.}}{{/data}}", model);
        assertThat(rendered, is("bar=1,foo=2,bob=3,alice=4,roger=5,joe=6,john=7,jack=8,"));
    }

    @Test
    void testMapOfList() {
        Block model = model(map("data",
                list("shapes", value("circle"), value("rectangle")),
                list("colors", value("red"), value("blue"))));
        String rendered = render("{{#data}}{{#shapes}}{{.}},{{/shapes}};{{#colors}}{{.}},{{/colors}}{{/data}}", model);
        assertThat(rendered, is("circle,rectangle,;red,blue,"));
    }

    @Test
    void testMapOfMap() {
        Block model = model(map("data",
                map("shapes", value("circle", "red"), value("rectangle", "blue")),
                map("colors", value("red", "circle"), value("blue", "rectangle"))));
        String rendered = render("{{#data}}{{#shapes}}{{circle}},{{rectangle}}{{/shapes}};{{#colors}}{{red}},{{blue}}{{/colors}}{{/data}}", model);
        assertThat(rendered, is("red,blue;circle,rectangle"));
    }

    @Test
    void testIterateOnValue() {
        Block model = model(value("data", "bar"));
        String rendered = render("{{#data}}{{.}}{{/data}}", model);
        assertThat(rendered, is("bar"));
    }

    @Test
    void testUnknownValue() {
        Block model = model();
        MustacheException ex = assertThrows(MustacheException.class, () -> render("{{bar}}", model));
        assertThat(ex.getMessage(), startsWith("Failed to get value for bar"));
    }

    @Test
    void testUnknownIterable() {
        Block model = model();
        MustacheException ex = assertThrows(MustacheException.class, () -> render("{{#bar}}{{.}}{{/bar}}", model));
        assertThat(ex.getMessage(), startsWith("Unresolved model value: 'bar'"));
    }

    @Test
    void testListOrder() {
        Block model = model(list("data", value("bar1", 0), value("bar2", 100)));
        assertThat(render("{{#data}}{{.}},{{/data}}", model), is("bar2,bar1,"));
    }

    @Test
    void testMapValueOverrideByOrder() {
        Block model = model(map("data", value("shape", "circle", 0), value("shape", "rectangle", 100)));
        assertThat(render("{{#data}}{{shape}}{{/data}}", model), is("rectangle"));
    }

    @Test
    void testMapValueOverride() {
        Block model = model(map("data", value("color", "red", 100), value("color", "blue", 100)));
        assertThat(render("{{#data}}{{color}}{{/data}}", model), is("red"));
    }

    @Test
    void testMapOfListMerge() {
        Block model = model(map("data",
                list("shapes", value("circle", 0), value("rectangle", 1)),
                list("shapes", value("triangle", 2))));
        assertThat(render("{{#data}}{{#shapes}}{{.}},{{/shapes}}{{/data}}", model), is("triangle,rectangle,circle,"));
    }

    @Test
    void testListOfMapMerge() {
        Block model = model(
                list("data", map(value("shape", "circle"),value("color", "red"))),
                list("data", map(value("shape", "rectangle"), value("color", "blue"))));
        assertThat(render("{{#data}}{{shape}}:{{color}},{{/data}}", model), is("circle:red,rectangle:blue,"));
    }

    @Test
    void testListMerge() {
        Block model = model(
                list("data", value("bar1"), value("bar2")),
                list("data", value("foo1"), value("foo2")));
        assertThat(render("{{#data}}{{.}},{{/data}}", model), is("bar1,bar2,foo1,foo2,"));
    }

    @Test
    void testMapValueWithoutKey() {
        Block model = model(map("data", value("circle")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> render("", model));
        assertThat(ex.getMessage(), is("Cannot add a model with no key to a map"));
    }

    @Test
    void testMapAsString() {
        Block model = model(map("data"));
        MustacheException ex = assertThrows(MustacheException.class, () -> render("{{data}}", model));
        assertThat(ex.getCause(), is(not(nullValue())));
        assertThat(ex.getCause(), is(instanceOf(UnsupportedOperationException.class)));
    }

    @Test
    void testListAsString() {
        Block model = model(list("data"));
        MustacheException ex = assertThrows(MustacheException.class, () -> render("{{data}}", model));
        assertThat(ex.getCause(), is(not(nullValue())));
        assertThat(ex.getCause(), is(instanceOf(UnsupportedOperationException.class)));
    }

    private static String render(String template, Block model) {
        InputStream is = new ByteArrayInputStream(template.getBytes(UTF_8));
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        MustacheSupport support = new MustacheSupport(model, Context.create());
        support.render(is, "test", UTF_8, os);
        return os.toString(UTF_8);
    }

    private static Block model(Block.Builder... statements) {
        Block.Builder builder = Block.builder(null, null, Block.Kind.MODEL);
        for (Block.Builder statement : statements) {
            builder.statement(statement);
        }
        return builder.build();
    }

    private static Block.Builder map(Block.Builder... statements) {
        return modelBuilder(null, Block.Kind.MAP, 100, statements);
    }

    private static Block.Builder map(String key, Block.Builder... statements) {
        return modelBuilder(key, Block.Kind.MAP, 100, statements);
    }

    private static Block.Builder list(Block.Builder... statements) {
        return modelBuilder(null, Block.Kind.LIST, 100, statements);
    }

    private static Block.Builder list(String key, Block.Builder... statements) {
        return modelBuilder(key, Block.Kind.LIST, 100, statements);
    }

    private static Block.Builder value(String value) {
        return modelBuilder(null, Block.Kind.VALUE, 100).value(value);
    }

    private static Block.Builder value(String value, int order) {
        return modelBuilder(null, Block.Kind.VALUE, order).value(value);
    }

    private static Block.Builder value(String key, String value) {
        return modelBuilder(key, Block.Kind.VALUE, 100).value(value);
    }

    private static Block.Builder value(String key, String value, int order) {
        return modelBuilder(key, Block.Kind.VALUE, order).value(value);
    }

    private static Block.Builder modelBuilder(String key, Block.Kind kind, int order, Block.Builder... statements) {
        Block.Builder builder = Model.builder(null, null, kind)
                                     .attributes(Map.of("order", String.valueOf(order)));
        if (key != null) {
            builder.attributes(Map.of("key", key));
        }
        for (Block.Builder statement : statements) {
            builder.statement(statement);
        }
        return builder;
    }
}
