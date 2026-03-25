/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.build.common;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static io.helidon.build.common.FileUtils.requireJavaExecutable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link ProcessMonitor}.
 */
class ProcessMonitorTest {

    @Test
    void testWaitForCompletionAfterExit() throws Exception {
        ProcessMonitor monitor = ProcessMonitor.builder()
                .processBuilder(new ProcessBuilder(requireJavaExecutable().toString(), "--bad-option"))
                .capture(true)
                .build()
                .start();

        waitUntilExited(monitor);

        ProcessMonitor.ProcessFailedException ex = assertThrows(ProcessMonitor.ProcessFailedException.class,
                () -> monitor.waitForCompletion(10, TimeUnit.SECONDS));
        assertThat(ex.monitor().output(), not(is("")));
    }

    private static void waitUntilExited(ProcessMonitor monitor) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (monitor.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(monitor.isAlive(), is(false));
    }
}
