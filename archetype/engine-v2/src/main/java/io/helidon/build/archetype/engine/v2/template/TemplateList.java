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

import java.util.LinkedList;
import java.util.List;

import io.helidon.build.archetype.engine.v2.descriptor.ModelList;
import io.helidon.build.archetype.engine.v2.descriptor.ModelMap;
import io.helidon.build.archetype.engine.v2.descriptor.ModelValue;

/**
 * Template list used in {@link TemplateModel}.
 */
public class TemplateList implements Comparable {

    private final List<ModelValue> templateValues = new LinkedList<>();
    private final List<TemplateList> templateLists = new LinkedList<>();
    private final List<TemplateMap> templateMaps = new LinkedList<>();
    private final int order;

    /**
     * TemplateList constructor.
     *
     * @param list list containing xml descriptor data
     */
    public TemplateList(ModelList list) {
        this.order = list.order();
        templateValues.addAll(list.values());
        for (ModelMap map : list.maps()) {
            templateMaps.add(new TemplateMap(map));
        }
        for (ModelList listType : list.lists()) {
            templateLists.add(new TemplateList(listType));
        }
    }

    /**
     * Get its order.
     *
     * @return order
     */
    public int order() {
        return order;
    }

    /**
     * Get the map of {@link ModelValue} merged by key for this {@link TemplateList}.
     *
     * @return values
     */
    public List<ModelValue> values() {
        return templateValues;
    }

    /**
     * Get the map of {@link TemplateList} merged by key for this {@link TemplateList}.
     *
     * @return values
     */
    public List<TemplateList> lists() {
        return templateLists;
    }

    /**
     * Get the map of {@link TemplateMap} merged by key for this {@link TemplateList}.
     *
     * @return values
     */
    public List<TemplateMap> maps() {
        return templateMaps;
    }

    @Override
    public int compareTo(Object o) {
        if (o instanceof TemplateList) {
            return Integer.compare(this.order, ((TemplateList) o).order);
        }
        return 0;
    }
}
