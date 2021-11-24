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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

//import io.helidon.build.archetype.engine.v2.descriptor.ArchetypeDescriptor;

/**
 * Map with unique key. For the same key, Object are merge together
 * instead of being replaced.
 *
 * @param <K>   key
 * @param <V>   Template Object
 */
public class MergingMap<K, V> extends LinkedHashMap<K, V> {

    @Override
    public V put(K key, V value) {
        if (this.containsKey(key)) {
            try {
                value = merge(value, this.get(key));
            } catch (IOException ignored) {
            }
        }
        return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        for (K key : map.keySet()) {
            this.put(key, map.get(key));
        }
    }

    private V merge(V first, V second) throws IOException {
        if (second == null) {
            return first;
        }
        if (first == null) {
            return second;
        }

//        if (first instanceof Object) {
//            return mergeValues((Object) first, (Object) second);
//        }

        if (first instanceof TemplateList) {
            return mergeLists((TemplateList) first, (TemplateList) second);
        }

        if (first instanceof TemplateMap) {
            return mergeMaps((TemplateMap) first, (TemplateMap) second);
        }
        return null;
    }

    private V mergeMaps(TemplateMap first, TemplateMap second) {
        second.values().putAll(first.values());
        second.lists().putAll(first.lists());
        second.maps().putAll(first.maps());
        return (V) second;
    }

    private V mergeLists(TemplateList first, TemplateList second) {
//        second.values().addAll(first.values());
//        second.lists().addAll(first.lists());
//        second.maps().addAll(first.maps());
//        return (V) second;
        return (V) null;
    }

    private V mergeValues(Object first, Object second) throws IOException {
//        String value = mergeValue(first, second);
//        String url = mergeURL(first, second);
//        String file = mergeFile(first, second);
//        String template = mergeTemplate(first, second);
//        int order = mergeOrder(first, second);
//        Object valueType =  new Object(url, file, template, order, first.ifProperties());
//        valueType.value(value);
//        return (V) valueType;
        return (V) null;
    }

    private int mergeOrder(Object first, Object second) {
//        return Math.min(first.order(), second.order());
        return 1;
    }

    private String mergeTemplate(Object first, Object second) {
//        if (first.template() == null || second.template() == null) {
//            return "mustache";
//        }
//        if (first.template().equals("mustache") || second.template().equals("mustache")) {
//            return "mustache";
//        }
//        return first.template();
        return null;
    }

    private String mergeFile(Object first, Object second) throws IOException {
//        return first.file();
        return null;
    }

    private String mergeURL(Object first, Object second) {
//        return first.url() == null ? second.url() : first.url();
        return null;
    }

    private String mergeValue(Object first, Object second) {
//        return first.value() + second.value();
        return null;
    }

}
