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
import java.io.IOException;
import java.util.List;

import io.helidon.build.userflow.Expression.ParserException;
import freemarker.template.TemplateException;
import java.io.FileInputStream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Goal to process user flow templates.
 */
@Mojo(name = "processor",
      defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
      requiresProject = true)
public class UserFlowProcessMojo extends AbstractMojo {

    private static final String PROPERTY_PREFIX = "userflow.";

    /**
     * Directory containing the output files.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File outputDirectory;

    /**
     * The user-flow descriptor.
     */
    @Parameter(required = true)
    private File descriptor;

    /**
     * Flag to indicate if the flow bash includes should be generated.
     */
    @Parameter(defaultValue = "true")
    private boolean bashIncludes;

    /**
     * Flag to indicate if the flow bat includes should be generated.
     */
    @Parameter(defaultValue = "false")
    private boolean batIncludes;

    /**
     * Templates to process.
     */
    @Parameter(required = true)
    private List<File> templates;

    /**
     * Skip this goal execution.
     */
    @Parameter(property = PROPERTY_PREFIX + "processSkip",
            defaultValue = "false",
            required = false)
    private boolean generateSkip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (generateSkip) {
            getLog().info("processing is skipped.");
            return;
        }

        if (!descriptor.exists()) {
            throw new MojoFailureException("User flow descriptor does not exist: " + descriptor.getAbsolutePath());
        }

        if (templates.isEmpty()) {
            getLog().warn("No templates files to process");
            return;
        }

        try {
            UserFlow flow = UserFlow.create(new FileInputStream(descriptor));
            UserFlowProcessor processor = new UserFlowProcessor(flow);
            if (bashIncludes) {
                processor.bashIncludes();
            }
            if (batIncludes) {
                processor.batIncludes();
            }
            getLog().info("Processing user flow templates");
            for (File template : templates) {
                if (!template.exists()) {
                    throw new MojoFailureException("template does not exist: " + template.getAbsolutePath());
                }
                getLog().info("Processing template: " + template);
                File outputFile = new File(outputDirectory, template.getName().replace(".ftl", ""));
                FileOutputStream fos = new FileOutputStream(outputFile);
                processor.process(template.toURI().toURL(), fos);
            }
        } catch (ParserException ex) {
            throw new MojoExecutionException("An error occurred whiled parsing the user flow descriptor", ex);
        } catch (IOException ex) {
            throw new MojoFailureException(ex.getMessage(), ex);
        } catch (TemplateException ex) {
            throw new MojoExecutionException("An error occurred during the rendering", ex);
        }
    }
}
