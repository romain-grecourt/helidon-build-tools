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

import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextBooleanNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextEnumNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextTextNode;
import io.helidon.build.archetype.engine.v2.ast.Node;

import static java.util.stream.Collectors.joining;

final class ContextConvertor implements Visitor<Node, String> {

    @Override
    public String visit(ContextBooleanNode input, Node arg) {
        return String.valueOf(input.value());
    }

    @Override
    public String visit(ContextEnumNode input, Node arg) {
        return input.value();
    }

    @Override
    public String visit(ContextListNode input, Node arg) {
        return input.value().stream().collect(joining("', '", "['", "']"));
    }

    @Override
    public String visit(ContextTextNode input, Node arg) {
        String text = input.value();
        if (text.startsWith("'") && text.endsWith("'")) {
            return text;
        }
        return "'" + text + "'";
    }
}
