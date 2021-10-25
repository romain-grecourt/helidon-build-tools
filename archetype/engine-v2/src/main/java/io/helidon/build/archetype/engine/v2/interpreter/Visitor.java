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

import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ArchetypeNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextBlockNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextBooleanNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextEnumNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ContextTextNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ExecNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.FileSetNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.FileSetsNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.IfStatementNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputBlockNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputBooleanNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputEnumNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputOptionNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.InputTextNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelKeyedListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelKeyedMapNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelKeyedValueNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelListNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelMapNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.ModelValueNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.OutputNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.SourceNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.StepNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.TemplateNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.TemplatesNode;
import io.helidon.build.archetype.engine.v2.ast.DescriptorNodes.TransformationNode;
import io.helidon.build.archetype.engine.v2.ast.UserInputNode;

/**
 * Visitor for the  script interpreter.
 *
 * @param <A> argument
 * @param <R> type of the returned value
 */
public interface Visitor<A, R> {

    /**
     * Visit an archetype.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(ArchetypeNode node, A arg) {
        return null;
    }

    /**
     * Visit a step.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(StepNode node, A arg) {
        return null;
    }

    /**
     * Visit an input block.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(InputBlockNode node, A arg) {
        return null;
    }

    /**
     * Visit an input boolean.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(InputBooleanNode node, A arg) {
        return null;
    }

    /**
     * Visit an input enum.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(InputEnumNode node, A arg) {
        return null;
    }

    /**
     * Visit an input list.
     *
     * @param node node
     * @param arg  argument
     */
    default R visit(InputListNode node, A arg) {
        return null;
    }

    /**
     * Visit an input text.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(InputTextNode node, A arg) {
        return null;
    }

    /**
     * Visit an exec.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(ExecNode node, A arg) {
        return null;
    }

    /**
     * Visit a source.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(SourceNode node, A arg) {
        return null;
    }

    /**
     * Visit a context block.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(ContextBlockNode node, A arg) {
        return null;
    }

    /**
     * Visit a context boolean.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(ContextBooleanNode node, A arg) {
        return null;
    }

    /**
     * Visit a context enum.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(ContextEnumNode node, A arg) {
        return null;
    }

    /**
     * Visit a context list.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(ContextListNode node, A arg) {
        return null;
    }

    /**
     * Visit a context text.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(ContextTextNode node, A arg) {
        return null;
    }

    /**
     * Visit an input option.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(InputOptionNode node, A arg) {
        return null;
    }

    /**
     * Visit an output.
     *
     * @param node node
     * @param arg  argument
     */
    default R visit(OutputNode node, A arg) {
        return null;
    }

    /**
     * Visit a transformation.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(TransformationNode node, A arg) {
        return null;
    }

    /**
     * Visit a filesets.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(FileSetsNode node, A arg) {
        return null;
    }

    /**
     * Visit a fileset.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(FileSetNode node, A arg) {
        return null;
    }

    /**
     * Visit a template.
     *
     * @param node node
     * @param arg  argument
     */
    default R visit(TemplateNode node, A arg) {
        return null;
    }

    /**
     * Visit a templates.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(TemplatesNode node, A arg) {
        return null;
    }

    /**
     * Visit model.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(DescriptorNodes.ModelNode node, A arg) {
        return null;
    }

    /**
     * Visit an if statement.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(IfStatementNode node, A arg) {
        return null;
    }

    /**
     * Visit a model keyed value.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(ModelKeyedValueNode node, A arg) {
        return null;
    }

    /**
     * Visit a model key value.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(ModelValueNode<?> node, A arg) {
        return null;
    }

    /**
     * Visit a model keyed list.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(ModelKeyedListNode node, A arg) {
        return null;
    }

    /**
     * Visit a model map.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(ModelMapNode<?> node, A arg) {
        return null;
    }

    /**
     * Visit a model list.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(ModelListNode<?> node, A arg) {
        return null;
    }

    /**
     * Visit a model keyed map.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(ModelKeyedMapNode node, A arg) {
        return null;
    }

    /**
     * Visit a user input.
     *
     * @param node node
     * @param arg  argument
     * @return visit result
     */
    default R visit(UserInputNode node, A arg) {
        return null;
    }
}
