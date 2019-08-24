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

import freemarker.cache.FileTemplateLoader;
import freemarker.core.Environment;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.Version;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * User flow processor.
 */
final class UserFlowProcessor {

    private static final String DEFAULT_ENCODING = "UTF-8";
    private static final Version FREEMARKER_VERSION = Configuration.VERSION_2_3_23;

    private final Configuration freemarker;
    private final UserFlow userFlow;

    UserFlowProcessor(UserFlow userFlow, File basedir) throws IOException {
        this.userFlow = userFlow;
        freemarker = new Configuration(FREEMARKER_VERSION);
        freemarker.setTemplateLoader(new FileTemplateLoader(basedir));
        freemarker.setDefaultEncoding(DEFAULT_ENCODING);
        freemarker.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        freemarker.setLogTemplateExceptions(false);
    }

    String process(String templatePath) throws IOException, TemplateException {
        Template tpl = freemarker.getTemplate(templatePath);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos);
        Environment env = tpl.createProcessingEnvironment(userFlow, writer);
        env.process();
        return baos.toString(DEFAULT_ENCODING);
    }
}
