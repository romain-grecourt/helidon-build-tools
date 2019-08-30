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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;

import freemarker.cache.URLTemplateLoader;
import freemarker.core.Environment;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.Version;
import java.io.OutputStream;

import static io.helidon.common.CollectionsHelper.mapOf;

/**
 * User flow processor.
 */
final class UserFlowProcessor {

    private static final String DEFAULT_ENCODING = "UTF-8";
    private static final Version FREEMARKER_VERSION = Configuration.VERSION_2_3_23;

    private final Configuration freemarker;
    private final UserFlow userFlow;
    private String bashIncludes;
    private String batIncludes;

    /**
     * Create a new instance of the user flow processor.
     * @param userFlow user flow
     * @throws IOException if an error occurs while setting up the template engine
     */
    UserFlowProcessor(UserFlow userFlow) throws IOException {
        this.userFlow = userFlow;
        freemarker = new Configuration(FREEMARKER_VERSION);
        freemarker.setTemplateLoader(new TemplateLoader());
        freemarker.setDefaultEncoding(DEFAULT_ENCODING);
        freemarker.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        freemarker.setLogTemplateExceptions(false);
    }

    /**
     * Render a single template.
     * @param template URL pointing at the template
     * @param os output stream
     * @param model the template model
     * @throws IOException if an IO error occurs during rendering
     * @throws TemplateException if a template error occurs during rendering 
     */
    void renderTemplate(URL template, OutputStream os, Object model)
            throws IOException, TemplateException {

        Template tpl = freemarker.getTemplate(template.toExternalForm());
        OutputStreamWriter writer = new OutputStreamWriter(os);
        Environment env = tpl.createProcessingEnvironment(model, writer);
        env.process();
    }

    /**
     * Lazily render and get the bash includes.
     * @return String
     * @throws IOException if an IO error occurs during rendering
     * @throws TemplateException if a template error occurs during rendering
     */
    String bashIncludes() throws IOException, TemplateException {
        if (bashIncludes == null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            URL template = this.getClass().getResource("userflow-bash.ftl");
            if (template == null) {
                throw new IllegalStateException("Cannot locate bash source template");
            }
            renderTemplate(template, baos, userFlow);
            bashIncludes = baos.toString();
        }
        return bashIncludes;
    }

    /**
     * Lazily render and get the bat includes.
     * @return String
     * @throws IOException if an IO error occurs during rendering
     * @throws TemplateException if a template error occurs during rendering
     */
    String batIncludes() throws IOException, TemplateException {
        if (batIncludes == null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            URL template = this.getClass().getResource("userflow-bat.ftl");
            if (template == null) {
                throw new IllegalStateException("Cannot locate bat source template");
            }
            renderTemplate(template, baos, userFlow);
            batIncludes = baos.toString();
        }
        return batIncludes;
    }

    /**
     * Process the given template.
     * @param template template
     * @return ByteArrayOutputStream
     * @throws IOException if an IO error occurs while rendering
     * @throws TemplateException if a template error occurs while rendering
     */
    void process(URL template, OutputStream os) throws IOException, TemplateException {
        renderTemplate(template, os, mapOf(
                "bashIncludes", bashIncludes,
                "batIncludes", batIncludes,
                "flow", userFlow));
    }

    /**
     * A URL based template loader.
     */
    private static final class TemplateLoader extends URLTemplateLoader {

        @Override
        protected URL getURL(String url) {
            try {
                URL u = new URL(url);
                // check if the URL actually exist since
                // freemarker invokes this method with all kind of variants
                // for the locales...
                u.openStream();
                return u;
            } catch (IOException ex) {
                return null;
            }
        }
    }
}
