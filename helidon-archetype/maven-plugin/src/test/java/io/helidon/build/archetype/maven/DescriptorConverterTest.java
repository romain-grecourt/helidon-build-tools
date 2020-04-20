/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.build.archetype.maven;

import io.helidon.build.archetype.engine.ArchetypeDescriptor;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link DescriptorConverter}.
 */
public class DescriptorConverterTest {

    @Test
    public void testConvert() {
        ArchetypeDescriptor desc = ArchetypeDescriptor.read(DescriptorConverterTest.class.getResourceAsStream("helidon-archetype.xml"));
        StringWriter sw = new StringWriter();
        DescriptorConverter.convert(desc, sw);
        System.out.println(sw.toString());
    }
}
