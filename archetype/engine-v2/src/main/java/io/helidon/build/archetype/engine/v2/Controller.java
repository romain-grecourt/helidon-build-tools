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

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Condition;
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Output;
import io.helidon.build.archetype.engine.v2.ast.Preset;

/**
 * Controller.
 */
class Controller extends VisitorAdapter<Context> {

    // TODO unit test

    private Controller(Input.Visitor<Context> visitor) {
        super(visitor);
    }

    private Controller(Output.Visitor<Context> visitor) {
        super(visitor);
    }

    private Controller(Model.Visitor<Context> visitor) {
        super(visitor);
    }

    @Override
    public VisitResult visitPreset(Preset preset, Context ctx) {
        ctx.put(preset.path(), preset.value());
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult visitCondition(Condition condition, Context ctx) {
        if (condition.expression().eval(ctx::lookup)) {
            return VisitResult.CONTINUE;
        }
        return VisitResult.SKIP_SUBTREE;
    }

    static void run(Model.Visitor<Context> visitor, Context context, Block block) {
        Controller controller = new Controller(visitor);
        Walker.walk(controller, block, context);
    }

    static void run(Output.Visitor<Context> visitor, Context context, Block block) {
        Controller controller = new Controller(visitor);
        Walker.walk(controller, block, context);
    }

    static void run(Input.Visitor<Context> visitor, Context context, Block block) {
        Controller controller = new Controller(visitor);
        Walker.walk(controller, block, context);
    }
}
