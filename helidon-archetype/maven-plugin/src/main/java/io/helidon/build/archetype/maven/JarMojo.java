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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * {@code archetype:jar} mojo.
 */
@Mojo(name = "jar")
public class JarMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // TODO list
        // 1. convert META-INF/helidon-archetype.xml to META-INF/maven/archetype-metadata.xml, include both files
        // 2. include a META-INF/archetype-post-generate.groovy that triggers the helidon archetype engine
        // 3. include a MANIFEST.MF entry to retain the version of the engine used to build the archetype
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
