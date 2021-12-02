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

package io.helidon.build.archetype.engine.v2;

import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Value;

/**
 * Input resolver.
 */
public abstract class InputResolver implements Input.Visitor<Context> {

    protected Value defaultValue(Input.NamedInput input, Context context) {
        String path = context.peek();
        if (path != null) {
            path += "." + input.name();
        } else {
            path = input.name();
        }
        Value defaultValue = context.getDefault(path);
        if (defaultValue == null) {
            defaultValue = input.defaultValue();
        }
        return defaultValue;
    }

    // TODO provide logic to control option traversal
    // i.e. only traverse selected options for enum and list

    // TODO unit test
}
