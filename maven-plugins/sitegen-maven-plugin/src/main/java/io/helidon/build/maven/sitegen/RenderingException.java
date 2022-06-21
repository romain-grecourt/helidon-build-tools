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

package io.helidon.build.maven.sitegen;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.stream.Collectors;

import freemarker.template.TemplateException;

/**
 * An exception to represent any error occurring as part of site processing.
 */
public class RenderingException extends RuntimeException {

    /**
     * Create a new instance.
     *
     * @param msg exception message
     */
    protected RenderingException(String msg) {
        super(msg);
    }

    /**
     * Create a new instance.
     *
     * @param msg   exception message
     * @param cause cause
     */
    protected RenderingException(String msg, Throwable cause) {
        super(msg, cause);
        setStackTrace(filteredStackTrace(getStackTrace()));
    }

    /**
     * Create a new instance.
     *
     * @param msg   exception message
     * @param cause cause
     * @return new instance
     */
    public static RenderingException create(String msg, Throwable cause) {
        Deque<Throwable> causes = new ArrayDeque<>();
        while (cause != null) {
            causes.push(cause);
            cause = cause.getCause();
        }
        while (!causes.isEmpty()) {
            cause = causes.pop();
            StackTraceElement[] ste = filteredStackTrace(cause.getStackTrace());
            if (cause instanceof TemplateException) {
                cause = new RenderingException(filterMsg(cause.getMessage()));
            }
            cause.setStackTrace(ste);
        }
        return new RenderingException(msg, cause);
    }

    private static String filterMsg(String message) {
        return Arrays.stream(message.split("\\R"))
                     .limit(2).collect(Collectors.joining(System.lineSeparator()));
    }

    private static StackTraceElement[] filteredStackTrace(StackTraceElement[] elements) {
        return Arrays.stream(elements)
                     .filter(RenderingException::filterStackTrace)
                     .toArray(StackTraceElement[]::new);
    }

    private static boolean filterStackTrace(StackTraceElement elt) {
        return !elt.getClassName().startsWith("org.jruby")
                && (elt.getFileName() == null || !elt.getFileName().endsWith(".rb"));
    }
}
