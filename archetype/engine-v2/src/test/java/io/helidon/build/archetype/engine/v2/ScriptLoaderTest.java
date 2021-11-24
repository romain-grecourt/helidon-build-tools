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

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Condition;
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Invocation;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.Output;
import io.helidon.build.archetype.engine.v2.ast.Preset;

import io.helidon.build.archetype.engine.v2.ast.Script;
import io.helidon.build.archetype.engine.v2.ast.Step;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 * Tests {@link ScriptLoader}.
 */
public class ScriptLoaderTest {

    static Script load(String path) {
        InputStream is = ScriptLoaderTest.class.getClassLoader().getResourceAsStream(path);
        assertThat(is, is(notNullValue()));
        return ScriptLoader.load0(is);
    }

    @Test
    public void testInputs() {
        Script script = load("loader/inputs.xml");
        new Walker<>(new Node.Visitor<Void>() {
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
                                assertThat(input.defaultValue().orElse(null), is("default#1"));
                                assertThat(input.prompt(), is("Enter 1"));
                                return null;
                            }

                            @Override
                            public Void visitBoolean(Input.Boolean input, Void arg) {
                                assertThat(++index, is(2));
                                assertThat(input.name(), is("input2"));
                                assertThat(input.label(), is("Boolean input"));
                                assertThat(input.help(), is("Help 2"));
                                assertThat(input.defaultValue(), is(true));
                                assertThat(input.prompt(), is("Enter 2"));
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
                                assertThat(input.defaultValue().orElse(null), is("option3.1"));
                                assertThat(input.prompt(), is("Enter 3"));
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
                                assertThat(input.defaultValue(), contains("item4.1", "item4.2"));
                                assertThat(input.prompt(), is("Enter 4"));
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
        }).walk(script.body(), null);
    }

    @Test
    public void testNestedInputs() {
        Script script = load("loader/nested-inputs.xml");
        new Walker<>(new Node.Visitor<Void>() {
            int index = 0;

            @Override
            public Node.VisitResult preVisitBlock(Block block, Void arg) {
                block.accept(new Block.Visitor<Void, Void>() {
                    @Override
                    public Void visitInput(Input input, Void arg) {
                        return input.accept(new Input.Visitor<>() {
                            @Override
                            public Void visitBoolean(Input.Boolean input, Void arg) {
                                assertThat(++index <= 5, is(true));
                                assertThat(input.name(), is("input" + index));
                                assertThat(input.label(), is("label" + index));
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
                    assertThat(index, is(5));
                }
                return Node.VisitResult.CONTINUE;
            }
        }).walk(script.body(), null);
    }

    @Test
    public void testInvocations() {
        Script script = load("loader/invocations.xml");
        new Walker<>(new Node.Visitor<Void>() {
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
                // stop here to avoid io exceptions on the fake files
                return Node.VisitResult.SKIP_SUBTREE;
            }

            @Override
            public Node.VisitResult postVisitBlock(Block block, Void arg) {
                if (block.blockKind() == Block.Kind.SCRIPT) {
                    assertThat(index, is(2));
                }
                return Node.VisitResult.CONTINUE;
            }
        }).walk(script.body(), null);
    }

    @Test
    public void testPresets() {
        Script script = load("loader/presets.xml");
        new Walker<>(new Node.Visitor<Void>() {
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
        }).walk(script.body(), null);
    }

    @Test
    public void testOutput() {
        Script script = load("loader/output.xml");
        new Walker<>(new Node.Visitor<Void>() {
            int index = 0;

            @Override
            public Node.VisitResult preVisitBlock(Block block, Void arg) {
                block.accept(new Block.Visitor<Void, Void>() {
                    @Override
                    public Void visitOutput(Output output, Void arg) {
                        output.accept(new Output.Visitor<Void, Void>() {
                            @Override
                            public Void visitTransformation(Output.Transformation transformation, Void arg) {
                                assertThat(++index, is(1));
                                assertThat(transformation.id(), is("t1"));
                                assertThat(transformation.operations().size(), is(1));
                                assertThat(transformation.operations().get(0).replacement(), is("token1"));
                                assertThat(transformation.operations().get(0).regex(), is("regex1"));
                                return null;
                            }

                            @Override
                            public Void visitTemplates(Output.Templates templates, Void arg) {
                                assertThat(++index, is(2));
                                assertThat(templates.transformations(), contains("t1"));
                                assertThat(templates.engine(), is("tpl-engine-1"));
                                assertThat(templates.directory(), is("dir1"));
                                assertThat(templates.includes(), contains("**/*.tpl1"));
                                assertThat(templates.excludes(), is(empty()));
                                return null;
                            }

                            @Override
                            public Void visitFiles(Output.Files files, Void arg) {
                                assertThat(++index, is(3));
                                assertThat(files.transformations(), contains("t2"));
                                assertThat(files.directory(), is("dir2"));
                                assertThat(files.excludes(), contains("**/*.txt"));
                                assertThat(files.includes(), is(empty()));
                                return null;
                            }

                            @Override
                            public Void visitTemplate(Output.Template template, Void arg) {
                                assertThat(++index, is(4));
                                assertThat(template.engine(), is("tpl-engine-2"));
                                assertThat(template.source(), is("file1.tpl"));
                                assertThat(template.target(), is("file1.txt"));
                                return null;
                            }

                            @Override
                            public Void visitFile(Output.File file, Void arg) {
                                assertThat(++index, is(5));
                                assertThat(file.source(), is("file1.txt"));
                                assertThat(file.target(), is("file2.txt"));
                                return null;
                            }

                            @Override
                            public Void visitModel(Model model, Void arg) {
                                if (model.blockKind() == Block.Kind.MODEL) {
                                    assertThat(++index, is(6));
                                }
                                model.accept(new Model.Visitor<Void, Void>() {
                                    @Override
                                    public Void visitMap(Model.Map map, Void arg) {
                                        switch (++index) {
                                            case (7):
                                                assertThat(map.key(), is("key1"));
                                                break;
                                            case (18):
                                                assertThat(map.key(), is(nullValue()));
                                                break;
                                            default:
                                                Assertions.fail("Unexpected index: " + index);

                                        }
                                        return null;
                                    }

                                    @Override
                                    public Void visitList(Model.List list, Void arg) {
                                        switch (++index) {
                                            case (9):
                                                assertThat(list.key(), is("key1.2"));
                                                break;
                                            case (13):
                                                assertThat(list.key(), is("key3"));
                                                break;
                                            case (15):
                                                assertThat(list.key(), is(nullValue()));
                                                break;
                                            default:
                                                Assertions.fail("Unexpected index: " + index);
                                        }
                                        return null;
                                    }

                                    @Override
                                    public Void visitValue(Model.Value value, Void arg) {
                                        switch (++index) {
                                            case (8):
                                                assertThat(value.key(), is("key1.1"));
                                                assertThat(value.value(), is("value1.1"));
                                                break;
                                            case (10):
                                                assertThat(value.key(), is(nullValue()));
                                                assertThat(value.value(), is("value1.2a"));
                                                break;
                                            case (11):
                                                assertThat(value.key(), is(nullValue()));
                                                assertThat(value.value(), is("value1.2b"));
                                                break;
                                            case (12):
                                                assertThat(value.key(), is("key2"));
                                                assertThat(value.order(), is(50));
                                                assertThat(value.value(), is("value2"));
                                                break;
                                            case (14):
                                                assertThat(value.key(), is(nullValue()));
                                                assertThat(value.value(), is("value3.1"));
                                                break;
                                            case (16):
                                                assertThat(value.key(), is(nullValue()));
                                                assertThat(value.value(), is("value3.2-a"));
                                                break;
                                            case (17):
                                                assertThat(value.key(), is(nullValue()));
                                                assertThat(value.value(), is("value3.2-b"));
                                                break;
                                            case (19):
                                                assertThat(value.key(), is("key3.3-a"));
                                                assertThat(value.value(), is("value3.3-a"));
                                                break;
                                            case (20):
                                                assertThat(value.key(), is("key3.3-b"));
                                                assertThat(value.value(), is("value3.3-b"));
                                                break;
                                            default:
                                                Assertions.fail("Unexpected index: " + index);
                                        }
                                        return null;
                                    }
                                }, null);
                                return null;
                            }
                        }, null);
                        return null;
                    }
                }, null);
                return Node.VisitResult.CONTINUE;
            }

            @Override
            public Node.VisitResult postVisitBlock(Block block, Void arg) {
                if (block.blockKind() == Block.Kind.SCRIPT) {
                    assertThat(index, is(20));
                }
                return Node.VisitResult.CONTINUE;
            }
        }).walk(script.body(), null);
    }

    @Test
    public void testScopedModel() {
        Script script = load("loader/scoped-model.xml");
        new Walker<>(new Node.Visitor<Void>() {
            int index = 0;

            @Override
            public Node.VisitResult preVisitBlock(Block block, Void arg) {
                block.accept(new Block.Visitor<Void, Void>() {
                    @Override
                    public Void visitOutput(Output output, Void arg) {
                        output.accept(new Output.Visitor<Void, Void>() {
                            @Override
                            public Void visitTemplate(Output.Template template, Void arg) {
                                assertThat(++index, is(1));
                                assertThat(template.engine(), is("tpl-engine-1"));
                                assertThat(template.source(), is("file1.tpl"));
                                assertThat(template.target(), is("file1.txt"));
                                new Walker<>(new Node.Visitor<Void>() {
                                    @Override
                                    public Node.VisitResult preVisitBlock(Block block, Void arg) {
                                        block.accept(new Block.Visitor<Void, Void>() {
                                            @Override
                                            public Void visitOutput(Output output, Void arg) {
                                                output.accept(new Output.Visitor<Void, Void>() {
                                                    @Override
                                                    public Void visitModel(Model model, Void arg) {
                                                        model.accept(new Model.Visitor<Void, Void>() {
                                                            @Override
                                                            public Void visitValue(Model.Value value, Void arg) {
                                                                assertThat(++index, is(2));
                                                                assertThat(value.key(), is("key1"));
                                                                assertThat(value.value(), is("value1"));
                                                                return null;
                                                            }
                                                        }, null);
                                                        return null;
                                                    }
                                                }, null);
                                                return null;
                                            }
                                        }, null);
                                        return Node.VisitResult.CONTINUE;
                                    }
                                }).walk(template, null);
                                return null;
                            }

                            @Override
                            public Void visitModel(Model model, Void arg) {
                                if (model.blockKind() == Block.Kind.MODEL) {
                                    assertThat(++index, is(3));
                                }
                                model.accept(new Model.Visitor<Void, Void>() {
                                    @Override
                                    public Void visitValue(Model.Value value, Void arg) {
                                        assertThat(++index, is(4));
                                        assertThat(value.key(), is("key2"));
                                        assertThat(value.value(), is("value2"));
                                        return null;
                                    }
                                }, null);
                                return null;
                            }
                        }, null);
                        return null;
                    }
                }, null);
                if (block.blockKind() == Block.Kind.TEMPLATE) {
                    // skip traversing to "scope" the model
                    return Node.VisitResult.SKIP_SUBTREE;
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
        }).walk(script.body(), null);
    }

    @Test
    public void testConditional() {
        Script script = load("loader/conditional.xml");
        new Walker<>(new Node.Visitor<Void>() {
            int index = 0;

            @Override
            public Node.VisitResult visitCondition(Condition condition, Void arg) {
                if (condition.expression().equals("true")) {
                    return Node.VisitResult.CONTINUE;
                }
                return Node.VisitResult.SKIP_SUBTREE;
            }

            @Override
            public Node.VisitResult preVisitBlock(Block block, Void arg) {
                block.accept(new Block.Visitor<Void, Void>() {

                    @Override
                    public Void visitStep(Step step, Void arg) {
                        assertThat(++index, is(1));
                        assertThat(step.label(), is("Step 1"));
                        assertThat(step.help(), is("Help about step 1"));
                        return null;
                    }

                    @Override
                    public Void visitInput(Input input, Void arg) {
                        input.accept(new Input.Visitor<Void, Void>() {
                            @Override
                            public Void visitBoolean(Input.Boolean input, Void arg) {
                                assertThat(++index, is(2));
                                assertThat(input.name(), is("input1"));
                                assertThat(input.label(), is("Input 1"));
                                return null;
                            }
                        }, null);
                        return null;
                    }

                    @Override
                    public Void visitOutput(Output output, Void arg) {
                        output.accept(new Output.Visitor<Void, Void>() {
                            @Override
                            public Void visitFile(Output.File file, Void arg) {
                                assertThat(++index, is(3));
                                assertThat(file.source(), is("file1.txt"));
                                assertThat(file.target(), is("file2.txt"));
                                return null;
                            }
                        }, null);
                        return null;
                    }
                }, null);
                return Node.VisitResult.CONTINUE;
            }

            @Override
            public Node.VisitResult postVisitBlock(Block block, Void arg) {
                if (block.blockKind() == Block.Kind.SCRIPT) {
                    assertThat(index, is(3));
                }
                return Node.VisitResult.CONTINUE;
            }
        }).walk(script.body(), null);
    }
}
