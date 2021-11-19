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

import java.util.Arrays;
import java.util.LinkedList;

import io.helidon.build.archetype.engine.v2.ast.Script;
import io.helidon.build.archetype.engine.v2.prompter.Prompter;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class InputInterpreterTest {

    @Test
    public void simpleTest() {
        Script script = Script.builder(null, null)
//                      .path(scriptPath)
//                      .body(s -> {
//                          s.step(st -> {
//                              st.label("my step")
//                                .body(b -> {
//                                    b.inputs(i -> {
//                                        i.textInput(t -> {
//                                            t.name("foo")
//                                             .label("Foo")
//                                             .prompt("Enter a foo");
//                                        });
//                                    });
//                                });
//                          });
//                      })
                                   .build();

        Context ctx = eval(script, "bar");
        assertThat(lookupString(ctx, "foo"), is("bar"));
    }

    private static String lookupString(Context ctx, String inputPath) {
        return ctx.lookup(inputPath).value().map(cv -> cv.value().asString()).orElse(null);
    }

    private static Context eval(Script script, Object... userInput) {
        Context ctx = Context.create(script.location().getParent());
//        script.accept(new InputInterpreter(new TestPrompter(userInput), true), ctx);
        return ctx;
    }

    private static final class TestPrompter {

        final LinkedList<?> userInput;

        TestPrompter(Object... userInput) {
            this.userInput = new LinkedList<>(Arrays.asList(userInput));
        }

//        @Override
//        public String prompt(TextInput input) {
//            return (String) userInput.pop();
//        }
//
//        @Override
//        public boolean prompt(BooleanInput input) {
//            return (boolean) userInput.pop();
//        }
//
//        @Override
//        public String prompt(EnumInput input) {
//            return (String) userInput.pop();
//        }
//
//        @Override
//        public List<String> prompt(ListInput input) {
//            return (List<String>) userInput.pop();
//        }
    }
}
