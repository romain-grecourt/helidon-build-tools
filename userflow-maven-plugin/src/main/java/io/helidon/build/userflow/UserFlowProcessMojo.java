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
import java.io.IOException;

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

        try {
            UserFlow flow = UserFlow.create(new FileInputStream(descriptor));
            // TODO
        } catch (IOException ex) {
            throw new MojoFailureException(ex.getMessage(), ex);
        }
    }
}
