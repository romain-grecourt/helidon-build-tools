package io.helidon.build.archetype.engine.v2.ast;

import io.helidon.build.archetype.engine.v2.descriptor.ArchetypeDescriptor;
import io.helidon.build.archetype.engine.v2.descriptor.Context;
import io.helidon.build.archetype.engine.v2.descriptor.ContextBlock;
import io.helidon.build.archetype.engine.v2.descriptor.ContextBoolean;
import io.helidon.build.archetype.engine.v2.descriptor.ContextEnum;
import io.helidon.build.archetype.engine.v2.descriptor.ContextList;
import io.helidon.build.archetype.engine.v2.descriptor.ContextText;
import io.helidon.build.archetype.engine.v2.descriptor.Exec;
import io.helidon.build.archetype.engine.v2.descriptor.FileSet;
import io.helidon.build.archetype.engine.v2.descriptor.FileSets;
import io.helidon.build.archetype.engine.v2.descriptor.Input;
import io.helidon.build.archetype.engine.v2.descriptor.InputBlock;
import io.helidon.build.archetype.engine.v2.descriptor.InputBoolean;
import io.helidon.build.archetype.engine.v2.descriptor.InputEnum;
import io.helidon.build.archetype.engine.v2.descriptor.InputList;
import io.helidon.build.archetype.engine.v2.descriptor.InputOption;
import io.helidon.build.archetype.engine.v2.descriptor.InputText;
import io.helidon.build.archetype.engine.v2.descriptor.Model;
import io.helidon.build.archetype.engine.v2.descriptor.ModelKeyedList;
import io.helidon.build.archetype.engine.v2.descriptor.ModelKeyedMap;
import io.helidon.build.archetype.engine.v2.descriptor.ModelKeyedValue;
import io.helidon.build.archetype.engine.v2.descriptor.ModelList;
import io.helidon.build.archetype.engine.v2.descriptor.ModelMap;
import io.helidon.build.archetype.engine.v2.descriptor.ModelValue;
import io.helidon.build.archetype.engine.v2.descriptor.Output;
import io.helidon.build.archetype.engine.v2.descriptor.Source;
import io.helidon.build.archetype.engine.v2.descriptor.Step;
import io.helidon.build.archetype.engine.v2.descriptor.Template;
import io.helidon.build.archetype.engine.v2.descriptor.Templates;
import io.helidon.build.archetype.engine.v2.descriptor.Transformation;
import io.helidon.build.archetype.engine.v2.expression.Expression;
import io.helidon.build.archetype.engine.v2.interpreter.Visitor;

import java.util.List;
import java.util.Objects;

/**
 * {@link DescriptorNode} implementations.
 */
public final class DescriptorNodes {

    private DescriptorNodes() {
    }

    /**
     * Archetype node.
     */
    public static final class ArchetypeNode extends DescriptorNode<ArchetypeDescriptor> {

        ArchetypeNode(ArchetypeDescriptor desc, Location location) {
            super(desc, null, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Context block node.
     */
    public static final class ContextBlockNode extends DescriptorNode<ContextBlock> {

        public ContextBlockNode() {
            super(null, null, Location.create());
        }

        public ContextBlockNode(ContextBlock desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Context boolean node.
     */
    public static final class ContextBooleanNode extends ContextNode<ContextBoolean> {

        private boolean value;

        ContextBooleanNode(ContextBoolean desc, Node parent, Location location) {
            super(desc, desc.path(), parent, location);
        }

        /**
         * Set the value.
         *
         * @param value value
         */
        public void value(boolean value) {
            this.value = value;
        }

        /**
         * Get the value.
         *
         * @return value
         */
        public boolean value() {
            return value;
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Context enum node.
     */
    public static final class ContextEnumNode extends ContextNode<ContextEnum> {

        private String value;

        public ContextEnumNode(String path, String value){
            super(null, path, null, Location.create());
            this.value = value;
        }

        ContextEnumNode(ContextEnum desc, Node parent, Location location) {
            super(desc, desc.path(), parent, location);
        }

        /**
         * Set the value.
         *
         * @param value value
         */
        public void value(String value) {
            this.value = value;
        }

        /**
         * Get the value.
         *
         * @return value
         */
        public String value() {
            return value;
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Context list node.
     */
    public static final class ContextListNode extends ContextNode<ContextList> {

        private List<String> value;

        public ContextListNode(String path, List<String> value) {
            super(null, path, null, Location.create());
            this.value = value;
        }

        ContextListNode(ContextList desc, Node parent, Location location) {
            super(desc, desc.path(), parent, location);
        }

        /**
         * Set the value.
         *
         * @param value value
         */
        public void value(List<String> value) {
            this.value = value;
        }

        /**
         * Get the value.
         *
         * @return value
         */
        public List<String> value() {
            return value;
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Base class for context nodes.
     *
     * @param <T> descriptor type
     */
    public abstract static class ContextNode<T extends Context> extends DescriptorNode<T> {

        private final String path;

        ContextNode(T desc, String path, Node parent, Location location) {
            super(desc, parent, location);
            this.path = Objects.requireNonNull(path, "path is null");
        }

        /**
         * Get the context path for this node.
         *
         * @return path
         */
        public String path() {
            return path;
        }
    }

    /**
     * Context text node.
     */
    public static final class ContextTextNode extends ContextNode<ContextText> {

        private String value;

        public ContextTextNode(String value) {
            super(null, null, null, Location.create());
            this.value = value;
        }

        ContextTextNode(ContextText desc, Node parent, Location location) {
            super(desc, desc.path(), parent, location);
            this.value = desc.text();
        }

        /**
         * Get the value.
         *
         * @return value
         */
        public String value() {
            return value;
        }

        /**
         * Set the value.
         *
         * @param value value
         */
        public void value(String value) {
            this.value = value;
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Exec node.
     */
    public static final class ExecNode extends DescriptorNode<Exec> {

        private String help;

        ExecNode(Exec desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        /**
         * Set the help text.
         *
         * @param help text
         */
        public void help(String help) {
            this.help = help;
        }

        /**
         * Get the help text.
         *
         * @return help text
         */
        public String help() {
            return help;
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * FileSet node.
     */
    public static final class FileSetNode extends DescriptorNode<FileSet> {

        FileSetNode(FileSet desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * FileSets node.
     */
    public static final class FileSetsNode extends DescriptorNode<FileSets> implements ConditionalNode {

        private String directory;

        FileSetsNode(FileSets desc, Node parent, Location location) {
            super(desc, parent, location);
            this.directory = desc.directory().orElse(null);
        }

        /**
         * Get the directory of this file set.
         *
         * @return directory optional, never {@code null}
         */
        public String directory() {
            return directory;
        }

        /**
         * Set the directory of this file set.
         *
         * @param directory new directory
         */
        public void directory(String directory) {
            this.directory = directory;
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * {@code If} statement node.
     */
    public static final class IfStatementNode extends Node {

        private final Expression expression;

        IfStatementNode(String ifExpression, Node parent, Location location) {
            super(parent, location);
            expression = Expression.builder().expression(ifExpression).build();
        }

        /**
         * Return logical expression.
         *
         * @return Expression
         */
        public Expression expression() {
            return expression;
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Input block node.
     */
    public static final class InputBlockNode extends DescriptorNode<InputBlock> {

        InputBlockNode(InputBlock desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Boolean input node.
     */
    public static final class InputBooleanNode extends InputNode<InputBoolean> {

        public InputBooleanNode(InputBoolean desc, Node parent, Location location) {
            super(desc, desc.help(), parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Input enum node.
     */
    public static final class InputEnumNode extends InputNode<InputEnum> {

        public InputEnumNode(InputEnum desc, Node parent, Location location) {
            super(desc, desc.help(), parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Input list node.
     */
    public static final class InputListNode extends InputNode<InputList> {

        public InputListNode(InputList desc, Node parent, Location location) {
            super(desc, desc.help(), parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Base class for input nodes.
     */
    public abstract static class InputNode<T extends Input> extends DescriptorNode<T> {

        private String defaultValue;
        private final String help;

        InputNode(T desc, String help, Node parent, Location location) {
            super(desc, parent, location);
            this.help = help;
            this.defaultValue = desc.defaultValue();
        }

        /**
         * Get the help.
         *
         * @return help
         */
        public String help() {
            return help;
        }

        /**
         * Get the default value.
         *
         * @return default value
         */
        public String defaultValue() {
            return defaultValue;
        }

        /**
         * Set the default value.
         *
         * @param defaultValue defaultValue
         */
        public void defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        /**
         * Get the flow context path.
         *
         * @return path
         */
        public String path() {
            StringBuilder path = new StringBuilder(descriptor().name());
            Node parent = parent();
            while (parent != null) {
                if (parent.parent() instanceof InputNode) {
                    InputNode<? extends Input> inputNode = (InputNode<? extends Input>) parent.parent();
                    path.insert(0, inputNode.descriptor().name() + ".");
                }
                parent = parent.parent();
            }
            return path.toString();
        }
    }

    /**
     * Input option node.
     */
    public static final class InputOptionNode extends DescriptorNode<InputOption> {

        private String value;

        InputOptionNode(InputOption desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        /**
         * Set the value.
         *
         * @param value value
         */
        public void value(String value) {
            this.value = value;
        }

        /**
         * Get the value.
         *
         * @return value
         */
        public String value() {
            return value;
        }

        public boolean is(ContextEnumNode node) {
            return value.equals(node.value);
        }

        public boolean is(ContextListNode node) {
            return value.equals(node.value);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Input text node.
     */
    public static final class InputTextNode extends InputNode<InputText> {

        private String placeHolder;

        public InputTextNode(InputText desc, Node parent, Location location) {
            super(desc, desc.help(), parent, location);
            this.placeHolder = desc.placeHolder();
        }

        /**
         * Get the placeholder (default value).
         *
         * @return placeholder
         */
        public String placeHolder() {
            return placeHolder;
        }

        /**
         * Set the placeholder (default value).
         *
         * @param placeHolder placeholder
         */
        public void placeHolder(String placeHolder) {
            this.placeHolder = placeHolder;
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Model list node with a key.
     */
    public static final class ModelKeyedListNode extends ModelListNode<ModelKeyedList> implements ConditionalNode {

        ModelKeyedListNode(ModelKeyedList desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }

    }

    /**
     * Model map node with a key.
     */
    public static final class ModelKeyedMapNode extends ModelMapNode<ModelKeyedMap> {

        ModelKeyedMapNode(ModelKeyedMap desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Model value node with a key.
     */
    public static final class ModelKeyedValueNode extends ModelValueNode<ModelKeyedValue> {

        ModelKeyedValueNode(ModelKeyedValue desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Model list node.
     */
    public static class ModelListNode<T extends ModelList> extends DescriptorNode<T> implements ConditionalNode {

        ModelListNode(T desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Model map node.
     */
    public static class ModelMapNode<T extends ModelMap> extends DescriptorNode<T> implements ConditionalNode {

        ModelMapNode(T desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Model node.
     */
    public static final class ModelNode extends DescriptorNode<Model> implements ConditionalNode {

        ModelNode(Model desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Model value node.
     */
    public static class ModelValueNode<T extends ModelValue> extends DescriptorNode<T> implements ConditionalNode {

        private String value;

        ModelValueNode(T desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        /**
         * Set the value.
         *
         * @param value value
         */
        public void value(String value) {
            this.value = value;
        }

        /**
         * Get the value.
         *
         * @return value
         */
        public String value() {
            return value;
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Output node.
     */
    public static final class OutputNode extends DescriptorNode<Output> implements ConditionalNode {

        OutputNode(Output output, Node parent, Location location) {
            super(output, parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Source node.
     */
    public static final class SourceNode extends DescriptorNode<Source> {

        private String help;

        SourceNode(Source desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        /**
         * Get the help.
         *
         * @return help
         */
        public String help() {
            return help;
        }

        /**
         * Set the help content.
         *
         * @param help help content
         */
        public void help(String help) {
            this.help = help;
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Step nodes.
     */
    public static final class StepNode extends DescriptorNode<Step> implements ConditionalNode {

        StepNode(Step desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Template node.
     */
    public static final class TemplateNode extends DescriptorNode<Template> implements ConditionalNode {

        TemplateNode(Template desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Templates node.
     */
    public static final class TemplatesNode extends DescriptorNode<Templates> implements ConditionalNode {

        private String directory;

        TemplatesNode(Templates desc, Node parent, Location location) {
            super(desc, parent, location);
            this.directory = desc.directory();
        }

        /**
         * Get the directory.
         *
         * @return directory
         */
        public String directory() {
            return directory;
        }

        /**
         * Set the directory.
         *
         * @param directory new directory
         */
        public void directory(String directory) {
            this.directory = directory;
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }

    /**
     * Transformation node.
     */
    public final static class TransformationNode extends DescriptorNode<Transformation> {

        TransformationNode(Transformation desc, Node parent, Location location) {
            super(desc, parent, location);
        }

        @Override
        public <A, R> R accept(Visitor<A, R> visitor, A arg) {
            return visitor.visit(this, arg);
        }
    }
}
