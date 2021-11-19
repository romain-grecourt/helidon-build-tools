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
 * Visitor.
 *
 * @param <T> argument
 */
public interface Visitor<T> {

    /**
     * Visit a script.
     *
     * @param script script
     * @param arg    argument
     * @return visit result
     */
    default VisitResult visitScript(Script script, T arg) {
        return VisitResult.CONTINUE;
    }

    /**
     * Visit a condition.
     *
     * @param condition condition
     * @param arg       argument
     * @return visit result
     */
    default VisitResult visitCondition(Condition condition, T arg) {
        return VisitResult.CONTINUE;
    }

    /**
     * Visit an invocation.
     *
     * @param invocation invocation
     * @param arg        argument
     * @return visit result
     */
    default VisitResult visitInvocation(Invocation invocation, T arg) {
        return VisitResult.CONTINUE;
    }

    /**
     * Visit a preset.
     *
     * @param preset preset
     * @param arg    argument
     * @return visit result
     */
    default VisitResult visitPreset(Preset preset, T arg) {
        return VisitResult.CONTINUE;
    }

    /**
     * Visit a block before traversing the nested statements.
     *
     * @param block block
     * @param arg   argument
     * @return visit result
     */
    default VisitResult preVisitBlock(Block block, T arg) {
        return VisitResult.CONTINUE;
    }

    /**
     * Visit a block after traversing the nested statements.
     *
     * @param block block
     * @param arg   argument
     * @return visit result
     */
    default VisitResult postVisitBlock(Block block, T arg) {
        return VisitResult.CONTINUE;
    }

    /**
     * Visit a noop.
     *
     * @param noop noop
     * @param arg  argument
     * @return visit result
     */
    default VisitResult visitNoop(Noop noop, T arg) {
        return VisitResult.CONTINUE;
    }
}
