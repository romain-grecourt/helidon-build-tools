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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.build.archetype.engine.v2.ScriptCompiler;
import io.helidon.build.archetype.engine.v2.Variations;

import org.junit.jupiter.api.Test;

import static io.helidon.build.common.test.utils.TestFiles.testResourcePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VariationPlanTest {

    @Test
    void testLoadRejectsNullPath() {
        assertThrows(NullPointerException.class, () -> VariationPlan.load((Path) null));
    }

    @Test
    void testLoadRejectsMissingPath() {
        Path missing = resource("plans.xml").resolveSibling("missing-plans.xml");
        assertThrows(IllegalStateException.class, () -> VariationPlan.load(missing));
    }

    @Test
    void testLoad() {
        List<VariationPlan> plans = VariationPlan.load(resource("plans.xml"));

        assertThat(plans, hasSize(3));
        assertThat(plans.get(0).id(), is("red"));
        assertThat(plans.get(0).externalValues(), is(Map.of("color", "red")));
        assertThat(plans.get(0).externalDefaults(), is(Map.of("artifactId", "demo-red")));
        assertThat(plans.get(0).filters(), hasSize(1));
        assertThat(plans.get(0).filters().get(0).variables(), containsInAnyOrder("color", "docker"));
        assertThat(plans.get(1).filters(), hasSize(1));
        assertThat(plans.get(1).filters().get(0).variables(), containsInAnyOrder("docker"));
        assertThat(plans.get(2).externalValues(), is(Map.of("color", "red", "docker", "false")));
    }

    @Test
    void testMergePlanVariations() {
        Path cwd = resource("script").toAbsolutePath().normalize();
        ScriptCompiler compiler = new ScriptCompiler(() -> cwd.resolve("main.xml"), cwd);
        List<VariationPlan> plans = VariationPlan.load(resource("plans.xml"));
        List<Variations> computed = new ArrayList<>();

        for (VariationPlan plan : plans) {
            Map<String, String> externalValues = new LinkedHashMap<>(plan.externalValues());
            Map<String, String> externalDefaults = new LinkedHashMap<>(plan.externalDefaults());
            computed.add(Variations.compute(compiler, plan.filters(), externalValues, externalDefaults, Long.MAX_VALUE));
        }

        Variations actual = Variations.union(computed);

        assertThat(actual.stream()
                .map(entry -> entry.toString(false, " "))
                .collect(Collectors.toList()), containsInAnyOrder(
                "color=blue docker=false",
                "color=red docker=false"));
    }

    private static Path resource(String path) {
        return testResourcePath(VariationPlanTest.class, "variation-plans/" + path);
    }
}
