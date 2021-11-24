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

package io.helidon.build.archetype.engine.v2.expression;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * String array.
 */
final class StringArrayLiteral extends Literal<List<String>> {

    private static final Pattern ELEMENT_PATTERN = Pattern.compile("(?<element>'[^']*')((\\s*,\\s*)|(\\s*]))");

    /**
     * Create a new literal that represents a string array.
     *
     * @param content the raw literal value.
     */
    StringArrayLiteral(String content) {
        super(ELEMENT_PATTERN.matcher(content)
                             .results()
                             .map(r -> r.group())
                             .collect(Collectors.toList()));
    }

    @Override
    Type type() {
        return Type.ARRAY;
    }
}
