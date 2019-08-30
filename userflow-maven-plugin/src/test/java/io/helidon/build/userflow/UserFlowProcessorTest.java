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
import java.io.FileOutputStream;
import java.net.URL;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link UserFlowProcessor#process(java.net.URL, java.io.OutputStream) }.
 */
public class UserFlowProcessorTest {

    private static final URL FLOW_DESCRIPTOR = UserFlowProcessorTest.class.getResource("what-to-eat.properties");

    @Test
    public void testBashRendering() throws Exception {
        UserFlow flow = UserFlow.create(FLOW_DESCRIPTOR.openStream());
        File output = new File("target/test-classes/what-to-eat.sh");
        FileOutputStream fos = new FileOutputStream(output);
        UserFlowProcessor processor = new UserFlowProcessor(flow);
        processor.bashIncludes();
        processor.process(UserFlowProcessorTest.class.getResource("what-to-eat.sh.ftl"), fos);
    }
}
