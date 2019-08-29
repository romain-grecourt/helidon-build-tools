/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.build.userflow;

import java.io.File;
import java.util.LinkedList;

import io.helidon.build.userflow.UserFlow.Step;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link UserFlowProcessor}.
 */
public class UserFlowProcessorTest {

    private static final String TEMPLATE_PATH = "src/test/resources/io/helidon/build/userflow/userflow-bash.ftl";
    private static final File BASEDIR = new File(getBasedirPath());

    /**
     * Get the base directory path of the project.
     *
     * @return base directory path
     */
    static String getBasedirPath() {
        String basedirPath = System.getProperty("basedir");
        if (basedirPath == null) {
            basedirPath = new File("").getAbsolutePath();
        }
        return basedirPath.replace("\\","/");
    }

    private static String process(String expr) throws Exception {
        LinkedList<Step> steps = new LinkedList<>();
        steps.add(new Step("step1", "Here is a step!", new Expression(expr)));
        UserFlow flow = new UserFlow(steps);
        UserFlowProcessor processor = new UserFlowProcessor(flow, BASEDIR);
        return processor.process(TEMPLATE_PATH).substring("function step1() { ".length());
    }

    @Test
    public void testEqual() throws Exception {
        assertThat(process("$foo == \"bar\""),
                is(startsWith("if [ \"$foo\" = \"bar\" ] ;then")));
    }

    @Test
    public void testNotEqual() throws Exception {
        assertThat(process("$foo != \"bar\""),
                is(startsWith("if [ \"$foo\" != \"bar\" ] ;then")));
    }

    @Test
    public void testAnd() throws Exception {
        assertThat(process("$foo == \"bar\" && $bar == \"foo\""),
                is(startsWith("if { [ \"$foo\" = \"bar\" ] && [ \"$bar\" = \"foo\" ] ;} ;then")));
    }

    @Test
    public void testOr() throws Exception {
        assertThat(process("$foo == \"bar\" || $bar == \"foo\""),
                is(startsWith("if { [ \"$foo\" = \"bar\" ] || [ \"$bar\" = \"foo\" ] ;} ;then")));
    }

    @Test
    public void testXor() throws Exception {
        assertThat(process("$foo == \"bar\" ^^ $bar == \"foo\""),
                is(startsWith("if { { [ \"$foo\" = \"bar\" ] || [ \"$bar\" = \"foo\" ] ;} "
                        + "&& ! { { [ \"$foo\" = \"bar\" ] && [ \"$bar\" = \"foo\" ] ;} ;} ;} ;then")));
    }

    @Test
    public void testIs() throws Exception {
        assertThat(process("($foo == \"bar\") == ($bar == \"foo\")"),
                is(startsWith("if { { [ \"$foo\" = \"bar\" ] && [ \"$bar\" = \"foo\" ] ;} "
                        + "|| { ! { [ \"$foo\" = \"bar\" ] ;} && ! { [ \"$bar\" = \"foo\" ] ;} ;} ;} ;then")));
    }

    @Test
    public void testIsNot() throws Exception {
        assertThat(process("($foo == \"bar\") != ($bar == \"foo\")"),
                is(startsWith("if ! { { { [ \"$foo\" = \"bar\" ] && [ \"$bar\" = \"foo\" ] ;} "
                        + "|| { ! { [ \"$foo\" = \"bar\" ] ;} && ! { [ \"$bar\" = \"foo\" ] ;} ;} ;} ;} ;then")));
    }

    // TODO test precedence with parenthesis
}
