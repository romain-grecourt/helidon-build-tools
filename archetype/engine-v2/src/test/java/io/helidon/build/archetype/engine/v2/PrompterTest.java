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

import java.io.ByteArrayInputStream;
import java.util.List;

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Value;
import io.helidon.build.archetype.engine.v2.ast.ValueTypes;

import org.junit.jupiter.api.Test;

import static io.helidon.build.archetype.engine.v2.Nodes.inputBoolean;
import static io.helidon.build.archetype.engine.v2.Nodes.inputEnum;
import static io.helidon.build.archetype.engine.v2.Nodes.inputList;
import static io.helidon.build.archetype.engine.v2.Nodes.inputOption;
import static io.helidon.build.archetype.engine.v2.Nodes.inputText;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests {@link Prompter}.
 */
class PrompterTest {

    @Test
    void testBooleanWithEmptyResponse() {
        Block block = inputBoolean("boolean-input1", true);

        Context context = prompt(block, "");
        Value value = context.lookup("boolean-input1");

        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.BOOLEAN));
        assertThat(value.asBoolean(), is(Boolean.TRUE));
    }

    @Test
    void testBooleanWithEmptyResponse2() {
        Block block = inputBoolean("boolean-input2", false);

        Context context = prompt(block, "");
        Value value = context.lookup("boolean-input2");

        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.BOOLEAN));
        assertThat(value.asBoolean(), is(Boolean.FALSE));
    }

    @Test
    void testInputBoolean() {
        Block block = inputBoolean("boolean-input3", true);

        Context context = prompt(block, "NO");
        Value value = context.lookup("boolean-input3");

        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.BOOLEAN));
        assertThat(value.asBoolean(), is(Boolean.FALSE));
    }

    @Test
    void testInputListWithEmptyResponse() {
        Block block = inputList("list-input1", List.of("value1"),
                inputOption("option1", "value1"),
                inputOption("option2", "value2"));

        Context context = prompt(block, "");
        Value value = context.lookup("list-input1");

        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING_LIST));
        assertThat(value.asList(), contains("value1"));
    }

    @Test
    void testInputListWithEmptyResponseMultipleDefault() {
        Block block = inputList("list-input2", List.of("value1", "value2"),
                inputOption("option1", "value1"),
                inputOption("option2", "value2"));

        Context context = prompt(block, "");
        Value value = context.lookup("list-input2");

        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING_LIST));
        assertThat(value.asList(), contains("value1", "value2"));
    }

    @Test
    void testInputList() {
        Block block = inputList("list-input3", List.of(),
                inputOption("option1", "value1"),
                inputOption("option2", "value2"),
                inputOption("option3", "value3"));

        Context context = prompt(block, "1 3");
        Value value = context.lookup("list-input3");

        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING_LIST));
        assertThat(value.asList(), contains("value1", "value3"));
    }

    @Test
    void testInputListResponseDuplicate() {
        Block block = inputList("list-input4", List.of(),
                inputOption("option1", "value1"),
                inputOption("option2", "value2"),
                inputOption("option3", "value3"));

        Context context = prompt(block, "1 3 3 1");
        Value value = context.lookup("list-input4");

        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING_LIST));
        assertThat(value.asList(), contains("value1", "value3"));
    }

    @Test
    void testInputEnumWithEmptyResponse() {
        Block block = inputEnum("enum-input1", "value1",
                inputOption("option1", "value1"),
                inputOption("option2", "value2"));

        Context context = prompt(block, "");
        Value value = context.lookup("enum-input1");

        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING));
        assertThat(value.asString(), is("value1"));
    }

    @Test
    void testInputEnum() {
        Block block = inputEnum("enum-input2", "value3",
                inputOption("option1", "value1"),
                inputOption("option2", "value2"),
                inputOption("option3", "value3"));

        Context context = prompt(block, "2");
        Value value = context.lookup("enum-input2");

        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING));
        assertThat(value.asString(), is("value2"));
    }

    @Test
    void testInputTextWithEmptyResponseNoDefault() {
        Block block = inputText("text-input1", null);

        Context context = prompt(block, "");
        Value value = context.lookup("text-input1");

        assertThat(value, is(nullValue()));
    }

    @Test
    void testInputTextWithEmptyResult() {
        Block block = inputText("text-input2", "value1");

        Context context = prompt(block, "");
        Value value = context.lookup("text-input2");

        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING));
        assertThat(value.asString(), is("value1"));
    }

    @Test
    void testInputText() {
        Block block = inputText("text-input3", "value1");

        Context context = prompt(block, "not-value1");
        Value value = context.lookup("text-input3");

        assertThat(value, is(notNullValue()));
        assertThat(value.type(), is(ValueTypes.STRING));
        assertThat(value.asString(), is("not-value1"));
    }

    private static Context prompt(Block block, String userInput) {
        Context context = Context.create();
        block.accept(new Block.Visitor<>() {
            @Override
            public VisitResult visitInput(Input input, Context ctx) {
                input.accept(new Prompter(new ByteArrayInputStream(userInput.getBytes())), ctx);
                return VisitResult.CONTINUE;
            }
        }, context);
        return context;
    }
}
