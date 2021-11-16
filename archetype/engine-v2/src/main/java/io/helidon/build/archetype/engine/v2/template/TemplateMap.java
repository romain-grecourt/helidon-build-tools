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
 * Template map used in {@link TemplateModel}.
 */
public class TemplateMap implements Comparable {

    private final MergingMap<String, ArchetypeDescriptor.ModelValue>     templateValues  = new MergingMap<>();
    private final MergingMap<String, TemplateList>  templateLists   = new MergingMap<>();
    private final MergingMap<String, TemplateMap>   templateMaps    = new MergingMap<>();
    private final int order;

    /**
     * Template map constructor.
     *
     * @param map Map containing xml descriptor data
     */
    public TemplateMap(ArchetypeDescriptor.ModelMap map) {
        this.order = map.order();
        for (ArchetypeDescriptor.ModelKeyedValue value : map.keyValues()) {
            templateValues.put(value.key(), value);
        }
        for (ArchetypeDescriptor.ModelKeyedList list : map.keyLists()) {
            templateLists.put(list.key(), new TemplateList(list));
        }
        for (ArchetypeDescriptor.ModelKeyedMap keyMap : map.keyMaps()) {
            templateMaps.put(keyMap.key(), new TemplateMap(keyMap));
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
     * Get the map of {@link ArchetypeDescriptor.ModelValue} merged by key for this {@link TemplateMap}.
     *
     * @return values
     */
    public MergingMap<String, ArchetypeDescriptor.ModelValue> values() {
        return templateValues;
    }

    /**
     * Get the map of {@link TemplateList} merged by key for this {@link TemplateMap}.
     *
     * @return lists
     */
    public MergingMap<String, TemplateList> lists() {
        return templateLists;
    }

    /**
     * Get the map of {@link TemplateMap} merged by key for this {@link TemplateMap}.
     *
     * @return maps
     */
    public MergingMap<String, TemplateMap> maps() {
        return templateMaps;
    }

    @Override
    public int compareTo(Object o) {
        if (o instanceof TemplateMap) {
            return Integer.compare(this.order, ((TemplateMap) o).order);
        }
        return 0;
    }
}
