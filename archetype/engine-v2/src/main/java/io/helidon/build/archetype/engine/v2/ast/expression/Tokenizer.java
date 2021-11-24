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

package io.helidon.build.archetype.engine.v2.ast.expression;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;

/**
 * Parse the string representation of the expression and extract tokens from it.
 */
final class Tokenizer implements Iterator<Token> {

    private final String line;
    private int cursor;

    /**
     * Create a new tokenizer.
     *
     * @param line line
     */
    Tokenizer(String line) {
        this.line = line;
        this.cursor = 0;
    }

    @Override
    public boolean hasNext() {
        return cursor < line.length();
    }

    @Override
    public Token next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        String current = line.substring(cursor);
        for (Token.Type type : Token.Type.values()) {
            Matcher matcher = type.pattern().matcher(current);
            if (matcher.find()) {
                String value = matcher.group();
                cursor += value.length();
                if (type == Token.Type.SKIP) {
                    return next();
                }
                return new Token(type, value);
            }
        }
        throw new NoSuchElementException("Unexpected token - " + current);
    }
}
