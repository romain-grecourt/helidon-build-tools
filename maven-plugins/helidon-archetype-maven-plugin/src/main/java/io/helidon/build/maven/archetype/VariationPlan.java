/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.build.maven.archetype;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.build.archetype.engine.v2.Expression;
import io.helidon.build.common.xml.XMLElement;

final class VariationPlan {

    private final String id;
    private final Map<String, String> externalValues;
    private final Map<String, String> externalDefaults;
    private final List<Expression> filters;

    private VariationPlan(String id,
                          Map<String, String> externalValues,
                          Map<String, String> externalDefaults,
                          List<Expression> filters) {
        this.id = Objects.requireNonNull(id);
        this.externalValues = Collections.unmodifiableMap(new LinkedHashMap<>(externalValues));
        this.externalDefaults = Collections.unmodifiableMap(new LinkedHashMap<>(externalDefaults));
        this.filters = List.copyOf(filters);
    }

    static List<VariationPlan> load(Path file) {
        Objects.requireNonNull(file);
        if (!Files.exists(file)) {
            throw new IllegalStateException("Variation plans file does not exist: " + file);
        }
        try (InputStream is = Files.newInputStream(file)) {
            XMLElement root = XMLElement.parse(is);
            if (root.name().equals("variation-plans")) {
                throw new IllegalStateException("Variation plans file uses <variation-plans>; use <plans> instead");
            }
            if (!root.name().equals("plans")) {
                throw new IllegalStateException("Unexpected variation plans root element: " + root.name());
            }
            List<VariationPlan> plans = load(root);
            if (plans.isEmpty()) {
                throw new IllegalStateException("Variation plans file does not contain any <plan> elements: " + file);
            }
            return plans;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static List<VariationPlan> load(XMLElement root) {
        List<VariationPlan> plans = new ArrayList<>();
        int index = 1;
        for (XMLElement plan : root.children("plan")) {
            String id = plan.attribute("id", "plan-" + index++);
            if (plan.child("externalValues").isPresent()) {
                throw new IllegalStateException("Plan '" + id + "' uses <externalValues>; use <values> instead");
            }
            Map<String, String> externalValues = plan.child("values")
                    .map(VariationPlan::readMap)
                    .orElse(Map.of());
            Map<String, String> externalDefaults = plan.child("externalDefaults")
                    .map(VariationPlan::readMap)
                    .orElse(Map.of());
            List<Expression> filters = plan.child("rules")
                    .map(VariationRules::load)
                    .orElse(List.of());
            plans.add(new VariationPlan(id, externalValues, externalDefaults, filters));
        }
        return List.copyOf(plans);
    }

    String id() {
        return id;
    }

    Map<String, String> externalValues() {
        return externalValues;
    }

    Map<String, String> externalDefaults() {
        return externalDefaults;
    }

    List<Expression> filters() {
        return filters;
    }

    private static Map<String, String> readMap(XMLElement root) {
        Map<String, String> values = new LinkedHashMap<>();
        for (XMLElement entry : root.children()) {
            values.put(entry.name(), entry.value());
        }
        return values;
    }
}
