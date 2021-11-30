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

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Model;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link MustacheSupport}.
 */
public class MustacheSupportTest {

    @Test
    void testSimpleValue() {
        Block model = model(value("foo", "bar"));
        assertThat(render("{{foo}}", "test", model), is("bar"));
    }

    @Test
    void testDottedKeyValue() {
        Block model = model(value("foo.bar", "foobar"));
        assertThat(render("{{foo.bar}}", "test", model), is("foobar"));
    }

    @Test
    void testSimpleList() {
        Block model = model(list("foo", value("bar1"), value("bar2")));
        assertThat(render("{{#foo}}{{.}},{{/foo}}", "test", model), is("bar1,bar2,"));
    }

    @Test
    void testListOfMap() {
        Block model = model(list("foo",
                map(value("name", "bar"), value("id", "1")),
                map(value("name", "foo"), value("id", "2"))));
        assertThat(render("{{#foo}}{{name}}={{id}},{{/foo}}", "test", model), is("bar=1,foo=2,"));
    }

    @Test
    void testListOfListOfMap() {
        Block model = model(list("foo",
                list(
                        map(value("name", "bar"), value("id", "1")),
                        map(value("name", "foo"), value("id", "2"))),
                list(
                        map(value("name", "bob"), value("id", "3")),
                        map(value("name", "alice"), value("id", "4")))));

        String rendered = render("{{#foo}}{{#.}}{{name}}={{id}},{{/.}}{{/foo}}", "test", model);
        assertThat(rendered, is("bar=1,foo=2,bob=3,alice=4,"));
    }

    @Test
    void testListOfListOfListOfMap() {
        Block model = model(list("foo",
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

        String rendered = render("{{#foo}}{{#.}}{{#.}}{{name}}={{id}},{{/.}}{{/.}}{{/foo}}", "test", model);
        assertThat(rendered, is("bar=1,foo=2,bob=3,alice=4,roger=5,joe=6,john=7,jack=8,"));
    }

    // TODO test iteration on a value (bad)
    // TODO test iteration on a map (bad)
    // TODO test unknown values (empty model)
    // TODO test MapOfList
    // TODO test order with list (list ordering)
    // TODO test order with map (merge)

    private static String render(String template, String name, Block model) {
        InputStream is = new ByteArrayInputStream(template.getBytes(UTF_8));
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        MustacheSupport support = new MustacheSupport(Context.create(Path.of("")));
        support.render(is, name, UTF_8, os, model);
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
        return map(null, statements);
    }

    private static Block.Builder map(String key, Block.Builder... statements) {
        return modelBuilder(key, Block.Kind.MAP, statements);
    }

    private static Block.Builder list(Block.Builder... statements) {
        return list(null, statements);
    }

    private static Block.Builder list(String key, Block.Builder... statements) {
        return modelBuilder(key, Block.Kind.LIST, statements);
    }

    private static Block.Builder value(String value) {
        return value(null, value);
    }

    private static Block.Builder value(String key, String value) {
        return modelBuilder(key, Block.Kind.VALUE).value(value);
    }

    private static Block.Builder modelBuilder(String key, Block.Kind kind, Block.Builder... statements) {
        Block.Builder builder = Model.builder(null, null, kind);
        if (key != null) {
            builder.attributes(Map.of("key", key));
        }
        for (Block.Builder statement : statements) {
            builder.statement(statement);
        }
        return builder;
    }
}
