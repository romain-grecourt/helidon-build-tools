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
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Value;
import io.helidon.build.archetype.engine.v2.ast.ValueTypes;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests {@link Prompter}.
 */
public class PrompterTest {

    @Test
    public void testBooleanWithEmptyResponse() {
        Block input = booleanInput("boolean-input1", true);
        Context context = Context.create();
        input.accept(new Block.Visitor<>() {
            @Override
            public VisitResult visitInput(Input input, Context ctx) {
                input.accept(prompter(""), ctx);
                return VisitResult.CONTINUE;
            }
        }, context);
        Value value = context.lookup("boolean-input1");
        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.BOOLEAN));
        assertThat(value.asBoolean(), is(Boolean.TRUE));
    }

    @Test
    public void testBooleanWithEmptyResponse2() {
        Block input = booleanInput("boolean-input2", false);
        Context context = Context.create();
        input.accept(new Block.Visitor<>() {
            @Override
            public VisitResult visitInput(Input input, Context ctx) {
                input.accept(prompter(""), ctx);
                return VisitResult.CONTINUE;
            }
        }, context);

        Value value = context.lookup("boolean-input2");
        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.BOOLEAN));
        assertThat(value.asBoolean(), is(Boolean.FALSE));
    }

    @Test
    public void testInputBoolean() {
        Block input = booleanInput("boolean-input3", true);
        Context context = Context.create();
        input.accept(new Block.Visitor<>() {
            @Override
            public VisitResult visitInput(Input input, Context ctx) {
                input.accept(prompter("NO"), ctx);
                return VisitResult.CONTINUE;
            }
        }, context);

        Value value = context.lookup("boolean-input3");
        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.BOOLEAN));
        assertThat(value.asBoolean(), is(Boolean.FALSE));
    }

    @Test
    public void testInputListWithEmptyResponse() {
        Block input = listInput("list-input1",
                List.of(option("option1", "value1"),
                        option("option2", "value2")),
                List.of("value1"));
        Context context = Context.create();
        input.accept(new Block.Visitor<>() {
            @Override
            public VisitResult visitInput(Input input, Context ctx) {
                input.accept(prompter(""), ctx);
                return VisitResult.CONTINUE;
            }
        }, context);

        Value value = context.lookup("list-input1");
        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING_LIST));
        assertThat(value.asList(), contains("value1"));
    }

    @Test
    public void testInputListWithEmptyResponseMultipleDefault() {
        Block input = listInput("list-input2",
                List.of(option("option1", "value1"),
                        option("option2", "value2")),
                List.of("value1", "value2"));
        Context context = Context.create();
        input.accept(new Block.Visitor<>() {
            @Override
            public VisitResult visitInput(Input input, Context ctx) {
                input.accept(prompter(""), ctx);
                return VisitResult.CONTINUE;
            }
        }, context);

        Value value = context.lookup("list-input2");
        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING_LIST));
        assertThat(value.asList(), contains("value1", "value2"));
    }

    @Test
    public void testInputList() {
        Block input = listInput("list-input3",
                List.of(option("option1", "value1"),
                        option("option2", "value2"),
                        option("option3", "value3")),
                List.of());
        Context context = Context.create();
        input.accept(new Block.Visitor<>() {
            @Override
            public VisitResult visitInput(Input input, Context ctx) {
                input.accept(prompter("1 3"), ctx);
                return VisitResult.CONTINUE;
            }
        }, context);

        Value value = context.lookup("list-input3");
        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING_LIST));
        assertThat(value.asList(), contains("value1", "value3"));
    }

    @Test
    public void testInputListResponseDuplicate() {
        Block input = listInput("list-input4",
                List.of(option("option1", "value1"),
                        option("option2", "value2"),
                        option("option3", "value3")),
                List.of());
        Context context = Context.create();
        input.accept(new Block.Visitor<>() {
            @Override
            public VisitResult visitInput(Input input, Context ctx) {
                input.accept(prompter("1 3 3 1"), ctx);
                return VisitResult.CONTINUE;
            }
        }, context);

        Value value = context.lookup("list-input4");
        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING_LIST));
        assertThat(value.asList(), contains("value1", "value3"));
    }

    @Test
    public void testInputEnumWithEmptyResponse() {
        Block input = enumInput("enum-input1",
                List.of(option("option1", "value1"),
                        option("option2", "value2")),
                "value1");
        Context context = Context.create();
        input.accept(new Block.Visitor<>() {
            @Override
            public VisitResult visitInput(Input input, Context ctx) {
                input.accept(prompter(""), ctx);
                return VisitResult.CONTINUE;
            }
        }, context);

        Value value = context.lookup("enum-input1");
        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING));
        assertThat(value.asString(), is("value1"));
    }

    @Test
    public void testInputEnum() {
        Block input = enumInput("enum-input2",
                List.of(option("option1", "value1"),
                        option("option2", "value2"),
                        option("option3", "value3")),
                "value3");
        Context context = Context.create();
        input.accept(new Block.Visitor<>() {
            @Override
            public VisitResult visitInput(Input input, Context ctx) {
                input.accept(prompter("2"), ctx);
                return VisitResult.CONTINUE;
            }
        }, context);

        Value value = context.lookup("enum-input2");
        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING));
        assertThat(value.asString(), is("value2"));
    }

    @Test
    public void testInputTextWithEmptyResponseNoDefault() {
        Block input = textInput("text-input1", null);
        Context context = Context.create();
        input.accept(new Block.Visitor<>() {
            @Override
            public VisitResult visitInput(Input input, Context ctx) {
                input.accept(prompter(""), ctx);
                return VisitResult.CONTINUE;
            }
        }, context);

        Value value = context.lookup("text-input1");
        assertThat(value, is(nullValue()));
    }

    @Test
    public void testInputTextWithEmptyResult() {
        Block input = textInput("text-input2", "value1");
        Context context = Context.create();
        input.accept(new Block.Visitor<>() {
            @Override
            public VisitResult visitInput(Input input, Context ctx) {
                input.accept(prompter(""), ctx);
                return VisitResult.CONTINUE;
            }
        }, context);

        Value value = context.lookup("text-input2");
        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING));
        assertThat(value.asString(), is("value1"));
    }

    @Test
    public void testInputText() {
        Block input = textInput("text-input3", "value1");
        Context context = Context.create();
        input.accept(new Block.Visitor<>() {
            @Override
            public VisitResult visitInput(Input input, Context ctx) {
                input.accept(prompter("not-value1"), ctx);
                return VisitResult.CONTINUE;
            }
        }, context);

        Value value = context.lookup("text-input3");
        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING));
        assertThat(value.asString(), is("not-value1"));
    }

    private static Prompter prompter(String input) {
        return new Prompter(new ByteArrayInputStream(input.getBytes()));
    }

    private static Block.Builder option(String name, String value) {
        return Input.builder(null, null, Block.Kind.OPTION)
                    .attributes(attributes(name, value));
    }

    private static Block textInput(String name, String defaultValue) {
        return Input.builder(null, null, Block.Kind.TEXT)
                    .attributes(attributes(name, defaultValue, name))
                    .build();
    }

    private static Block booleanInput(String name, boolean defaultValue) {
        return Input.builder(null, null, Block.Kind.BOOLEAN)
                    .attributes(attributes(name, String.valueOf(defaultValue), name))
                    .build();
    }

    private static Block enumInput(String name, List<Block.Builder> options, String defaultValue) {
        Block.Builder builder = Input.builder(null, null, Block.Kind.ENUM)
                                     .attributes(attributes(name, defaultValue, name));
        for (Block.Builder option : options) {
            builder.statement(option);
        }
        return builder.build();
    }

    private static Block listInput(String name, List<Block.Builder> options, List<String> defaultValue) {
        Block.Builder builder = Input.builder(null, null, Block.Kind.LIST)
                                     .attributes(attributes(name, String.join(",", defaultValue), name));
        for (Block.Builder option : options) {
            builder.statement(option);
        }
        return builder.build();
    }

    private static Map<String, String> attributes(String name, String value) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("name", name);
        attributes.put("value", value);
        return attributes;
    }

    private static Map<String, String> attributes(String name, String defaultValue, String prompt) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("name", name);
        attributes.put("default", defaultValue);
        attributes.put("prompt", prompt);
        return attributes;
    }
}
