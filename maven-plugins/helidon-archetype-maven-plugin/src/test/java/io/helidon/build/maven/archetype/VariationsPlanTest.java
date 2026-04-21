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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.build.archetype.engine.v2.Expression;
import io.helidon.build.archetype.engine.v2.VariationEngine;
import io.helidon.build.archetype.engine.v2.Variations;
import io.helidon.build.common.xml.XMLElement;

import org.junit.jupiter.api.Test;

import static io.helidon.build.common.test.utils.TestFiles.testResourcePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VariationsPlanTest {

    @Test
    void testLoadRejectsNullPath() {
        assertThrows(NullPointerException.class, () -> Variations.Plan.load((Path) null));
    }

    @Test
    void testLoadRejectsMissingPath() {
        Path missing = resource("plans.xml").resolveSibling("missing-plans.xml");
        assertThrows(IllegalStateException.class, () -> Variations.Plan.load(missing));
    }

    @Test
    void testLoad() {
        List<Variations.Plan> plans = Variations.Plan.load(resource("plans.xml"));

        assertThat(plans, hasSize(3));
        assertThat(plans.get(0).id(), is("red"));
        assertThat(plans.get(0).externalValues(), is(Map.of("color", "red")));
        assertThat(plans.get(0).externalDefaults(), is(Map.of("artifactId", "demo-red")));
        assertThat(plans.get(0).filters(), hasSize(1));
        assertThat(plans.get(0).filters().get(0).literal(), containsString("${color}"));
        assertThat(plans.get(0).filters().get(0).literal(), containsString("${docker}"));
        assertThat(plans.get(1).filters(), hasSize(1));
        assertThat(plans.get(1).filters().get(0).literal(), containsString("${docker}"));
        assertThat(plans.get(2).externalValues(), is(Map.of("color", "red", "docker", "false")));
        assertThat(plans.get(2).externalDefaults(), is(Map.of("artifactId", "demo-red")));
    }

    @Test
    void testLoadRejectsFragmentWithoutId() {
        XMLElement root = xml("""
                <plans xmlns="https://helidon.io/archetype-plans/1.0">
                    <fragment>
                        <values>
                            <color>red</color>
                        </values>
                    </fragment>
                    <plan id="red"/>
                </plans>
                """);

        assertThrows(IllegalStateException.class, () -> Variations.Plan.load(root));
    }

    @Test
    void testLoadRejectsDuplicateFragments() {
        XMLElement root = xml("""
                <plans xmlns="https://helidon.io/archetype-plans/1.0">
                    <fragment id="color/red"/>
                    <fragment id="color/red"/>
                    <plan id="red"/>
                </plans>
                """);

        assertThrows(IllegalStateException.class, () -> Variations.Plan.load(root));
    }

    @Test
    void testLoadRejectsUnknownFragmentReference() {
        XMLElement root = xml("""
                <plans xmlns="https://helidon.io/archetype-plans/1.0">
                    <plan id="red" extends="missing"/>
                </plans>
                """);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> Variations.Plan.load(root));
        assertThat(ex.getMessage(), is("Unknown fragment 'missing' referenced by 'red'"));
    }

    @Test
    void testLoadRejectsCircularFragmentInheritance() {
        XMLElement root = xml("""
                <plans xmlns="https://helidon.io/archetype-plans/1.0">
                    <fragment id="a" extends="b"/>
                    <fragment id="b" extends="a"/>
                    <plan id="red" extends="a"/>
                </plans>
                """);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> Variations.Plan.load(root));
        assertThat(ex.getMessage(), is("Circular fragment inheritance: a -> b -> a"));
    }

    @Test
    void testLoadPreservesInheritedValueOrder() {
        XMLElement root = xml("""
                <plans xmlns="https://helidon.io/archetype-plans/1.0">
                    <fragment id="base">
                        <values>
                            <one>1</one>
                            <two>2</two>
                        </values>
                    </fragment>
                    <fragment id="extra">
                        <values>
                            <three>3</three>
                            <four>4</four>
                        </values>
                    </fragment>
                    <plan id="ordered" extends="base, extra">
                        <values>
                            <five>5</five>
                        </values>
                    </plan>
                </plans>
                """);

        List<Variations.Plan> plans = Variations.Plan.load(root);

        assertThat(plans, hasSize(1));
        assertThat(plans.get(0).externalValues().keySet(), contains("one", "two", "three", "four", "five"));
    }

    @Test
    void testLoadPreservesOverriddenValueOrder() {
        XMLElement root = xml("""
                <plans xmlns="https://helidon.io/archetype-plans/1.0">
                    <fragment id="base">
                        <values>
                            <one>1</one>
                            <two>2</two>
                        </values>
                    </fragment>
                    <plan id="ordered" extends="base">
                        <values>
                            <one>10</one>
                            <three>3</three>
                        </values>
                    </plan>
                </plans>
                """);

        List<Variations.Plan> plans = Variations.Plan.load(root);

        assertThat(plans, hasSize(1));
        assertThat(plans.get(0).externalValues().keySet(), contains("one", "two", "three"));
        assertThat(plans.get(0).externalValues().get("one"), is("10"));
    }

    @Test
    void testLoadPreservesOverriddenDefaultOrder() {
        XMLElement root = xml("""
                <plans xmlns="https://helidon.io/archetype-plans/1.0">
                    <fragment id="base">
                        <defaults>
                            <one>1</one>
                            <two>2</two>
                        </defaults>
                    </fragment>
                    <plan id="ordered" extends="base">
                        <defaults>
                            <one>10</one>
                            <three>3</three>
                        </defaults>
                    </plan>
                </plans>
                """);

        List<Variations.Plan> plans = Variations.Plan.load(root);

        assertThat(plans, hasSize(1));
        assertThat(plans.get(0).externalDefaults().keySet(), contains("one", "two", "three"));
        assertThat(plans.get(0).externalDefaults().get("one"), is("10"));
    }

    @Test
    void testLoadPreservesFilterOrder() {
        XMLElement root = xml("""
                <plans xmlns="https://helidon.io/archetype-plans/1.0">
                    <fragment id="base">
                        <rules>
                            <exclude if="${base}"/>
                        </rules>
                    </fragment>
                    <fragment id="extra">
                        <rules>
                            <exclude if="${extra}"/>
                        </rules>
                    </fragment>
                    <plan id="ordered" extends="base, extra">
                        <rules>
                            <exclude if="${local}"/>
                        </rules>
                    </plan>
                </plans>
                """);

        List<Variations.Plan> plans = Variations.Plan.load(root);

        assertThat(plans, hasSize(1));
        assertThat(plans.get(0).filters(), hasSize(3));
        assertThat(plans.get(0).filters().get(0).literal(), containsString("${base}"));
        assertThat(plans.get(0).filters().get(1).literal(), containsString("${extra}"));
        assertThat(plans.get(0).filters().get(2).literal(), containsString("${local}"));
    }

    @Test
    void testLoadAllowsPlanIdToMatchFragmentId() {
        XMLElement root = xml("""
                <plans xmlns="https://helidon.io/archetype-plans/1.0">
                    <fragment id="shared">
                        <values>
                            <one>1</one>
                        </values>
                    </fragment>
                    <plan id="shared" extends="shared">
                        <values>
                            <two>2</two>
                        </values>
                    </plan>
                </plans>
                """);

        List<Variations.Plan> plans = Variations.Plan.load(root);

        assertThat(plans, hasSize(1));
        assertThat(plans.get(0).externalValues().keySet(), contains("one", "two"));
        assertThat(plans.get(0).externalValues(), is(Map.of("one", "1", "two", "2")));
    }

    @Test
    void testPlanBuilder() {
        Variations.Plan plan = Variations.Plan.builder()
                .id("red")
                .externalValue("color", "red")
                .externalDefault("artifactId", "demo-red")
                .filter(Expression.create("${color} == 'red'"))
                .build();

        assertThat(plan.id(), is("red"));
        assertThat(plan.externalValues(), is(Map.of("color", "red")));
        assertThat(plan.externalDefaults(), is(Map.of("artifactId", "demo-red")));
        assertThat(plan.filters(), hasSize(1));
        assertThat(plan.filters().get(0).literal(), containsString("${color}"));
    }

    @Test
    void testPlanBuilderBulkMethodsAreAdditive() {
        Expression red = Expression.create("${color} == 'red'");
        Expression docker = Expression.create("${docker}");
        Map<String, String> externalValues = new LinkedHashMap<>();
        externalValues.put("color", "red");
        externalValues.put("docker", "false");
        Map<String, String> externalDefaults = new LinkedHashMap<>();
        externalDefaults.put("artifactId", "demo-red");

        Variations.Plan plan = Variations.Plan.builder()
                .id("red")
                .externalValues(externalValues)
                .externalValue("name", "demo")
                .externalDefaults(externalDefaults)
                .externalDefault("package", "io.demo")
                .filters(List.of(red))
                .filter(docker)
                .build();

        assertThat(plan.externalValues().keySet(), contains("color", "docker", "name"));
        assertThat(plan.externalDefaults().keySet(), contains("artifactId", "package"));
        assertThat(plan.filters(), contains(red, docker));
    }

    @Test
    void testPlanBuilderRequiresId() {
        assertThrows(NullPointerException.class, () -> Variations.Plan.builder().build());
    }

    @Test
    void testRequestBuilderDefaults() {
        Variations.Request request = Variations.Request.EMPTY;

        assertThat(request.filters(), is(List.of()));
        assertThat(request.externalValues(), is(Map.of()));
        assertThat(request.externalDefaults(), is(Map.of()));
        assertThat(request.plans(), is(List.of()));
        assertThat(request.maxIntermediate(), is(Long.MAX_VALUE));
    }

    @Test
    void testRequestBuilderBulkMethodsAreAdditive() {
        Expression red = Expression.create("${color} == 'red'");
        Expression docker = Expression.create("${docker}");
        Map<String, String> externalValues = new LinkedHashMap<>();
        externalValues.put("color", "red");
        externalValues.put("docker", "false");
        Map<String, String> externalDefaults = new LinkedHashMap<>();
        externalDefaults.put("artifactId", "demo-red");
        Variations.Plan blue = Variations.Plan.builder().id("blue").build();
        Variations.Plan redPlan = Variations.Plan.builder().id("red").build();

        Variations.Request request = Variations.Request.builder()
                .filters(List.of(red))
                .expression("${docker}")
                .externalValues(externalValues)
                .externalValue("name", "demo")
                .externalDefaults(externalDefaults)
                .externalDefault("package", "io.demo")
                .plans(List.of(blue))
                .plan(redPlan)
                .maxIntermediate(10)
                .build();

        assertThat(request.filters(), contains(red, docker));
        assertThat(request.externalValues().keySet(), contains("color", "docker", "name"));
        assertThat(request.externalDefaults().keySet(), contains("artifactId", "package"));
        assertThat(request.plans(), contains(blue, redPlan));
        assertThat(request.maxIntermediate(), is(10L));
    }

    @Test
    void testRequestBuilderRejectsNegativeMax() {
        assertThrows(IllegalArgumentException.class, () -> Variations.Request.builder()
                .maxIntermediate(-1)
                .build());
    }

    @Test
    void testAnalyzeDiagnostics() {
        Path cwd = resource("script").toAbsolutePath().normalize();
        VariationEngine variationEngine = new VariationEngine(() -> cwd.resolve("main.xml"), cwd);
        Variations.Request request = request(
                List.of(),
                Map.of(),
                Map.of(),
                Variations.Plan.load(xml("""
                        <plans xmlns="https://helidon.io/archetype-plans/1.0">
                            <plan id="red">
                                <values>
                                    <color>red</color>
                                </values>
                            </plan>
                            <plan id="red-false">
                                <values>
                                    <color>red</color>
                                    <docker>false</docker>
                                </values>
                            </plan>
                            <plan id="blue-false">
                                <values>
                                    <color>blue</color>
                                    <docker>false</docker>
                                </values>
                            </plan>
                            <plan id="blue-true-blocked">
                                <values>
                                    <color>blue</color>
                                    <docker>true</docker>
                                </values>
                                <rules>
                                    <exclude if="${docker}"/>
                                </rules>
                            </plan>
                        </plans>
                        """)),
                Long.MAX_VALUE);

        Variations.Diagnostics diagnostics = variationEngine.analyze(request);

        assertThat(diagnostics.hasErrors(), is(true));
        assertThat(diagnostics.hasWarnings(), is(true));

        Variations.Finding unsatisfiable = finding(
                diagnostics,
                Variations.Finding.Kind.UNSATISFIABLE_PLAN,
                "blue-true-blocked");
        assertThat(unsatisfiable.severity(), is(Variations.Finding.Severity.ERROR));
        assertThat(unsatisfiable.planId().orElseThrow(), is("blue-true-blocked"));
        assertThat(unsatisfiable.expression().isPresent(), is(true));
        assertThat(unsatisfiable.relatedPlanId().isEmpty(), is(true));
        assertThat(unsatisfiable.key().isEmpty(), is(true));
        assertThat(unsatisfiable.value().isEmpty(), is(true));

        Variations.Finding overlap = finding(diagnostics, Variations.Finding.Kind.PLAN_OVERLAP, "red");
        assertThat(overlap.severity(), is(Variations.Finding.Severity.WARNING));
        assertThat(overlap.relatedPlanId().orElseThrow(), is("red-false"));
        assertThat(overlap.expression().isPresent(), is(true));
        assertThat(overlap.key().isEmpty(), is(true));
        assertThat(overlap.value().isEmpty(), is(true));

        Variations.Finding redundantPlan = finding(diagnostics, Variations.Finding.Kind.REDUNDANT_PLAN, "red-false");
        assertThat(redundantPlan.relatedPlanId().orElseThrow(), is("red"));
        assertThat(redundantPlan.expression().isEmpty(), is(true));
        assertThat(redundantPlan.key().isEmpty(), is(true));
        assertThat(redundantPlan.value().isEmpty(), is(true));

        Variations.Finding coverageGap = finding(diagnostics, Variations.Finding.Kind.COVERAGE_GAP);
        assertThat(coverageGap.severity(), is(Variations.Finding.Severity.WARNING));
        assertThat(coverageGap.planId().isEmpty(), is(true));
        assertThat(coverageGap.relatedPlanId().isEmpty(), is(true));
        assertThat(coverageGap.key().isEmpty(), is(true));
        assertThat(coverageGap.value().isEmpty(), is(true));
        assertThat(coverageGap.expression().isPresent(), is(true));
    }

    @Test
    void testAnalyzeSinglePlanHasNoCoverageGap() {
        Path cwd = resource("script").toAbsolutePath().normalize();
        VariationEngine variationEngine = new VariationEngine(() -> cwd.resolve("main.xml"), cwd);
        Variations.Request request = request(
                List.of(),
                Map.of(),
                Map.of(),
                Variations.Plan.load(xml("""
                        <plans xmlns="https://helidon.io/archetype-plans/1.0">
                            <plan id="red">
                                <values>
                                    <color>red</color>
                                </values>
                            </plan>
                        </plans>
                        """)),
                Long.MAX_VALUE);

        Variations.Diagnostics diagnostics = variationEngine.analyze(request);

        assertThat(diagnostics.hasErrors(), is(false));
        assertThat(diagnostics.hasWarnings(), is(false));
        assertThat(diagnostics.findings(), hasSize(0));
    }

    @Test
    void testRedundantPinFindingAccessors() throws Exception {
        Method method = Variations.Finding.class.getDeclaredMethod(
                "redundantPin",
                String.class,
                String.class,
                String.class);
        method.setAccessible(true);
        Variations.Finding redundantPin = (Variations.Finding) method.invoke(null, "basic", "mode", "basic");

        assertThat(redundantPin.kind(), is(Variations.Finding.Kind.REDUNDANT_PIN));
        assertThat(redundantPin.severity(), is(Variations.Finding.Severity.WARNING));
        assertThat(redundantPin.planId().orElseThrow(), is("basic"));
        assertThat(redundantPin.key().orElseThrow(), is("mode"));
        assertThat(redundantPin.value().orElseThrow(), is("basic"));
        assertThat(redundantPin.relatedPlanId().isEmpty(), is(true));
        assertThat(redundantPin.expression().isEmpty(), is(true));
    }

    @Test
    void testComputePlanVariations() {
        Path cwd = resource("script").toAbsolutePath().normalize();
        VariationEngine variationEngine = new VariationEngine(() -> cwd.resolve("main.xml"), cwd);
        Variations.Request request = request(
                List.of(),
                Map.of(),
                Map.of(),
                Variations.Plan.load(resource("plans.xml")),
                Long.MAX_VALUE);

        Variations actual = variationEngine.compute(request);
        Variations expected = Variations.of(
                Variations.entry(Map.of("color", "blue", "docker", "false")),
                Variations.entry(Map.of("color", "red", "docker", "false")));

        assertThat(actual, is(expected));
    }

    @Test
    void testComputeSingleUnsatisfiablePlanFails() {
        Path cwd = resource("script").toAbsolutePath().normalize();
        VariationEngine variationEngine = new VariationEngine(() -> cwd.resolve("main.xml"), cwd);
        Variations.Request request = request(
                List.of(),
                Map.of(),
                Map.of(),
                Variations.Plan.load(xml("""
                        <plans xmlns="https://helidon.io/archetype-plans/1.0">
                            <plan id="blue-true-blocked">
                                <values>
                                    <color>blue</color>
                                    <docker>true</docker>
                                </values>
                                <rules>
                                    <exclude if="${docker}"/>
                                </rules>
                            </plan>
                        </plans>
                        """)),
                Long.MAX_VALUE);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> variationEngine.compute(request));
        assertThat(ex.getMessage(), containsString("Variation plan 'blue-true-blocked' is unsatisfiable"));
    }

    private static Path resource(String path) {
        return testResourcePath(VariationsPlanTest.class, "variation-plans/" + path);
    }

    private static Variations.Finding finding(Variations.Diagnostics diagnostics, Variations.Finding.Kind kind) {
        return diagnostics.findings().stream()
                .filter(it -> it.kind() == kind)
                .findFirst()
                .orElseThrow();
    }

    private static Variations.Finding finding(Variations.Diagnostics diagnostics,
                                              Variations.Finding.Kind kind,
                                              String planId) {
        return diagnostics.findings().stream()
                .filter(it -> it.kind() == kind)
                .filter(it -> it.planId().filter(planId::equals).isPresent())
                .findFirst()
                .orElseThrow();
    }

    private static XMLElement xml(String value) {
        try {
            return XMLElement.parse(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static Variations.Request request(List<Expression> filters,
                                              Map<String, String> externalValues,
                                              Map<String, String> externalDefaults,
                                              List<Variations.Plan> plans,
                                              long maxIntermediateVariations) {
        return Variations.Request.builder()
                .maxIntermediate(maxIntermediateVariations)
                .filters(filters)
                .externalValues(externalValues)
                .externalDefaults(externalDefaults)
                .plans(plans)
                .build();
    }
}
