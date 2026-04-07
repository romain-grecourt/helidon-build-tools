/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

import static io.helidon.build.archetype.engine.v2.Nodes.condition;
import static io.helidon.build.archetype.engine.v2.Nodes.inputBoolean;
import static io.helidon.build.archetype.engine.v2.Nodes.inputEnum;
import static io.helidon.build.archetype.engine.v2.Nodes.inputOption;
import static io.helidon.build.archetype.engine.v2.Nodes.inputText;
import static io.helidon.build.archetype.engine.v2.Nodes.inputs;
import static io.helidon.build.archetype.engine.v2.Nodes.method;
import static io.helidon.build.archetype.engine.v2.Nodes.model;
import static io.helidon.build.archetype.engine.v2.Nodes.modelList;
import static io.helidon.build.archetype.engine.v2.Nodes.modelValue;
import static io.helidon.build.archetype.engine.v2.Nodes.output;
import static io.helidon.build.archetype.engine.v2.Nodes.presetBoolean;
import static io.helidon.build.archetype.engine.v2.Nodes.presetEnum;
import static io.helidon.build.archetype.engine.v2.Nodes.presetList;
import static io.helidon.build.archetype.engine.v2.Nodes.presetText;
import static io.helidon.build.archetype.engine.v2.Nodes.presets;
import static io.helidon.build.archetype.engine.v2.Nodes.regex;
import static io.helidon.build.archetype.engine.v2.Nodes.script;
import static io.helidon.build.archetype.engine.v2.Nodes.step;
import static io.helidon.build.archetype.engine.v2.Nodes.validation;
import static io.helidon.build.archetype.engine.v2.Nodes.validations;
import static io.helidon.build.archetype.engine.v2.Nodes.variableBoolean;
import static io.helidon.build.archetype.engine.v2.Nodes.variableEnum;
import static io.helidon.build.archetype.engine.v2.Nodes.variableList;
import static io.helidon.build.archetype.engine.v2.Nodes.variableText;
import static io.helidon.build.archetype.engine.v2.Nodes.variables;
import static io.helidon.build.common.test.utils.TestFiles.testResourcePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class JsonScriptWriterTest {

    @Test
    void testExpressions() {
        int i = 1;
        Node node = script(
                condition("['', 'adc', 'def'] contains 'foo'", step("Step" + i++)),
                condition("!(['', 'adc', 'def'] contains 'foo' == false && false)", step("Step" + i++)),
                condition("!false", step("Step" + i++)),
                condition("['', 'adc', 'def'] contains 'foo' == false && true || !false", step("Step" + i++)),
                condition("['', 'adc', 'def'] contains 'foo' == false && true || !true", step("Step" + i++)),
                condition("['', 'adc', 'def'] contains 'def'", step("Step" + i++)),
                condition("['', 'adc', 'def'] contains 'foo' == true && false", step("Step" + i++)),
                condition("['', 'adc', 'def'] contains 'foo' == false && true", step("Step" + i++)),
                condition("'aaa' == 'aaa' && ['', 'adc', 'def'] contains ''", step("Step" + i++)),
                condition("true && \"bar\" == 'foo1' || true", step("Step" + i++)),
                condition("true && \"bar\" == 'foo1' || false", step("Step" + i++)),
                condition("('def' != 'def1') && false == true", step("Step" + i++)),
                condition("('def' != 'def1') && false", step("Step" + i++)),
                condition("('def' != 'def1') && true", step("Step" + i++)),
                condition("'def' != 'def1'", step("Step" + i++)),
                condition("'def' == 'def'", step("Step" + i++)),
                condition("'def' != 'def'", step("Step" + i++)),
                condition("true==((true|| false)&&true)", step("Step" + i++)),
                condition("false==((true|| false)&&true)", step("Step" + i++)),
                condition("false==((true|| false)&&false)", step("Step" + i++)),
                condition("true == 'def'", step("Step" + i++)),
                condition("'true' || 'def'", step("Step" + i++)),
                condition("['', 'adc', 'def'] contains ['', 'adc', 'def']", step("Step" + i++)),
                condition("true == ${def}", step("Step" + i)));

        assertThat(toJson(node), is(expected("writer/expressions.json")));
    }

    @Test
    void testValidations() {
        Node script = script(validations(validation("validation1", regex("^foo"))));
        assertThat(toJson(script), is(expected("writer/validations.json")));
    }

    @Test
    void testVariables() {
        Node script = script(
                variables(
                        variableBoolean("variable-boolean1", true),
                        variableEnum("variable-enum1", "value1"),
                        variableText("variable-text1", "value1"),
                        variableList("variable-list1", List.of("value1"))));
        assertThat(toJson(script), is(expected("writer/variables.json")));
    }

    @Test
    void testPresets() {
        Node script = script(
                presets(
                        presetBoolean("preset-boolean1", true),
                        presetEnum("preset-enum1", "value1"),
                        presetText("preset-text1", "value1"),
                        presetList("preset-list1", List.of("value1"))));
        assertThat(toJson(script), is(expected("writer/presets.json")));
    }

    @Test
    void testMethods() {
        Node block = script();
        block.script().methods().put("method1", method("method1",
                output(model(modelList("model-list1", modelValue("value1"))))));
        assertThat(toJson(block), is(expected("writer/methods.json")));
    }

    @Test
    void testInputs() {
        Node script = script(
                step("Step1", b -> b.attribute("help", "Help1"),
                        inputs(
                                condition("${foo}", inputEnum("enum1", b -> b.attribute("default", "option1"),
                                        inputOption("Option1", "option1"),
                                        inputOption("Option2", "option2"))),
                                inputText("text1").attribute("default", "Default1"))));
        assertThat(toJson(script), is(expected("writer/inputs.json")));
    }

    @Test
    void testBooleanDefaultsKeepScriptSemantics() {
        Node script = script(
                step("Step1",
                        inputs(
                                inputBoolean("flag", b -> b.attribute("default", "false")),
                                inputBoolean("legacy", b -> b.attribute("default", "no")))));

        String json = toJson(script);

        assertThat(json, containsString("\"default\": false"));
        assertThat(json, containsString("\"default\": \"no\""));
    }

    @Test
    void testInputsCompatProjectionDropsOutputAndMethods() {
        Node source = script(
                presets(presetText("preset", "value")),
                step("Prompt",
                        inputs(inputText("name")),
                        output(model(modelValue("secret")))));
        source.script().methods().put("method1", method("method1",
                output(model(modelValue("ignored")))));

        Node projected = Nodes.project(source, Nodes.Projection.INPUTS_COMPAT);

        assertThat(projected.script().methods().isEmpty(), is(true));
        assertThat(projected.traverse(Node.Kind.OUTPUT::equals).iterator().hasNext(), is(false));
        assertThat(projected.traverse(Node.Kind.MODEL::equals).iterator().hasNext(), is(false));
        assertThat(projected.traverse(Node.Kind.INPUT_TEXT::equals).iterator().hasNext(), is(true));
        assertThat(projected.traverse(Node.Kind.PRESET_TEXT::equals).iterator().hasNext(), is(true));
    }

    private static String toJson(Node node) {
        StringWriter writer = new StringWriter();
        try (JsonScriptWriter json = new JsonScriptWriter(writer, true)) {
            json.writeScript(node);
        }
        return normalize(writer.toString());
    }

    private static String expected(String path) {
        try {
            return normalize(Files.readString(testResourcePath(JsonScriptWriterTest.class, "json-writer/" + path)));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").replace("\r", "\n");
    }
}
