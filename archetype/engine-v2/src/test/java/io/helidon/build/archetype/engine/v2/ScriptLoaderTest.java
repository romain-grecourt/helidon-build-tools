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

import io.helidon.build.archetype.engine.v2.ast.Attributes;
import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Invocation;
import io.helidon.build.archetype.engine.v2.ast.Noop;
import io.helidon.build.archetype.engine.v2.ast.Preset;
import io.helidon.build.archetype.engine.v2.ast.Script;
import io.helidon.build.archetype.engine.v2.ast.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Visitor;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

public class ScriptLoaderTest {

    // TODO test default values

    @Test
    public void testTopLevelInputs() {
        InputsVisitor visitor = new InputsVisitor();
        load("loader/top-level-inputs.xml").accept(visitor, null);
        assertThat(visitor.index, is(8));
    }

    @Test
    public void testTopLevelInvocations() {
        InvocationVisitor visitor = new InvocationVisitor();
        load("loader/top-level-invocations.xml").accept(visitor, null);
        assertThat(visitor.index, is(2));
    }

    @Test
    public void testTopLevelPresets() {
        PresetsVisitor visitor = new PresetsVisitor();
        load("loader/top-level-presets.xml").accept(visitor, null);
        assertThat(visitor.index, is(4));
    }

    @Test
    public void testTopLevelOutput() {
        OutputVisitor visitor = new OutputVisitor();
        load("loader/top-level-output.xml").accept(visitor, null);
        assertThat(visitor.index, is(4));
    }

    static Script load(String path) {
        InputStream is = ScriptLoaderTest.class.getClassLoader().getResourceAsStream(path);
        assertThat(is, is(notNullValue()));
        return ScriptLoader.load0(is);
    }

    static final class InputsVisitor implements Visitor<Void> {

        int index = 0;
        boolean inputs = false;

        @Override
        public VisitResult preVisitBlock(Block block, Void arg) {
            Block.Kind blockKind = block.blockKind();
            if (blockKind == Block.Kind.INPUTS) {
                inputs = true;
                return VisitResult.CONTINUE;
            } else if (!inputs) {
                return VisitResult.CONTINUE;
            }
            String label = Attributes.LABEL.get(block).asString();
            switch (index) {
                case 1:
                    assertThat(blockKind, is(Block.Kind.TEXT));
                    assertThat(Attributes.NAME.get(block).asString(), is("input1"));
                    assertThat(label, is("Text input"));
                    break;
                case 2:
                    assertThat(blockKind, is(Block.Kind.BOOLEAN));
                    assertThat(Attributes.NAME.get(block).asString(), is("input2"));
                    assertThat(label, is("Boolean input"));
                    break;
                case 3:
                    assertThat(blockKind, is(Block.Kind.ENUM));
                    assertThat(Attributes.NAME.get(block).asString(), is("input3"));
                    assertThat(label, is("Enum input"));
                    break;
                case 4:
                    assertThat(blockKind, is(Block.Kind.OPTION));
                    assertThat(Attributes.VALUE.get(block).asString(), is("option3.1"));
                    assertThat(label, is("Option 3.1"));
                    break;
                case 5:
                    assertThat(blockKind, is(Block.Kind.OPTION));
                    assertThat(Attributes.VALUE.get(block).asString(), is("option3.2"));
                    assertThat(label, is("Option 3.2"));
                    break;
                case 6:
                    assertThat(blockKind, is(Block.Kind.LIST));
                    assertThat(Attributes.NAME.get(block).asString(), is("input4"));
                    assertThat(label, is("List input"));
                    break;
                case 7:
                    assertThat(blockKind, is(Block.Kind.OPTION));
                    assertThat(Attributes.VALUE.get(block).asString(), is("item4.1"));
                    assertThat(label, is("Item 4.1"));
                    break;
                case 8:
                    assertThat(blockKind, is(Block.Kind.OPTION));
                    assertThat(Attributes.VALUE.get(block).asString(), is("item4.2"));
                    assertThat(label, is("Item 4.2"));
                    break;
            }
            return VisitResult.CONTINUE;
        }

        @Override
        public VisitResult postVisitBlock(Block block, Void arg) {
            if (block.blockKind() == Block.Kind.INPUTS) {
                inputs = false;
            }
            return VisitResult.CONTINUE;
        }
    }

    static final class InvocationVisitor implements Visitor<Void> {

        int index = 0;

        @Override
        public VisitResult visitInvocation(Invocation invocation, Void arg) {
            switch (++index) {
                case 1:
                    assertThat(invocation.invocationKind(), is(Invocation.Kind.SOURCE));
                    assertThat(invocation.src(), is("./dir1/script1.xml"));
                    break;
                case 2:
                    assertThat(invocation.invocationKind(), is(Invocation.Kind.EXEC));
                    assertThat(invocation.src(), is("./dir2/script2.xml"));
                    break;
            }
            return VisitResult.CONTINUE;
        }
    }

    static final class PresetsVisitor implements Visitor<Void> {

        int index = 0;

        @Override
        public VisitResult visitPreset(Preset preset, Void arg) {
            switch (index) {
                case 1:
                    assertThat(preset.path(), is("preset1"));
                    assertThat(preset.presetKind(), is(Preset.Kind.BOOLEAN));
                    assertThat(preset.value().asBoolean(), is(true));
                    break;
                case 2:
                    assertThat(preset.path(), is("preset2"));
                    assertThat(preset.presetKind(), is(Preset.Kind.TEXT));
                    assertThat(preset.value().asString(), is("text1"));
                    break;
                case 3:
                    assertThat(preset.path(), is("preset3"));
                    assertThat(preset.presetKind(), is(Preset.Kind.ENUM));
                    assertThat(preset.value().asString(), is("enum1"));
                    break;
                case 4:
                    assertThat(preset.path(), is("preset4"));
                    assertThat(preset.presetKind(), is(Preset.Kind.LIST));
                    assertThat(preset.value().asList(), contains("list1"));
                    break;
            }
            return VisitResult.CONTINUE;
        }
    }

    static final class OutputVisitor implements Visitor<Void> {

        boolean output = false;
        int index = 0;

        @Override
        public VisitResult visitNoop(Noop noop, Void arg) {
            assertThat(index, is(1));
            assertThat(Attributes.REGEX.get(noop).asString(), is("regex1"));
            assertThat(Attributes.REPLACEMENT.get(noop).asString(), is("token1"));
            return VisitResult.CONTINUE;
        }

        @Override
        public VisitResult preVisitBlock(Block block, Void arg) {
            Block.Kind blockKind = block.blockKind();
            if (blockKind == Block.Kind.OUTPUT) {
                output = true;
                return VisitResult.CONTINUE;
            } else if (!output) {
                return VisitResult.CONTINUE;
            }
            switch (++index) {
                case 1:
                    assertThat(blockKind, is(Block.Kind.TRANSFORMATION));
                    assertThat(Attributes.ID.get(block).asList(), contains("t1"));
                    break;
                case 2:
                    assertThat(blockKind, is(Block.Kind.TEMPLATES));
                    assertThat(Attributes.TRANSFORMATIONS.get(block).asList(), contains("t1"));
                    assertThat(Attributes.ENGINE.get(block).asString(), is("tpl-engine-1"));
                    break;
                case 3:
                    assertThat(blockKind, is(Block.Kind.FILES));
                    assertThat(Attributes.TRANSFORMATIONS.get(block).asList(), contains("t2"));
                    break;
                case 4:
                    assertThat(blockKind, is(Block.Kind.MODEL));
                    break;
            }
            return VisitResult.CONTINUE;
        }

        @Override
        public VisitResult postVisitBlock(Block block, Void arg) {
            if (block.blockKind() == Block.Kind.OUTPUT) {
                output = false;
            }
            return VisitResult.CONTINUE;
        }
    }
}
