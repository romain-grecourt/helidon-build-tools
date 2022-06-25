/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.build.maven.sitegen.freemarker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import io.helidon.build.maven.sitegen.Config;
import io.helidon.build.maven.sitegen.RenderingContext;
import io.helidon.build.maven.sitegen.RenderingException;

import freemarker.core.Environment;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateNotFoundException;
import freemarker.template.Version;
import org.asciidoctor.ast.ContentNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.helidon.build.common.Strings.requireValid;
import static java.util.Objects.requireNonNull;

/**
 * A facade over freemarker.
 */
@SuppressWarnings("unused")
public class FreemarkerEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(FreemarkerEngine.class);
    private static final Version FREEMARKER_VERSION = Configuration.VERSION_2_3_23;
    private static final ObjectWrapper OBJECT_WRAPPER = new ObjectWrapper(FREEMARKER_VERSION);

    private final String backend;
    private final Map<String, String> directives;
    private final Map<String, String> model;
    private final Configuration freemarker;

    private FreemarkerEngine(Builder builder) {
        backend = requireValid(builder.backend, "backend is invalid!");
        directives = builder.directives;
        model = builder.model;
        freemarker = new Configuration(FREEMARKER_VERSION);
        freemarker.setTemplateLoader(new TemplateLoader());
        freemarker.setDefaultEncoding("UTF-8");
        freemarker.setObjectWrapper(OBJECT_WRAPPER);
        freemarker.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        freemarker.setLogTemplateExceptions(false);
    }

    /**
     * Get the custom directives in-use.
     *
     * @return map, never {@code null}
     */
    public Map<String, String> directives() {
        return directives;
    }

    /**
     * Get the custom model in-use.
     *
     * @return map, never {@code null}
     */
    public Map<String, String> model() {
        return model;
    }

    /**
     * Render a template to a file.
     *
     * @param template   the relative path of the template to render
     * @param targetPath the relative target path of the file to create
     * @param model      the model for the template to use
     * @param ctx        the processing context
     * @throws RenderingException if an error occurred
     */
    public void renderFile(String template, String targetPath, Map<String, Object> model, RenderingContext ctx)
            throws RenderingException {

        try {
            String rendered = renderString(template, model, ctx.templateSession());
            Path target = ctx.outputDir().resolve(targetPath);
            Files.createDirectories(target.getParent());
            Files.writeString(target, rendered);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Render a template.
     *
     * @param template the relative path of the template to render
     * @param node     the asciidoctor node to use as model for the template
     * @return the rendered output
     * @throws RenderingException if an error occurred
     */
    public String renderString(String template, ContentNode node) throws RenderingException {
        Object session = node.getDocument().getAttribute("templateSession");
        requireNonNull(session, "template session is null!");
        if (!(session instanceof TemplateSession)) {
            throw new IllegalStateException("Invalid template session");
        }
        // TODO extract page, pages, templateSession
        // and set them as variables
        return renderString(template, node, (TemplateSession) session);
    }

    /**
     * Render a template to a string.
     *
     * @param template the relative path of the template to render
     * @param model    the model for the template to use
     * @return the rendered output
     * @throws RenderingException if an error occurred
     */
    public String renderString(String template, Object model) throws RenderingException {
        return renderString(template, model, null);
    }

    /**
     * Render a template to a string.
     *
     * @param template the relative path of the template to render
     * @param model    the model for the template to use
     * @param session  the session to share the global variable across invocations
     * @return the rendered output
     * @throws RenderingException if an error occurred
     */
    public String renderString(String template, Object model, TemplateSession session) throws RenderingException {
        String path = backend + "/" + template;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Template tpl = freemarker.getTemplate(path);
            OutputStreamWriter writer = new OutputStreamWriter(baos);
            LOGGER.debug("Applying template: {}", path);
            Environment env = tpl.createProcessingEnvironment(model, writer);
            if (session != null) {
                for (Entry<String, TemplateDirectiveModel> directive : session.directives().entrySet()) {
                    env.setVariable(directive.getKey(), directive.getValue());
                }
            }
            env.setVariable("helper", new HelperHashModel(OBJECT_WRAPPER));
            env.setVariable("passthroughfix", new PassthroughFixDirective());
            env.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            env.setLogTemplateExceptions(false);
            env.process();
            return baos.toString(StandardCharsets.UTF_8);
        } catch (TemplateNotFoundException ex) {
            LOGGER.warn("Unable to find template: {}", path);
            return "";
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (TemplateException ex) {
            throw new FreemarkerRenderingException(path, ex);
        }
    }

    /**
     * Freemarker rendering exception.
     */
    public static final class FreemarkerRenderingException extends RenderingException {

        FreemarkerRenderingException(String template, Throwable cause) {
            super(String.format("An error occurred while rendering '%s'", template),
                    RenderingException.cause(cause));
        }
    }

    /**
     * A builder of {@link FreemarkerEngine}.
     */
    @SuppressWarnings("unused")
    public static final class Builder {

        private final Map<String, String> directives = new HashMap<>();
        private final Map<String, String> model = new HashMap<>();
        private final String backend;

        private Builder(String backend) {
            this.backend = backend;
        }

        /**
         * Add custom directives.
         *
         * @param directives the directives to set
         * @return this builder
         */
        public Builder directives(Map<String, String> directives) {
            if (directives != null) {
                this.directives.putAll(directives);
            }
            return this;
        }

        /**
         * Add some custom model.
         *
         * @param model the model to set
         * @return this builder
         */
        public Builder model(Map<String, String> model) {
            if (model != null) {
                this.model.putAll(model);
            }
            return this;
        }

        /**
         * Apply the specified configuration.
         *
         * @param config config
         * @return this builder
         */
        public Builder config(Config config) {
            directives.putAll(config.get("directives")
                                    .asMap(String.class)
                                    .orElseGet(Map::of));
            model.putAll(config.get("model")
                               .asMap(String.class)
                               .orElseGet(Map::of));
            return this;
        }

        /**
         * Build the instance.
         *
         * @return new instance
         */
        public FreemarkerEngine build() {
            return new FreemarkerEngine(this);
        }
    }

    /**
     * Create a new instance from configuration.
     *
     * @param backend backend name
     * @param config  config
     * @return new instance
     */
    public static FreemarkerEngine create(String backend, Config config) {
        return builder(backend).config(config).build();
    }

    /**
     * Create a new instance.
     *
     * @param backend backend name
     * @return new instance
     */
    public static FreemarkerEngine create(String backend) {
        return builder(backend).build();
    }

    /**
     * Create a new builder.
     *
     * @param backend backend name
     * @return new builder
     */
    public static Builder builder(String backend) {
        return new Builder(backend);
    }
}
