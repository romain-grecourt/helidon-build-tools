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

import java.util.LinkedList;
import java.util.Objects;

/**
 * Archetype step.
 */
public class Step extends Conditional {

    private final String label;
    private String help;

    private final LinkedList<ContextBlock> contexts = new LinkedList<>();
    private final LinkedList<Exec> execs = new LinkedList<>();
    private final LinkedList<Source> sources = new LinkedList<>();
    private final LinkedList<InputBlock> inputs = new LinkedList<>();

    protected Step(String label, String ifProperty) {
        super(ifProperty);
        this.label = label;
    }

    /**
     * Get the label.
     *
     * @return label
     */
    public String label() {
        return label;
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
     * Get the contexts.
     *
     * @return list of contexts
     */
    public LinkedList<ContextBlock> contexts() {
        return contexts;
    }

    /**
     * Get the execs.
     *
     * @return list of execs
     */
    public LinkedList<Exec> execs() {
        return execs;
    }

    /**
     * Get the sources.
     *
     * @return list of sources
     */
    public LinkedList<Source> sources() {
        return sources;
    }

    /**
     * Get the inputs.
     *
     * @return list of input
     */
    public LinkedList<InputBlock> inputBlocks() {
        return inputs;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Step step = (Step) o;
        return label.equals(step.label)
                && help.equals(step.help)
                && contexts.equals(step.contexts)
                && execs.equals(step.execs)
                && sources.equals(step.sources)
                && inputs.equals(step.inputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), label, help, contexts, execs, sources, inputs);
    }

    @Override
    public String toString() {
        return "Step{"
                + "label=" + label()
                + ", help=" + help()
                + ", contexts=" + contexts()
                + ", execs=" + execs()
                + ", sources=" + sources()
                + ", inputs=" + inputBlocks()
                + '}';
    }
}
