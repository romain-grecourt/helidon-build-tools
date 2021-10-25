/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.build.archetype.engine.v2.interpreter;

/**
 * Script interpreter exception.
 */
public final class InterpreterException extends RuntimeException {

    /**
     * Create a new exception.
     *
     * @param message error message
     */
    public InterpreterException(String message) {
        super(message);
    }

    /**
     * Create a new exception.
     *
     * @param format error message format
     * @param format error message args
     */
    public InterpreterException(String format, Object... args) {
        super(String.format(format, args));
    }

    /**
     * Create a new exception.
     *
     * @param message error message
     */
    InterpreterException(String message, Throwable cause) {
        super(message, cause);
    }
}
