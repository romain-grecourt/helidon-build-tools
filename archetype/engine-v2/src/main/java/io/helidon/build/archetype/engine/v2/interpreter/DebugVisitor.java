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
import io.helidon.build.archetype.engine.v2.ast.Node;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class DebugVisitor implements Visitor<Node, Void> {

    private static final Logger LOGGER = Logger.getLogger(DebugVisitor.class.getName());
    private final boolean showVisits;
    private final Set<Node> visitedNodes = new HashSet<>();

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s %n");
    }

    DebugVisitor(boolean showVisits) {
        this.showVisits = showVisits;
    }

    @Override
    public Void visit(InputTextNode input, Node parent) {
        String message = String.format("%s InputText {path=\"%s\"; label=\"%s\"}",
                indent(input),
                input.path(),
                input.descriptor().label());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(StepNode input, Node parent) {
        String message = String.format("%s Step {label=\"%s\"}",
                indent(input),
                input.descriptor().label());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(InputBlockNode input, Node parent) {
        String message = String.format("%s Input", indent(input));
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(DescriptorNodes.InputBooleanNode input, Node parent) {
        String message = String.format("%s InputBoolean {path=\"%s\"; label=\"%s\"}",
                indent(input),
                input.path(),
                input.descriptor().label());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(InputEnumNode input, Node parent) {
        String message = String.format("%s InputEnum {path=\"%s\"; label=\"%s\"}",
                indent(input),
                input.path(),
                input.descriptor().label());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(InputListNode input, Node parent) {
        String message = String.format("%s InputList {path=\"%s\"; label=\"%s\"}",
                indent(input),
                input.path(),
                input.descriptor().label());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(ExecNode input, Node parent) {
        String message = String.format("%s Exec {src=\"%s\"}",
                indent(input),
                input.descriptor().src());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(SourceNode input, Node parent) {
        String message = String.format("%s Source {source=\"%s\"}",
                indent(input),
                input.descriptor().source());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(ContextBlockNode input, Node parent) {
        if (!input.children().isEmpty()) {
            String message = String.format("%s Context", indent(input));
            processMessage(input, message);
        }
        return null;
    }

    @Override
    public Void visit(ContextBooleanNode input, Node parent) {
        String message = String.format("%s ContextBoolean {path=\"%s\"; bool=%s}",
                indent(input),
                input.path(),
                input.value());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(ContextEnumNode input, Node parent) {
        String message = String.format("%s ContextEnum {path=\"%s\"; value=\"%s\"}",
                indent(input),
                input.path(),
                input.value());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(ContextListNode input, Node parent) {
        String message = String.format("%s ContextList {path=\"%s\"; values=\"%s\"}",
                indent(input),
                input.path(),
                input.descriptor().values());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(ContextTextNode input, Node parent) {
        String message = String.format("%s ContextText {path=\"%s\"; text=\"%s\"}",
                indent(input),
                input.path(),
                input.value());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(InputOptionNode input, Node parent) {
        String message = String.format("%s Option {label=\"%s\"; value=\"%s\"}",
                indent(input),
                input.descriptor().label(),
                input.descriptor().value());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(OutputNode input, Node parent) {
        String message = String.format("%s Output", indent(input));
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(TransformationNode input, Node parent) {
        String message = String.format("%s Transformation {id=\"%s\"}",
                indent(input),
                input.descriptor().id());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(TemplatesNode input, Node parent) {
        String message = String.format(
                "%s Templates {transformation=\"%s\"; directory=\"%s\"; engine=\"%s\"}",
                indent(input),
                input.descriptor().transformation(),
                input.directory(),
                input.descriptor().engine());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(DescriptorNodes.ModelNode input, Node parent) {
        String message = String.format("%s Model", indent(input));
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(FileSetsNode input, Node parent) {
        String message = String.format("%s FileSets {directory=\"%s\"}",
                indent(input),
                input.directory());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(FileSetNode input, Node parent) {
        String message = String.format(
                "%s FileSet {source=\"%s\"; target=\"%s\"}",
                indent(input),
                input.descriptor().source(),
                input.descriptor().target());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(ModelKeyedValueNode input, Node parent) {
        String message = String.format("%s ModelKeyValue {key=\"%s\"; order=\"%s\"}",
                indent(input),
                input.descriptor().key(),
                input.descriptor().order());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(ModelKeyedListNode input, Node parent) {
        String message = String.format(
                "%s ModelKeyList {key=\"%s\"; order=\"%s\"}",
                indent(input),
                input.descriptor().key(),
                input.descriptor().order());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(ModelKeyedMapNode input, Node parent) {
        String message = String.format("%s ModelKeyMap {key=\"%s\"; order=\"%s\"}",
                indent(input),
                input.descriptor().key(),
                input.descriptor().order());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(TemplateNode input, Node parent) {
        String message = String.format("%s Template {engine=\"%s\"; source=\"%s\"; target=\"%s\"}",
                indent(input),
                input.descriptor().engine(),
                input.descriptor().source(),
                input.descriptor().target());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(ModelValueNode<?> input, Node parent) {
        String message = String.format("%s ModelValue {file=\"%s\"; url=\"%s\"; template=\"%s\"}",
                indent(input),
                input.descriptor().file(),
                input.descriptor().url(),
                input.descriptor().template());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(ModelMapNode<?> input, Node parent) {
        String message = String.format("%s ModelMap {order=\"%s\"}",
                indent(input),
                input.descriptor().order());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(ModelListNode<?> input, Node parent) {
        String message = String.format("%s ModelList {order=\"%s\"}",
                indent(input),
                input.descriptor().order());
        processMessage(input, message);
        return null;
    }

    @Override
    public Void visit(IfStatementNode input, Node parent) {
        String message = String.format("%s IfStatement {expression=\"%s\"}",
                indent(input),
                input.expression().expression());
        processMessage(input, message);
        return null;
    }

    private void processMessage(Node input, String message) {
        if (showVisits) {
            LOGGER.info(message);
            return;
        }
        if (!visitedNodes.contains(input)) {
            LOGGER.info(message);
            visitedNodes.add(input);
        }
    }

    /**
     * Get the {@code showVisits} flag.
     *
     * @return showVisits
     */
    public boolean showVisits() {
        return showVisits;
    }

    private String indent(Node input) {
        return " ".repeat(getLevel(input, 0));
    }

    private int getLevel(Node input, int startLevel) {
        if (input.parent() == null) {
            return startLevel;
        } else {
            return getLevel(input.parent(), startLevel + 1);
        }
    }

    /**
     * Create a new builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new default debug visitor.
     *
     * @return visitor
     */
    public static DebugVisitor create() {
        return builder().build();
    }

    /**
     * {@code DebuggerVisitor} builder static inner class.
     */
    public static final class Builder {

        private boolean showVisits = false;

        private Builder() {
        }

        /**
         * Sets the {@code archetype} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param showVisits the {@code showVisits} to set
         * @return a reference to this Builder
         */
        public Builder archetype(boolean showVisits) {
            this.showVisits = showVisits;
            return this;
        }

        /**
         * Returns a {@code DebuggerVisitor} built from the parameters previously set.
         *
         * @return a {@code DebuggerVisitor} built with parameters of this {@code Builder}
         */
        public DebugVisitor build() {
            return new DebugVisitor(showVisits);
        }
    }

}
