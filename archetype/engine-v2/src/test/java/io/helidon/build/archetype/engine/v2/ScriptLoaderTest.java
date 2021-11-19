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

import java.io.InputStream;

import io.helidon.build.archetype.engine.v2.ast.Script;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

@SuppressWarnings("rawtypes")
public class ScriptLoaderTest {

    public static final String DESCRIPTOR_RESOURCE_NAME = "script-loader-test.xml";

    // TODO split into multiple files and multiple tests
    // TODO test output separately
    // TODO test deep nesting of model values
    // TODO test deep nesting of inputs and steps
    // TODO test conditional for all supported elements
    // TODO test default values

    @Test
    public void testLoad0() {
        InputStream is = ScriptLoaderTest.class.getClassLoader().getResourceAsStream(DESCRIPTOR_RESOURCE_NAME);
        assertThat(is, is(notNullValue()));

        Script script = ScriptLoader.load0(is);
        System.out.printf("ok");

        // TODO model codeBlock ?
//        List<InputValue> iv = script.statements(InputValues.class)
//                                    .findFirst()
//                                    .stream()
//                                    .flatMap(InputValues::inputValues)
//                                    .collect(toList());
//
//        assertThat(iv.size(), is(4));
//        Iterator<InputValue> scriptInputValuesIt = iv.iterator();
//
//        InputValue iv1 = scriptInputValuesIt.next();
//        assertThat(iv1.path(), is("test.option1"));
//        assertThat(iv1, is(instanceOf(BooleanInputValue.class)));
//        assertThat(((BooleanInputValue) iv1).value(), is(true));
//
//        InputValue iv2 = scriptInputValuesIt.next();
//        assertThat(iv2.path(), is("test.array1"));
//        assertThat(iv2, is(instanceOf(ListInputValue.class)));
//        assertThat(((ListInputValue) iv2).value(), contains("item 1"));
//
//        InputValue iv3 = scriptInputValuesIt.next();
//        assertThat(iv3.path(), is("test.enum1"));
//        assertThat(iv3, is(instanceOf(EnumInputValue.class)));
//        assertThat(((EnumInputValue) iv3).value(), is("option 1"));
//
//        InputValue iv4 = scriptInputValuesIt.next();
//        assertThat(iv4.path(), is("test.text1"));
//        assertThat(iv4, is(instanceOf(TextInputValue.class)));
//        assertThat(((TextInputValue) iv4).value(), is("text value 1"));
//
//        Step s1 = script.statements(Step.class).findFirst().orElse(null);
//        assertThat(s1, is(notNullValue()));
//        assertThat(s1.label(), is("Step 1"));
//
//        assertThat(s1.statements().size(), is(2));
//
//        Help s1h = s1.statements(Help.class).findFirst().orElse(null);
//        assertThat(s1h, is(notNullValue()));
//        assertThat(s1h.help(), is("\nStep 1 - Help text line 1\nStep 1 - Help text line 2\n"));
//
//        List<Input> s1Inputs = s1.statements(Inputs.class)
//                                 .findFirst()
//                                 .stream()
//                                 .flatMap(block -> block.statements(Input.class))
//                                 .collect(toList());
//
//        assertThat(s1Inputs.size(), is(1));
//        Iterator<Input> ss1InputsIt = s1Inputs.iterator();
//
//        Input s1i1 = ss1InputsIt.next();
//        assertThat(s1i1.name(), is("input1"));
//        assertThat(s1i1.label(), is("Input text 1"));
//        assertThat(s1i1, is(instanceOf(TextInput.class)));
//        assertThat(s1i1.statements(), is(empty()));
//
//        List<Inputs> inputBlocks = script.statements(Inputs.class).collect(toList());
//
//        assertThat(inputBlocks.size(), is(2));
//        Iterator<Inputs> inputBlocksIt = inputBlocks.iterator();
//
//        Inputs ib1 = inputBlocksIt.next();
//        assertThat(ib1.statements().size(), is(2));
//
//        List<Input> ib1i1 = ib1.statements(Input.class).collect(toList());
//        assertThat(ib1i1.size(), is(2));
//        Iterator<Input> inputsIt = ib1i1.iterator();
//
//        Input i2 = inputsIt.next();
//        assertThat(i2.name(), is("input2"));
//        assertThat(i2.label(), is("Input text 2"));
//        assertThat(i2, is(instanceOf(TextInput.class)));
//
//        Input i3 = inputsIt.next();
//        assertThat(i3.name(), is("input3"));
//        assertThat(i3.label(), is("Input boolean 3"));
//        assertThat(i3, is(instanceOf(BooleanInput.class)));
//
//        Step i3s1 = i3.statements(Step.class).findFirst().orElse(null);
//        assertThat(i3s1, is(notNullValue()));
//        assertThat(i3s1.label(), is("Input 3 nested step 1"));
//
//        Help i3s1h = i3s1.statements(Help.class).findFirst().orElse(null);
//        assertThat(i3s1h, is(notNullValue()));
//        assertThat(i3s1h.help(), is("Input 3 - Nested step 1 - help text"));
//
//        List<Input> i3s1Inputs = i3s1.statements(Inputs.class)
//                                     .findFirst()
//                                     .stream()
//                                     .flatMap(block -> block.statements(Input.class))
//                                     .collect(toList());
//        assertThat(i3s1Inputs.size(), is(4));
//        Iterator<Input> i3s1InputsIt = i3s1Inputs.iterator();
//
//        Input i3s1i1 = i3s1InputsIt.next();
//        assertThat(i3s1i1.name(), is("nested-input-1"));
//        assertThat(i3s1i1.label(), is("Nested input text 1"));
//        assertThat(i3s1i1, is(instanceOf(TextInput.class)));
//        assertThat(((TextInput) i3s1i1).defaultValue(), is("default-value-1"));
//
//        Input i3s1i2 = i3s1InputsIt.next();
//        assertThat(i3s1i2.name(), is("nested-input-2"));
//        assertThat(i3s1i2.label(), is("Nested input boolean 2"));
//        assertThat(i3s1i2, is(instanceOf(BooleanInput.class)));
//        List<Inputs> i3s1i2Inputs = i3s1i2.statements(Inputs.class).collect(toList());
//        assertThat(i3s1i2Inputs.size(), is(1));
//        assertThat(i3s1i2Inputs.iterator().next().statements(), is(empty()));
//
//        Input i3s1i3 = i3s1InputsIt.next();
//        assertThat(i3s1i3.name(), is("nested-input-3"));
//        assertThat(i3s1i3.label(), is("Nested input enum 3"));
//        assertThat(i3s1i3, is(instanceOf(EnumInput.class)));
//
//        Help i3s1i3h = i3s1i3.statements(Help.class).findFirst().orElse(null);
//        assertThat(i3s1i3h, is(notNullValue()));
//        assertThat(i3s1i3h.help(), is("Nested input 3 - help text"));
//
//        List<Option> i3s1i3Options = i3s1i3.statements(Option.class).collect(toList());
//        assertThat(i3s1i3Options.size(), is(2));
//        Iterator<Option> i3s1i3OptionsIt = i3s1i3Options.iterator();
//
//        Option i3s1i3o1 = i3s1i3OptionsIt.next();
//        assertThat(i3s1i3o1.value(), is("option1"));
//        assertThat(i3s1i3o1.label(), is("Option 1"));
//        assertThat(i3s1i3o1.statements().size(), is(1));
//        Help i3s1i3o1h = i3s1i3o1.statements(Help.class).findFirst().orElse(null);
//        assertThat(i3s1i3o1h, is(notNullValue()));
//        assertThat(i3s1i3o1h.help(), is("Nested input 3 - Option 1 - help text"));
//
//        Option i3s1i3o2 = i3s1i3OptionsIt.next();
//        assertThat(i3s1i3o2.value(), is("option2"));
//        assertThat(i3s1i3o2.label(), is("Option 2"));
//        assertThat(i3s1i3o2.statements(), is(empty()));
//
//        Input i3s1i4 = i3s1InputsIt.next();
//        assertThat(i3s1i4.name(), is("nested-input-4"));
//        assertThat(i3s1i4.label(), is("Nested input list 4"));
//        assertThat(i3s1i4, is(instanceOf(ListInput.class)));
//
//        Help i3s1i4h = i3s1i4.statements(Help.class).findFirst().orElse(null);
//        assertThat(i3s1i4h, is(notNullValue()));
//        assertThat(i3s1i4h.help(), is("Nested input 4 - help text"));
//
//        List<Option> i3s1i4Options = i3s1i4.statements(Option.class).collect(toList());
//        assertThat(i3s1i4Options.size(), is(1));
//        Iterator<Option> i3s1i4OptionsIt = i3s1i4Options.iterator();
//
//        Option i3s1i4o1 = i3s1i4OptionsIt.next();
//        assertThat(i3s1i4o1.value(), is("item1"));
//        assertThat(i3s1i4o1.label(), is("Item 1"));
//        assertThat(i3s1i4o1.statements().size(), is(1));
//        Help i3s1i4o1h = i3s1i4o1.statements(Help.class).findFirst().orElse(null);
//        assertThat(i3s1i4o1h, is(notNullValue()));
//        assertThat(i3s1i4o1h.help(), is("Nested input 4 - Item 1 - help text"));
//
//        ib1 = inputBlocksIt.next();
//        assertThat(ib1.statements().size(), is(1));
//        ib1i1 = ib1.statements(Input.class).collect(toList());
//        assertThat(ib1i1.size(), is(1));
//        inputsIt = ib1i1.iterator();
//
//        Input i4 = inputsIt.next();
//        assertThat(i4.name(), is("input4"));
//        assertThat(i4.label(), is("Input boolean 4"));
//        assertThat(i4, is(instanceOf(BooleanInput.class)));
//
//        assertThat(i4.statements().size(), is(2));
//
//        Help i4h = i4.statements(Help.class).findFirst().orElse(null);
//        assertThat(i4h, is(notNullValue()));
//        assertThat(i4h.help(), is("Input 4 - help text"));
//
//        Input i4i1 = i4.statements(Inputs.class)
//                       .findFirst()
//                       .stream()
//                       .flatMap(block -> block.statements(Input.class))
//                       .findFirst()
//                       .orElse(null);
//
//        assertThat(i4i1, is(notNullValue()));
//        assertThat(i4i1.name(), is("nested-nested-input-5"));
//        assertThat(i4i1.label(), is("Nested nested input boolean 5"));
//
//        assertThat(i4i1.statements().size(), is(1));
//        Output i4i1o1 = i4i1.statements(Output.class).findFirst().orElse(null);
//        assertThat(i4i1o1, is(notNullValue()));
//        assertThat(i4i1o1.statements().size(), is(2));
//
//        Template i4i1o1t1 = i4i1o1.statements(Template.class).findFirst().orElse(null);
//        assertThat(i4i1o1t1, is(notNullValue()));
//        assertThat(i4i1o1t1.engine(), is("tpl-engine-1"));
//        assertThat(i4i1o1t1.source(), is("file1.tpl"));
//        assertThat(i4i1o1t1.target(), is("file1.txt"));
//        Model i4i1o1t1m = i4i1o1t1.statements(Model.class).findFirst().orElse(null);
//        assertThat(i4i1o1t1m, is(notNullValue()));
//        List<ModelValue> i4i1o1t1mModelValues = i4i1o1t1m.statements(ModelValue.class).collect(toList());
//        assertThat(i4i1o1t1mModelValues.size(), is(1));
//        ModelValue i4i1o1t1mv1 = i4i1o1t1mModelValues.iterator().next();
//        assertThat(i4i1o1t1mv1.key(), is("key1"));
//        assertThat(i4i1o1t1mv1, is(instanceOf(ModelStringValue.class)));
//        assertThat(((ModelStringValue) i4i1o1t1mv1).value(), is("value1"));
//
//        Templates i4i1o1t2 = i4i1o1.statements(Templates.class).findFirst().orElse(null);
//        assertThat(i4i1o1t2, is(notNullValue()));
//        assertThat(i4i1o1t2.engine(), is("tpl-engine-2"));
//        assertThat(i4i1o1t2.transformations(), contains("t1"));
//        assertThat(i4i1o1t2.directory(), is("dir1"));
//        assertThat(i4i1o1t2.includes(), contains("**/*.tpl"));
//
//        Model i4i1o1t2m = i4i1o1t2.statements(Model.class).findFirst().orElse(null);
//        assertThat(i4i1o1t2m, is(notNullValue()));
//        List<ModelValue> i4i1o1t2mModelValues = i4i1o1t2m.statements(ModelValue.class).collect(toList());
//        assertThat(i4i1o1t2mModelValues.size(), is(1));
//        ModelValue i4i1o1t2mv1 = i4i1o1t2mModelValues.iterator().next();
//        assertThat(i4i1o1t2mv1.key(), is("key2"));
//        assertThat(i4i1o1t2mv1, is(instanceOf(ModelStringValue.class)));
//        assertThat(((ModelStringValue) i4i1o1t2mv1).value(), is("value2"));
//
//        List<Invocation> invocations = script.statements(Invocation.class).collect(toList());
//        assertThat(invocations.size(), is(2));
//        Iterator<Invocation> invocationsIt = invocations.iterator();
//
//        Invocation inv1 = invocationsIt.next();
//        assertThat(inv1.src(), is("./dir1/script1.xml"));
//        assertThat(inv1.invocationKind(), is(Invocation.Kind.SOURCE));
//
//        Invocation inv2 = invocationsIt.next();
//        assertThat(inv2.src(), is("./dir2/script2.xml"));
//        assertThat(inv2.invocationKind(), is(Invocation.Kind.EXEC));
//
//        Output o1 = script.statements(IfStatement.class)
//              .map(IfStatement::thenStatement)
//                .filter(Output.class::isInstance)
//                .map(Output.class::cast)
//                .findFirst()
//                .orElse(null);
//        assertThat(o1, is(notNullValue()));
//
//        Transformation o1t1 = o1.statements(Transformation.class).findFirst().orElse(null);
//        assertThat(o1t1, is(notNullValue()));
//        assertThat(o1t1.id(), is("t1"));
//        Replacement o1t1r1 = o1t1.statements(Replacement.class).findFirst().orElse(null);
//        assertThat(o1t1r1, is(notNullValue()));
//        assertThat(o1t1r1.replacement(), is("token1"));
//        assertThat(o1t1r1.regex(), is("regex1"));
//
//        Templates o1tp1 = o1.statements(Templates.class).findFirst().orElse(null);
//        assertThat(o1tp1, is(notNullValue()));
//        assertThat(o1tp1.engine(), is("tpl-engine-3"));
//        assertThat(o1tp1.transformations(), contains("t3"));
//        assertThat(o1tp1.directory(), is("dir3"));
//        assertThat(o1tp1.includes(), contains("**/*.tpl3"));
//
//        Files o1fs1 = o1.statements(Files.class).findFirst().orElse(null);
//        assertThat(o1fs1, is(notNullValue()));
//        assertThat(o1fs1.transformations(), contains("t4"));
//        assertThat(o1fs1.directory(), is("dir4"));
//        assertThat(o1fs1.excludes(), contains("**/*.tpl4"));
//
//        Model o1m = o1.statements(Model.class).findFirst().orElse(null);
//        assertThat(o1m, is(notNullValue()));
//
//        List<ModelValue> o1mModelValues = o1m.statements(ModelValue.class).collect(toList());
//        assertThat(o1mModelValues.size(), is(3));
//        Iterator<ModelValue> o1mModelValuesIt = o1mModelValues.iterator();
//
//        ModelValue o1mv1 = o1mModelValuesIt.next();
//        assertThat(o1mv1.key(), is("key3"));
//        assertThat(o1mv1, is(instanceOf(ModelMapValue.class)));
//
//        List<ModelValue> o1mv1ModelValues = o1mv1.statements(ModelValue.class).collect(toList());
//        assertThat(o1mv1ModelValues.size(), is(2));
//        Iterator<ModelValue> o1mv1ModelValuesIt = o1mv1ModelValues.iterator();
//
//        ModelValue o1mv1v1 = o1mv1ModelValuesIt.next();
//        assertThat(o1mv1v1.key(), is("key3.1"));
//        assertThat(o1mv1v1, is(instanceOf(ModelStringValue.class)));
//        assertThat(((ModelStringValue) o1mv1v1).value(), is("value3.1"));
//
//        ModelValue o1mv1v2 = o1mv1ModelValuesIt.next();
//        assertThat(o1mv1v2.key(), is("key3.2"));
//        assertThat(o1mv1v2, is(instanceOf(ModelListValue.class)));
//
//        List<ModelStringValue> o1mv1v2ModelValues = o1mv1v2.statements(ModelStringValue.class).collect(toList());
//        assertThat(o1mv1v2ModelValues.size(), is(2));
//        Iterator<ModelStringValue> o1mv1v2ModelValuesIt = o1mv1v2ModelValues.iterator();
//
//        ModelStringValue o1mv1v2v1 = o1mv1v2ModelValuesIt.next();
//        assertThat(o1mv1v2v1.key(), is(nullValue()));
//        assertThat(o1mv1v2v1.value(), is("value3.2a"));
//
//        ModelStringValue o1mv1v2v2 = o1mv1v2ModelValuesIt.next();
//        assertThat(o1mv1v2v2.key(), is(nullValue()));
//        assertThat(o1mv1v2v2.value(), is("value3.2b"));
//
//        ModelValue o1mv2 = o1mModelValuesIt.next();
//        assertThat(o1mv2.key(), is("key4"));
//        assertThat(o1mv2.order(), is(50));
//        assertThat(o1mv2, is(instanceOf(ModelStringValue.class)));
//        assertThat(((ModelStringValue) o1mv2).value(), is("value4"));
//
//        ModelValue o1mv3 = o1mModelValuesIt.next();
//        assertThat(o1mv3.key(), is("key5"));
//        assertThat(o1mv3, is(instanceOf(ModelListValue.class)));
//
//        List<ModelValue> o1mv3ModelValues = o1mv3.statements(ModelValue.class).collect(toList());
//        assertThat(o1mv3ModelValues.size(), is(3));
//        Iterator<ModelValue> o1mv3ModelValuesIt = o1mv3ModelValues.iterator();
//
//        ModelValue o1mv3v1 = o1mv3ModelValuesIt.next();
//        assertThat(o1mv3v1.key(), is(nullValue()));
//        assertThat(o1mv3v1, is(instanceOf(ModelStringValue.class)));
//        assertThat(((ModelStringValue) o1mv3v1).value(), is("value5a"));
//
//        ModelValue o1mv3v2 = o1mv3ModelValuesIt.next();
//        assertThat(o1mv3v2.key(), is(nullValue()));
//        assertThat(o1mv3v2, is(instanceOf(ModelListValue.class)));
//
//        List<ModelStringValue> o1mv3v2ModelValues = o1mv3v2.statements(ModelStringValue.class).collect(toList());
//        assertThat(o1mv3v2ModelValues.size(), is(2));
//        Iterator<ModelStringValue> o1mv3v2ModelValuesIt = o1mv3v2ModelValues.iterator();
//
//        ModelStringValue o1mv3v2v1 = o1mv3v2ModelValuesIt.next();
//        assertThat(o1mv3v2v1.key(), is(nullValue()));
//        assertThat(o1mv3v2v1.value(), is("value5a1"));
//
//        ModelStringValue o1mv3v2v2 = o1mv3v2ModelValuesIt.next();
//        assertThat(o1mv3v2v2.key(), is(nullValue()));
//        assertThat(o1mv3v2v2.value(), is("value5a2"));
//
//        ModelValue o1mv3v3 = o1mv3ModelValuesIt.next();
//        assertThat(o1mv3v3.key(), is(nullValue()));
//        assertThat(o1mv3v3, is(instanceOf(ModelMapValue.class)));
//
//        List<ModelStringValue> o1mv3v3ModelValues = o1mv3v3.statements(ModelStringValue.class).collect(toList());
//        assertThat(o1mv3v3ModelValues.size(), is(2));
//        Iterator<ModelStringValue> o1mv3v3ModelValuesIt = o1mv3v3ModelValues.iterator();
//
//        ModelStringValue o1mv3v3v1 = o1mv3v3ModelValuesIt.next();
//        assertThat(o1mv3v3v1.key(), is("key5.1"));
//        assertThat(o1mv3v3v1.value(), is("value5b1"));
//
//        ModelStringValue o1mv3v3v2 = o1mv3v3ModelValuesIt.next();
//        assertThat(o1mv3v3v2.key(), is("key5.2"));
//        assertThat(o1mv3v3v2.value(), is("value5b2"));
    }
}
