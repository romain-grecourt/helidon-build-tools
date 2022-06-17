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

/**
 * An exception to represent any error occurring as part of site processing.
 */
public class RenderingException extends RuntimeException {

    /**
     * Create a new instance.
     *
     * @param msg exception message
     */
    @SuppressWarnings("unused")
    public RenderingException(String msg) {
        super(msg);
    }

    /**
     * Create a new instance.
     *
     * @param msg   exception message
     * @param cause cause
     */
    public RenderingException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
