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

package io.helidon.build.archetype.engine.v2.template;

import java.io.File;
import java.io.IOException;
import java.util.List;

import io.helidon.build.archetype.engine.v2.MustacheProvider;
import io.helidon.build.archetype.engine.v2.MustacheSupport;
import io.helidon.build.archetype.engine.v2.spi.TemplateSupportProvider;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class MustacheSupportTest {

    @Test
    void testRender() throws IOException {
        String templateFileName = "test.mustache";
        File templateFile = new File(getClass().getClassLoader().getResource(templateFileName).getFile());
//        Model scope = Model.builder()
//                           .value(ModelStringValue.create("title", "title content"))
//                           .value(ModelStringValue.create("name", "name content"))
//                           .build();
//
//        try (OutputStream os = new ByteArrayOutputStream();
//             InputStream template = new FileInputStream(templateFile)) {
//            TemplateEngine engine = new MustacheTemplateEngine();
//            engine.render(template, templateFileName, StandardCharsets.UTF_8, os, scope);
//            assertThat(os.toString(), containsString("title content"));
//            assertThat(os.toString(), containsString("name content"));
//        }
    }

    @Test
    void testAllProviders() {
        List<TemplateSupportProvider> providers = TemplateSupportProvider.all();
        assertThat(providers.stream()
                          .filter(MustacheSupport.class::isInstance)
                          .findAny()
                          .orElse(null),
                notNullValue());
    }

    @Test
    void testProviderByName() {
        assertThat(TemplateSupportProvider.providerByName("mustache").orElse(null), instanceOf(MustacheSupport.class));
    }

    @Test
    void testName() {
        assertThat(new MustacheProvider().name(), is("mustache"));
    }
}
