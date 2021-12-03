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
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.Output;
import io.helidon.build.archetype.engine.v2.ast.Script;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 * Test helper.
 */
class Helper {

    static Script load0(String path) {
        InputStream is = Helper.class.getClassLoader().getResourceAsStream(path);
        assertThat(is, is(notNullValue()));
        return ScriptLoader.load0(is);
    }

    static void walk(Node.Visitor<Void> visitor, Script script) {
        Walker.walk(visitor, script.body(), null);
    }

    static void walk(Input.Visitor<Void> visitor, Script script) {
        Walker.walk(new VisitorAdapter<>(visitor, null, null), script.body(), null);
    }

    static void walk(Output.Visitor<Void> visitor, Script script) {
        Walker.walk(new VisitorAdapter<>(null, visitor, null), script.body(), null);
    }

    static void walk(Model.Visitor<Void> visitor, Script script) {
        Walker.walk(new VisitorAdapter<>(null, null, visitor), script.body(), null);
    }

    static Block.Builder block(Block.Kind kind, Block.Builder... statements) {
        Block.Builder builder = Block.builder(null, null, kind);
        for (Block.Builder statement : statements) {
            builder.statement(statement);
        }
        return builder;
    }

    static Block.Builder output(Block.Builder... statements) {
        return block(Block.Kind.OUTPUT, statements);
    }

    static Block.Builder model(Block.Builder... statements) {
        return block(Block.Kind.MODEL, statements);
    }

    static Block.Builder modelMap(Block.Builder... statements) {
        return modelBuilder(null, Block.Kind.MAP, 100, statements);
    }

    static Block.Builder modelMap(String key, Block.Builder... statements) {
        return modelBuilder(key, Block.Kind.MAP, 100, statements);
    }

    static Block.Builder modelList(Block.Builder... statements) {
        return modelBuilder(null, Block.Kind.LIST, 100, statements);
    }

    static Block.Builder modelList(String key, Block.Builder... statements) {
        return modelBuilder(key, Block.Kind.LIST, 100, statements);
    }

    static Block.Builder modelValue(String value) {
        return modelBuilder(null, Block.Kind.VALUE, 100).value(value);
    }

    static Block.Builder modelValue(String value, int order) {
        return modelBuilder(null, Block.Kind.VALUE, order).value(value);
    }

    static Block.Builder modelValue(String key, String value) {
        return modelBuilder(key, Block.Kind.VALUE, 100).value(value);
    }

    static Block.Builder modelValue(String key, String value, int order) {
        return modelBuilder(key, Block.Kind.VALUE, order).value(value);
    }

    static Block.Builder option(String name, String value, Block.Builder... statements) {
        Block.Builder builder = Input.builder(null, null, Block.Kind.OPTION)
                                     .attributes(inputAttributes(name, value));
        for (Block.Builder statement : statements) {
            builder.statement(statement);
        }
        return builder;
    }

    static Block textInput(String name, String defaultValue) {
        return Input.builder(null, null, Block.Kind.TEXT)
                    .attributes(inputAttributes(name, defaultValue, name))
                    .build();
    }

    static Block booleanInput(String name, boolean defaultValue) {
        return Input.builder(null, null, Block.Kind.BOOLEAN)
                    .attributes(inputAttributes(name, String.valueOf(defaultValue), name))
                    .build();
    }

    static Block enumInput(String name, List<Block.Builder> options, String defaultValue) {
        Block.Builder builder = Input.builder(null, null, Block.Kind.ENUM)
                                     .attributes(inputAttributes(name, defaultValue, name));
        for (Block.Builder option : options) {
            builder.statement(option);
        }
        return builder.build();
    }

    static Block listInput(String name, List<Block.Builder> options, List<String> defaultValue) {
        Block.Builder builder = Input.builder(null, null, Block.Kind.LIST)
                                     .attributes(inputAttributes(name, String.join(",", defaultValue), name));
        for (Block.Builder option : options) {
            builder.statement(option);
        }
        return builder.build();
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

    private static Map<String, String> inputAttributes(String name, String value) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("name", name);
        attributes.put("value", value);
        return attributes;
    }

    private static Map<String, String> inputAttributes(String name, String defaultValue, String prompt) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("name", name);
        attributes.put("default", defaultValue);
        attributes.put("prompt", prompt);
        return attributes;
    }
}
