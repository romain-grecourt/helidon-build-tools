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

import static io.helidon.build.archetype.engine.v2.Nodes.inputEnum;
import static io.helidon.build.archetype.engine.v2.Nodes.inputList;
import static io.helidon.build.archetype.engine.v2.Nodes.model;
import static io.helidon.build.archetype.engine.v2.Nodes.modelList;
import static io.helidon.build.archetype.engine.v2.Nodes.modelValue;
import static io.helidon.build.archetype.engine.v2.Nodes.inputOption;
import static io.helidon.build.archetype.engine.v2.Nodes.output;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/**
 * Tests {@link InputResolver}.
 */
public class InputResolverTest {

    @Test
    void testEnumOption() {
        Block block = inputEnum("enum-input", "value3",
                inputOption("option1", "value1", output(model(modelList("colors", modelValue("red"))))),
                inputOption("option2", "value2", output(model(modelList("colors", modelValue("green"))))),
                inputOption("option3", "value3", output(model(modelList("colors", modelValue("blue"))))));

        Context context = Context.create();
        context.put("enum-input", Value.create("value2"));

        assertThat(modelValues(block, context), contains("green"));
    }

    @Test
    void testListOptions() {
        Block block = inputList("list-input", List.of(),
                inputOption("option1", "value1", output(model(modelList("colors", modelValue("red"))))),
                inputOption("option2", "value2", output(model(modelList("colors", modelValue("green"))))),
                inputOption("option3", "value3", output(model(modelList("colors", modelValue("blue"))))));

        Context context = Context.create();
        context.put("list-input", Value.create(List.of("value1", "value3")));

        assertThat(modelValues(block, context), contains("red", "blue"));
    }

    private static List<String> modelValues(Block block, Context context) {
        List<String> colors = new LinkedList<>();
        Walker.walk(new VisitorAdapter<>(new InputResolver(), null,
                new Model.Visitor<>() {
                    @Override
                    public VisitResult visitValue(Model.Value value, Context arg) {
                        colors.add(value.value());
                        return VisitResult.CONTINUE;
                    }
                }), block, context);
        return colors;
    }
}
