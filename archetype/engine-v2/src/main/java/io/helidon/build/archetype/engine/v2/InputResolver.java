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
import io.helidon.build.archetype.engine.v2.ast.Input.NamedInput;
import io.helidon.build.archetype.engine.v2.ast.Input.Option;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Value;

/**
 * Input resolver.
 */
public class InputResolver implements Input.Visitor<Context> {

    private NamedInput lastVisited;

    /**
     * Invoked for every named input visit.
     *
     * @param input   input
     * @param context context
     * @return {@code true} if the input is resolved, {@code false} otherwise
     */
    protected boolean onVisitInput(NamedInput input, Context context) {
        lastVisited = input;
        return context.push(input.name());
    }

    /**
     * Compute the default value for an input.
     *
     * @param input   input
     * @param context context
     * @return default value or {@code null} if none
     */
    protected Value defaultValue(NamedInput input, Context context) {
        Value defaultValue = context.defaultValue(input.name());
        if (defaultValue == null) {
            defaultValue = input.defaultValue();
        }
        return defaultValue;
    }

    @Override
    public VisitResult visitBoolean(Input.Boolean input, Context context) {
        return visit(input, context);
    }

    @Override
    public VisitResult visitText(Input.Text input, Context context) {
        return visit(input, context);
    }

    @Override
    public VisitResult visitEnum(Input.Enum input, Context context) {
        return visit(input, context);
    }

    @Override
    public VisitResult visitList(Input.List input, Context context) {
        return visit(input, context);
    }

    /**
     * Default visit implementation.
     *
     * @param input   input
     * @param context context
     * @return result
     */
    protected VisitResult visit(NamedInput input, Context context) {
        if (onVisitInput(input, context)) {
            return VisitResult.CONTINUE;
        }
        return VisitResult.SKIP_SUBTREE;
    }

    @Override
    public VisitResult visitOption(Option option, Context context) {
        if (lastVisited == null) {
            throw new IllegalStateException("lastVisited must be non null");
        }
        Context.ContextValue inputValue = context.lookup(lastVisited.name());
        if (inputValue != null) {
            if (lastVisited instanceof Input.List) {
                if (inputValue.asList().contains(option.value())) {
                    return VisitResult.CONTINUE;
                }
            } else if (inputValue.asString().equals(option.value())) {
                return VisitResult.SKIP_SIBLINGS;
            }
        }
        return VisitResult.SKIP_SUBTREE;
    }

    @Override
    public VisitResult postVisitAny(Input input, Context context) {
        if (!(input instanceof Option)) {
            context.pop();
        }
        return VisitResult.CONTINUE;
    }
}
