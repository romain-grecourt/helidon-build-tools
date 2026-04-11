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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.archetype.engine.v2.Domain.Guard;
import io.helidon.build.archetype.engine.v2.Domain.Symbol.Fact;
import io.helidon.build.archetype.engine.v2.Expression.Token;
import io.helidon.build.archetype.engine.v2.Node.Kind;
import io.helidon.build.archetype.engine.v2.Value.Type;
import io.helidon.build.common.Combinatorics;
import io.helidon.build.common.InputStreams;
import io.helidon.build.common.Lists;
import io.helidon.build.common.Maps;
import io.helidon.build.common.PropertyEvaluator;
import io.helidon.build.common.SourcePath;

import static io.helidon.build.archetype.engine.v2.Domain.Guard.FALSE;
import static io.helidon.build.archetype.engine.v2.Domain.Guard.TRUE;
import static io.helidon.build.common.Checksum.md5;
import static io.helidon.build.common.FileUtils.ensureDirectory;
import static io.helidon.build.common.FileUtils.readAllBytes;
import static java.util.Objects.requireNonNull;

/**
 * Script compiler.
 */
public class ScriptCompiler {

    /**
     * Compiler validation exception.
     */
    public static class ValidationException extends RuntimeException {
        private final List<String> errors;

        ValidationException(List<String> errors) {
            this.errors = errors;
        }

        /**
         * Get the errors.
         *
         * @return errors
         */
        public List<String> errors() {
            return errors;
        }

        @Override
        public String getMessage() {
            return System.lineSeparator() + String.join(System.lineSeparator(), errors);
        }
    }

    /**
     * Compiler image.
     */
    public static final class Image {
        private final Node node = Nodes.script();
        private final Map<String, byte[]> blobs = new HashMap<>();

        private Image() {
        }

        /**
         * Get the node.
         *
         * @return Node
         */
        public Node node() {
            return node;
        }

        /**
         * Write the image.
         *
         * @param outputDir output directory
         */
        public void write(Path outputDir) {
            try {
                // write entrypoint
                ensureDirectory(outputDir);
                Path entrypoint = outputDir.resolve("main.xml");
                try (XMLScriptWriter writer = new XMLScriptWriter(Files.newBufferedWriter(entrypoint), true)) {
                    writer.writeScript(node);
                }

                // write blobs
                if (!blobs.isEmpty()) {
                    Path blobsDir = ensureDirectory(outputDir.resolve("blobs"));
                    for (Entry<String, byte[]> entry : blobs.entrySet()) {
                        Files.write(blobsDir.resolve(entry.getKey()), entry.getValue());
                    }
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    /**
     * Compiler option.
     */
    public interface Option {
        /**
         * Run the option.
         *
         * @param compiler compiler
         */
        default void run(ScriptCompiler compiler) {
        }
    }

    /**
     * Compiler options.
     */
    public enum Options implements Option {
        /**
         * Only validate.
         */
        VALIDATE_ONLY,
        /**
         * Skip the validation.
         */
        SKIP_VALIDATION,
        /**
         * Ignore errors.
         */
        IGNORE_ERRORS,
        /**
         * Remove outputs.
         */
        NO_OUTPUT,
        /**
         * Remove transient variables.
         */
        NO_TRANSIENT
    }

    static final String PRESET_UNRESOLVED = "Preset input cannot be resolved";
    static final String PRESET_TYPE_MISMATCH = "Preset type mismatch";
    static final String EXPR_INCOMPATIBLE_OPERATOR = "Expression contains a non compatible operator";
    static final String EXPR_UNSUPPORTED_CONDITION = "Expression contains an unsupported condition shape";
    static final String EXPR_UNRESOLVED_VARIABLE = "Expression contains an unresolved variable";
    static final String EXPR_EVAL_ERROR = "Expression evaluated with an error";
    static final String STEP_NO_INPUT = "Step does not contain any input";
    static final String STEP_DECLARED_OPTIONAL = "Step is declared as optional but includes non optional input";
    static final String STEP_NOT_DECLARED_OPTIONAL = "Step is not declared as optional but includes only optional input";
    static final String INPUT_ALREADY_DECLARED = "Input already declared in current scope";
    static final String INPUT_TYPE_MISMATCH = "Input is declared in another scope with a different type";
    static final String INPUT_OPTIONAL_NO_DEFAULT = "Input is optional but does not have a default value";
    static final String INPUT_NOT_IN_STEP = "Input is not nested within a step";
    static final String INPUT_TEXT_NESTED_VALUES = "Text input cannot contain nested values or directives";
    static final String OPTION_VALUE_ALREADY_DECLARED = "Option value is already declared";
    static final String EXPR_TEXT_INPUT_CONTROL_FLOW = "Expression depends on a user text input";

    private boolean initialized;
    private final Map<Node, Map<String, Fact>> factCache = new IdentityHashMap<>();
    private final Map<Node, Guard> renderGuardsByNode = new IdentityHashMap<>();
    private final Map<Node, Guard> activeGuardsByNode = new IdentityHashMap<>();
    private final Map<String, Boolean> textInputProvenance = new HashMap<>();
    private final Set<String> errors = new LinkedHashSet<>();
    private final Script.Source source;
    private final Context ctx = new Context();
    private final ScriptInliner inliner;
    private final ScriptIndexer indexer;
    private final Flow flow;
    private Node sourceNode;
    private List<Option> options;

    /**
     * Create a new instance.
     *
     * @param source source
     * @param cwd    cwd
     */
    public ScriptCompiler(Script.Source source, Path cwd) {
        this.ctx.pushCwd(requireNonNull(cwd, "cwd is null"));
        this.source = requireNonNull(source, "source is null");
        this.flow = new Flow(ctx.scope());
        this.inliner = new ScriptInliner(ctx);
        this.indexer = new ScriptIndexer(rootScope());
    }

    /**
     * Compile the given script.
     *
     * @param options options
     * @return {@code true} if successful, {@code false} otherwise
     */
    public Image compile(List<Option> options) {
        this.options = options;
        init();

        // validate
        if (!options.contains(Options.SKIP_VALIDATION)) {
            validate();
        }

        // run options
        for (Option option : options) {
            option.run(this);
        }

        // exit on error
        if (!errors.isEmpty() && !options.contains(Options.IGNORE_ERRORS)) {
            throw new ValidationException(Lists.drain(errors));
        }

        Image image = new Image();
        if (!options.contains(Options.VALIDATE_ONLY)) {
            new LegacyBackend(image).emit();
        }
        return image;
    }

    /**
     * Get the source node.
     *
     * @return Node
     */
    public Node sourceNode() {
        init();
        return sourceNode;
    }

    /**
     * Get the validation errors.
     *
     * @return errors
     */
    public Set<String> errors() {
        return errors;
    }

    void validateOnly() {
        init();
        validate();
        if (!errors.isEmpty()) {
            throw new ValidationException(Lists.drain(errors));
        }
    }

    private void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        sourceNode = inliner.inline(source);
        indexer.index(sourceNode);
        flow.process(sourceNode);
        projectSourceGuards();
    }

    private void projectSourceGuards() {
        for (Node node : sourceNode.traverse()) {
            Guard renderGuard = node == sourceNode
                    ? Guard.TRUE
                    : requireNonNull(activeGuardsByNode.get(node.parent()),
                            "Missing parent active guard for source node: " + node.kind() + "#" + node.id());
            renderGuardsByNode.put(node, renderGuard);
            activeGuardsByNode.put(node, localActiveGuard(node, renderGuard));
        }
    }

    private Guard localActiveGuard(Node node, Guard currentGuard) {
        Expression expr = controlExpression(node);
        if (expr == Expression.TRUE) {
            return currentGuard;
        }
        if (flow.isFalse(currentGuard) || expr == Expression.FALSE) {
            return FALSE;
        }
        Scope scope = scope(node);
        ExpressionAnalyzer analyzer = new ExpressionAnalyzer(scope, facts(node, currentGuard), false, true);
        ExpressionAnalysis analysis = analyzer.analyze(expr);
        Guard local = localGuard(expr, scope, currentGuard, analysis);
        Guard full = flow.and(currentGuard, local);
        return node.kind() == Kind.CONDITION ? simplifyProjectedConditionGuard(full, scope) : full;
    }

    private Guard localGuard(Expression expr, Scope scope, Guard currentGuard, ExpressionAnalysis analysis) {
        if (analysis.reach != null) {
            return analysis.reach;
        }
        Expression truth = flow.expression(currentGuard);
        if (analysis.supportedTerms != null && analysis.supportedTerms.hasSupportedTerms) {
            Guard supported = analysis.supportedTerms.supported;
            Guard supportedTruth = flow.and(currentGuard, supported);
            Expression unsupported = normalize(analysis.supportedTerms.unsupported,
                    scope).reduce(flow.expression(supportedTruth));
            if (unsupported == Expression.FALSE) {
                return FALSE;
            }
            if (unsupported == Expression.TRUE) {
                return supported;
            }
            return flow.and(supported, flow.residualGuard(unsupported));
        }
        Expression normalized = normalize(expr, scope).reduce(truth);
        return flow.residualGuard(normalized);
    }

    private Scope rootScope() {
        Scope scope = ctx.scope();
        while (scope.parent() != null) {
            scope = scope.parent();
        }
        return scope;
    }

    private final class LegacyBackend {
        private final Image image;

        private LegacyBackend(Image image) {
            this.image = image;
        }

        private void emit() {
            Map<Node, Node> mirrors = new HashMap<>();
            Map<Node, Guard> reachableBlocks = new IdentityHashMap<>();
            Map<Node, Set<String>> conditionRefs = new IdentityHashMap<>();
            mirrors.put(sourceNode, image.node);
            mirrors.put(image.node, sourceNode);
            reachableBlocks.put(image.node, TRUE);
            sourceNode.visit(new InputVisitor(image, mirrors, reachableBlocks, conditionRefs));
            if (!options.contains(Options.NO_OUTPUT)) {
                sourceNode.visit(new OutputVisitor(image, conditionRefs));
            }
            image.node.visit(new StubsVisitor(mirrors, reachableBlocks, conditionRefs));
            image.node.visit(new DedupVisitor(mirrors));
        }
    }

    private void validate() {
        validateBooleanInputDeclarations();
        validatePresets();
        validateInputTypes();
        validateExpressions();
        validateOptions();
        validateInputs();
        validateSteps();
    }

    private void validateBooleanInputDeclarations() {
        for (Node node : sourceNode.traverse(Kind.INPUT_BOOLEAN::equals)) {
            if (inactiveInput(node)) {
                continue;
            }
            String key = flow.key(node);
            Value<?> value = flow.declaredValue(node, key);
            if (value.type() == Type.EMPTY || value.type() == Type.BOOLEAN) {
                continue;
            }
            try {
                value.getBoolean();
            } catch (RuntimeException ex) {
                errors.add(String.format(
                        "%s %s: '%s'",
                        node.location(),
                        EXPR_EVAL_ERROR,
                        ex.getMessage()));
            }
        }
    }

    private void validatePresets() {
        Map<String, List<Node>> inputs = new HashMap<>();
        for (Node node : sourceNode.traverse(Kind::isInput)) {
            inputs.computeIfAbsent(flow.key(node), k -> new ArrayList<>()).add(node);
        }
        for (Node node : sourceNode.traverse(Kind::isPreset)) {
            String path = node.attribute("path").getString();
            List<Node> resolved = inputs.getOrDefault(path, List.of());
            if (resolved.isEmpty()) {
                errors.add(String.format(
                        "%s %s: '%s'",
                        node.location(),
                        PRESET_UNRESOLVED,
                        path));
            } else {
                Type valueType = node.kind().valueType();
                for (Node n : resolved) {
                    if (valueType != n.kind().valueType()) {
                        errors.add(String.format(
                                "%s %s: '%s', expected: %s, actual: %s",
                                n.location(),
                                PRESET_TYPE_MISMATCH,
                                path,
                                node.kind(),
                                n.kind()));
                    }
                }
            }
        }
    }

    private void validateInputTypes() {
        Map<String, Kind> kinds = new HashMap<>();
        for (Node node : sourceNode.traverse(Kind::isInput)) {
            if (inactiveInput(node)) {
                continue;
            }
            String scopeId = flow.key(node);
            Kind expected = kinds.computeIfAbsent(scopeId, k -> node.kind());
            if (expected != node.kind()) {
                errors.add(String.format("%s %s: '%s'",
                        node.location(),
                        INPUT_TYPE_MISMATCH,
                        scopeId));
            }
        }
    }

    private void validateExpressions() {
        for (Node node : sourceNode.traverse(Kind.CONDITION::equals)) {
            if (node.expression() == Expression.FALSE) {
                continue;
            }
            Expression expr = node.expression();
            Scope scope = scope(node);
            Map<String, Fact> facts = facts(node);
            Guard blockReach = flow.activeGuard(node.parent());
            ExpressionAnalyzer analyzer = new ExpressionAnalyzer(scope, facts, true, false);
            ExpressionAnalysis analysis = analyzer.analyze(expr);
            boolean compatible = analysis.incompatibleOps.isEmpty();

            // check operators compatibility
            for (Expression.Operator operator : analysis.incompatibleOps) {
                errors.add(String.format("%s %s: '%s'",
                        node.location(),
                        EXPR_INCOMPATIBLE_OPERATOR,
                        operator.symbol()));
            }

            if (dependsOnTextInput(analysis.refs(), new HashSet<>())) {
                errors.add(String.format(
                        "%s %s: '%s'",
                        node.location(),
                        EXPR_TEXT_INPUT_CONTROL_FLOW,
                        expr.literal()));
                continue;
            }

            if (unreachable(node)) {
                continue;
            }

            boolean resolved = true;
            Map<String, Guard> variableDemands = analysis.variableDemands;
            for (Entry<String, String> variable : analysis.variables.entrySet()) {
                String ref = variable.getValue();
                boolean variableResolved = false;
                Guard requiredReach = blockReach;
                Guard variableDemand = variableDemands.get(ref);
                if (variableDemand != null) {
                    requiredReach = requiredReach == null ? variableDemand : flow.and(requiredReach, variableDemand);
                }
                Fact refFact = fact(facts, ref);
                Guard refReach = refFact == null ? null : refFact.definedUnder();
                if (requiredReach != null && refReach != null) {
                    variableResolved = flow.contains(refReach, requiredReach);
                    if (!variableResolved) {
                        variableResolved = coversUnderFacts(refReach, requiredReach, scope, facts);
                    }
                }
                Guard bindingReach = null;
                if (!variableResolved && requiredReach != null && refReach != null) {
                    // replay constant bindings against the recorded definition reachability first
                    analyzer = new ExpressionAnalyzer(scope, facts, false, false);
                    bindingReach = analyzer.analyze(flow.expression(refReach)).reach;
                    if (bindingReach != null) {
                        variableResolved = flow.contains(bindingReach, requiredReach);
                        if (!variableResolved) {
                            variableResolved = coversUnderFacts(bindingReach, requiredReach, scope, facts);
                        }
                    }
                }
                if (!variableResolved && bindingReach == null) {
                    // unsupported residual shapes still need a prior declaration
                    // check so they report EXPR_UNSUPPORTED_CONDITION instead of
                    // EXPR_UNRESOLVED_VARIABLE.
                    variableResolved = hasPriorDefinition(node, ref);
                }
                if (!variableResolved) {
                    resolved = false;
                    errors.add(String.format("%s %s: '%s'",
                            node.location(),
                            EXPR_UNRESOLVED_VARIABLE,
                            variable.getKey()));
                }
            }

            // check evaluation
            if (resolved) {
                String evalError = analysis.evaluationError;
                if (evalError != null) {
                    errors.add(String.format(
                            "%s %s: '%s'",
                            node.location(),
                            EXPR_EVAL_ERROR,
                            evalError));
                    continue;
                }
                if (compatible && analysis.reach == null) {
                    errors.add(String.format(
                            "%s %s: '%s'",
                            node.location(),
                            EXPR_UNSUPPORTED_CONDITION,
                            expr.literal()));
                }
            }
        }
    }

    private Value<?> validationValue(Scope scope, String variable) {
        Type type = indexer.refType(scope.key(variable));
        return Value.typed(type);
    }

    private boolean hasPriorDefinition(Node node, String key) {
        for (Node definition : indexer.definedRefs(key)) {
            if (node.id() <= definition.id()) {
                continue;
            }
            Guard reach = definitionGuard(definition);
            if (flow.isFalse(reach)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean dependsOnTextInput(Iterable<String> refs, Set<String> visitingRefs) {
        for (String ref : refs) {
            if (dependsOnTextInput(ref, visitingRefs)) {
                return true;
            }
        }
        return false;
    }

    private boolean dependsOnTextInput(String key, Set<String> visitingRefs) {
        Boolean cached = textInputProvenance.get(key);
        if (cached != null) {
            return cached;
        }
        if (!visitingRefs.add(key)) {
            return false;
        }
        boolean result = indexer.textInputRef(key);
        if (!result) {
            for (Node node : indexer.declaredValues(key)) {
                if (attached(node) && declarationDependsOnTextInput(node, visitingRefs)) {
                    result = true;
                    break;
                }
            }
        }
        visitingRefs.remove(key);
        textInputProvenance.put(key, result);
        return result;
    }

    private boolean declarationDependsOnTextInput(Node node, Set<String> visitingRefs) {
        for (Node ancestor = node.parent(); ancestor != null; ancestor = ancestor.parent()) {
            if (ancestor.kind() == Kind.INPUT_TEXT) {
                return true;
            }
            if (ancestor.kind() == Kind.CONDITION) {
                Scope scope = scope(ancestor);
                ExpressionAnalyzer analyzer = new ExpressionAnalyzer(scope, Map.of(), false, false);
                ExpressionAnalysis analysis = analyzer.analyze(ancestor.expression());
                if (dependsOnTextInput(analysis.refs(), visitingRefs)) {
                    return true;
                }
            }
        }
        if (node.value().type() != Type.STRING && node.value().type() != Type.DYNAMIC) {
            return false;
        }
        String value = node.value().getString();
        if (!value.contains("${")) {
            return false;
        }
        try {
            Scope scope = scope(node.parent());
            Set<String> refs = new LinkedHashSet<>();
            PropertyEvaluator.evaluate(value, var -> {
                refs.add(scope.key(var));
                return "";
            });
            for (String ref : refs) {
                if (dependsOnTextInput(ref, visitingRefs)) {
                    return true;
                }
            }
        } catch (RuntimeException ex) {
            return false;
        }
        return false;
    }

    private void validateOptions() {
        Map<Node, Set<Node>> options = new HashMap<>();
        for (Node option : sourceNode.traverse(Kind.INPUT_OPTION::equals)) {
            if (inactiveOption(option)) {
                continue;
            }
            for (Node input : option.ancestors(Kind::isInput)) {
                if (inactiveInput(input)) {
                    break;
                }
                options.computeIfAbsent(input, k -> new HashSet<>()).add(option);
                break;
            }
        }
        options.forEach((input, nodes) -> {
            Set<String> values = new HashSet<>();
            for (Node option : nodes) {
                String value = option.value().getString();
                if (!values.add(value)) {
                    errors.add(String.format(
                            "%s %s: '%s'",
                            input.location(),
                            OPTION_VALUE_ALREADY_DECLARED,
                            value));
                }
            }
        });
    }

    private void validateInputs() {
        Map<String, List<Node>> inputs = new HashMap<>();
        for (Node input : sourceNode.traverse(Kind::isInput)) {
            if (inactiveInput(input)) {
                continue;
            }
            Scope scope = scope(input);

            // optional no default
            if (input.attribute("optional").asBoolean().orElse(false)
                && input.attribute("default").isEmpty()) {
                switch (input.kind()) {
                    case INPUT_ENUM:
                    case INPUT_TEXT:
                        errors.add(String.format("%s %s: '%s'",
                                input.location(),
                                INPUT_OPTIONAL_NO_DEFAULT,
                                scope.key()));
                        break;
                    default:
                }
            }

            if (input.kind() == Kind.INPUT_TEXT && !input.children().isEmpty()) {
                errors.add(String.format("%s %s: '%s'",
                        input.location(),
                        INPUT_TEXT_NESTED_VALUES,
                        scope.key()));
            }

            if (input.ancestor(Kind.STEP::equals).isEmpty()) {
                // input not in a step
                errors.add(String.format("%s %s: '%s'",
                        input.location(),
                        INPUT_NOT_IN_STEP,
                        scope.key()));
            } else {
                // input already declared
                Iterable<Node> actual = input.ancestors(Kind::isInput);
                for (Node n : inputs.getOrDefault(scope.key(), List.of())) {
                    // only allow duplicates if the common ancestor is an enum
                    Node firstMatch = Lists.firstMatch(actual, n.ancestors(Kind::isInput));
                    if (firstMatch == null || firstMatch.kind() != Kind.INPUT_ENUM) {
                        errors.add(String.format("%s %s: '%s'",
                                input.location(),
                                INPUT_ALREADY_DECLARED,
                                scope.key()));
                        break;
                    }
                }
                inputs.computeIfAbsent(scope.key(), k -> new ArrayList<>()).add(input);
            }
        }
    }

    private void validateSteps() {
        for (Node step : sourceNode.traverse(Kind.STEP::equals)) {
            if (unreachable(step)) {
                continue;
            }
            List<Node> allInputs = step.collect(Kind::isInput);
            if (allInputs.isEmpty()) {
                errors.add(String.format("%s %s",
                        step.location(),
                        STEP_NO_INPUT));
                continue;
            }
            List<Node> inputs = new ArrayList<>();
            for (Node input : allInputs) {
                if (!inactiveInput(input)) {
                    inputs.add(input);
                }
            }
            if (!inputs.isEmpty()) {
                boolean optionalStep = step.attribute("optional").asBoolean().orElse(false);
                boolean optionalInputs = true;
                for (Node input : inputs) {
                    if (!input.attribute("optional").asBoolean().orElse(false)) {
                        optionalInputs = false;
                        break;
                    }
                }
                if (optionalStep && !optionalInputs) {
                    errors.add(String.format("%s %s",
                            step.location(),
                            STEP_DECLARED_OPTIONAL));
                } else if (!optionalStep && optionalInputs) {
                    errors.add(String.format("%s %s",
                            step.location(),
                            STEP_NOT_DECLARED_OPTIONAL));
                }
            }
        }
    }

    Scope scope(Node node) {
        return flow.scope(node);
    }

    private Expression controlExpression(Node node) {
        switch (node.kind()) {
            case CONDITION:
                return node.expression();
            case INPUT_BOOLEAN:
            case INPUT_OPTION:
                return expressionContribution(node, flow::key);
            default:
                return Expression.TRUE;
        }
    }

    private Map<String, Fact> facts(Node node) {
        return factCache.computeIfAbsent(node, this::facts0);
    }

    private Map<String, Fact> facts(Node node, Guard required) {
        return constrainFacts(facts(node), required);
    }

    private Map<String, Fact> facts0(Node node) {
        Flow.State before = flow.before(node);
        if (before.env().isEmpty()) {
            return Map.of();
        }
        Map<String, Fact> byName = new LinkedHashMap<>();
        before.env().forEach((symbolId, fact) -> byName.put(flow.symbol(symbolId).name(), fact));
        return Map.copyOf(byName);
    }

    private Map<String, Fact> constrainFacts(Map<String, Fact> facts, Guard required) {
        if (facts.isEmpty() || required == null || required.equals(TRUE)) {
            return facts;
        }
        if (flow.isFalse(required)) {
            return Map.of();
        }
        Map<String, Fact> constrained = null;
        for (Entry<String, Fact> entry : facts.entrySet()) {
            Fact narrowed = constrainFact(entry.getValue(), required);
            if (narrowed == entry.getValue()) {
                continue;
            }
            if (constrained == null) {
                constrained = new LinkedHashMap<>(facts);
            }
            if (narrowed == null) {
                constrained.remove(entry.getKey());
            } else {
                constrained.put(entry.getKey(), narrowed);
            }
        }
        return constrained == null ? facts : Map.copyOf(constrained);
    }

    private Fact constrainFact(Fact fact, Guard required) {
        Guard definedUnder = flow.and(fact.definedUnder(), required);
        if (flow.isFalse(definedUnder)) {
            return null;
        }
        List<Fact.ExactCase> exactCases = fact.exactCases();
        if (exactCases.isEmpty()) {
            return definedUnder.equals(fact.definedUnder()) ? fact : new Fact(definedUnder, fact.value());
        }
        List<Fact.ExactCase> constrainedCases = new ArrayList<>(exactCases.size());
        boolean changed = !definedUnder.equals(fact.definedUnder());
        for (Fact.ExactCase exactCase : exactCases) {
            Guard caseGuard = flow.and(exactCase.guard(), required);
            if (flow.isFalse(caseGuard)) {
                changed = true;
                continue;
            }
            if (!caseGuard.equals(exactCase.guard())) {
                changed = true;
                constrainedCases.add(new Fact.ExactCase(exactCase.value(), caseGuard));
            } else {
                constrainedCases.add(exactCase);
            }
        }
        return changed ? new Fact(definedUnder, fact.value(), constrainedCases) : fact;
    }

    private Fact fact(Map<String, Fact> facts, String key) {
        Fact fact = facts.get(key);
        if (fact != null || key.startsWith("~")) {
            return fact;
        }
        return facts.get("~" + key);
    }

    private Flow.SymbolInfo symbolInfo(String key) {
        Flow.SymbolInfo info = flow.symbol(key);
        if (info != null || key.startsWith("~")) {
            return info;
        }
        return flow.symbol("~" + key);
    }

    private boolean attached(Node node) {
        Node current = node;
        while (current != null) {
            Node parent = current.parent();
            if (parent == null) {
                return current == sourceNode;
            }
            if (!parent.children().contains(current)) {
                return false;
            }
            current = parent;
        }
        return false;
    }

    private boolean unreachable(Node node) {
        return flow.isFalse(feasibleActiveGuard(node));
    }

    private boolean inactiveInput(Node node) {
        return flow.isFalse(feasibleRenderGuard(node));
    }

    private boolean inactiveOption(Node node) {
        return flow.isFalse(feasibleRenderGuard(node));
    }

    private boolean skipCompiledNode(Node node) {
        if (node == sourceNode) {
            return false;
        }
        if (node.kind().isInput()) {
            return inactiveInput(node);
        }
        if (node.kind() == Kind.INPUT_OPTION) {
            return inactiveOption(node);
        }
        return unreachable(node);
    }

    private boolean redundantBooleanInput(Node node) {
        if (node.kind() != Kind.INPUT_BOOLEAN) {
            return false;
        }
        Value<?> exact = flow.declaredValue(node);
        if (exact.type() != Type.BOOLEAN || exact.getBoolean()) {
            return false;
        }
        Value<?> defaultValue = node.attribute("default");
        if (defaultValue.isPresent() && defaultValue.asBoolean().orElse(true)) {
            return false;
        }
        for (Node n : node.traverse()) {
            if (n != node && (n.kind().isInput() || n.kind() == Kind.INPUT_OPTION)) {
                if (!skipCompiledNode(n)) {
                    return false;
                }
            }
        }
        return true;
    }

    private <T> Map<String, T> withoutKey(Map<String, T> values, String key) {
        if (!values.containsKey(key)) {
            return values;
        }
        Map<String, T> filtered = new LinkedHashMap<>(values);
        filtered.remove(key);
        return Map.copyOf(filtered);
    }

    private Guard normalizeGuard(Guard guard, Scope scope, Map<String, Fact> facts) {
        if (flow.isFalse(guard) || facts.isEmpty()) {
            return guard;
        }
        Expression expr = flow.expression(guard, scope);
        ExpressionAnalyzer analyzer = new ExpressionAnalyzer(scope, facts, false, true);
        ExpressionAnalysis analysis = analyzer.analyze(expr);
        return localGuard(expr, scope, TRUE, analysis);
    }

    private Guard feasibleGuard(Node node, Guard guard) {
        return node == null ? TRUE : normalizeGuard(guard, scope(node), facts(node));
    }

    private Guard feasibleRenderGuard(Node node) {
        return feasibleGuard(node, flow.renderGuard(node));
    }

    private Guard feasibleActiveGuard(Node node) {
        return feasibleGuard(node, flow.activeGuard(node));
    }

    private boolean coversUnderFacts(Guard available, Guard required, Scope scope, Map<String, Fact> facts) {
        if (available == null || required == null) {
            return false;
        }
        Guard normalizedRequired = normalizeGuard(required, scope, facts);
        Guard normalizedCovered = normalizeGuard(flow.and(required, available), scope, facts);
        return flow.equivalent(normalizedCovered, normalizedRequired);
    }

    private Guard definitionGuard(Node node) {
        switch (node.kind()) {
            case INPUT_BOOLEAN:
            case INPUT_ENUM:
            case INPUT_LIST:
            case INPUT_TEXT:
                return flow.renderGuard(node);
            case PRESET_BOOLEAN:
            case PRESET_ENUM:
            case PRESET_LIST:
            case PRESET_TEXT:
            case VARIABLE_BOOLEAN:
            case VARIABLE_ENUM:
            case VARIABLE_LIST:
            case VARIABLE_TEXT:
                return flow.activeGuard(node);
            default:
                return null;
        }
    }

    private Expression normalize(Expression expr, Scope scope) {
        return expr.map(t -> {
            if (t.isVariable()) {
                return Token.of(scope.key(t.variable()));
            }
            return t;
        }).reduce();
    }

    private boolean incompatibleOperator(Expression.Operator operator) {
        switch (operator) {
            case AS_INT:
            case AS_LIST:
            case AS_STRING:
            case SIZEOF:
            case GREATER_THAN:
            case GREATER_OR_EQUAL:
            case LOWER_THAN:
            case LOWER_OR_EQUAL:
                return true;
            default:
                return false;
        }
    }

    private Expression reachabilityExpression(Guard reach, Scope scope) {
        return reachabilityExpression(reach, scope, Map.of());
    }

    private Expression reachabilityExpression(Guard reach, Scope scope, Map<String, Fact> facts) {
        Guard normalized = normalizeGuard(reach, scope, facts);
        return flow.expression(normalized, scope);
    }

    private Expression residualExpression(Guard reach, Guard base, Scope scope) {
        return residualExpression(reach, base, scope, Map.of());
    }

    private Expression residualExpression(Guard reach, Guard base, Scope scope, Map<String, Fact> facts) {
        Guard normalizedReach = normalizeGuard(reach, scope, facts);
        Guard normalizedBase = normalizeGuard(base, scope, facts);
        return flow.expression(normalizedReach, scope).reduce(flow.expression(normalizedBase, scope));
    }

    private void rememberConditionRefs(Node node, Expression expr, Map<Node, Set<String>> conditionRefs) {
        if (node.kind() == Kind.CONDITION) {
            conditionRefs.put(node, Set.copyOf(expr.variables()));
        }
    }

    private void rememberConditionRefs(Node node, Map<Node, Set<String>> conditionRefs) {
        if (node.kind() == Kind.CONDITION) {
            conditionRefs.put(node, Set.copyOf(node.expression().variables()));
        }
    }

    private Map<String, Guard> conditionDemands(Expression expr, Scope scope, Map<String, Fact> facts) {
        if (expr == null || expr == Expression.TRUE || expr == Expression.FALSE) {
            return Map.of();
        }
        return new ExpressionAnalyzer(scope, facts, false, false).analyze(expr).variableDemands;
    }

    private Expression outputCondition(Node node, Scope scope) {
        Guard projected = activeGuardsByNode.get(node);
        Guard guard = projected != null && node.ancestor(Kind.OUTPUT::equals).isPresent()
                ? projected
                : flow.activeGuard(node);
        return reachabilityExpression(guard, scope, facts(node));
    }

    private Guard simplifyProjectedConditionGuard(Guard guard, Scope scope) {
        Expression expr = normalize(flow.expression(guard, scope), scope);
        Expression simplified = simplifyProjectedConditionExpression(expr);
        return simplified.equals(expr) ? guard : flow.residualGuard(simplified);
    }

    private Expression simplifyProjectedConditionExpression(Expression expr) {
        Guard target = projectedReach(expr);
        if (target == null) {
            return expr;
        }
        Expression minimized = expr;
        boolean changed;
        do {
            changed = false;
            List<Expression> terms = new ArrayList<>(minimized.conjunction());
            if (terms.size() < 2) {
                break;
            }
            for (int i = 0; i < terms.size(); i++) {
                Expression term = terms.get(i);
                List<Expression> remaining = Lists.withoutIndex(terms, i);
                if (!removableProjectedConditionTerm(term, remaining)) {
                    continue;
                }
                Expression candidate = Expression.TRUE.and(remaining).reduce();
                Guard candidateReach = projectedReach(candidate);
                if (flow.equivalent(candidateReach, target)) {
                    minimized = candidate;
                    changed = true;
                    break;
                }
            }
        } while (changed);
        return minimized;
    }

    private boolean removableProjectedConditionTerm(Expression term, List<Expression> remaining) {
        Guard termReach = projectedReach(term);
        if (termReach == null) {
            return false;
        }
        for (Expression other : remaining) {
            if (!carrierProjectedConditionTerm(other) || ancestorProjectedConditionTerm(term, other)) {
                continue;
            }
            Guard otherReach = projectedReach(other);
            if (otherReach != null && flow.contains(termReach, otherReach)) {
                return true;
            }
        }
        return false;
    }

    private boolean carrierProjectedConditionTerm(Expression expr) {
        List<Token> tokens = expr.tokens();
        if (tokens.size() != 3 || !tokens.get(0).isVariable() || !tokens.get(1).isOperand()) {
            return false;
        }
        Expression.Operator operator = tokens.get(2).operator();
        return operator == Expression.Operator.EQUAL || operator == Expression.Operator.CONTAINS;
    }

    private boolean ancestorProjectedConditionTerm(Expression left, Expression right) {
        for (String leftVar : left.variables()) {
            for (String rightVar : right.variables()) {
                if (rightVar.startsWith(leftVar + ".")) {
                    return true;
                }
            }
        }
        return false;
    }

    private Guard projectedReach(Expression expr) {
        return new ExpressionAnalyzer(rootScope(), Map.of(), false, false).analyze(expr).reach;
    }

    private Guard localProjectedReach(Expression expr, Scope scope) {
        return new ExpressionAnalyzer(scope, Map.of(), false, false, true).analyze(expr).reach;
    }

    private Guard localReach(Expression expr, Scope scope) {
        return new ExpressionAnalyzer(scope, Map.of(), false, false).analyze(expr).reach;
    }

    private Expression simplifyMergedConditionExpression(Expression expr, Scope scope) {
        Guard target = localReach(expr, scope);
        if (target == null) {
            return expr;
        }
        Expression minimized = expr;
        boolean changed;
        do {
            changed = false;
            List<Expression> terms = new ArrayList<>(minimized.disjunction());
            if (terms.size() < 2) {
                break;
            }
            for (int i = 0; i < terms.size(); i++) {
                List<Expression> remaining = Lists.withoutIndex(terms, i);
                Expression candidate = Expression.FALSE.or(remaining).reduce();
                Guard candidateReach = localReach(candidate, scope);
                if (flow.equivalent(candidateReach, target)) {
                    minimized = candidate;
                    changed = true;
                    break;
                }
            }
        } while (changed);
        return minimized;
    }

    private Value<?> constantValue(Node node) {
        try {
            Value<?> value = Value.typed(node.value(), node.kind().valueType());
            if (!value.isPresent()) {
                return null;
            }
            if (value.type() == Type.STRING && value.getString().matches("^\\$\\{~?[\\w.-]+}$")) {
                return null;
            }
            return value;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Expression expressionContribution(Node node, Function<Node, String> key) {
        switch (node.kind()) {
            case CONDITION:
                return node.expression();
            case INPUT_BOOLEAN:
                return Expression.create(String.format("${%s}", key.apply(node)));
            case INPUT_OPTION:
                Node input = node.ancestor(Kind::isInput).orElseThrow();
                switch (input.kind()) {
                    case INPUT_ENUM:
                        return Expression.create(String.format(
                                "${%s} == '%s'",
                                key.apply(input),
                                node.value().getString()));
                    case INPUT_LIST:
                        return Expression.create(String.format(
                                "${%s} contains '%s'",
                                key.apply(input),
                                node.value().getString()));
                    default:
                        return Expression.TRUE;
                }
            default:
                return Expression.TRUE;
        }
    }

    private final class ExpressionAnalyzer {
        private final Scope scope;
        private final Map<String, Fact> facts;
        private final boolean validate;
        private final boolean capture;
        private final boolean projected;

        ExpressionAnalyzer(Scope scope, Map<String, Fact> facts, boolean validate, boolean capture) {
            this(scope, facts, validate, capture, false);
        }

        ExpressionAnalyzer(Scope scope, Map<String, Fact> facts, boolean validate, boolean capture, boolean projected) {
            this.scope = scope;
            this.facts = facts;
            this.validate = validate;
            this.capture = capture;
            this.projected = projected;
        }

        ExpressionAnalysis analyze(Expression expr) {
            return analyze(expr.tokens(), expr);
        }

        ExpressionAnalysis analyze(List<Token> tokens, Expression source) {
            Map<String, String> variables = new LinkedHashMap<>();
            List<Expression.Operator> incompatibleOps = new ArrayList<>();
            Deque<AnalysisTerm> stack = new ArrayDeque<>();
            String evaluationError = evaluationError(source, tokens);
            boolean compatible = true;
            try {
                for (Token token : tokens) {
                    if (token.isOperand()) {
                        stack.push(new AnalysisValue(token.operand()));
                        continue;
                    }
                    if (token.isVariable()) {
                        String variable = token.variable();
                        String key = scope.key(variable);
                        variables.putIfAbsent(variable, key);
                        stack.push(new AnalysisRef(key));
                        continue;
                    }
                    if (incompatibleOperator(token.operator())) {
                        compatible = false;
                        incompatibleOps.add(token.operator());
                        stack.push(unsupportedOperation(token.operator(), stack));
                        continue;
                    }
                    switch (token.operator()) {
                        case NOT:
                            stack.push(booleanTerm(stack.pop()).negate());
                            break;
                        case AND:
                            stack.push(booleanTerm(stack.pop()).and(booleanTerm(stack.pop())));
                            break;
                        case OR:
                            stack.push(booleanTerm(stack.pop()).or(booleanTerm(stack.pop())));
                            break;
                        case EQUAL:
                            stack.push(equality(stack.pop(), stack.pop(), false));
                            break;
                        case NOT_EQUAL:
                            stack.push(equality(stack.pop(), stack.pop(), true));
                            break;
                        case CONTAINS:
                            stack.push(contains(stack.pop(), stack.pop()));
                            break;
                        default:
                            return new ExpressionAnalysis(null,
                                    Map.of(),
                                    variables,
                                    incompatibleOps,
                                    evaluationError,
                                    supportedTerms(source));
                    }
                }
                if (stack.size() != 1 || !compatible) {
                    return new ExpressionAnalysis(null,
                            Map.of(),
                            variables,
                            incompatibleOps,
                            evaluationError,
                            supportedTerms(source));
                }
                Map<String, Guard> demands = new HashMap<>();
                AnalysisBoolean result = booleanTerm(stack.pop());
                result.collect(TRUE, demands);
                return new ExpressionAnalysis(result.translated,
                        demands,
                        variables,
                        incompatibleOps,
                        evaluationError,
                        result.translated == null
                                ? supportedTerms(source)
                                : supportedTerms(result.translated));
            } catch (RuntimeException ex) {
                return new ExpressionAnalysis(null,
                        Map.of(),
                        variables,
                        incompatibleOps,
                        evaluationError,
                        supportedTerms(source));
            }
        }

        ResolvedGuard supportedTerms(Expression expr) {
            if (!capture || expr == null) {
                return null;
            }
            Guard supported = TRUE;
            Expression unsupported = Expression.TRUE;
            boolean hasSupportedTerms = false;
            for (Expression term : expr.conjunction()) {
                ExpressionAnalyzer analyzer = new ExpressionAnalyzer(scope, facts, false, false);
                ExpressionAnalysis analysis = analyzer.analyze(term);
                if (analysis.reach != null) {
                    supported = flow.and(supported, analysis.reach);
                    hasSupportedTerms = true;
                } else {
                    unsupported = unsupported.and(normalize(term, scope));
                }
            }
            return new ResolvedGuard(supported, unsupported.reduce(), hasSupportedTerms);
        }

        ResolvedGuard supportedTerms(Guard reach) {
            return capture ? ResolvedGuard.fullySupported(reach) : null;
        }

        String evaluationError(Expression source, List<Token> tokens) {
            if (!validate) {
                return null;
            }
            Expression expr = source != null ? source : new Expression(tokens, false);
            try {
                expr.eval(variable -> validationValue(scope, variable));
                return null;
            } catch (RuntimeException ex) {
                return ex.getMessage();
            }
        }

        AnalysisTerm unsupportedOperation(Expression.Operator operator, Deque<AnalysisTerm> stack) {
            switch (operator) {
                case AS_INT:
                case AS_LIST:
                case AS_STRING:
                case SIZEOF:
                    return new AnalysisOpaque(stack.pop());
                case GREATER_THAN:
                case GREATER_OR_EQUAL:
                case LOWER_THAN:
                case LOWER_OR_EQUAL:
                    return new AnalysisOpaque(stack.pop(), stack.pop());
                default:
                    throw new IllegalStateException("Unsupported operator: " + operator);
            }
        }

        AnalysisBoolean booleanTerm(AnalysisTerm term) {
            if (term instanceof AnalysisBoolean) {
                return (AnalysisBoolean) term;
            }
            if (term instanceof AnalysisValue) {
                Value<?> value = ((AnalysisValue) term).value;
                if (value.type() == Type.BOOLEAN) {
                    Guard truthy = value.getBoolean() ? TRUE : FALSE;
                    return new AnalysisBoolean(truthy, truthy, (demand, output) -> {
                    });
                }
                return new AnalysisBoolean(null, null, term::collect);
            }
            if (term instanceof AnalysisRef) {
                String key = ((AnalysisRef) term).key;
                Flow.SymbolInfo info = symbolInfo(key);
                if (info == null || info.symbol().domain().kind() != Domain.Spec.Kind.BOOLEAN) {
                    return new AnalysisBoolean(null, null, term::collect);
                }
                return new AnalysisBoolean(translatedEquality(key, Value.TRUE),
                        demandEquality(key, Value.TRUE),
                        term::collect);
            }
            return new AnalysisBoolean(null, null, term::collect);
        }

        AnalysisBoolean equality(AnalysisTerm right, AnalysisTerm left, boolean negate) {
            Guard translated = translatedEquality(left, right);
            Guard demand = demandEquality(left, right);
            if (negate) {
                translated = negate(translated);
                demand = negate(demand);
            }
            return new AnalysisBoolean(translated, demand, (d, o) -> {
                left.collect(d, o);
                right.collect(d, o);
            });
        }

        Guard translatedEquality(AnalysisTerm left, AnalysisTerm right) {
            if (left instanceof AnalysisValue && right instanceof AnalysisValue) {
                Value<?> leftValue = ((AnalysisValue) left).value;
                Value<?> rightValue = ((AnalysisValue) right).value;
                return Value.isEqual(leftValue, rightValue) ? TRUE : FALSE;
            }
            if (left instanceof AnalysisRef && right instanceof AnalysisValue) {
                return translatedEquality(((AnalysisRef) left).key, ((AnalysisValue) right).value);
            }
            if (left instanceof AnalysisValue && right instanceof AnalysisRef) {
                return translatedEquality(((AnalysisRef) right).key, ((AnalysisValue) left).value);
            }
            return null;
        }

        Guard demandEquality(AnalysisTerm left, AnalysisTerm right) {
            if (left instanceof AnalysisValue && right instanceof AnalysisValue) {
                Value<?> leftValue = ((AnalysisValue) left).value;
                Value<?> rightValue = ((AnalysisValue) right).value;
                return Value.isEqual(leftValue, rightValue) ? TRUE : FALSE;
            }
            if (left instanceof AnalysisRef && right instanceof AnalysisValue) {
                return demandEquality(((AnalysisRef) left).key, ((AnalysisValue) right).value);
            }
            if (left instanceof AnalysisValue && right instanceof AnalysisRef) {
                return demandEquality(((AnalysisRef) right).key, ((AnalysisValue) left).value);
            }
            return null;
        }

        AnalysisBoolean contains(AnalysisTerm right, AnalysisTerm left) {
            Guard translatedTruthy = translatedContains(left, right);
            Guard demandTruthy = demandContains(left, right);
            return new AnalysisBoolean(translatedTruthy, demandTruthy, (demand, output) -> {
                left.collect(demand, output);
                right.collect(demand, output);
            });
        }

        Guard translatedContains(AnalysisTerm left, AnalysisTerm right) {
            if (left instanceof AnalysisRef && right instanceof AnalysisValue) {
                return translatedListContains(((AnalysisRef) left).key, ((AnalysisValue) right).value);
            }
            if (left instanceof AnalysisValue && right instanceof AnalysisRef) {
                Value<?> value = ((AnalysisValue) left).value;
                if (value.type() == Type.LIST) {
                    return translatedScalarAny(((AnalysisRef) right).key, new TreeSet<>(value.getList()));
                }
            }
            if (left instanceof AnalysisValue && right instanceof AnalysisValue) {
                Value<?> leftValue = ((AnalysisValue) left).value;
                Value<?> rightValue = ((AnalysisValue) right).value;
                if (leftValue.type() == Type.LIST) {
                    Set<String> required = containsValues(rightValue);
                    if (required != null) {
                        Set<String> values = new HashSet<>(leftValue.getList());
                        return values.containsAll(required) ? TRUE : FALSE;
                    }
                }
            }
            return null;
        }

        Guard demandContains(AnalysisTerm left, AnalysisTerm right) {
            if (left instanceof AnalysisRef && right instanceof AnalysisValue) {
                return demandListContains(((AnalysisRef) left).key, ((AnalysisValue) right).value);
            }
            if (left instanceof AnalysisValue && right instanceof AnalysisRef) {
                Value<?> value = ((AnalysisValue) left).value;
                if (value.type() == Type.LIST) {
                    return demandScalarAny(((AnalysisRef) right).key, new TreeSet<>(value.getList()));
                }
            }
            if (left instanceof AnalysisValue && right instanceof AnalysisValue) {
                Value<?> leftValue = ((AnalysisValue) left).value;
                Value<?> rightValue = ((AnalysisValue) right).value;
                if (leftValue.type() == Type.LIST) {
                    Set<String> required = containsValues(rightValue);
                    if (required != null) {
                        Set<String> values = new HashSet<>(leftValue.getList());
                        return values.containsAll(required) ? TRUE : FALSE;
                    }
                }
            }
            return null;
        }

        Guard translatedEquality(String key, Value<?> value) {
            Fact fact = factFor(key);
            Guard bound = fact == null ? FALSE : fact.match(value, flow.guards());
            Guard raw = rawEquality(key, value);
            return combine(key, bound, raw);
        }

        Guard demandEquality(String key, Value<?> value) {
            Guard raw = rawEquality(key, value);
            Guard defined = definitionFor(key);
            if (raw == null) {
                return null;
            }
            return defined == null ? raw : flow.and(defined, raw);
        }

        Guard translatedScalarAny(String key, Set<String> values) {
            Fact fact = factFor(key);
            Guard bound = fact == null ? FALSE : fact.scalarAny(values, flow.guards());
            Guard raw = rawScalarAny(key, values);
            return combine(key, bound, raw);
        }

        Guard demandScalarAny(String key, Set<String> values) {
            Guard raw = rawScalarAny(key, values);
            Guard defined = definitionFor(key);
            if (raw == null) {
                return null;
            }
            return defined == null ? raw : flow.and(defined, raw);
        }

        Guard translatedListContains(String key, Value<?> value) {
            Set<String> required = containsValues(value);
            if (required == null) {
                return null;
            }
            Fact fact = factFor(key);
            Guard bound = fact == null ? FALSE : fact.listContains(required, flow.guards());
            Guard raw = rawListContains(key, required);
            return combine(key, bound, raw);
        }

        Guard demandListContains(String key, Value<?> value) {
            Set<String> required = containsValues(value);
            if (required == null) {
                return null;
            }
            Guard raw = rawListContains(key, required);
            Guard defined = definitionFor(key);
            if (raw == null) {
                return null;
            }
            return defined == null ? raw : flow.and(defined, raw);
        }

        Guard combine(String key, Guard bound, Guard raw) {
            Fact fact = factFor(key);
            Guard defined = definitionFor(key);
            if (fact == null) {
                if (raw == null) {
                    return bound;
                }
                return defined == null ? raw : flow.and(defined, raw);
            }
            if (raw == null) {
                if (bound == null) {
                    return null;
                }
                if (flow.isFalse(bound)) {
                    Guard exact = fact.exactDefined(flow.guards());
                    if (defined == null || flow.isFalse(exact) || !flow.contains(exact, defined)) {
                        return null;
                    }
                }
                return bound;
            }
            Guard available = defined == null ? TRUE : defined;
            Guard exact = fact.exactDefined(flow.guards());
            Guard unresolved = flow.and(flow.minus(available, exact), raw);
            return flow.or(bound, unresolved);
        }

        Fact factFor(String key) {
            return ScriptCompiler.this.fact(facts, key);
        }

        Guard definitionFor(String key) {
            Fact fact = factFor(key);
            if (fact != null) {
                return fact.definedUnder();
            }
            Flow.SymbolInfo info = symbolInfo(key);
            return info == null ? null : info.definition();
        }

        Guard rawEquality(String key, Value<?> value) {
            String scalarValue = Value.scalarLiteral(value);
            if (scalarValue == null) {
                return null;
            }
            Flow.SymbolInfo info = symbolInfo(key);
            if (info == null) {
                return null;
            }
            Domain.Symbol symbol = info.symbol();
            if (symbol.guardable() && !symbol.tainted()) {
                switch (symbol.domain().kind()) {
                    case BOOLEAN:
                        return Set.of("false", "true").contains(scalarValue)
                                ? available(info, scalarValue, flow.guards().eq(symbol.id(), scalarValue))
                                : FALSE;
                    case CHOICE:
                        return ((Domain.Spec.Choice) symbol.domain()).values().contains(scalarValue)
                                ? available(info, scalarValue, flow.guards().eq(symbol.id(), scalarValue))
                                : FALSE;
                    case FINITE_TEXT:
                        return ((Domain.Spec.FiniteText) symbol.domain()).values().contains(scalarValue)
                                ? available(info, scalarValue, flow.guards().eq(symbol.id(), scalarValue))
                                : FALSE;
                    default:
                        break;
                }
            }
            return fallbackScalarEquality(info, key, scalarValue);
        }

        Guard rawScalarAny(String key, Set<String> values) {
            Flow.SymbolInfo info = symbolInfo(key);
            if (info == null) {
                return null;
            }
            Domain.Symbol symbol = info.symbol();
            Set<String> allowed = new TreeSet<>(values);
            if (symbol.guardable() && !symbol.tainted()) {
                switch (symbol.domain().kind()) {
                    case BOOLEAN:
                        allowed.retainAll(Set.of("false", "true"));
                        break;
                    case CHOICE:
                        allowed.retainAll(((Domain.Spec.Choice) symbol.domain()).values());
                        break;
                    case FINITE_TEXT:
                        allowed.retainAll(((Domain.Spec.FiniteText) symbol.domain()).values());
                        break;
                    default:
                        allowed.clear();
                        break;
                }
                Guard raw = FALSE;
                for (String value : allowed) {
                    raw = flow.or(raw, available(info, value, flow.guards().eq(symbol.id(), value)));
                }
                return raw;
            }
            Guard raw = FALSE;
            for (String value : allowed) {
                Guard equality = fallbackScalarEquality(info, key, value);
                if (equality != null) {
                    raw = flow.or(raw, equality);
                }
            }
            return flow.isFalse(raw) ? null : raw;
        }

        Guard rawListContains(String key, Set<String> required) {
            Flow.SymbolInfo info = symbolInfo(key);
            if (info == null) {
                return null;
            }
            Domain.Symbol symbol = info.symbol();
            if (!symbol.guardable() || symbol.tainted() || symbol.domain().kind() != Domain.Spec.Kind.MEMBERSHIP) {
                return null;
            }
            Set<String> items = ((Domain.Spec.Membership) symbol.domain()).items();
            if (!items.containsAll(required)) {
                return FALSE;
            }
            Guard raw = TRUE;
            for (String value : required) {
                raw = flow.and(raw, available(info, value, flow.guards().contains(symbol.id(), value)));
            }
            return raw;
        }

        Guard available(Flow.SymbolInfo info, String value, Guard direct) {
            if (projected) {
                return direct;
            }
            Guard availability = info.availability(value);
            return availability == null ? FALSE : flow.and(availability, direct);
        }

        Guard fallbackScalarEquality(Flow.SymbolInfo info, String key, String value) {
            if (projected) {
                return flow.residualGuard(Expression.create(String.format("${%s} == '%s'", key, value)));
            }
            Guard availability = info.availability(value);
            if (availability == null) {
                return null;
            }
            Expression expr = Expression.create(String.format("${%s} == '%s'", key, value));
            return flow.and(availability, flow.residualGuard(expr));
        }

        Set<String> containsValues(Value<?> value) {
            switch (value.type()) {
                case STRING:
                case DYNAMIC:
                    return Set.of(value.getString());
                case LIST:
                    return new TreeSet<>(value.getList());
                default:
                    return null;
            }
        }

        void addDemand(String key, Guard demand, Map<String, Guard> output) {
            if (flow.isFalse(demand)) {
                return;
            }
            output.compute(key, (k, current) -> current == null ? demand : flow.or(current, demand));
        }

        Guard negate(Guard reach) {
            return reach == null ? null : flow.guards().not(reach);
        }

        private abstract class AnalysisTerm {
            abstract void collect(Guard demand, Map<String, Guard> output);
        }

        private final class AnalysisValue extends AnalysisTerm {
            private final Value<?> value;

            AnalysisValue(Value<?> value) {
                this.value = value;
            }

            @Override
            public void collect(Guard demand, Map<String, Guard> output) {
            }
        }

        private final class AnalysisRef extends AnalysisTerm {
            private final String key;

            AnalysisRef(String key) {
                this.key = key;
            }

            @Override
            public void collect(Guard demand, Map<String, Guard> output) {
                addDemand(key, demand, output);
            }
        }

        private final class AnalysisOpaque extends AnalysisTerm {
            private final List<AnalysisTerm> terms;

            AnalysisOpaque(AnalysisTerm... terms) {
                this.terms = List.of(terms);
            }

            @Override
            void collect(Guard demand, Map<String, Guard> output) {
                for (AnalysisTerm term : terms) {
                    term.collect(demand, output);
                }
            }
        }

        private final class AnalysisBoolean extends AnalysisTerm {
            private final Guard translated;
            private final Guard demand;
            private final BiConsumer<Guard, Map<String, Guard>> collector;

            AnalysisBoolean(Guard translated, Guard demand, BiConsumer<Guard, Map<String, Guard>> collector) {
                this.translated = translated;
                this.demand = demand;
                this.collector = collector;
            }

            AnalysisBoolean negate() {
                return new AnalysisBoolean(ExpressionAnalyzer.this.negate(translated),
                        ExpressionAnalyzer.this.negate(demand),
                        collector);
            }

            AnalysisBoolean and(AnalysisBoolean left) {
                Guard translated = left.translated == null || this.translated == null
                        ? null
                        : flow.and(left.translated, this.translated);
                Guard demand = left.demand == null || this.demand == null
                        ? null
                        : flow.and(left.demand, this.demand);
                return new AnalysisBoolean(translated, demand, (d, o) -> {
                    left.collect(d, o);
                    Guard right = left.demand == null ? d : flow.and(d, left.demand);
                    collect(right, o);
                });
            }

            AnalysisBoolean or(AnalysisBoolean left) {
                Guard translated = left.translated == null || this.translated == null
                        ? null
                        : flow.or(left.translated, this.translated);
                Guard demand = left.demand == null || this.demand == null
                        ? null
                        : flow.or(left.demand, this.demand);
                return new AnalysisBoolean(translated, demand, (d, o) -> {
                    left.collect(d, o);
                    Guard right = left.demand == null ? d
                            : flow.and(d, flow.minus(TRUE, left.demand));
                    collect(right, o);
                });
            }

            @Override
            public void collect(Guard demand, Map<String, Guard> output) {
                collector.accept(demand, output);
            }
        }
    }

    private static final class ExpressionAnalysis {
        private final Guard reach;
        private final Map<String, Guard> variableDemands;
        private final Map<String, String> variables;
        private final List<Expression.Operator> incompatibleOps;
        private final String evaluationError;
        private final ResolvedGuard supportedTerms;

        ExpressionAnalysis(Guard reach,
                           Map<String, Guard> variableDemands,
                           Map<String, String> variables,
                           List<Expression.Operator> incompatibleOps,
                           String evaluationError,
                           ResolvedGuard supportedTerms) {
            this.reach = reach;
            this.variableDemands = Map.copyOf(variableDemands);
            this.variables = Map.copyOf(variables);
            this.incompatibleOps = List.copyOf(incompatibleOps);
            this.evaluationError = evaluationError;
            this.supportedTerms = supportedTerms;
        }

        Set<String> refs() {
            return new LinkedHashSet<>(variables.values());
        }
    }

    private static final class ResolvedGuard {
        private final Guard supported;
        private final Expression unsupported;
        private final boolean hasSupportedTerms;

        ResolvedGuard(Guard supported, Expression unsupported, boolean hasSupportedTerms) {
            this.supported = supported;
            this.unsupported = unsupported;
            this.hasSupportedTerms = hasSupportedTerms;
        }

        static ResolvedGuard fullySupported(Guard reach) {
            return new ResolvedGuard(reach, Expression.TRUE, true);
        }
    }

    private class InputVisitor implements Node.Visitor {
        private final Deque<Node> stack = new ArrayDeque<>();
        private final Map<Node, Node> mirrors;
        private final Set<Node> hoistedSteps = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<Node, Guard> reachableNodes = new IdentityHashMap<>();
        private final Map<Node, Guard> reachableBlocks;
        private final Map<Node, Set<String>> conditionRefs;
        private final Image image;

        InputVisitor(Image image,
                     Map<Node, Node> mirrors,
                     Map<Node, Guard> reachableBlocks,
                     Map<Node, Set<String>> conditionRefs) {
            this.image = image;
            this.mirrors = mirrors;
            this.reachableBlocks = reachableBlocks;
            this.conditionRefs = conditionRefs;
            this.stack.push(image.node);
            this.reachableNodes.put(image.node, TRUE);
        }

        @Override
        public boolean visit(Node node) {
            if (skipCompiledNode(node)) {
                return false;
            }
            switch (node.kind()) {
                case PRESET_BOOLEAN:
                case PRESET_ENUM:
                case PRESET_LIST:
                case PRESET_TEXT:
                    // use ~ to be parented at the root context node
                    // to maintain scope.key == scope.internalKey
                    Node preset = process(node, (b, n) -> Nodes.ensureLast(b, Kind.PRESETS).append(n));
                    preset.attribute("path", "~" + flow.key(node));
                    stack.push(preset);
                    break;
                case VARIABLE_BOOLEAN:
                case VARIABLE_ENUM:
                case VARIABLE_LIST:
                case VARIABLE_TEXT:
                    if (!options.contains(Options.NO_TRANSIENT)
                        || !node.attribute("transient").asBoolean().orElse(false)) {

                        // use ~ to be parented at the root context node
                        // to maintain scope.key == scope.internalKey
                        Node variable = process(node, (b, n) -> Nodes.ensureLast(b, Kind.VARIABLES).append(n));
                        variable.attribute("path", "~" + flow.key(node));
                        stack.push(variable);
                    }
                    break;
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                case INPUT_TEXT:
                case INPUT_LIST:
                    // flatten inputs to maintain scope.key == scope.internalKey (!)
                    // I.e. inputs are only nested to produce a dotted path
                    if (redundantBooleanInput(node)) {
                        return false;
                    }

                    // flatten if enclosing input is effectively global (discrete)
                    // I.e., input is global or enclosing input is global
                    boolean global = node.attribute("global").asBoolean().orElse(false);
                    boolean discrete = global || node.ancestor(Kind::isInput)
                            .map(n -> n.attribute("global").asBoolean().orElse(false))
                            .orElse(false);

                    // process steps lazily
                    Node block = node.ancestor(Kind::isBlock).orElseThrow();
                    if (block.kind() == Kind.STEP || discrete) {
                        Node step = node.ancestor(Kind.STEP::equals).orElseThrow();
                        Node stepCopy = mirrors.get(step);
                        if (stepCopy == null) {
                            if (discrete) {
                                if (image.node != stack.peek()) {
                                    // ensure top level
                                    stack.push(image.node);
                                }
                            }

                            // create the step
                            if (discrete) {
                                hoistedSteps.add(step);
                            }
                            stepCopy = process(step, Node::append);
                            stack.push(stepCopy);
                        } else if (discrete) {
                            // push copy as the current block
                            if (stepCopy != stack.peek()) {
                                stack.push(stepCopy);
                            }
                        }
                    }

                    // add input
                    stack.push(process(node, (b, n) -> Nodes.ensureLast(b, Kind.INPUTS).append(n)));
                    break;
                case INPUT_OPTION:
                case EXEC:
                case SOURCE:
                case CALL:
                    stack.push(process(node, Node::append));
                    break;
                default:
            }
            return true;
        }

        @Override
        public void postVisit(Node node) {
            if (node.kind() == Kind.SCRIPT) {

                // group steps with same input keys (pseudo breadth first)
                // this is required to avoid stubs that trump input values (!)
                List<Node> nodes = image.node.children();
                List<Node> steps = Lists.filter(nodes, n -> n.kind().is(Kind.CONDITION, Kind.STEP));
                if (!steps.isEmpty()) {

                    // sort the steps by nested input keys
                    Map<String, Set<Node>> refs = new LinkedHashMap<>();
                    for (Node step : steps) {
                        for (Node input : step.traverse(Kind::isInput)) {
                            refs.computeIfAbsent(flow.key(mirror(input)), k -> new LinkedHashSet<>()).add(step);
                        }
                    }

                    // group sorted steps by name
                    List<List<Node>> groups = Lists.groupingBy(Lists.flatMapDistinct(refs.values()),
                            n -> n.unwrap().attribute("name").getString());

                    // sort the groups using the highest depth first index in the source tree
                    List<List<Node>> sortedGroups = Lists.sorted(groups,
                            Comparator.comparingInt(g -> Lists.max(g, n -> mirror(n.unwrap()).id())));

                    // flatten the groups
                    List<Node> sortedSteps = Lists.flatMap(sortedGroups);

                    // replace steps
                    for (int i = 0, j = 0; i < nodes.size(); i++) {
                        if (nodes.get(i).unwrap().kind() == Kind.STEP) {
                            nodes.set(i, Nodes.parent(sortedSteps.get(j++), image.node));
                        }
                    }
                }
            } else {
                Node copy = mirrors.get(node);
                if (copy != null) {
                    while (!stack.isEmpty()) {
                        Node n = stack.pop();
                        if (copy == n) {
                            break;
                        }
                    }
                }
            }
        }

        Node process(Node node, BiConsumer<Node, Node> appender) {
            Node parentCopy = stack.getFirst();
            Guard parentReach = parentCopy == image.node
                    ? TRUE
                    : requireNonNull(reachableNodes.get(parentCopy), "parent reachability not found");
            Guard nodeRenderReach = flow.renderGuard(node);
            Guard projectedRenderReach = renderGuardsByNode.get(node);
            boolean useProjectedStep = node.kind() == Kind.STEP && hoistedSteps.contains(node) && projectedRenderReach != null;
            boolean useProjectedCondition = node.parent() != null && node.parent().kind() == Kind.CONDITION
                                            && projectedRenderReach != null;
            boolean useProjected = useProjectedStep || useProjectedCondition;
            Map<String, Fact> emissionFacts = useProjected ? Map.of() : facts(node);
            if (useProjected) {
                nodeRenderReach = projectedRenderReach;
            }
            if (nodeRenderReach.equals(TRUE)
                && projectedRenderReach != null
                && !projectedRenderReach.equals(TRUE)) {
                nodeRenderReach = projectedRenderReach;
            }
            Expression expr = residualExpression(nodeRenderReach, parentReach, rootScope(), emissionFacts);

            // create copy
            Node copy = node.copy();
            Node wrappedCopy = copy.wrap(expr);
            appender.accept(parentCopy, wrappedCopy);
            rememberConditionRefs(wrappedCopy, expr, conditionRefs);
            Guard nodeActiveReach = flow.activeGuard(node);
            Guard projectedActiveReach = activeGuardsByNode.get(node);
            if (useProjected && projectedActiveReach != null) {
                nodeActiveReach = projectedActiveReach;
            }
            if (nodeActiveReach.equals(TRUE)
                && projectedActiveReach != null
                && !projectedActiveReach.equals(TRUE)) {
                nodeActiveReach = projectedActiveReach;
            }
            reachableNodes.put(copy, nodeActiveReach);
            if (node.kind().isBlock()) {
                reachableBlocks.put(copy, nodeActiveReach);
            }

            mirrors.put(node, copy);
            mirrors.put(copy, node);
            return copy;
        }

        Node mirror(Node node) {
            Node mirror = mirrors.get(node);
            if (mirror != null) {
                return mirror;
            } else {
                throw new IllegalStateException("Mirror not found: " + node);
            }
        }
    }

    private final class OutputVisitor implements Node.Visitor {
        private final Map<String, Map<List<FileOp>, Guard>> fileOps = new HashMap<>();
        private final Set<FileObject> files = new TreeSet<>();
        private final Set<FileObject> templates = new TreeSet<>();
        private final Map<Node, Set<String>> conditionRefs;
        private final Image image;

        OutputVisitor(Image image, Map<Node, Set<String>> conditionRefs) {
            this.image = image;
            this.conditionRefs = conditionRefs;
        }

        @Override
        public boolean visit(Node node) {
            if (skipCompiledNode(node)) {
                return false;
            }
            switch (node.kind()) {
                case TRANSFORMATION:
                    List<FileOp> ops = new ArrayList<>();
                    for (Node n : node.traverse(Kind.REPLACE::equals)) {
                        ops.add(new FileOp(
                                n.attribute("regex").getString(),
                                n.attribute("replacement").getString()));
                    }
                    Guard nodeReach = flow.activeGuard(node);
                    fileOps.computeIfAbsent(node.attribute("id").getString(), k -> new HashMap<>())
                            .compute(ops, (k, v) -> {
                                Guard reach = v != null ? v : FALSE;
                                return flow.or(reach, nodeReach);
                            });
                    break;
                case FILE:
                    files.add(resolveFile(node));
                    break;
                case TEMPLATE:
                    templates.add(resolveFile(node));
                    break;
                case FILES:
                    files.addAll(resolveFiles(node));
                    break;
                case TEMPLATES:
                    templates.addAll(resolveFiles(node));
                    break;
                default:
            }
            return true;
        }

        @Override
        public void postVisit(Node node) {
            if (node.kind() == Kind.SCRIPT) {
                Node output = Nodes.output();

                // add files
                for (Node n : renderFiles(files, "file", Nodes::files)) {
                    output.append(n);
                }

                // add templates
                for (Node n : renderFiles(templates, "template", Nodes::templates)) {
                    output.append(n);
                }

                // add models
                if (!output.children().isEmpty()) {
                    Node model = Nodes.model();
                    for (Node n : renderModels()) {
                        model.append(n);
                    }
                    if (!model.children().isEmpty()) {
                        output.append(model);
                    }
                }

                // add output
                if (!output.children().isEmpty()) {
                    image.node.append(output);
                }
            }
        }

        FileObject resolveFile(Node node) {
            String source = node.attribute("source").getString();
            String target = node.attribute("target").getString();
            Path path = inliner.workDir(node).resolve(source);
            String checksum = checksum(path);
            image.blobs.putIfAbsent(checksum, readAllBytes(path));
            List<FileOp> fileOps = List.of(new FileOp(Pattern.quote(checksum), target));
            return new FileObject(checksum, fileOps, outputCondition(node, scope(node)));
        }

        Set<FileObject> resolveFiles(Node node) {
            Set<FileObject> fileObjects = new TreeSet<>();

            // resolve variations of transformations
            List<List<FileOps>> allOps = Lists.filter(Combinatorics.cartesianProduct(fileOps(node)), l -> !l.isEmpty());
            Set<FileOps> resolvedOps = new TreeSet<>(Lists.map(allOps, this::combineFileOps));

            Map<String, Guard> includes = new HashMap<>();
            Map<String, Guard> excludes = new HashMap<>();

            Scope scope = scope(node);
            Map<String, Fact> facts = facts(node);
            Guard nodeReach = flow.activeGuard(node);
            for (Node n : node.traverse()) {
                switch (n.kind()) {
                    case INCLUDE:
                    case EXCLUDE:
                        Map<String, Guard> map = n.kind() == Kind.INCLUDE ? includes : excludes;
                        Guard filterReach = flow.activeGuard(n);
                        map.compute(n.value().getString(), (k, v) -> v == null ? filterReach : flow.or(v, filterReach));
                        break;
                    default:
                }
            }

            Path directory = inliner.workDir(node).resolve(node.attribute("directory").getString());
            for (SourcePath file : SourcePath.scan(directory)) {

                Guard includeReach = includes.isEmpty() ? TRUE : FALSE;
                for (Guard v : Maps.filterKey(includes, file::matches).values()) {
                    includeReach = flow.or(includeReach, v);
                }
                Guard excludeReach = FALSE;
                for (Guard v : Maps.filterKey(excludes, file::matches).values()) {
                    excludeReach = flow.or(excludeReach, v);
                }

                Guard blobReach = flow.minus(flow.and(nodeReach, includeReach), excludeReach);
                if (!flow.isFalse(blobReach)) {
                    String source = file.asString(false);
                    Path path = directory.resolve(source);

                    // record blob
                    String checksum = checksum(path);
                    image.blobs.putIfAbsent(checksum, readAllBytes(path));

                    if (resolvedOps.isEmpty()) {
                        List<FileOp> fileOps = List.of(new FileOp(Pattern.quote(checksum), source));
                        fileObjects.add(new FileObject(checksum, fileOps, reachabilityExpression(blobReach, scope, facts)));
                    } else {
                        // create a unique file object for each transformation variation
                        for (FileOps e : resolvedOps) {
                            List<FileOp> fileOps = e.resolve(checksum, source);
                            Guard fileReach = flow.and(blobReach, e.reach);
                            fileObjects.add(new FileObject(checksum, fileOps, reachabilityExpression(fileReach, scope, facts)));
                        }
                    }
                }
            }
            return fileObjects;
        }

        List<Node> renderFiles(Set<FileObject> files, String prefix, BiFunction<String, List<String>, Node> func) {
            List<Node> nodes = new ArrayList<>();

            // split duplicates in different groups
            Map<Integer, Set<FileObject>> groups = new HashMap<>();

            // the map key is the number of occurrences
            Map<String, Integer> groupIndexes = new HashMap<>();
            for (FileObject file : files) {
                int index = groupIndexes.compute(file.checksum, (k, v) -> v == null ? 1 : v + 1);
                groups.computeIfAbsent(index, i -> new TreeSet<>()).add(file);
            }

            // compute all transformations
            Map<String, Set<FileOp>> transformations = new TreeMap<>();

            // transformation ids by group index
            Map<Integer, List<String>> groupIds = new TreeMap<>();
            groups.forEach((index, groupFiles) -> {
                Set<String> ids = new TreeSet<>();
                for (FileObject file : groupFiles) {
                    String id;
                    if (file.ops.size() > 1) {
                        // ops are not folded, create a unique transformation
                        id = prefix + "-blobs-" + index + "-" + file.checksum;
                    } else {
                        id = prefix + "-blobs-" + index;
                    }
                    ids.add(id);
                    transformations.computeIfAbsent(id, k -> new LinkedHashSet<>()).addAll(file.ops);
                }
                groupIds.put(index, new ArrayList<>(ids));
            });

            // render all transformations
            transformations.forEach((id, v) -> {
                List<Node> children = Lists.map(v, op -> Nodes.replace(op.regex, op.replacement));
                nodes.add(Nodes.transformation(id, children));
            });

            // render resources directive
            groups.forEach((index, group) -> {
                List<String> ids = groupIds.getOrDefault(index, List.of());
                Node directive = func.apply("blobs", ids);
                Node includes = Nodes.includes();
                for (FileObject f : group) {
                    Expression expr = f.condition();
                    if (expr != Expression.FALSE) {
                        Node include = Nodes.include(f.checksum);
                        Node wrappedInclude = include.wrap(expr);
                        rememberConditionRefs(wrappedInclude, expr, conditionRefs);
                        includes.append(wrappedInclude);
                    }
                }
                // add an empty include to avoid matching all blobs
                includes.append(Nodes.include(""));
                directive.append(includes);
                nodes.add(directive);
            });
            return nodes;
        }

        List<List<FileOps>> fileOps(Node node) {
            List<List<FileOps>> ops = new ArrayList<>();
            List<String> ids = node.attribute("transformations").asList().orElse(List.of());
            for (String id : ids) {
                Map<List<FileOp>, Guard> idOps = fileOps.getOrDefault(id, Map.of());
                ops.add(Lists.map(idOps.entrySet(),
                        e -> new FileOps(e.getKey(), e.getValue(), flow.expression(e.getValue()).literal())));
            }
            return ops;
        }

        FileOps combineFileOps(List<FileOps> list) {
            List<FileOp> ops = new ArrayList<>();
            Guard reach = list.get(0).reach;
            for (FileOps e : list) {
                ops.addAll(e.ops);
            }
            for (int i = 1; i < list.size(); i++) {
                reach = flow.and(reach, list.get(i).reach);
            }
            return new FileOps(ops, reach, flow.expression(reach).literal());
        }

        List<Node> renderModels() {
            List<Node> nodes = new ArrayList<>();
            for (Node node : sourceNode.traverse(Kind::isModel)) {
                Node parent = node.parent();
                if (parent != null && parent.kind() == Kind.CONDITION) {
                    parent = parent.parent();
                }
                if (parent != null && parent.kind() == Kind.MODEL) {
                    Path basedir = inliner.workDir(node.ancestor(Kind.OUTPUT::equals).orElseThrow());
                    if (basedir == null) {
                        throw new IllegalStateException("Unresolved cwd");
                    }
                    Scope scope = scope(node);
                    Expression expr = outputCondition(node, scope);
                    if (expr != Expression.FALSE) {
                        Node copy = node.deepCopy();
                        for (Node n : copy.traverse(Kind.MODEL_VALUE::equals)) {
                            Node p = n.parent();
                            if (p.kind() == Kind.CONDITION) {
                                p.expression(normalize(p.expression(), scope));
                            }
                            String value = n.value().asString().orElse("");
                            if (value.matches("^\\s+.*") || value.matches(".*\\s+$") || value.contains("\n")) {
                                // move text model with leading, trailing whitespaces or newlines to blobs
                                String id = md5(n.location());
                                image.blobs.put(id, value.getBytes(StandardCharsets.UTF_8));
                                n.attribute("file", "blobs/" + id);
                                n.value(Value.empty());
                            } else {
                                String file = n.attribute("file").asString().orElse(null);
                                if (file != null) {
                                    Path path = basedir.resolve(file);
                                    String checksum = checksum(path);
                                    image.blobs.putIfAbsent(checksum, readAllBytes(path));
                                    n.attribute("file", "blobs/" + checksum);
                                    n.value(Value.empty());
                                } else if (value.isEmpty()) {
                                    // force an empty CDATA
                                    n.value(Value.EMPTY_STRING);
                                }
                            }
                        }
                        for (Node condition : copy.traverse(Kind.CONDITION::equals)) {
                            rememberConditionRefs(condition, conditionRefs);
                        }
                        Node wrappedCopy = copy.wrap(expr);
                        rememberConditionRefs(wrappedCopy, expr, conditionRefs);
                        nodes.add(wrappedCopy);
                    }
                }
            }
            return nodes;
        }

        String checksum(Path path) {
            try {
                return md5(InputStreams.normalizeNewLines(Files.newInputStream(path)));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    private class StubsVisitor implements Node.Visitor {
        private final Map<String, Fact> currentFacts = new HashMap<>();
        private final Map<Node, Map<String, Fact>> factRefs = new LinkedHashMap<>();
        private final Map<Node, Map<String, Set<List<Token>>>> existingConditionsByBlock = new IdentityHashMap<>();
        private final Map<Node, Map<String, Set<List<Token>>>> existingConditionsByContainer = new IdentityHashMap<>();
        private final Map<Node, Map<String, StubState>> emittedStubsByContainer = new IdentityHashMap<>();
        private final Map<Node, Node> mirrors;
        private final Map<Node, Guard> reachableBlocks;
        private final Map<Node, Set<String>> conditionRefs;

        StubsVisitor(Map<Node, Node> mirrors,
                     Map<Node, Guard> reachableBlocks,
                     Map<Node, Set<String>> conditionRefs) {
            this.mirrors = mirrors;
            this.reachableBlocks = reachableBlocks;
            this.conditionRefs = conditionRefs;
        }

        @Override
        public boolean visit(Node node) {
            switch (node.kind()) {
                case PRESET_BOOLEAN:
                case PRESET_ENUM:
                case PRESET_LIST:
                case PRESET_TEXT:
                case VARIABLE_BOOLEAN:
                case VARIABLE_ENUM:
                case VARIABLE_LIST:
                case VARIABLE_TEXT:
                    rememberFact(mirror(node), definitionGuard(mirror(node)), constantValue(node));
                    break;
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                case INPUT_TEXT:
                case INPUT_LIST:
                    rememberFact(mirror(node), flow.renderGuard(mirror(node)), null);
                    break;
                case CONDITION:
                    factRefs.put(node, Map.copyOf(currentFacts));
                    break;
                default:
            }
            return true;
        }

        private void rememberFact(Node node, Guard definition, Value<?> exactValue) {
            if (flow.isFalse(definition)) {
                return;
            }
            String key = flow.key(node);
            Flow.SymbolInfo info = symbolInfo(key);
            if (info == null) {
                return;
            }
            Domain.LatticeValue value = Domain.LatticeValue.top(info.symbol().domain());
            Fact next = exactValue == null
                    ? new Fact(definition, value)
                    : Fact.exact(definition, value, exactValue);
            currentFacts.merge(key, next, (left, right) -> Fact.merge(left, right, flow.guards()));
        }

        private <T> Map<String, T> withoutControlKey(Map<String, T> values, String key) {
            Map<String, T> trimmed = withoutKey(values, key);
            if (key.startsWith("~")) {
                return withoutKey(trimmed, key.substring(1));
            }
            return withoutKey(trimmed, "~" + key);
        }

        @Override
        public void postVisit(Node node) {
            if (node.kind() == Kind.SCRIPT) {
                factRefs.forEach((n, snapshot) -> {
                    List<StubSpec> stubs = resolveStubs(n, snapshot);
                    if (!stubs.isEmpty()) {
                        addVariables(n, stubs);
                    }
                });
            }
        }

        void addVariables(Node node, List<StubSpec> stubs) {
            if (stubs.isEmpty()) {
                return;
            }

            Node block = node.ancestor(Kind::isBlock).orElseThrow();
            Map<String, Set<List<Token>>> existingInBlock = existingConditionsByBlock.computeIfAbsent(
                    block,
                    this::existingConditionsInBlock);
            Node container = null;
            Map<String, Set<List<Token>>> existing = null;
            Map<String, StubState> emitted = null;
            for (StubSpec stub : stubs) {
                List<Token> conditionTokens = nodeConditionTokens(stub.render());
                Set<List<Token>> blockConditions = existingInBlock.computeIfAbsent(stub.path(), k -> new LinkedHashSet<>());
                if (blockConditions.contains(conditionTokens)) {
                    continue;
                }

                if (container == null) {
                    container = resolveContainer(node);
                    existing = existingConditionsByContainer.computeIfAbsent(
                            container,
                            this::existingConditionsInContainer);
                    emitted = emittedStubsByContainer.computeIfAbsent(container, k -> new LinkedHashMap<>());
                }

                StubState state = emitted.get(stub.path());
                if (state != null) {
                    if (!state.merge(stub, existing, existingInBlock)) {
                        emitted.remove(stub.path());
                    }
                    continue;
                }

                Node rendered = stub.render();
                if (!blockConditions.add(conditionTokens)) {
                    continue;
                }
                Set<List<Token>> conditions = existing.computeIfAbsent(stub.path(), k -> new LinkedHashSet<>());
                if (!conditions.add(conditionTokens)) {
                    blockConditions.remove(conditionTokens);
                    continue;
                }

                container.append(rendered);
                emitted.put(stub.path(), new StubState(stub, rendered));
            }
        }

        private Map<String, Set<List<Token>>> existingConditionsInBlock(Node block) {
            Map<String, Set<List<Token>>> existing = new LinkedHashMap<>();
            for (Node variables : block.children(Kind.VARIABLES::equals)) {
                for (Node child : variables.children()) {
                    String path = child.unwrap().attribute("path").getString();
                    existing.computeIfAbsent(path, k -> new LinkedHashSet<>()).add(nodeConditionTokens(child));
                }
            }
            return existing;
        }

        private Map<String, Set<List<Token>>> existingConditionsInContainer(Node container) {
            Map<String, Set<List<Token>>> existing = new LinkedHashMap<>();
            for (Node child : container.children()) {
                String path = child.unwrap().attribute("path").getString();
                existing.computeIfAbsent(path, k -> new LinkedHashSet<>()).add(nodeConditionTokens(child));
            }
            return existing;
        }

        private Node resolveContainer(Node node) {
            Node container = null;
            for (Node n = node; n != null && container == null; n = n.parent()) {
                Node parent = n.parent();
                if (parent.kind() == Kind.INPUTS) {
                    int index = n.index();
                    if (index == 0) {
                        container = Nodes.ensureBefore(parent, Kind.VARIABLES);
                    } else {
                        int p = parent.index();

                        // insert a variables container
                        container = Nodes.variables();
                        parent.parent().append(p + 1, container);

                        // move remaining siblings
                        // to their own inputs container
                        Node inputs = Nodes.inputs();
                        parent.parent().append(p + 2, inputs);
                        moveChildren(parent, index, inputs);
                    }
                } else if (parent.kind().isBlock()) {
                    container = Nodes.ensureBefore(n, Kind.VARIABLES);
                }
            }
            if (container == null) {
                throw new IllegalStateException("Unable to resolve stubs container");
            }
            return container;
        }

        private void moveChildren(Node parent, int startIndex, Node target) {
            List<Node> children = parent.children();
            List<Node> moved = new ArrayList<>(children.subList(startIndex, children.size()));
            children.subList(startIndex, children.size()).clear();
            for (Node child : moved) {
                target.append(child);
            }
        }

        List<Token> nodeConditionTokens(Node node) {
            return node.kind() == Kind.CONDITION
                    ? node.expression().tokens()
                    : Expression.TRUE.tokens();
        }

        List<StubSpec> resolveStubs(Node node, Map<String, Fact> snapshots) {
            Set<String> variables = conditionRefs.get(node);
            if (variables == null) {
                throw new IllegalStateException("Condition refs not found: " + node);
            }
            if (variables.isEmpty()) {
                return List.of();
            }

            List<StubSpec> stubs = new ArrayList<>();
            Node block = node.ancestor(Kind::isBlock).orElseThrow();
            Scope scope = scope(mirror(block));
            Guard base = reachability(block);
            Map<String, Guard> demands = conditionDemands(node.expression(), scope, snapshots);
            for (String ref : variables) {
                String key = scope.key(ref);
                Guard required = base;
                Guard demand = runtimeConditionDemand(key, demands);
                if (demand != null) {
                    required = flow.and(required, demand);
                }
                Fact snapshot = fact(snapshots, key);
                Guard defined = snapshot == null ? FALSE : snapshot.definedUnder();
                Guard missing = normalizeGuard(flow.minus(required, defined), scope, snapshots);
                if (flow.isFalse(missing)) {
                    continue;
                }
                Type type = indexer.refType(key);
                stubs.add(new StubSpec(type, key, missing, base, scope, snapshots));
            }
            return stubs;
        }

        private Guard runtimeConditionDemand(String key, Map<String, Guard> demands) {
            int dot = key.lastIndexOf('.');
            if (dot >= 0 && indexer.refType(key.substring(0, dot)) == Type.BOOLEAN) {
                // the runtime condition evaluator still resolves nested refs even when the boolean parent is false
                return null;
            }
            return demands.get(key);
        }

        Expression definitionComplementStubExpression(String key,
                                                      Guard missing,
                                                      Guard base,
                                                      Scope scope,
                                                      Map<String, Fact> facts) {
            Fact definitionFact = fact(facts, key);
            Guard definition = definitionFact == null ? FALSE : definitionFact.definedUnder();
            if (!flow.equivalent(flow.minus(base, definition), missing)) {
                return null;
            }
            Expression definitionExpr = flow.expression(definition, scope);
            Guard translated = new ExpressionAnalyzer(scope, facts, false, false).analyze(definitionExpr).reach;
            if (translated == null) {
                return Expression.TRUE;
            }
            return Expression.create("!(" + definitionExpr.literal() + ")").reduce(flow.expression(base));
        }

        Expression nestedDefinitionComplementStubExpression(String key,
                                                            Guard missing,
                                                            Guard base,
                                                            Scope scope,
                                                            Map<String, Fact> facts) {
            return nestedDefinitionComplementStubExpression(key, missing, base, scope, facts, false);
        }

        Expression coveringNestedDefinitionComplementStubExpression(String key,
                                                                    Guard missing,
                                                                    Guard base,
                                                                    Scope scope,
                                                                    Map<String, Fact> facts) {
            return nestedDefinitionComplementStubExpression(key, missing, base, scope, facts, true);
        }

        private Expression nestedDefinitionComplementStubExpression(String key,
                                                                    Guard missing,
                                                                    Guard base,
                                                                    Scope scope,
                                                                    Map<String, Fact> facts,
                                                                    boolean allowCovering) {
            int dot = key.lastIndexOf('.');
            if (dot < 0) {
                return null;
            }
            String parentKey = key.substring(0, dot);
            if (indexer.refType(parentKey) != Type.BOOLEAN) {
                return null;
            }
            Flow.SymbolInfo info = symbolInfo(key);
            Guard definition = info == null ? null : info.definition();
            if (flow.isFalse(definition)) {
                return null;
            }
            Expression direct = simpleNestedDefinitionComplementStubExpression(parentKey,
                    definition,
                    missing,
                    base,
                    scope,
                    allowCovering);
            if (direct != null) {
                return direct;
            }
            Expression parentExpr = Expression.create(String.format("${%s}", scope.key(parentKey)));
            Map<String, Fact> parentFacts = withoutControlKey(facts, parentKey);
            ExpressionAnalyzer analyzer = new ExpressionAnalyzer(scope, parentFacts, false, false);
            Guard parentReach = analyzer.analyze(parentExpr).reach;
            if (!flow.contains(parentReach, definition)) {
                return null;
            }
            Expression tail = residualExpression(definition, parentReach, scope);
            Expression definitionExpr = parentExpr.and(tail).reduce();
            Guard candidateDefinition = analyzer.analyze(definitionExpr).reach;
            if (!flow.equivalent(candidateDefinition, definition)) {
                return null;
            }
            Expression parentComplement = Expression.create(String.format("!${%s}", scope.key(parentKey)));
            Guard parentComplementReach = analyzer.analyze(parentComplement).reach;
            Guard tailReach = tail == Expression.TRUE ? TRUE : analyzer.analyze(tail).reach;
            Expression tailComplement = tail == Expression.TRUE
                    ? Expression.FALSE
                    : tailReach == null
                      ? Expression.create("!(" + tail.literal() + ")").reduce()
                            : reachabilityExpression(flow.minus(TRUE, tailReach), scope);
            Expression expr = parentComplement.or(tailComplement).reduce(flow.expression(base));
            Guard candidateMissing = analyzer.analyze(expr).reach;
            if (candidateMissing == null) {
                return null;
            }
            Expression normalized = reachabilityExpression(candidateMissing, scope).reduce(flow.expression(base));
            normalized = eliminateParentCoveredTerms(normalized, parentExpr, parentComplement, scope, facts,
                    flow.expression(base));
            Expression simplified = normalized;
            if (parentComplementReach != null) {
                Guard residualMissing = flow.minus(candidateMissing, parentComplementReach);
                Guard constrainedResidual = residualMissing;
                if (!flow.isFalse(residualMissing)) {
                    Guard constrained = new ExpressionAnalyzer(scope, facts, false, false)
                            .analyze(reachabilityExpression(residualMissing, scope))
                            .reach;
                    if (constrained != null) {
                        constrainedResidual = constrained;
                    }
                }
                Guard parentResidual = flow.and(parentReach, constrainedResidual);
                simplified = flow.isFalse(parentResidual)
                        ? parentComplement.reduce(flow.expression(base))
                        : parentComplement.or(reachabilityExpression(parentResidual, scope)).reduce(flow.expression(base));
                simplified = eliminateParentCoveredTerms(simplified,
                        parentExpr,
                        parentComplement,
                        scope,
                        facts,
                        flow.expression(base));
            }
            if (!allowCovering) {
                if (!flow.equivalent(candidateMissing, missing)) {
                    return null;
                }
                normalized = minimizeDisjunction(normalized,
                        missing,
                        missing,
                        true,
                        scope,
                        parentFacts,
                        flow.expression(base));
                simplified = minimizeDisjunction(simplified,
                        missing,
                        missing,
                        true,
                        scope,
                        parentFacts,
                        flow.expression(base));
                return simplified.tokens().size() < normalized.tokens().size() ? simplified : normalized;
            }
            Guard complement = flow.minus(TRUE, definition);
            if (!flow.contains(candidateMissing, missing) || !flow.contains(complement, candidateMissing)) {
                return null;
            }
            normalized = minimizeDisjunction(normalized,
                    missing,
                    complement,
                    false,
                    scope,
                    parentFacts,
                    flow.expression(base));
            simplified = minimizeDisjunction(simplified,
                    missing,
                    complement,
                    false,
                    scope,
                    parentFacts,
                    flow.expression(base));
            return simplified.tokens().size() < normalized.tokens().size() ? simplified : normalized;
        }

        private Expression simpleNestedDefinitionComplementStubExpression(String parentKey,
                                                                          Guard definition,
                                                                          Guard missing,
                                                                          Guard base,
                                                                          Scope scope,
                                                                          boolean allowCovering) {
            Expression parentExpr = Expression.create(String.format("${%s}", scope.key(parentKey)));
            ExpressionAnalyzer analyzer = new ExpressionAnalyzer(scope, Map.of(), false, false);
            Guard parentReach = analyzer.analyze(parentExpr).reach;
            if (!flow.contains(parentReach, definition)) {
                return null;
            }

            Expression parentComplement = Expression.create(String.format("!${%s}", scope.key(parentKey)));
            Expression tail = residualExpression(definition, parentReach, scope, Map.of());
            Expression expr = tail == Expression.TRUE
                    ? parentComplement
                    : parentComplement.or(Expression.create("!(" + tail.literal() + ")")).reduce();
            Guard candidate = analyzer.analyze(expr).reach;
            if (candidate == null) {
                return null;
            }

            Guard limited = base == null ? candidate : flow.and(base, candidate);
            Guard complement = flow.minus(TRUE, definition);
            if (allowCovering) {
                if (!flow.contains(limited, missing) || !flow.contains(complement, limited)) {
                    return null;
                }
            } else if (!flow.equivalent(limited, missing)) {
                return null;
            }
            return reachabilityExpression(limited, scope).reduce(flow.expression(base));
        }

        Expression nestedBooleanStubExpression(String key,
                                               Guard missing,
                                               Guard base,
                                               Scope scope,
                                               Map<String, Fact> facts) {
            int dot = key.lastIndexOf('.');
            if (dot < 0) {
                return null;
            }
            String parentKey = key.substring(0, dot);
            if (indexer.refType(parentKey) != Type.BOOLEAN) {
                return null;
            }
            Expression expr = Expression.create(String.format("!${%s}", scope.key(parentKey)));
            Guard candidate = new ExpressionAnalyzer(
                    scope,
                    withoutControlKey(facts, parentKey),
                    false,
                    false)
                    .analyze(expr)
                    .reach;
            if (candidate == null) {
                return null;
            }
            Guard relative = base == null ? candidate : flow.and(base, candidate);
            return flow.equivalent(relative, missing) ? expr : null;
        }

        private Expression minimizeDisjunction(Expression expr,
                                               Guard required,
                                               Guard limit,
                                               boolean exact,
                                               Scope scope,
                                               Map<String, Fact> facts,
                                               Expression base) {
            Expression minimized = expr;
            boolean changed;
            do {
                changed = false;
                List<Expression> disjunctions = new ArrayList<>(minimized.disjunction());
                if (disjunctions.size() > 1) {
                    for (int i = 0; i < disjunctions.size(); i++) {
                        List<Expression> remaining = Lists.withoutIndex(disjunctions, i);
                        Expression candidate = Expression.FALSE.or(remaining).reduce(base);
                        Guard candidateReach = new ExpressionAnalyzer(scope, facts, false, false)
                                .analyze(candidate)
                                .reach;
                        if (acceptable(candidateReach, required, limit, exact)) {
                            minimized = candidate;
                            changed = true;
                            break;
                        }
                    }
                    if (changed) {
                        continue;
                    }
                }

                for (int i = 0; i < disjunctions.size(); i++) {
                    List<Expression> conjunctions = new ArrayList<>(disjunctions.get(i).conjunction());
                    if (conjunctions.size() < 2) {
                        continue;
                    }
                    for (int j = 0; j < conjunctions.size(); j++) {
                        List<Expression> reducedConjunctions = Lists.withoutIndex(conjunctions, j);
                        List<Expression> reducedDisjunctions = new ArrayList<>(disjunctions);
                        reducedDisjunctions.set(i, Expression.TRUE.and(reducedConjunctions));
                        Expression candidate = Expression.FALSE.or(reducedDisjunctions).reduce(base);
                        Guard candidateReach = new ExpressionAnalyzer(scope, facts, false, false)
                                .analyze(candidate)
                                .reach;
                        if (acceptable(candidateReach, required, limit, exact)) {
                            minimized = candidate;
                            changed = true;
                            break;
                        }
                    }
                    if (changed) {
                        break;
                    }
                }
            } while (changed);
            return minimized;
        }

        private Expression eliminateParentCoveredTerms(Expression expr,
                                                       Expression parentExpr,
                                                       Expression parentComplement,
                                                       Scope scope,
                                                       Map<String, Fact> facts,
                                                       Expression base) {
            List<Expression> terms = new ArrayList<>(expr.disjunction());
            if (terms.size() < 2) {
                return expr;
            }
            boolean changed;
            do {
                changed = false;
                for (int i = 0; i < terms.size(); i++) {
                    Expression term = terms.get(i);
                    if (term.tokens().equals(parentComplement.tokens())) {
                        continue;
                    }
                    Guard overlap = new ExpressionAnalyzer(scope, facts, false, false)
                            .analyze(term.and(parentExpr).reduce(base))
                            .reach;
                    if (flow.isFalse(overlap)) {
                        terms = Lists.withoutIndex(terms, i);
                        expr = Expression.FALSE.or(terms).reduce(base);
                        changed = true;
                        break;
                    }
                }
            } while (changed && terms.size() > 1);
            return expr;
        }

        private boolean acceptable(Guard candidate,
                                   Guard required,
                                   Guard limit,
                                   boolean exact) {
            if (candidate == null) {
                return false;
            }
            if (exact) {
                return flow.equivalent(candidate, required);
            }
            return flow.contains(candidate, required) && flow.contains(limit, candidate);
        }

        private final class StubSpec {
            private final Type type;
            private final String key;
            private final Guard missing;
            private final Guard base;
            private final Scope scope;
            private final Map<String, Fact> facts;

            private StubSpec(Type type, String key, Guard missing, Guard base, Scope scope, Map<String, Fact> facts) {
                this.type = type;
                this.key = key;
                this.missing = missing;
                this.base = base;
                this.scope = scope;
                this.facts = facts;
            }

            private String path() {
                return "~" + key;
            }

            private StubSpec withMissing(Guard nextMissing) {
                return new StubSpec(type, key, nextMissing, base, scope, facts);
            }

            private Node render() {
                Expression residual = residualExpression(missing, base, scope);
                Expression expr = nestedBooleanStubExpression(key, missing, base, scope, facts);
                if (expr == null) {
                    expr = nestedDefinitionComplementStubExpression(key, missing, base, scope, facts);
                }
                if (expr == null) {
                    expr = coveringNestedDefinitionComplementStubExpression(key, missing, base, scope, facts);
                }
                if (expr == null) {
                    Expression complement = definitionComplementStubExpression(key, missing, base, scope, facts);
                    expr = complement != null && complement.tokens().size() < residual.tokens().size()
                            ? complement
                            : residual;
                }
                return Nodes.variable(type, path()).wrap(expr);
            }
        }

        private final class StubState {
            private StubSpec stub;
            private Node node;

            private StubState(StubSpec stub, Node node) {
                this.stub = stub;
                this.node = node;
            }

            private boolean merge(StubSpec other,
                                  Map<String, Set<List<Token>>> existingInContainer,
                                  Map<String, Set<List<Token>>> existingInBlock) {
                Guard merged = flow.or(stub.missing, other.missing);
                if (flow.equivalent(merged, stub.missing)) {
                    return true;
                }

                StubSpec next = stub.withMissing(merged);
                Node mergedNode = next.render();
                List<Token> previousTokens = nodeConditionTokens(node);
                List<Token> mergedTokens = nodeConditionTokens(mergedNode);
                if (!mergedTokens.equals(previousTokens)) {
                    Set<List<Token>> containerConditions = existingInContainer.computeIfAbsent(
                            stub.path(),
                            k -> new LinkedHashSet<>());
                    Set<List<Token>> blockConditions = existingInBlock.computeIfAbsent(
                            stub.path(),
                            k -> new LinkedHashSet<>());
                    containerConditions.remove(previousTokens);
                    blockConditions.remove(previousTokens);
                    boolean addedInContainer = containerConditions.add(mergedTokens);
                    boolean addedInBlock = blockConditions.add(mergedTokens);
                    if (!addedInContainer || !addedInBlock) {
                        if (addedInContainer) {
                            containerConditions.remove(mergedTokens);
                        }
                        if (addedInBlock) {
                            blockConditions.remove(mergedTokens);
                        }
                        node.remove();
                        return false;
                    }
                    node.replace(mergedNode);
                    node = mergedNode;
                }
                stub = next;
                return true;
            }
        }

        Guard reachability(Node node) {
            if (reachableBlocks.containsKey(node)) {
                return reachableBlocks.get(node);
            }
            throw new IllegalStateException("Block reachability not found: " + node);
        }

        Node mirror(Node node) {
            Node mirror = mirrors.get(node);
            if (mirror != null) {
                return mirror;
            } else {
                throw new IllegalStateException("Mirror not found: " + node);
            }
        }
    }

    private final class DedupVisitor implements Node.Visitor {

        private final Map<String, List<Node>> steps = new LinkedHashMap<>();
        private final Map<Node, Node> mirrors;

        DedupVisitor(Map<Node, Node> mirrors) {
            this.mirrors = mirrors;
        }

        @Override
        public boolean visit(Node node) {
            if (node.kind() == Kind.STEP) {
                String name = node.attribute("name").getString();
                steps.computeIfAbsent(name, k -> new ArrayList<>()).add(node);
                return false;
            }
            return true;
        }

        @Override
        public void postVisit(Node node) {
            if (node.kind() == Kind.SCRIPT) {
                for (List<Node> steps : steps.values()) {
                    List<List<Node>> groups = Lists.groupingBy(steps, step -> Lists.map(step.collect(), Nodes::hash));
                    for (List<Node> group : groups) {
                        if (group.size() < 2) {
                            continue;
                        }
                        Node first = group.get(0);
                        applyMergedCondition(first, mergedCondition(group));
                        for (int i = 1; i < group.size(); i++) {
                            group.get(i).remove();
                        }
                    }
                }
            }
        }

        void applyMergedCondition(Node step, Expression condition) {
            Node parent = step.parent();
            if (parent.kind() != Kind.CONDITION) {
                if (condition != Expression.TRUE) {
                    throw new IllegalStateException("Missing condition wrapper for merged step: " + step);
                }
                return;
            }
            if (condition == Expression.TRUE) {
                parent.replace(step);
            } else {
                parent.expression(condition);
            }
        }

        Expression mergedCondition(List<Node> group) {
            Scope scope = scope(mirror(group.get(0)));
            Set<Node> parents = mergedActivationAnchors(group);
            Guard mergedReach = mergedActivationReachability(group);
            if (mergedReach != null) {
                return simplifyMergedConditionExpression(reachabilityExpression(mergedReach, scope), scope);
            }

            Expression condition = Expression.FALSE;
            for (Node parent : parents) {
                condition = condition.or(flow.activationCondition(parent));
            }
            return simplifyMergedConditionExpression(condition, scope);
        }

        Guard mergedActivationReachability(List<Node> group) {
            Scope scope = scope(mirror(group.get(0)));
            Guard merged = null;
            for (Node parent : mergedActivationAnchors(group)) {
                Guard reach = localProjectedReach(flow.activationCondition(parent), scope);
                if (reach == null) {
                    return null;
                }
                merged = merged == null ? reach : flow.or(merged, reach);
            }
            return merged;
        }

        Set<Node> mergedActivationAnchors(List<Node> group) {
            LinkedHashSet<Node> parents = new LinkedHashSet<>();
            for (Node step : group) {
                Node parent = mirror(step).parent();
                if (parent == null) {
                    throw new IllegalStateException("Merged step is missing an activation anchor: " + step);
                }
                parents.add(parent);
            }
            LinkedHashSet<Node> anchors = new LinkedHashSet<>();
            Map<Node, Set<Node>> enumParentsByInput = new LinkedHashMap<>();
            for (Node parent : parents) {
                if (parent.kind() != Kind.INPUT_OPTION) {
                    anchors.add(parent);
                    continue;
                }
                Node input = parent.ancestor(Kind::isInput).orElse(null);
                if (input == null || input.kind() != Kind.INPUT_ENUM) {
                    anchors.add(parent);
                    continue;
                }
                enumParentsByInput.computeIfAbsent(input, key -> new LinkedHashSet<>()).add(parent);
            }
            for (Map.Entry<Node, Set<Node>> entry : enumParentsByInput.entrySet()) {
                if (coversAllEnumOptions(entry.getKey(), entry.getValue())) {
                    anchors.add(entry.getKey());
                } else {
                    anchors.addAll(entry.getValue());
                }
            }
            return anchors;
        }

        private boolean coversAllEnumOptions(Node input, Set<Node> coveredParents) {
            Set<String> coveredValues = new LinkedHashSet<>();
            for (Node parent : coveredParents) {
                coveredValues.add(parent.value().getString());
            }
            Set<String> reachableValues = new LinkedHashSet<>();
            for (Node child : input.children()) {
                if (skipCompiledNode(child)) {
                    continue;
                }
                Node option = child.unwrap();
                if (option.kind() == Kind.INPUT_OPTION) {
                    reachableValues.add(option.value().getString());
                }
            }
            return !reachableValues.isEmpty() && coveredValues.equals(reachableValues);
        }

        Node mirror(Node node) {
            Node mirror = mirrors.get(node);
            if (mirror != null) {
                return mirror;
            }
            throw new IllegalStateException("Mirror not found: " + node);
        }
    }

    private static final class FileObject implements Comparable<FileObject> {
        private final String checksum;
        private final List<FileOp> ops;
        private final Expression condition;

        FileObject(String checksum, List<FileOp> ops, Expression condition) {
            this.checksum = checksum;
            this.ops = List.copyOf(ops);
            this.condition = condition;
        }

        Expression condition() {
            return condition;
        }

        List<Expression.Token> conditionTokens() {
            return condition.tokens();
        }

        @Override
        public int compareTo(FileObject o) {
            int r = Lists.compare(conditionTokens(), o.conditionTokens());
            if (r == 0) {
                r = checksum.compareTo(o.checksum);
                if (r == 0) {
                    r = Lists.compare(ops, o.ops);
                }
            }
            return r;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FileObject)) {
                return false;
            }
            FileObject other = (FileObject) o;
            return Objects.equals(checksum, other.checksum)
                   && Objects.equals(ops, other.ops)
                   && Objects.equals(conditionTokens(), other.conditionTokens());
        }

        @Override
        public int hashCode() {
            return Objects.hash(checksum, ops, Objects.hash(conditionTokens()));
        }
    }

    private static final class FileOps implements Comparable<FileOps> {
        private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{[^}]+}");
        private final Guard reach;
        private final String reachLiteral;
        private final List<FileOp> ops;

        FileOps(List<FileOp> ops, Guard reach, String reachLiteral) {
            this.reach = reach;
            this.reachLiteral = reachLiteral;
            this.ops = List.copyOf(ops);
        }

        @Override
        public int compareTo(FileOps o) {
            int r = reachLiteral.compareTo(o.reachLiteral);
            if (r == 0) {
                r = Lists.compare(ops, o.ops);
            }
            return r;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FileOps)) {
                return false;
            }
            FileOps other = (FileOps) o;
            return Objects.equals(reach, other.reach)
                   && Objects.equals(ops, other.ops);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reach, ops);
        }

        List<FileOp> resolve(String id, String path) {
            if (isFoldable()) {
                return List.of(new FileOp(Pattern.quote(id), fold(path)));
            } else {
                return Lists.addAll(ops, 0, new FileOp(Pattern.quote(id), path));
            }
        }

        String fold(String path) {
            String target = path;
            for (FileOp op : ops) {
                // escape ${}
                String replace = VAR_PATTERN.matcher(op.replacement)
                        .replaceAll(r -> Matcher.quoteReplacement(Matcher.quoteReplacement(r.group(0))));
                target = target.replaceAll(op.regex, replace);
            }
            return target;
        }

        boolean isFoldable() {
            boolean interpolated = false;
            ListIterator<FileOp> it = ops.listIterator();
            while (it.hasNext()) {
                FileOp op = it.next();
                if (op.replacement.contains("${")) {
                    if (interpolated) {
                        if (it.hasNext() || !"^(.*)$".equals(op.regex)) {
                            // only the last op can use interpolation
                            // except if the last regex is "globbing"
                            return false;
                        }
                    }
                    interpolated = true;
                }
            }
            return true;
        }
    }

    private static final class FileOp implements Comparable<FileOp> {
        private final String regex;
        private final String replacement;

        FileOp(String regex, String replacement) {
            this.regex = regex;
            this.replacement = replacement;
        }

        @Override
        public int compareTo(FileOp o) {
            int r = replacement.compareTo(o.replacement);
            if (r == 0) {
                r = regex.compareTo(o.regex);
            }
            return r;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FileOp)) {
                return false;
            }
            FileOp other = (FileOp) o;
            return Objects.equals(regex, other.regex)
                   && Objects.equals(replacement, other.replacement);
        }

        @Override
        public int hashCode() {
            return Objects.hash(regex, replacement);
        }
    }
}
