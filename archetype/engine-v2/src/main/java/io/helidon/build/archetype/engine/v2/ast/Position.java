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

package io.helidon.build.archetype.engine.v2.ast;

/**
 * Source position.
 */
public final class Position {

    private final int lineNo;
    private final int charNo;

    /**
     * Create a new position.
     *
     * @param lineNo line number
     * @param charNo line character number
     */
    public Position(int lineNo, int charNo) {
        this.lineNo = lineNo;
        this.charNo = charNo;
    }

    /**
     * Get the current line number.
     *
     * @return line number
     */
    public int lineNumber() {
        return lineNo;
    }

    /**
     * Get the current line character number.
     *
     * @return line character number
     */
    public int charNumber() {
        return charNo;
    }

    @Override
    public String toString() {
        return lineNo + ":" + charNo;
    }
}
