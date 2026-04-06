/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
package io.helidon.build.archetype.engine.v2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import io.helidon.build.archetype.engine.v2.Node.Kind;
import io.helidon.build.archetype.engine.v2.ScriptCompiler.ValidationException;
import io.helidon.build.common.Strings;
import io.helidon.build.common.VirtualFileSystem;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.EXPR_EVAL_ERROR;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.EXPR_INCOMPATIBLE_OPERATOR;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.EXPR_TEXT_INPUT_CONTROL_FLOW;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.EXPR_UNSUPPORTED_CONDITION;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.EXPR_UNRESOLVED_VARIABLE;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.Options.IGNORE_ERRORS;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.Options.VALIDATE_ONLY;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.INPUT_ALREADY_DECLARED;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.INPUT_NOT_IN_STEP;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.INPUT_OPTIONAL_NO_DEFAULT;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.INPUT_TEXT_NESTED_VALUES;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.INPUT_TYPE_MISMATCH;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.OPTION_VALUE_ALREADY_DECLARED;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.PRESET_TYPE_MISMATCH;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.PRESET_UNRESOLVED;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.STEP_DECLARED_OPTIONAL;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.STEP_NOT_DECLARED_OPTIONAL;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.STEP_NO_INPUT;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.Options.NO_TRANSIENT;
import static io.helidon.build.archetype.engine.v2.ScriptCompiler.Options.NO_OUTPUT;
import static io.helidon.build.common.FileUtils.fileName;
import static io.helidon.build.common.FileUtils.unique;
import static io.helidon.build.common.test.utils.TestFiles.targetDir;
import static io.helidon.build.common.test.utils.TestFiles.testResourcePath;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@link ScriptCompiler}.
 */
class ScriptCompilerTest {

    @Test
    void testEmptyScript() {
        Path outputDir = compile("compiler/empty-script", "main.xml", IGNORE_ERRORS, NO_OUTPUT);
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/empty-script.xml")));
    }

    @Test
    void testEmptyMethod() {
        Path outputDir = compile("compiler/empty-method", "main.xml");
        Node node = Script.load(outputDir.resolve("main.xml"));
        assertThat(node, allOf(
                hasProperty("methods", n -> n.script().methods(), is(Map.of())),
                hasProperty("children", Node::children, is(List.of()))));
    }

    @Test
    void testExecUrl() {
        Path outputDir = compile("compiler/exec-url", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/exec-url.xml")));
    }

    @Test
    void testFiltering1() {
        Path outputDir = compile("compiler/filtering1", "main.xml", IGNORE_ERRORS, NO_OUTPUT, NO_TRANSIENT);
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/filtering1.xml")));
    }

    @Test
    void testFiltering2() {
        Path outputDir = compile("compiler/filtering2", "main.xml", NO_OUTPUT, NO_TRANSIENT);
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/filtering2.xml")));
    }

    @Test
    void testFiltering3() {
        Path outputDir = compile("compiler/filtering3", "main.xml", NO_OUTPUT);
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/filtering3.xml")));
    }

    @Test
    void testInlined1() {
        Path outputDir = compile("compiler/inlined1", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/inlined1.xml")));
    }

    @Test
    void testInlined2() {
        Path outputDir = compile("compiler/inlined2", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/inlined2.xml")));
    }

    @Test
    void testInlined3() {
        Path outputDir = compile("compiler/inlined3", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/inlined3.xml")));
    }

    @Test
    void testInlined4() {
        Path outputDir = compile("compiler/inlined4", "main.xml", IGNORE_ERRORS);
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/inlined4.xml")));
    }

    @Test
    void testInlined5() {
        Path outputDir = compile("compiler/inlined5", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/inlined5.xml")));
    }

    @Test
    void testOutput1() {
        Path outputDir = compile("compiler/output1", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/output1.xml")));
    }

    @Test
    void testOutput2() {
        Path outputDir = compile("compiler/output2", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/output2.xml")));
    }

    @Test
    void testOutput3() {
        Path outputDir = compile("compiler/output3", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/output3.xml")));
    }

    @Test
    void testOutput4() {
        Path outputDir = compile("compiler/output4", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/output4.xml")));
    }

    @Test
    void testInput1() {
        Path outputDir = compile("compiler/input1", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/input1.xml")));
    }

    @Test
    void testInput2() {
        Path outputDir = compile("compiler/input2", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/input2.xml")));
    }

    @Test
    void testInput3() {
        Path outputDir = compile("compiler/input3", "main.xml", IGNORE_ERRORS);
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/input3.xml")));
    }

    @Test
    void testInput4() {
        Path outputDir = compile("compiler/input4", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/input4.xml")));
    }

    @Test
    void testInput5() {
        Path outputDir = compile("compiler/input5", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/input5.xml")));
    }

    @Test
    void testInput6() {
        Path outputDir = compile("compiler/input6", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/input6.xml")));
    }

    @Test
    void testInput7() {
        Path outputDir = compile("compiler/input7", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/input7.xml")));
    }

    @Test
    void testInput8() {
        Path outputDir = compile("compiler/input8", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/input8.xml")));
    }

    @Test
    void testBranchSpecificInputPruning() {
        Path outputDir = compile("compiler/branch-pruning", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")),
                is(normalizeXml("compiler/expected/branch-pruning.xml")));
    }

    @Test
    void testDuplicateDiscreteStepsPreserveMergedCondition() {
        Path outputDir = compile("compiler/duplicate-discrete-steps", "main.xml");
        Node output = Script.load(outputDir.resolve("main.xml"));
        assertThat(output, hasProperty("shared steps",
                node -> StreamSupport.stream(node.traverse(Kind.STEP::equals).spliterator(), false)
                        .filter(step -> "Shared".equals(step.attribute("name").getString()))
                        .collect(toList()),
                contains(isNode(
                        hasProperty("parent", Node::parent, isNode(
                                hasProperty("kind", Node::kind, is(Kind.CONDITION)),
                                hasProperty("condition",
                                        node -> node.expression().literal().replaceAll("\\s+", " ").trim(),
                                        anyOf(
                                                is("${app-type} != 'custom'"),
                                                is("${app-type} == 'quickstart' || ${app-type} == 'database'"),
                                                is("${app-type} == 'database' || ${app-type} == 'quickstart'")))))))));
    }

    @Test
    void testDuplicateCrossProductStepsCollapseCoveredDimension() {
        Path outputDir = compile("compiler/duplicate-cross-product-covered-dimension", "main.xml");
        Node output = Script.load(outputDir.resolve("main.xml"));
        assertThat(output, hasProperty("shared steps",
                node -> StreamSupport.stream(node.traverse(Kind.STEP::equals).spliterator(), false)
                        .filter(step -> "Shared".equals(step.attribute("name").getString()))
                        .collect(toList()),
                contains(isNode(
                        hasProperty("parent", Node::parent, isNode(
                                hasProperty("kind", Node::kind, is(Kind.CONDITION)),
                                hasProperty("condition",
                                        node -> node.expression().literal().replaceAll("\\s+", " ").trim(),
                                        anyOf(
                                                is("${app-type} == 'quickstart' || ${app-type} == 'database'"),
                                                is("${app-type} == 'database' || ${app-type} == 'quickstart'")))))))));
    }

    @Test
    void testOptionSpecificValueImplicationRemovesRedundantParentGuard() {
        Path outputDir = compile("compiler/option-specific-value-implication", "main.xml");
        Node output = Script.load(outputDir.resolve("main.xml"));
        assertThat(output, hasProperty("oci steps",
                node -> StreamSupport.stream(node.traverse(Kind.STEP::equals).spliterator(), false)
                        .filter(step -> "OCI".equals(step.attribute("name").getString()))
                        .collect(toList()),
                contains(isNode(
                        hasProperty("parent", Node::parent, isNode(
                                hasProperty("kind", Node::kind, is(Kind.CONDITION)),
                                hasProperty("condition", node -> node.expression().literal(),
                                        is("${app-type} == 'oci'"))))))));
    }

    @Test
    void testOptionSpecificValueImplicationPrunesImpossibleSiblingGuard() {
        Path outputDir = compile("compiler/option-specific-value-implication", "main.xml");
        String actual = normalizeXml(outputDir.resolve("main.xml"));
        assertThat(actual, not(containsString("name=\"Impossible\"")));
    }

    @Test
    void testDuplicateCrossProductStepsCollapseUnsupportedOptionComplement() {
        Path outputDir = compile("compiler/duplicate-cross-product-unsupported-option-complement", "main.xml");
        Node output = Script.load(outputDir.resolve("main.xml"));
        assertThat(output, hasProperty("shared steps",
                node -> StreamSupport.stream(node.traverse(Kind.STEP::equals).spliterator(), false)
                        .filter(step -> "Shared".equals(step.attribute("name").getString()))
                        .collect(toList()),
                contains(isNode(
                        hasProperty("parent", Node::parent, isNode(
                                hasProperty("kind", Node::kind, is(Kind.CONDITION)),
                                hasProperty("condition", node -> node.expression().literal(),
                                        is("${app-type} != 'oci'"))))))));
    }

    @Test
    void testConditionallyDefinedEnumValuePrunesImpossibleUnsupportedFlavorBranch() {
        Path outputDir = compile("compiler/conditional-json-lib", "main.xml");
        String actual = normalizeXml(outputDir.resolve("main.xml"));
        assertThat(actual, not(containsString("name=\"Impossible JSONP\"")));
    }

    @Test
    void testConditionallyDefinedEnumValueKeepsSelectedValueGuard() {
        Path outputDir = compile("compiler/conditional-json-lib", "main.xml");
        Node output = Script.load(outputDir.resolve("main.xml"));
        assertThat(output, hasProperty("jsonp steps",
                node -> StreamSupport.stream(node.traverse(Kind.STEP::equals).spliterator(), false)
                        .filter(step -> "JSON-P Only".equals(step.attribute("name").getString()))
                        .collect(toList()),
                contains(isNode(
                        hasProperty("parent", Node::parent, isNode(
                                hasProperty("kind", Node::kind, is(Kind.CONDITION)),
                                hasProperty("condition",
                                        node -> node.expression().literal().replaceAll("\\s+", " ").trim(),
                                        anyOf(
                                        is("${media} contains 'json' && ${media.json-lib} == 'jsonp'"),
                                        is("${media.json-lib} == 'jsonp' && ${media} contains 'json'")))))))));
    }

    @Test
    void testConditionallyDefinedEnumComplementDropsUnsupportedValueGuard() {
        Path outputDir = compile("compiler/conditional-json-lib", "main.xml");
        Node output = Script.load(outputDir.resolve("main.xml"));
        assertThat(output, hasProperty("binding steps",
                node -> StreamSupport.stream(node.traverse(Kind.STEP::equals).spliterator(), false)
                        .filter(step -> "JSON Binding".equals(step.attribute("name").getString()))
                        .collect(toList()),
                contains(isNode(
                        hasProperty("parent", Node::parent, isNode(
                                hasProperty("kind", Node::kind, is(Kind.CONDITION)),
                                hasProperty("condition",
                                        node -> node.expression().literal().replaceAll("\\s+", " ").trim(),
                                        anyOf(
                                        is("${flavor} != 'se' && ${media} contains 'json'"),
                                        is("${media} contains 'json' && ${flavor} != 'se'")))))))));
    }

    @Test
    void testOpenScalarDomainDoesNotEmitClosedComplement() {
        Path outputDir = compile("compiler/open-domain-complement", "main.xml");
        Node output = Script.load(outputDir.resolve("main.xml"));
        assertThat(output, hasProperty("json steps",
                node -> StreamSupport.stream(node.traverse(Kind.STEP::equals).spliterator(), false)
                        .filter(step -> "JSON Model".equals(step.attribute("name").getString()))
                        .collect(toList()),
                contains(isNode(
                        hasProperty("parent", Node::parent, isNode(
                                hasProperty("kind", Node::kind, is(Kind.CONDITION)),
                                hasProperty("condition",
                                        node -> node.expression().literal().replaceAll("\\s+", " ").trim(),
                                        allOf(
                                        not(containsString("!= 'jsonp'")),
                                        containsString("== 'jsonb'"),
                                        containsString("== 'jackson'")))))))));
    }

    @Test
    void testOpenScalarDomainKeepsOptionSpecificOutputGuard() {
        Path outputDir = compile("compiler/open-domain-option-output", "main.xml");
        Node output = Script.load(outputDir.resolve("main.xml"));
        assertThat(output, hasProperty("imports lists",
                node -> StreamSupport.stream(node.traverse(Kind.MODEL_LIST::equals).spliterator(), false)
                        .filter(list -> "imports".equals(list.attribute("key").getString()))
                        .collect(toList()),
                containsInAnyOrder(List.of(
                        isNode(hasProperty("parent", Node::parent, isNode(
                                hasProperty("kind", Node::kind, is(Kind.CONDITION)),
                                hasProperty("condition",
                                        node -> node.expression().literal().replaceAll("\\s+", " ").trim(),
                                        allOf(
                                        containsString("${app-type} == 'quickstart'"),
                                        containsString("${media.json-lib} == 'jsonp'")))))),
                        isNode(hasProperty("parent", Node::parent, isNode(
                                hasProperty("kind", Node::kind, is(Kind.CONDITION)),
                                hasProperty("condition",
                                        node -> node.expression().literal().replaceAll("\\s+", " ").trim(),
                                        allOf(
                                        containsString("${app-type} == 'database'"),
                                        containsString("${media.json-lib} == 'jsonp'")))))),
                        isNode(hasProperty("parent", Node::parent, isNode(
                                hasProperty("kind", Node::kind, is(Kind.CONDITION)),
                                hasProperty("condition",
                                        node -> node.expression().literal().replaceAll("\\s+", " ").trim(),
                                        allOf(
                                        containsString("${app-type} == 'custom'"),
                                        containsString("${media.json-lib} == 'jsonp'"))))))))));
    }

    @Test
    void testListOptionAvailabilityRemovesRedundantFlavorGuard() {
        Path outputDir = compile("compiler/list-option-availability", "main.xml");
        Node output = Script.load(outputDir.resolve("main.xml"));
        assertThat(output, hasProperty("client steps",
                node -> StreamSupport.stream(node.traverse(Kind.STEP::equals).spliterator(), false)
                        .filter(step -> "WebClient Support".equals(step.attribute("name").getString()))
                        .collect(toList()),
                contains(isNode(
                        hasProperty("parent", Node::parent, isNode(
                                hasProperty("kind", Node::kind, is(Kind.CONDITION)),
                                hasProperty("condition", node -> node.expression().literal(),
                                        is("${extra} contains 'webclient'"))))))));
    }

    @Test
    void testListOptionAvailabilityPrunesImpossibleFlavorBranch() {
        Path outputDir = compile("compiler/list-option-availability", "main.xml");
        String actual = normalizeXml(outputDir.resolve("main.xml"));
        assertThat(actual, not(containsString("name=\"Impossible WebClient\"")));
    }

    @Test
    void testUnsupportedConditionStillPrunesBranchSpecificInputs() {
        Path outputDir = compile("compiler/unsupported-pruning", "main.xml", IGNORE_ERRORS);
        assertThat(normalizeXml(outputDir.resolve("main.xml")),
                is(normalizeXml("compiler/expected/unsupported-pruning.xml")));
    }

    @Test
    void testUnsupportedParentConditionStillRelativizesBranchSpecificInputs() {
        Path outputDir = compile("compiler/unsupported-block-pruning", "main.xml", IGNORE_ERRORS);
        assertThat(normalizeXml(outputDir.resolve("main.xml")),
                is(normalizeXml("compiler/expected/unsupported-block-pruning.xml")));
    }

    @Test
    void testConstantVariableConditionUsesInputReachability() {
        Path outputDir = compile("compiler/preset-condition", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")),
                is(normalizeXml("compiler/expected/preset-condition.xml")));
    }

    @Test
    void testCompilerInitBuildsFlowCoreForCurrentSourceTree() {
        Path source = testResourcePath(ScriptCompilerTest.class, "compiler/preset-condition/main.xml");
        ScriptCompiler compiler = new ScriptCompiler(Script.Source.of(source), source.getParent());

        assertThat(compiler.sourceNode(), notNullValue());
        assertThat(compiler.flowIr(), notNullValue());
        assertThat(compiler.flowAnalysis(), notNullValue());
        assertThat(compiler.flowIr().ops().stream().filter(op -> op.kind() == Flow.Op.Kind.DEFINE_VALUE).count(),
                greaterThan(0L));
        assertThat(compiler.flowAnalysis().usesById().stream()
                        .map(use -> compiler.flowIr().symbols().symbol(use.symbolId()).name())
                        .collect(toList()),
                contains("db"));
    }

    @Test
    void testPresetImpliedBooleanPrunesImpossibleNestedInputStubBranch() {
        Path outputDir = compile("compiler/preset-implied-nested-input", "main.xml");
        Node output = Script.load(outputDir.resolve("main.xml"));
        assertThat(output, hasProperty("database steps",
                node -> StreamSupport.stream(node.traverse(Kind.STEP::equals).spliterator(), false)
                        .filter(step -> "Database".equals(step.attribute("name").getString()))
                        .collect(toList()),
                contains(isNode(
                        hasProperty("parent", Node::parent, isNode(
                                hasProperty("kind", Node::kind, is(Kind.CONDITION)),
                                hasProperty("condition",
                                        node -> node.expression().literal().replaceAll("\\s+", " ").trim(),
                                        anyOf(
                                                is("${app-type} != 'quickstart'"),
                                                is("${app-type} == 'custom' || ${app-type} == 'database'"),
                                                is("${app-type} == 'database' || ${app-type} == 'custom'")))))))));
        assertThat(output, hasProperty("db server stubs",
                node -> StreamSupport.stream(node.traverse(Kind.VARIABLE_TEXT::equals).spliterator(), false)
                        .filter(variable -> "~db.server".equals(variable.attribute("path").getString()))
                        .collect(toList()),
                is(List.of())));
    }

    @Test
    void testMergedNestedDefinitionStubsUseDefinitionComplement() {
        Path outputDir = compile("compiler/merged-stub-complement", "main.xml", IGNORE_ERRORS);
        Node output = Script.load(outputDir.resolve("main.xml"));
        Matcher<Iterable<? extends String>> complement = contains(anyOf(
                is("!${db} || ${flavor} != 'mp'"),
                is("${flavor} != 'mp' || !${db}")));
        assertThat(output, allOf(
                hasProperty("jpa stubs",
                        node -> (Iterable<String>) StreamSupport.stream(node.traverse(Kind::isVariable).spliterator(), false)
                                .filter(variable -> "~db.jpa-impl".equals(variable.attribute("path").getString()))
                                .map(variable -> normalizedCondition(variable.parent()))
                                .collect(toList()),
                        complement),
                hasProperty("cp stubs",
                        node -> (Iterable<String>) StreamSupport.stream(node.traverse(Kind::isVariable).spliterator(), false)
                                .filter(variable -> "~db.cp".equals(variable.attribute("path").getString()))
                                .map(variable -> normalizedCondition(variable.parent()))
                                .collect(toList()),
                        complement)));
    }

    @Test
    void testCoveringNestedDefinitionStubUsesDefinitionComplement() {
        Path outputDir = compile("compiler/covering-stub-complement", "main.xml", IGNORE_ERRORS);
        Node output = Script.load(outputDir.resolve("main.xml"));
        assertThat(output, hasProperty("cp stubs",
                node -> (Iterable<String>) StreamSupport.stream(node.traverse(Kind::isVariable).spliterator(), false)
                        .filter(variable -> "~db.cp".equals(variable.attribute("path").getString()))
                        .map(variable -> normalizedCondition(variable.parent()))
                        .collect(toList()),
                contains(anyOf(
                        is("!${db} || ${flavor} != 'mp'"),
                        is("${flavor} != 'mp' || !${db}")))));
    }

    @Test
    void testUnsupportedConditionKeepsBindingBackedReachability() {
        Path outputDir = compile("compiler/unsupported-binding-condition", "main.xml", IGNORE_ERRORS);
        assertThat(normalizeXml(outputDir.resolve("main.xml")),
                is(normalizeXml("compiler/expected/unsupported-binding-condition.xml")));
    }

    @Test
    void testUnsupportedConditionKeepsBindingBackedModelOutputCondition() {
        Path outputDir = compile("compiler/unsupported-output-model-condition", "main.xml", IGNORE_ERRORS);
        assertThat(normalizeXml(outputDir.resolve("main.xml")),
                is(normalizeXml("compiler/expected/unsupported-output-model-condition.xml")));
    }

    @Test
    void testVariables1() {
        Path outputDir = compile("compiler/variables1", "main.xml", IGNORE_ERRORS);
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/variables1.xml")));
    }

    @Test
    void testVariables2() {
        Path outputDir = compile("compiler/variables2", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/variables2.xml")));
    }

    @Test
    void testVariables3() {
        Path outputDir = compile("compiler/variables3", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/variables3.xml")));
    }

    @Test
    void testBooleanInputConditionDoesNotGenerateStubVariable() {
        Path outputDir = compile("compiler/no-boolean-stubs", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")),
                is(normalizeXml("compiler/expected/no-boolean-stubs.xml")));
    }

    @Test
    void testResidualListConditions() {
        Path outputDir = compile("compiler/residual-list", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/residual-list.xml")));
    }

    @Test
    void testNormalizedExpressions() {
        Path outputDir = compile("compiler/normalized-expr", "main.xml");
        assertThat(normalizeXml(outputDir.resolve("main.xml")), is(normalizeXml("compiler/expected/normalized-expr.xml")));
    }

    @Test
    void testMergedStepConditionUsesPreReducedEmission() {
        Path outputDir = compile("compiler/merged-step-domain", "main.xml");
        String actual = normalizeXml(outputDir.resolve("main.xml"));
        assertThat(actual, containsString("<step name=\"Packaging\" optional=\"true\">"));
        assertThat(actual, not(containsString("name=\"Packaging\" optional=\"true\" if=\"")));
    }

    @Test
    void testOptionalInputWithoutDefault() {
        try {
            compile("compiler/validate", "optional-input-no-default.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(INPUT_OPTIONAL_NO_DEFAULT))));
        }
    }

    @Test
    void testOptionalStepWithNonOptionalInputs() {
        try {
            compile("compiler/validate", "optional-step-required-input.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(STEP_DECLARED_OPTIONAL))));
        }
    }

    @Test
    void testRequiredStepWithOnlyOptionalInputs() {
        try {
            compile("compiler/validate", "required-step-optional-input.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(STEP_NOT_DECLARED_OPTIONAL))));
        }
    }

    @Test
    void testRequiredStepWithinOptionalStep() {
        try {
            compile("compiler/validate", "required-step-within-optional-step.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(STEP_DECLARED_OPTIONAL))));
        }
    }

    @Test
    void testStepWithNoInputs() {
        try {
            compile("compiler/validate", "step-with-no-inputs.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(STEP_NO_INPUT))));
        }
    }

    @Test
    void testOptionalStepWithinRequiredStep() {
        compile("compiler/validate", "optional-step-within-required-step.xml", VALIDATE_ONLY);
    }

    @Test
    void testUnresolvedPreset() {
        try {
            compile("compiler/validate", "unresolved-preset.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(PRESET_UNRESOLVED))));
        }
    }

    @Test
    void testPresetTypeMismatch() {
        try {
            compile("compiler/validate", "preset-type-mismatch.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(EXPR_EVAL_ERROR),
                    containsString(PRESET_TYPE_MISMATCH))));
        }
    }

    @Test
    void testExpressionWithIncompatibleOperators() {
        try {
            compile("compiler/validate", "expression-incompatible-operators.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(EXPR_INCOMPATIBLE_OPERATOR),
                    containsString(EXPR_INCOMPATIBLE_OPERATOR),
                    containsString(EXPR_INCOMPATIBLE_OPERATOR))));
        }
    }

    @Test
    void testExpressionWithIncompatibleOperatorsAndTypeMismatch() {
        try {
            compile("compiler/validate",
                    "expression-incompatible-operators-type-mismatch.xml",
                    VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(EXPR_INCOMPATIBLE_OPERATOR),
                    containsString(EXPR_INCOMPATIBLE_OPERATOR),
                    containsString(EXPR_EVAL_ERROR))));
        }
    }

    @Test
    void testExpressionWithUnsupportedConditionShape() {
        try {
            compile("compiler/validate", "expression-unsupported-condition-shape.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(EXPR_UNSUPPORTED_CONDITION))));
        }
    }

    @Test
    void testUnsupportedConditionWithGuardedVariablesDoesNotReportUnresolvedVariable() {
        try {
            compile("compiler/validate", "expression-unsupported-guarded-variable.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(EXPR_UNSUPPORTED_CONDITION))));
        }
    }

    @Test
    void testExpressionWithDirectTextInputControlFlow() {
        try {
            compile("compiler/validate", "expression-text-input-direct.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(EXPR_TEXT_INPUT_CONTROL_FLOW))));
        }
    }

    @Test
    void testExpressionWithTextInputDerivedValueControlFlow() {
        try {
            compile("compiler/validate", "expression-text-input-via-value.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(EXPR_TEXT_INPUT_CONTROL_FLOW))));
        }
    }

    @Test
    void testExpressionWithTextInputDerivedDeclarationAvailability() {
        try {
            compile("compiler/validate",
                    "expression-text-input-via-declaration.xml",
                    VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(EXPR_TEXT_INPUT_CONTROL_FLOW),
                    containsString(EXPR_TEXT_INPUT_CONTROL_FLOW))));
        }
    }

    @Test
    void testExpressionWithUnresolvedVariable1() {
        try {
            compile("compiler/validate", "expression-unresolved-variable1.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(EXPR_UNRESOLVED_VARIABLE))));
        }
    }

    @Test
    void testExpressionWithUnresolvedVariable2() {
        try {
            compile("compiler/validate", "expression-unresolved-variable2.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(EXPR_UNRESOLVED_VARIABLE))));
        }
    }

    @Test
    void testExpressionWithUnresolvedVariable3() {
        compile("compiler/validate", "expression-unresolved-variable3.xml", VALIDATE_ONLY);
    }

    @Test
    void testExpressionWithGuardedListVariable() {
        compile("compiler/validate", "expression-guarded-variable-list.xml", VALIDATE_ONLY);
    }

    @Test
    void testExpressionWithGuardedBooleanVariable() {
        compile("compiler/validate", "expression-guarded-variable-boolean.xml", VALIDATE_ONLY);
    }

    @Test
    void testExpressionWithGuardedVariableResolvedViaPresetBinding() {
        compile("compiler/validate", "expression-guarded-variable-via-preset.xml", VALIDATE_ONLY);
    }

    @Test
    void testExpressionWithGuardedVariableResolvedViaTextDeclaration() {
        compile("compiler/validate",
                "expression-guarded-variable-via-text-declaration.xml",
                VALIDATE_ONLY);
    }

    @Test
    void testExpressionWithGuardedVariableResolvedViaUnsupportedDeclaration() {
        try {
            compile("compiler/validate",
                    "expression-guarded-variable-via-unsupported-declaration.xml",
                    VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(EXPR_UNSUPPORTED_CONDITION),
                    containsString(EXPR_UNSUPPORTED_CONDITION),
                    containsString(EXPR_UNSUPPORTED_CONDITION))));
        }
    }

    @Test
    void testExpressionWithGuardedVariableResolvedViaInlinedUnsupportedAlias() {
        try {
            compile("compiler/validate",
                    "expression-guarded-variable-via-inlined-unsupported-alias.xml",
                    VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(EXPR_UNSUPPORTED_CONDITION))));
        }
    }

    @Test
    void testExpressionWithGuardedVariableResolvedViaUnsupportedAvailability() {
        try {
            compile("compiler/validate",
                    "expression-guarded-variable-via-unsupported-availability.xml",
                    VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors().size(), is(2));
            assertThat(ex.errors().get(0), containsString(EXPR_UNSUPPORTED_CONDITION));
            assertThat(ex.errors().get(1), containsString(EXPR_UNSUPPORTED_CONDITION));
        }
    }

    @Test
    void testExpressionWithBareTextInputControlFlow() {
        try {
            compile("compiler/validate", "expression-type-mismatch1.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(EXPR_TEXT_INPUT_CONTROL_FLOW))));
        }
    }

    @Test
    void testExpressionWithTypeMismatch2() {
        try {
            compile("compiler/validate", "expression-type-mismatch2.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(EXPR_EVAL_ERROR))));
        }
    }

    @Test
    void testInputAlreadyDeclared1() {
        try {
            compile("compiler/validate", "input-already-declared1.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(INPUT_ALREADY_DECLARED))));
        }
    }

    @Test
    void testInputAlreadyDeclared2() {
        try {
            compile("compiler/validate", "input-already-declared2.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(INPUT_ALREADY_DECLARED))));
        }
    }

    @Test
    void testInputTypeMismatch() {
        try {
            compile("compiler/validate", "input-type-mismatch.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(INPUT_TYPE_MISMATCH))));
        }
    }

    @Test
    void testInputNotInStep() {
        try {
            compile("compiler/validate", "input-not-in-step.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(INPUT_NOT_IN_STEP))));
        }
    }

    @Test
    void testTextInputRejectsNestedValues() {
        Path cwd = targetDir(ScriptCompilerTest.class).toAbsolutePath().normalize();
        try {
            compile(new Script.Source() {
                @Override
                public Node readScript(boolean readOnly, Script.Loader loader) {
                    Node textInput = Nodes.inputText("name", Nodes.inputs(Nodes.inputBoolean("flag")));
                    Node step = Nodes.step("Text", Nodes.inputs(textInput));
                    return Nodes.script(step);
                }

                @Override
                public Path path() {
                    return cwd.resolve("programmatic.xml");
                }
            }, cwd, VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(INPUT_TEXT_NESTED_VALUES))));
        }
    }

    @Test
    void testOptionValueAlreadyDeclared() {
        try {
            compile("compiler/validate", "option-value-already-declared.xml", VALIDATE_ONLY);
            fail("An exception should have been thrown");
        } catch (ValidationException ex) {
            assertThat(ex.errors(), contains(List.of(
                    containsString(OPTION_VALUE_ALREADY_DECLARED))));
        }
    }

    static Path compile(String path, String entrypoint, ScriptCompiler.Options... features) {
        Path targetDir = targetDir(ScriptCompilerTest.class);
        try (FileSystem fs = VirtualFileSystem.create(targetDir.resolve("test-classes"))) {
            Path cwd = fs.getPath(path);
            Path source = cwd.resolve(entrypoint).toAbsolutePath().normalize();
            return compile(Script.Source.of(source), cwd, features);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex.getMessage(), ex);
        }
    }

    private static Path compile(Script.Source source,
                                Path cwd,
                                ScriptCompiler.Options... features) {
        Path targetDir = targetDir(ScriptCompilerTest.class);
        ScriptCompiler compiler = new ScriptCompiler(source, cwd);
        Path outputDir = unique(targetDir.resolve("compiler-ut"), fileName(cwd));
        compiler.compile(List.of(features)).write(outputDir);
        return outputDir;
    }

    static final Pattern XML_COMMENT = Pattern.compile("[^\\S\\r\\n]*<!--[^>]*-->\n", Pattern.DOTALL);
    static final Pattern XML_NAMESPACE = Pattern.compile("\\s+((xmlns|xsi)(:\\w+)?=\"[^\"]+)");

    static String normalizeXml(String path) {
        return normalizeXml(testResourcePath(XMLScriptWriterTest.class, path));
    }

    static String normalizeXml(Path file) {
        try {
            String raw = Strings.normalizeNewLines(Files.readString(file));
            return normalizeXmlString(raw);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String normalizeXmlString(String xml) {
        String str = XML_COMMENT.matcher(xml).replaceAll("").trim();
        return XML_NAMESPACE.matcher(str).replaceAll("\n        $1");
    }

    static String normalizedCondition(Node node) {
        return node.kind() == Kind.CONDITION
                ? node.expression().literal().replaceAll("\\s+", " ").trim()
                : Expression.TRUE.literal();
    }

    static <T, U> Matcher<T> hasProperty(String name, Function<T, U> extractor, Matcher<U> subMatcher) {
        return new FeatureMatcher<>(subMatcher, "has property " + name, name) {
            @Override
            protected U featureValueOf(T target) {
                return extractor.apply(target);
            }
        };
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    static Matcher<Node> isNode(Matcher<? extends Node>... matchers) {
        var list = new ArrayList<Matcher<? super Node>>();
        for (var matcher : matchers) {
            list.add((Matcher<Node>) matcher);
        }
        return allOf(list);
    }
}
