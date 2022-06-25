/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.build.maven.sitegen.asciidoctor;

import java.util.logging.Handler;

import io.helidon.build.maven.sitegen.RenderingException;

import org.asciidoctor.log.LogHandler;
import org.asciidoctor.log.LogRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom log handler.
 */
final class AsciidocLogHandler implements LogHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsciidocLogHandler.class);

    @Override
    public void log(LogRecord logRecord) {
        String message = logRecord.getMessage();
        switch (logRecord.getSeverity()) {
            case DEBUG:
                LOGGER.debug(message);
                break;
            case WARN:
            case ERROR:
            case FATAL:
                throw new AsciidocLoggedException(message);
            default:
                if (message.startsWith("possible invalid reference")) {
                    throw new AsciidocLoggedException(message);
                }
                LOGGER.info(message);
        }
    }

    /**
     * One time setup.
     */
    static void setup() {
        java.util.logging.Logger asciidoctorLogger = java.util.logging.Logger.getLogger("asciidoctor");
        asciidoctorLogger.setUseParentHandlers(false);
        asciidoctorLogger.addHandler(new Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        });
    }

    /**
     * Asciidoc warning exception.
     */
    static final class AsciidocLoggedException extends RenderingException {

        private AsciidocLoggedException(String msg) {
            super(msg);
        }
    }
}
