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

package io.helidon.build.archetype.engine.v2.descriptor;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Helidon archetype V2 descriptor.
 */
public class ArchetypeDescriptor {

    private final Map<String, String> attributes;
    private final List<ContextBlock> contexts;
    private final List<Step> steps;
    private final List<InputBlock> inputs;
    private final List<Source> sources;
    private final List<Exec> execs;
    private final Output output;
    private String help;

    ArchetypeDescriptor(Map<String, String> archetypeAttributes,
                        List<ContextBlock> context,
                        List<Step> step,
                        List<InputBlock> inputs,
                        List<Source> source,
                        List<Exec> exec,
                        Output output,
                        String help) {

        this.attributes = archetypeAttributes;
        this.contexts = context == null ? List.of() : context;
        this.steps = step == null ? List.of() : step;
        this.inputs = inputs == null ? List.of() : inputs;
        this.sources = source == null ? List.of() : source;
        this.execs = exec == null ? List.of() : exec;
        this.output = output;
        this.help = help;
    }

    /**
     * Create an archetype descriptor instance from an input stream.
     *
     * @param is input stream
     * @return ArchetypeDescriptor
     */
    public static ArchetypeDescriptor read(InputStream is) {
        return ArchetypeDescriptorReader.read(is);
    }

    /**
     * Get the list of context archetype from main archetype-script xml element.
     *
     * @return List of Context
     */
    public List<ContextBlock> contexts() {
        return contexts;
    }

    /**
     * Get the list of step archetype from main archetype-script xml element.
     *
     * @return List of Step
     */
    public List<Step> steps() {
        return steps;
    }

    /**
     * Get a list of input archetype from main archetype-script xml element.
     *
     * @return List of Input
     */
    public List<InputBlock> inputBlocks() {
        return inputs;
    }

    /**
     * Get the list of source archetype from main archetype-script xml element.
     *
     * @return List of Source
     */
    public List<Source> sources() {
        return sources;
    }

    /**
     * Get the list of exec archetype from main archetype-script xml element.
     *
     * @return List of Exec
     */
    public List<Exec> execs() {
        return execs;
    }

    /**
     * Get the output archetype from main archetype-script xml element.
     *
     * @return Output
     */
    public Output output() {
        return output;
    }

    /**
     * Get the help archetype from main archetype-script xml element.
     *
     * @return help as a String
     */
    public String help() {
        return help;
    }

    /**
     * Get the main archetype-script xml element attributes.
     *
     * @return Map of attributes
     */
    public Map<String, String> attributes() {
        return attributes;
    }

    /**
     * Set the help archetype from main archetype-script xml element.
     *
     * @param help String contained into help element
     */
    public void help(String help) {
        this.help = help;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ArchetypeDescriptor a = (ArchetypeDescriptor) o;
        return contexts.equals(a.contexts)
                && steps.equals(a.steps)
                && inputs.equals(a.inputs)
                && sources.equals(a.sources)
                && execs.equals(a.execs)
                && output.equals(a.output);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), attributes, contexts, steps, inputs, sources, execs, output);
    }

    @Override
    public String toString() {
        return "ArchetypeDescriptor{"
                + "archetypeAttributes=" + attributes()
                + ", contexts=" + contexts()
                + ", steps=" + steps()
                + ", inputs=" + inputBlocks()
                + ", sources=" + sources()
                + ", execs=" + execs()
                + ", output=" + output()
                + ", help=" + help()
                + '}';
    }
}
