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

package io.helidon.build.archetype.engine.v2.template;

import io.helidon.build.archetype.engine.v2.descriptor.ArchetypeDescriptor;

/**
 * Template Model Archetype.
 */
public class TemplateModel {

    private ArchetypeDescriptor.Model model;

    /**
     * Template default constructor.
     */
    public TemplateModel() {
        this.model = null;
    }

    /**
     * Merge a new model to the unique model.
     *
     * @param model model to be merged
     */
    public void mergeModel(ArchetypeDescriptor.Model model) {
        if (model == null) {
            return;
        }
        if (this.model == null) {
            this.model = model;
            return;
        }

        this.model.keyedValues().addAll(model.keyedValues());
        this.model.keyedLists().addAll(model.keyedLists());
        this.model.keyedMaps().addAll(model.keyedMaps());
    }

    /**
     * Get the unique model descriptor.
     *
     * @return model descriptor
     */
    public ArchetypeDescriptor.Model model() {
        return model;
    }

}
