/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.build.archetype.engine;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import javax.xml.bind.JAXB;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Helidon archetype XML descriptor.
 */
@XmlRootElement(name = "archetype-descriptor", namespace = ArchetypeDescriptor.NAMESPACE)
@XmlType(namespace = ArchetypeDescriptor.NAMESPACE,
        propOrder = {"name", "properties", "transformations", "templateSets", "fileSets", "inputFlow"})
public final class ArchetypeDescriptor {

    /**
     * XML namespace.
     */
    public static final String NAMESPACE = "https://archetype.helidon.io";

    private String name;
    private List<Property> properties;
    private List<Transformation> transformations;
    private TemplateSets templateSets;
    private FileSets fileSets;
    private InputFlow inputFlow;

    /**
     * Create a archetype descriptor instance from an input stream.
     *
     * @param is input stream
     * @return ArchetypeDescriptor
     */
    public static ArchetypeDescriptor read(InputStream is) {
        return JAXB.unmarshal(is, ArchetypeDescriptor.class);
    }

    /**
     * Get the descriptor name.
     *
     * @return String
     */
    @XmlElement(name = "name", required = true)
    public String getName() {
        return name;
    }

    /**
     * Set the descriptor name.
     *
     * @param name name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the archetype properties.
     *
     * @return list of {@link Property}
     */
    @XmlElementWrapper(name = "properties", required = true)
    @XmlElement(name = "property", required = true)
    public List<Property> getProperties() {
        return properties;
    }

    /**
     * Set the properties.
     *
     * @param properties property
     */
    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    /**
     * Get the transformations.
     *
     * @return list of {@link Transformation}
     */
    @XmlElementWrapper(name = "transformations")
    @XmlElement(name = "transformation")
    public List<Transformation> getTransformations() {
        return transformations;
    }

    /**
     * Set the transformations.
     *
     * @param transformations transformations
     */
    public void setTransformations(List<Transformation> transformations) {
        this.transformations = transformations;
    }

    /**
     * Get the template sets.
     *
     * @return TemplateSets
     */
    @XmlElement(name = "template-sets")
    public TemplateSets getTemplateSets() {
        return templateSets;
    }

    /**
     * Set the template sets.
     *
     * @param templateSets template sets
     */
    public void setTemplateSets(TemplateSets templateSets) {
        this.templateSets = templateSets;
    }

    /**
     * Get the file sets.
     *
     * @return file sets
     */
    @XmlElement(name = "file-sets")
    public FileSets getFileSets() {
        return fileSets;
    }

    /**
     * Set the file sets.
     *
     * @param fileSets file sets
     */
    public void setFileSets(FileSets fileSets) {
        this.fileSets = fileSets;
    }

    /**
     * Get the input flow.
     *
     * @return input flow
     */
    @XmlElement(name = "input-flow")
    public InputFlow getInputFlow() {
        return inputFlow;
    }

    /**
     * Set the input flow.
     *
     * @param inputFlow input flow
     */
    public void setInputFlow(InputFlow inputFlow) {
        this.inputFlow = inputFlow;
    }

    @XmlType(namespace = NAMESPACE)
    public static final class Property {

        private String id;
        private String description;

        /**
         * Set the property id.
         *
         * @param id id
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * Get the property id.
         *
         * @return String
         */
        @XmlID
        @XmlAttribute(name = "id", required = true)
        public String getId() {
            return id;
        }

        /**
         * Set the property description.
         *
         * @param description property description
         */
        public void setDescription(String description) {
            this.description = description;
        }

        /**
         * get the property description.
         *
         * @return description
         */
        @XmlAttribute(name = "description", required = true)
        public String getDescription() {
            return description;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 29 * hash + Objects.hashCode(this.id);
            hash = 29 * hash + Objects.hashCode(this.description);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Property other = (Property) obj;
            if (!Objects.equals(this.id, other.id)) {
                return false;
            }
            return Objects.equals(this.description, other.description);
        }
    }

    /**
     *  transformation, a pipeline of string replacement operations.
     */
    @XmlType(namespace = NAMESPACE)
    public static final class Transformation {

        private String id;
        private List<Replacement> replacements;

        /**
         * Set the transformation id.
         *
         * @param id id
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * Get the transformation id.
         *
         * @return String
         */
        @XmlID
        @XmlAttribute(name = "id", required = true)
        public String getId() {
            return id;
        }

        /**
         * Get the replacements.
         *
         * @return linked list of {@link Replacement}
         */
        @XmlElement(name = "replace")
        public List<Replacement> getReplacements() {
            return replacements;
        }

        /**
         * Set the replacements.
         *
         * @param replacements replacements
         */
        public void setReplacements(List<Replacement> replacements) {
            this.replacements = replacements;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 41 * hash + Objects.hashCode(this.id);
            hash = 41 * hash + Objects.hashCode(this.replacements);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Transformation other = (Transformation) obj;
            if (!Objects.equals(this.id, other.id)) {
                return false;
            }
            return Objects.equals(this.replacements, other.replacements);
        }
    }

    /**
     * Replace operation for a transformation.
     */
    @XmlType(namespace = NAMESPACE)
    public static final class Replacement {

        private String regex;
        private String replacement;

        /**
         * Get the source regular expression to match the section to be replaced.
         *
         * @return regular expression
         */
        @XmlAttribute(required = true)
        public String getRegex() {
            return regex;
        }

        /**
         * Set the source regular expression.
         *
         * @param regex regular expression
         */
        public void setRegex(String regex) {
            this.regex = regex;
        }

        /**
         * Get the replacement for the matches of the source regular expression.
         *
         * @return replacement
         */
        @XmlAttribute(required = true)
        public String getReplacement() {
            return replacement;
        }

        /**
         * Set the replacement.
         *
         * @param replacement replacement
         */
        public void setReplacement(String replacement) {
            this.replacement = replacement;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 23 * hash + Objects.hashCode(this.regex);
            hash = 23 * hash + Objects.hashCode(this.replacement);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Replacement other = (Replacement) obj;
            if (!Objects.equals(this.regex, other.regex)) {
                return false;
            }
            return Objects.equals(this.replacement, other.replacement);
        }
    }

    /**
     * Base class for conditional nodes.
     */
    @XmlType(namespace = NAMESPACE)
    public abstract static class Conditional {

        private List<Property> ifProperties;
        private List<Property> unlessProperties;

        /**
         * Get the if properties.
         *
         * @return list of properties
         */
        @XmlIDREF
        @XmlAttribute(name = "if")
        public List<Property> getIfProperties() {
            return ifProperties;
        }

        /**
         * Set the {@code if} properties.
         *
         * @param ifProperties {@code if} properties
         */
        public void setIfProperties(List<Property> ifProperties) {
            this.ifProperties = ifProperties;
        }

        /**
         * Get the {@code unless} properties.
         *
         * @return list of properties
         */
        @XmlIDREF
        @XmlAttribute(name = "unless")
        public List<Property> getUnlessProperties() {
            return unlessProperties;
        }

        /**
         * Set the {@code unless} properties.
         *
         * @param unlessProperties {@code unless} properties
         */
        public void setUnlessProperties(List<Property> unlessProperties) {
            this.unlessProperties = unlessProperties;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 17 * hash + Objects.hashCode(this.ifProperties);
            hash = 17 * hash + Objects.hashCode(this.unlessProperties);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Conditional other = (Conditional) obj;
            if (!Objects.equals(this.ifProperties, other.ifProperties)) {
                return false;
            }
            return Objects.equals(this.unlessProperties, other.unlessProperties);
        }
    }

    /**
     * Set of included template files.
     */
    @XmlType(namespace = NAMESPACE)
    public static final class TemplateSets extends Sets {

        private List<FileSet> templateSets;

        /**
         * Get the template sets.
         *
         * @return list of {@link FileSet}
         */
        @XmlElement(name = "template-set")
        public List<FileSet> getTemplateSets() {
            return templateSets;
        }

        /**
         * Set the template sets.
         *
         * @param templateSets template sets
         */
        public void setTemplateSets(List<FileSet> templateSets) {
            this.templateSets = templateSets;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 31 * hash + Objects.hashCode(this.templateSets);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            if (!super.equals((Sets) obj)) {
                return false;
            }
            final TemplateSets other = (TemplateSets) obj;
            return Objects.equals(this.templateSets, other.templateSets);
        }
    }

    /**
     * Set of included template files.
     */
    @XmlType(namespace = NAMESPACE)
    public static final class FileSets extends Sets {

        private List<FileSet> fileSets;

        /**
         * Get the file sets.
         *
         * @return list of {@link FileSet}
         */
        @XmlElement(name = "file-set")
        public List<FileSet> getFileSets() {
            return fileSets;
        }

        /**
         * Set the file sets.
         *
         * @param fileSets file sets
         */
        public void setFileSets(List<FileSet> fileSets) {
            this.fileSets = fileSets;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 31 * hash + Objects.hashCode(this.fileSets);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            if (!super.equals((Sets) obj)) {
                return false;
            }
            final FileSets other = (FileSets) obj;
            return Objects.equals(this.fileSets, other.fileSets);
        }
    }

    /**
     * Base class for {@link TemplateSets} and {@link FileSets}.
     */
    @XmlType(namespace = NAMESPACE)
    public abstract static class Sets {

        private List<Transformation> transformations;

        /**
         * Get the transformations.
         *
         * @return transformations applied to this file sets
         */
        @XmlIDREF
        @XmlAttribute(name = "transformations")
        public List<Transformation> getTransformations() {
            return transformations;
        }

        /**
         * Set the transformations.
         *
         * @param transformations transformations
         */
        public void setTransformations(List<Transformation> transformations) {
            this.transformations = transformations;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 11 * hash + Objects.hashCode(this.transformations);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Sets other = (Sets) obj;
            return Objects.equals(this.transformations, other.transformations);
        }
    }

    /**
     * A list of included files.
     */
    @XmlType(namespace = NAMESPACE)
    public static final class FileSet extends Conditional {

        private List<Transformation> transformations;
        private String directory;
        private List<String> includes;
        private List<String> excludes;

        /**
         * Get the directory of this file set.
         *
         * @return directory
         */
        public String getDirectory() {
            return directory;
        }

        /**
         * Set the directory for this file set.
         *
         * @param directory directory
         */
        public void setDirectory(String directory) {
            this.directory = directory;
        }

        /**
         * Get the exclude filters.
         *
         * @return exclude filters
         */
        @XmlElementWrapper(name = "excludes")
        @XmlElement(name = "exclude")
        public List<String> getExcludes() {
            return excludes;
        }

        /**
         * Set the exclude filters.
         *
         * @param excludes excludes filters
         */
        public void setExcludes(List<String> excludes) {
            this.excludes = excludes;
        }

        /**
         * Get the include filters.
         *
         * @return include filters
         */
        @XmlElementWrapper(name = "includes")
        @XmlElement(name = "include")
        public List<String> getIncludes() {
            return includes;
        }

        /**
         * Set the include filters.
         *
         * @param includes include filters
         */
        public void setIncludes(List<String> includes) {
            this.includes = includes;
        }

        /**
         * Get the applied transformations.
         *
         * @return list of {@link Transformation}
         */
        @XmlIDREF
        @XmlAttribute
        public List<Transformation> getTransformations() {
            return transformations;
        }

        /**
         * Set the applied transformations.
         *
         * @param transformations transformations
         */
        public void setTransformations(List<Transformation> transformations) {
            this.transformations = transformations;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash += super.hashCode();
            hash = 67 * hash + Objects.hashCode(this.transformations);
            hash = 67 * hash + Objects.hashCode(this.directory);
            hash = 67 * hash + Objects.hashCode(this.includes);
            hash = 67 * hash + Objects.hashCode(this.excludes);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            if (!super.equals((Conditional) obj)) {
                return false;
            }
            final FileSet other = (FileSet) obj;
            if (!Objects.equals(this.directory, other.directory)) {
                return false;
            }
            if (!Objects.equals(this.transformations, other.transformations)) {
                return false;
            }
            if (!Objects.equals(this.includes, other.includes)) {
                return false;
            }
            return Objects.equals(this.excludes, other.excludes);
        }
    }

    /**
     * User input flow.
     */
    @XmlType(namespace = NAMESPACE)
    public static final class InputFlow {

        private List<FlowNode> nodes;

        /**
         * Get the flow nodes.
         *
         * @return list of {@link FlowNode}
         */
        @XmlElements({
            @XmlElement(name = "select", type = Select.class),
            @XmlElement(name = "input", type = Input.class)
        })
        public List<FlowNode> getNodes() {
            return nodes;
        }

        /**
         * Set the flow nodes.
         *
         * @param nodes nodes
         */
        public void setNodes(List<FlowNode> nodes) {
            this.nodes = nodes;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 29 * hash + Objects.hashCode(this.nodes);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final InputFlow other = (InputFlow) obj;
            return Objects.equals(this.nodes, other.nodes);
        }
    }

    /**
     * Base class for flow nodes.
     */
    @XmlType(namespace = NAMESPACE)
    public abstract static class FlowNode extends Conditional {

        private String text;

        /**
         * Get the input text for this select.
         *
         * @return input text
         */
        @XmlAttribute(required = true)
        public String getText() {
            return text;
        }

        /**
         * Set the input text for this select.
         *
         * @param text input text
         */
        public void setText(String text) {
            this.text = text;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash += super.hashCode();
            hash = 11 * hash + Objects.hashCode(this.text);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            if (!super.equals((Conditional) obj)) {
                return false;
            }
            final FlowNode other = (FlowNode) obj;
            return Objects.equals(this.text, other.text);
        }
    }

    /**
     * Select input, one of N choices.
     */
    @XmlType(namespace = NAMESPACE)
    public static final class Select extends FlowNode {

        private String id;
        private List<Choice> choices;

        /**
         * Get the select id.
         *
         * @return String
         */
        @XmlAttribute(required = true)
        public String getId() {
            return id;
        }

        /**
         * Set the select id.
         *
         * @param id id
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * Get the choices.
         *
         * @return list of {@code Choice}
         */
        @XmlElement(name = "choice")
        public List<Choice> getChoices() {
            return choices;
        }

        /**
         * Set the choices.
         *
         * @param choices choices
         */
        public void setChoices(List<Choice> choices) {
            this.choices = choices;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash += super.hashCode();
            hash = 89 * hash + Objects.hashCode(this.id);
            hash = 89 * hash + Objects.hashCode(this.choices);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            if (!super.equals((FlowNode) obj)) {
                return false;
            }
            final Select other = (Select) obj;
            if (!Objects.equals(this.id, other.id)) {
                return false;
            }
            return Objects.equals(this.choices, other.choices);
        }
    }

    /**
     * A selectable choice.
     */
    @XmlType(namespace = NAMESPACE)
    public static final class Choice extends FlowNode {

        private Property property;

        /**
         * Get the property mapping for this choice.
         *
         * @return String
         */
        @XmlIDREF
        @XmlAttribute(required = true)
        public Property getProperty() {
            return property;
        }

        /**
         * Set the property mapping for this choice.
         *
         * @param property property mapping
         */
        public void setProperty(Property property) {
            this.property = property;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash += super.hashCode();
            hash = 79 * hash + Objects.hashCode(this.property);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            if (!super.equals((FlowNode) obj)) {
                return false;
            }
            final Choice other = (Choice) obj;
            return Objects.equals(this.property, other.property);
        }
    }

    /**
     * A user input.
     */
    @XmlType(namespace = NAMESPACE)
    public static final class Input extends FlowNode {

        private String id;
        private Property property;
        private String defaultValue;

        /**
         * Get the input id.
         *
         * @return input id
         */
        @XmlAttribute(required = true)
        public String getId() {
            return id;
        }

        /**
         * Set the input id.
         *
         * @param id input id
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * Get the mapped property.
         *
         * @return Property
         */
        @XmlIDREF
        @XmlAttribute(required = true)
        public Property getProperty() {
            return property;
        }

        /**
         * Set the mapped property.
         *
         * @param property property
         */
        public void setProperty(Property property) {
            this.property = property;
        }

        /**
         * Get the default value.
         *
         * @return default value
         */
        @XmlAttribute(name = "default")
        public String getDefaultValue() {
            return defaultValue;
        }

        /**
         * Set the input default value.
         *
         * @param defaultValue default value
         */
        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash += super.hashCode();
            hash = 17 * hash + Objects.hashCode(this.id);
            hash = 17 * hash + Objects.hashCode(this.property);
            hash = 17 * hash + Objects.hashCode(this.defaultValue);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            if (!super.equals((FlowNode) obj)) {
                return false;
            }
            final Input other = (Input) obj;
            if (!Objects.equals(this.id, other.id)) {
                return false;
            }
            if (!Objects.equals(this.defaultValue, other.defaultValue)) {
                return false;
            }
            return Objects.equals(this.property, other.property);
        }
    }
}
