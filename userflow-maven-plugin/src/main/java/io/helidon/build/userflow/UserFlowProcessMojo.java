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

import freemarker.template.TemplateException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
     * Project base directory.
     */
    @Parameter(defaultValue = "${project.basedir}", required = true)
    private File baseDirectory;

    /**
     * Directory containing the output files.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File outputDirectory;

    @Parameter(required = true)
    private File descriptor;

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
            throw new MojoFailureException("descriptor does not exist: " + descriptor.getAbsolutePath());
        }

        if (templates.isEmpty()) {
            throw new MojoFailureException("no template files to process");
        }

        UserFlow userFlow;
        try {
             userFlow = UserFlow.create(descriptor);
        } catch (IOException ex) {
            throw new MojoExecutionException("An error occurred whiled creating the user flow model", ex);
        }

        UserFlowProcessor processor;
        try {
            processor = new UserFlowProcessor(userFlow, baseDirectory);
        } catch (IOException ex) {
            throw new MojoExecutionException("An error occurred whiled initializing the template engine", ex);
        }

        try {
            int basedirLen = baseDirectory.getCanonicalPath().length();
            getLog().info("Processing user flow templates");
            for (File template : templates) {
                if (!template.exists()) {
                    throw new MojoFailureException("template does not exist: " + template.getAbsolutePath());
                }
                String path = template.getCanonicalPath();
                if (path.length() <= basedirLen) {
                    throw new MojoFailureException("template file is not inside the project base directory: " + path);
                }
                path = path.substring(basedirLen);
                getLog().info("Processing template: " + path);
                try {
                    processor.process(path);
                    // TODO create output file.
                } catch (TemplateException ex) {
                    throw new MojoExecutionException("An error occurred during the rendering", ex);
                }
            }
        } catch (IOException ex) {
            throw new MojoFailureException(ex.getMessage(), ex);
        }
    }
}
