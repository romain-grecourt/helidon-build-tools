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
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Invocation;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.Noop;
import io.helidon.build.archetype.engine.v2.ast.Preset;
import io.helidon.build.archetype.engine.v2.ast.Script;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

public class ScriptLoaderTest {

    // TODO test default values
    // TODO test nested inputs (variable depth)
    // TODO test conditional
    // TODO test model (variable depth)

    static Script load(String path) {
        InputStream is = ScriptLoaderTest.class.getClassLoader().getResourceAsStream(path);
        assertThat(is, is(notNullValue()));
        return ScriptLoader.load0(is);
    }

    @Test
    public void testTopLevelInputs() {
        load("loader/top-level-inputs.xml").accept(new Node.Visitor<Void>() {
            int index = 0;

            @Override
            public Node.VisitResult preVisitBlock(Block block, Void arg) {
                block.accept(new Block.Visitor<Void, Void>() {
                    @Override
                    public Void visitInput(Input input, Void arg) {
                        return input.accept(new Input.Visitor<>() {

                            @Override
                            public Void visitText(Input.Text input, Void arg) {
                                assertThat(++index, is(1));
                                assertThat(input.name(), is("input1"));
                                assertThat(input.label(), is("Text input"));
                                assertThat(input.help(), is("Help 1"));
                                return null;
                            }

                            @Override
                            public Void visitBoolean(Input.Boolean input, Void arg) {
                                assertThat(++index, is(2));
                                assertThat(input.name(), is("input2"));
                                assertThat(input.label(), is("Boolean input"));
                                assertThat(input.help(), is("Help 2"));
                                return null;
                            }

                            @Override
                            public Void visitEnum(Input.Enum input, Void arg) {
                                assertThat(++index, is(3));
                                assertThat(input.name(), is("input3"));
                                assertThat(input.label(), is("Enum input"));
                                assertThat(input.help(), is("Help 3"));
                                assertThat(input.options().size(), is(2));
                                assertThat(input.options().get(0).value(), is("option3.1"));
                                assertThat(input.options().get(0).label(), is("Option 3.1"));
                                assertThat(input.options().get(1).value(), is("option3.2"));
                                assertThat(input.options().get(1).label(), is("Option 3.2"));
                                return null;
                            }

                            @Override
                            public Void visitList(Input.List input, Void arg) {
                                assertThat(++index, is(4));
                                assertThat(input.name(), is("input4"));
                                assertThat(input.label(), is("List input"));
                                assertThat(input.help(), is("Help 4"));
                                assertThat(input.options().get(0).value(), is("item4.1"));
                                assertThat(input.options().get(0).label(), is("Item 4.1"));
                                assertThat(input.options().get(1).value(), is("item4.2"));
                                assertThat(input.options().get(1).label(), is("Item 4.2"));
                                return null;
                            }
                        }, arg);
                    }
                }, arg);
                return Node.VisitResult.CONTINUE;
            }

            @Override
            public Node.VisitResult postVisitBlock(Block block, Void arg) {
                if (block.blockKind() == Block.Kind.SCRIPT) {
                    assertThat(index, is(4));
                }
                return Node.VisitResult.CONTINUE;
            }
        }, null);
    }

    @Test
    public void testTopLevelInvocations() {
        load("loader/top-level-invocations.xml").accept(new Node.Visitor<Void>() {
            int index = 0;

            @Override
            public Node.VisitResult visitInvocation(Invocation invocation, Void arg) {
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
                return Node.VisitResult.CONTINUE;
            }

            @Override
            public Node.VisitResult postVisitBlock(Block block, Void arg) {
                if (block.blockKind() == Block.Kind.SCRIPT) {
                    assertThat(index, is(2));
                }
                return Node.VisitResult.CONTINUE;
            }
        }, null);
    }

    @Test
    public void testTopLevelPresets() {
        load("loader/top-level-presets.xml").accept(new Node.Visitor<Void>() {
            int index = 0;

            @Override
            public Node.VisitResult visitPreset(Preset preset, Void arg) {
                switch (++index) {
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
                return Node.VisitResult.CONTINUE;
            }

            @Override
            public Node.VisitResult postVisitBlock(Block block, Void arg) {
                if (block.blockKind() == Block.Kind.SCRIPT) {
                    assertThat(index, is(4));
                }
                return Node.VisitResult.CONTINUE;
            }
        }, null);
    }

    @Test
    public void testTopLevelOutput() {
        load("loader/top-level-output.xml").accept(new Node.Visitor<Void>() {
            boolean output = false;
            int index = 0;

            @Override
            public Node.VisitResult visitNoop(Noop noop, Void arg) {
                assertThat(index, is(1));
                assertThat(Attributes.REGEX.get(noop).asString(), is("regex1"));
                assertThat(Attributes.REPLACEMENT.get(noop).asString(), is("token1"));
                return Node.VisitResult.CONTINUE;
            }

            @Override
            public Node.VisitResult preVisitBlock(Block block, Void arg) {
                Block.Kind blockKind = block.blockKind();
                if (blockKind == Block.Kind.OUTPUT) {
                    output = true;
                    return Node.VisitResult.CONTINUE;
                } else if (!output) {
                    return Node.VisitResult.CONTINUE;
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
                return Node.VisitResult.CONTINUE;
            }

            @Override
            public Node.VisitResult postVisitBlock(Block block, Void arg) {
                switch (block.blockKind()) {
                    case OUTPUT:
                        output = false;
                        break;
                    case SCRIPT:
                        assertThat(index, is(4));
                        break;
                    default:
                }
                return Node.VisitResult.CONTINUE;
            }
        }, null);
    }
}
