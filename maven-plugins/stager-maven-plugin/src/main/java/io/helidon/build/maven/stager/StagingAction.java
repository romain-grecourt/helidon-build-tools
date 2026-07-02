/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

package io.helidon.build.maven.stager;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Staging action.
 */
interface StagingAction extends StagingElement, Joinable {

    /**
     * Execute the action.
     *
     * @param ctx  staging context
     * @param dir  directory
     * @param vars substitution variables
     * @return completion stage that is completed when all tasks have been executed
     */
    CompletionStage<Void> execute(StagingContext ctx, Path dir, Map<String, String> vars);

    /**
     * Describe the task.
     *
     * @param dir  stage directory
     * @param vars variables for the current iteration
     * @return String that describes the task
     */
    String toString(Path dir, Map<String, String> vars);
}
