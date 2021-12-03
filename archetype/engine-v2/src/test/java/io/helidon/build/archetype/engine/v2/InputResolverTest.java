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

import java.util.LinkedList;
import java.util.List;

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Value;

import org.junit.jupiter.api.Test;

import static io.helidon.build.archetype.engine.v2.Helper.enumInput;
import static io.helidon.build.archetype.engine.v2.Helper.listInput;
import static io.helidon.build.archetype.engine.v2.Helper.model;
import static io.helidon.build.archetype.engine.v2.Helper.modelList;
import static io.helidon.build.archetype.engine.v2.Helper.modelValue;
import static io.helidon.build.archetype.engine.v2.Helper.option;
import static io.helidon.build.archetype.engine.v2.Helper.output;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link InputResolver}.
 */
public class InputResolverTest {

    @Test
    void testEnumOption() {
        Block input = enumInput("enum-input",
                List.of(option("option1", "value1", output(model(modelList("colors", modelValue("red"))))),
                        option("option2", "value2", output(model(modelList("colors", modelValue("green"))))),
                        option("option3", "value3", output(model(modelList("colors", modelValue("blue")))))),
                "value3");

        Context context = Context.create();
        context.put("enum-input", Value.create("value2"));

        String[] color = new String[1];
        Walker.walk(new VisitorAdapter<>(new InputResolver(), null,
                new Model.Visitor<>() {
                    @Override
                    public VisitResult visitValue(Model.Value value, Context arg) {
                        color[0] = value.value();
                        return VisitResult.CONTINUE;
                    }
                }), input, context);
        assertThat(color[0], is("green"));
    }

    @Test
    void testListOptions() {
        Block input = listInput("list-input",
                List.of(option("option1", "value1", output(model(modelList("colors", modelValue("red"))))),
                        option("option2", "value2", output(model(modelList("colors", modelValue("green"))))),
                        option("option3", "value3", output(model(modelList("colors", modelValue("blue")))))),
                List.of());

        Context context = Context.create();
        context.put("list-input", Value.create(List.of("value1", "value3")));

        List<String> colors = new LinkedList<>();

        // TODO should use the controller for that
        // TODO controller should store the input resolver to allow for "resolved traversals" for output and model
        // TemplateSupport needs a handle on the controller order to resolve model for a block
        // maybe just a Function<Block, MergedModel> instead
        // Generator also is passed the same function since it's the one that creates template support

        Walker.walk(new VisitorAdapter<>(new InputResolver(), null,
                new Model.Visitor<>() {
                    @Override
                    public VisitResult visitValue(Model.Value value, Context arg) {
                        colors.add(value.value());
                        return VisitResult.CONTINUE;
                    }
                }), input, context);
        assertThat(colors, contains("red", "blue"));
    }
}
