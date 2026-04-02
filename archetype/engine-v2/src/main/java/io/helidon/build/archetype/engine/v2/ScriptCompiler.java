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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.archetype.engine.v2.Expression.Token;
import io.helidon.build.archetype.engine.v2.Node.Kind;
import io.helidon.build.archetype.engine.v2.Value.Type;
import io.helidon.build.common.Combinatorics;
import io.helidon.build.common.InputStreams;
import io.helidon.build.common.Lists;
import io.helidon.build.common.Maps;
import io.helidon.build.common.PropertyEvaluator;
import io.helidon.build.common.SourcePath;
import io.helidon.build.common.logging.Log;

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

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<Node, String> scopes = new HashMap<>();
    private final Map<Node, Map<String, Value<?>>> paths = new HashMap<>();
    private final Map<String, Set<Node>> declaredValues = new HashMap<>();
    private final Map<Node, Map<String, Value<?>>> declaredValueCache = new IdentityHashMap<>();
    private final Map<Node, ConditionSemantics> conditionSemanticsByNode = new IdentityHashMap<>();
    private final Map<String, Set<Node>> definedRefs = new HashMap<>();
    private final Map<Node, Reachability> reachByNode = new HashMap<>();
    private final Map<Node, Map<String, Reachability>> refReachByNode = new HashMap<>();
    private final Map<Node, Map<String, ConstantBindings>> refBindingsByNode = new HashMap<>();
    private final Map<String, InputDomain> domains = new LinkedHashMap<>();
    private final Map<String, Type> refTypes = new HashMap<>();
    private final Set<String> textInputRefs = new HashSet<>();
    private final Map<String, Boolean> textInputProvenance = new HashMap<>();
    private final Map<Node, Path> workDirs = new HashMap<>();
    private final Set<String> errors = new LinkedHashSet<>();
    private final Node sourceNode;
    private final Context ctx = new Context();
    private volatile List<Option> options;

    /**
     * Create a new instance.
     *
     * @param source source
     * @param cwd    cwd
     */
    public ScriptCompiler(Script.Source source, Path cwd) {
        this.ctx.pushCwd(requireNonNull(cwd, "cwd is null"));
        this.sourceNode = Script.load(requireNonNull(source, "source is null"), false);
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
            compile(image);
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

    Path cwd() {
        return ctx.cwd();
    }

    private void init() {
        if (initialized.compareAndSet(false, true)) {
            new InlineInvoker().invoke(sourceNode); // inline call sites
            new RefsInvoker().invoke(sourceNode); // gather refs and prune tree
        }
    }

    private void compile(Image image) {
        Map<Node, Node> mirrors = new HashMap<>();
        Map<Node, ConditionSemantics> renderedConditionSemantics = new IdentityHashMap<>();
        Map<Node, Reachability> reachableBlocks = new IdentityHashMap<>();
        Map<Node, Set<String>> conditionRefs = new IdentityHashMap<>();
        mirrors.put(sourceNode, image.node);
        mirrors.put(image.node, sourceNode);
        renderedConditionSemantics.put(image.node, conditionSemantics(sourceNode));
        reachableBlocks.put(image.node, trueReach());
        sourceNode.visit(new InputVisitor(image,
                mirrors,
                renderedConditionSemantics,
                reachableBlocks,
                conditionRefs));
        // render inputs
        if (!options.contains(Options.NO_OUTPUT)) {
            sourceNode.visit(new OutputVisitor(image, conditionRefs)); // render outputs
        }
        image.node.visit(new StubsVisitor(mirrors, reachableBlocks, conditionRefs)); // render stubs
        image.node.visit(new DedupVisitor(mirrors)); // de-dup steps
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
            String key = scopeId(node);
            Value<?> value = declaredValue(node, key);
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
            inputs.computeIfAbsent(scopeId(node), k -> new ArrayList<>()).add(node);
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
            String scopeId = scopeId(node);
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
            Expression expr = node.expression();
            Scope scope = scope(node);
            Reachability blockReach = reachability(node.parent());
            Map<String, Reachability> refReachMap = refReachByNode.getOrDefault(node, Map.of());
            Map<String, ConstantBindings> refBindings = refBindingsByNode.getOrDefault(node, Map.of());
            ExpressionAnalyzer analyzer = new ExpressionAnalyzer(scope, refBindings, refReachMap, true, false);
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

            boolean resolved = true;
            Map<String, Reachability> variableDemands = analysis.variableDemands;
            for (Entry<String, String> variable : analysis.variables.entrySet()) {
                String ref = variable.getValue();
                boolean variableResolved = false;
                Reachability requiredReach = blockReach;
                Reachability variableDemand = variableDemands.get(ref);
                if (variableDemand != null) {
                    requiredReach = requiredReach == null ? variableDemand : requiredReach.and(variableDemand);
                }
                Reachability refReach = refReachMap.get(ref);
                if (requiredReach != null && refReach != null) {
                    variableResolved = refReach.contains(requiredReach);
                }
                Reachability bindingReach = null;
                if (!variableResolved && requiredReach != null && refReach != null) {
                    // replay constant bindings against the recorded definition reachability first
                    analyzer = new ExpressionAnalyzer(scope, refBindings, refReachMap, false, false);
                    bindingReach = analyzer.analyze(reachabilityExpression(refReach, scope)).reach;
                    if (bindingReach != null) {
                        variableResolved = bindingReach.contains(requiredReach);
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

    private Value<?> validateExpressionOperator(Expression.Operator operator, Deque<Value<?>> stack) {
        Value<?> op1 = stack.pop();
        switch (operator) {
            case NOT:
                return Value.of(!op1.getBoolean());
            case SIZEOF:
                if (op1.type() == Value.Type.LIST) {
                    return Value.of(op1.getList().size());
                }
                return Value.of(op1.getString().length());
            case AS_INT:
                return Value.of(op1.getInt());
            case AS_LIST:
                return Value.of(op1.getList());
            case AS_STRING:
                return Value.of(op1.getString());
            default:
                Value<?> op2 = stack.pop();
                switch (operator) {
                    case OR:
                        return Value.of(op2.asBoolean().orElse(false) || op1.asBoolean().orElse(false));
                    case AND:
                        return Value.of(op2.asBoolean().orElse(false) && op1.asBoolean().orElse(false));
                    case EQUAL:
                        return Value.of(Value.isEqual(op2, op1));
                    case NOT_EQUAL:
                        return Value.of(!Value.isEqual(op2, op1));
                    case GREATER_THAN:
                        return Value.of(op2.getInt() > op1.getInt());
                    case GREATER_OR_EQUAL:
                        return Value.of(op2.getInt() >= op1.getInt());
                    case LOWER_THAN:
                        return Value.of(op2.getInt() < op1.getInt());
                    case LOWER_OR_EQUAL:
                        return Value.of(op2.getInt() <= op1.getInt());
                    case CONTAINS:
                        if (op1.type() == Value.Type.LIST) {
                            return Value.of(new HashSet<>(op2.getList()).containsAll(op1.getList()));
                        }
                        if (op2.type() == Value.Type.LIST) {
                            return Value.of(op2.getList().contains(op1.asString().orElse(null)));
                        }
                        return Value.of(op1.isPresent() && op2.asString().orElse("").contains(op1.getString()));
                    default:
                        throw new IllegalStateException("Unsupported operator: " + operator);
                }
        }
    }

    private Value<?> validationValue(Scope scope, String variable) {
        Type type = refTypes.getOrDefault(scope.key(variable), Type.EMPTY);
        return Value.typed(type);
    }

    private boolean hasPriorDefinition(Node node, String key) {
        for (Node definition : definedRefs.getOrDefault(key, Set.of())) {
            if (!attached(definition) || node.id() <= definition.id()) {
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
        boolean result = textInputRefs.contains(key);
        if (!result) {
            for (Node node : declaredValues.getOrDefault(key, Set.of())) {
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
                ExpressionAnalyzer analyzer = new ExpressionAnalyzer(scope, Map.of(), Map.of(), false, false);
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
            for (Node input : option.ancestors(Kind::isInput)) {
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
            List<Node> inputs = step.collect(Kind::isInput);
            if (inputs.isEmpty()) {
                errors.add(String.format("%s %s",
                        step.location(),
                        STEP_NO_INPUT));
            } else {
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

    String scopeId(Node node) {
        for (Node n = node; n != null; n = n.parent()) {
            String scopeId = scopes.get(n);
            if (scopeId != null) {
                return scopeId;
            }
        }
        return "";
    }

    Scope scope(Node node) {
        return ctx.scope().get("~" + scopeId(node));
    }

    Value<?> declaredValue(Node node, String key) {
        return declaredValueCache
                .computeIfAbsent(node, n -> new HashMap<>())
                .computeIfAbsent(key, k -> declaredValue0(node, k));
    }

    private Value<?> declaredValue0(Node node, String key) {
        Node node0 = declaredValueByReachability(node, key);
        if (node0 != null) {
            return Value.typed(node0.value(), node0.kind().valueType());
        }
        return Value.empty();
    }

    private Node declaredValueByReachability(Node node, String key) {
        Reachability blockReach = reachability(node.parent());
        if (blockReach == null) {
            return null;
        }
        Node node0 = null;
        for (Node n : declaredValues.getOrDefault(key, Set.of())) {
            if (!attached(n) || node.id() <= n.id()) {
                continue;
            }
            Reachability refReachability = reachability(n);
            if (refReachability == null) {
                continue;
            }
            Reachability overlap = blockReach.and(refReachability);
            if (overlap.isFalse()) {
                continue;
            }
            if (refReachability.contains(blockReach)) {
                if (node0 == null || n.id() > node0.id()) {
                    node0 = n;
                }
            } else if (node0 != null && n.id() > node0.id()) {
                node0 = null;
            }
        }
        return node0;
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

    private Reachability conditionReach(Node node, ReachabilityState currentState) {
        Reachability known = currentState.known;
        Reachability path = pathReach(node);
        if (known == null) {
            return path;
        }
        return known.and(path);
    }

    private Reachability pathReach(Node node) {
        Reachability reach = trueReach();
        for (Entry<String, Value<?>> entry : path(node).entrySet()) {
            Reachability valueReach = valueReach(entry.getKey(), entry.getValue());
            if (valueReach != null) {
                reach = reach.and(valueReach);
            }
        }
        return reach;
    }

    private Reachability valueReach(String key, Value<?> value) {
        String scalarValue = scalarLiteral(value);
        if (scalarValue != null) {
            return Reachability.scalar(key, Set.of(scalarValue), domains);
        }
        if (value.type() == Type.LIST) {
            return Reachability.listContains(key, new TreeSet<>(value.getList()), domains);
        }
        return null;
    }

    private boolean prunedByReachability(Node node) {
        Scope scope = scope(node);
        Map<String, Reachability> definitions = refReachByNode.getOrDefault(node, Map.of());
        Map<String, ConstantBindings> bindings = refBindingsByNode.getOrDefault(node, Map.of());
        if (node.kind().isInput()) {
            String key = scopeId(node);
            definitions = withoutKey(definitions, key);
            bindings = withoutKey(bindings, key);
        }
        return translatePruningTerms(node, conditionSemantics(node).pruning, scope, bindings, definitions).isFalse();
    }

    private <T> Map<String, T> withoutKey(Map<String, T> values, String key) {
        if (key == null || !values.containsKey(key)) {
            return values;
        }
        Map<String, T> filtered = new LinkedHashMap<>(values);
        filtered.remove(key);
        return Map.copyOf(filtered);
    }

    private Map<String, Value<?>> path(Node node) {
        return paths.computeIfAbsent(node.parent(), this::path0);
    }

    private Map<String, Value<?>> path0(Node node) {
        Map<String, Value<?>> values = new LinkedHashMap<>();
        for (Node n = node; n != null; n = n.parent()) {
            switch (n.kind()) {
                case INPUT_BOOLEAN:
                    values.put(scopeId(n), Value.TRUE);
                    break;
                case INPUT_OPTION:
                    Node input = n.ancestor(Kind::isInput).orElseThrow();
                    if (input.kind() == Kind.INPUT_ENUM) {
                        values.put(scopeId(input), Value.of(n.value().getString()));
                    }
                    break;
                default:
            }
        }
        return values;
    }

    private Map<String, ConstantBindings> snapshotBindings(Map<String, ConstantBindings> bindings) {
        if (bindings.isEmpty()) {
            return Map.of();
        }
        Map<String, ConstantBindings> snapshot = new LinkedHashMap<>();
        bindings.forEach((key, value) -> snapshot.put(key, value.copy()));
        return Map.copyOf(snapshot);
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

    private void registerDomain(Node node, String key) {
        switch (node.kind()) {
            case INPUT_BOOLEAN:
                domains.putIfAbsent(key, InputDomain.booleanDomain());
                break;
            case INPUT_ENUM:
                domains.put(key, InputDomain.scalar(optionValues(node)));
                break;
            case INPUT_LIST:
                domains.put(key, InputDomain.list(optionValues(node)));
                break;
            default:
                registerImplicitDomain(key, node.kind().valueType());
        }
    }

    private void registerImplicitDomain(String key, Type type) {
        if (domains.containsKey(key)) {
            return;
        }
        if (type == Type.BOOLEAN) {
            domains.put(key, InputDomain.booleanDomain());
        }
    }

    private Set<String> optionValues(Node node) {
        Set<String> values = new TreeSet<>();
        for (Node option : node.traverse(Kind.INPUT_OPTION::equals)) {
            if (option.ancestor(Kind::isInput).orElse(null) == node) {
                values.add(option.value().getString());
            }
        }
        return values;
    }

    private Reachability trueReach() {
        return Reachability.trueValue(domains);
    }

    private Reachability falseReach() {
        return Reachability.falseValue(domains);
    }

    private Reachability reachability(Node node) {
        return node == null ? trueReach() : reachByNode.get(node);
    }

    private ConditionSemantics conditionSemantics(Node node) {
        ConditionSemantics semantics = conditionSemanticsByNode.get(node);
        if (semantics != null) {
            return semantics;
        }
        throw new IllegalStateException("Condition semantics not found: " + node);
    }

    private Reachability definitionReachability(Node node) {
        switch (node.kind()) {
            case INPUT_BOOLEAN:
            case INPUT_ENUM:
            case INPUT_LIST:
            case INPUT_TEXT:
                return reachability(node.parent());
            case PRESET_BOOLEAN:
            case PRESET_ENUM:
            case PRESET_LIST:
            case PRESET_TEXT:
            case VARIABLE_BOOLEAN:
            case VARIABLE_ENUM:
            case VARIABLE_LIST:
            case VARIABLE_TEXT:
                return reachability(node);
            default:
                return null;
        }
    }

    private static Expression reachabilityExpression(Reachability reach, Scope scope) {
        return reach.toExpression(scope).reduce();
    }

    private static Expression residualExpression(Reachability reach, Reachability base, Scope scope) {
        return reach.toExpression(scope).reduce(base.toExpression(scope));
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

    private Expression outputCondition(Node node, Scope scope) {
        Reachability reach = reachability(node);
        if (reach != null) {
            return reachabilityExpression(reach, scope);
        }
        return conditionSemantics(node).emittedCondition(scope);
    }

    Expression activationCondition(Node node) {
        return node == null ? Expression.TRUE : outputCondition(node, scope(node));
    }

    private List<Expression> conjunctionTerms(Expression expr) {
        List<String> literals = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String literal = expr.literal();
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < literal.length(); i++) {
            char ch = literal.charAt(i);
            if (ch == '\'') {
                quoted = !quoted;
                current.append(ch);
                continue;
            }
            if (!quoted) {
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    if (depth == 0) {
                        return List.of(expr);
                    }
                    depth--;
                } else if (depth == 0 && ch == '&' && i + 1 < literal.length() && literal.charAt(i + 1) == '&') {
                    String term = current.toString().trim();
                    if (term.isEmpty()) {
                        return List.of(expr);
                    }
                    literals.add(term);
                    current.setLength(0);
                    i++;
                    continue;
                }
            }
            current.append(ch);
        }
        if (quoted || depth != 0) {
            return List.of(expr);
        }
        String term = current.toString().trim();
        if (term.isEmpty()) {
            return List.of(expr);
        }
        literals.add(term);
        return Lists.map(literals, Expression::create);
    }

    private List<ConditionTerm> expressionTerms(Expression expr) {
        if (expr == null || expr == Expression.TRUE) {
            return List.of();
        }
        LinkedHashSet<ConditionTerm> terms = new LinkedHashSet<>();
        for (Expression term0 : conjunctionTerms(expr)) {
            ConditionTerm term = ConditionTerm.of(term0);
            if (term.isTrue()) {
                continue;
            }
            if (term.isFalse()) {
                return List.of(ConditionTerm.FALSE);
            }
            terms.add(term);
        }
        return terms.isEmpty() ? List.of() : List.copyOf(terms);
    }

    private Reachability translatePruningTerms(Node node,
                                               List<ConditionTerm> terms,
                                               Scope scope,
                                               Map<String, ConstantBindings> bindings,
                                               Map<String, Reachability> definitions) {
        Reachability reach = trueReach();
        for (ConditionTerm term : terms) {
            ExpressionAnalyzer analyzer = new ExpressionAnalyzer(scope, bindings, definitions, false, false);
            ExpressionAnalysis analysis = analyzer.analyze(term.tokens);
            if (analysis.reach == null) {
                throw new IllegalStateException("Unsupported pruning expression for: " + node);
            }
            reach = reach.and(analysis.reach);
            if (reach.isFalse()) {
                return reach;
            }
        }
        return reach;
    }

    private Value<?> constantValue(Node node) {
        try {
            Value<?> value = Value.typed(node.value(), node.kind().valueType());
            if (value.type() == Type.STRING && value.getString().matches("^\\$\\{~?[\\w.-]+}$")) {
                return null;
            }
            return value;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private final class ExpressionAnalyzer {
        private final Scope scope;
        private final Map<String, ConstantBindings> bindings;
        private final Map<String, Reachability> definitions;
        private final boolean validate;
        private final boolean capture;

        ExpressionAnalyzer(Scope scope,
                           Map<String, ConstantBindings> bindings,
                           Map<String, Reachability> definitions,
                           boolean validate,
                           boolean capture) {
            this.scope = scope;
            this.bindings = bindings;
            this.definitions = definitions;
            this.validate = validate;
            this.capture = capture;
        }

        ExpressionAnalysis analyze(Expression expr) {
            return analyze(expr.tokens(), expr);
        }

        ExpressionAnalysis analyze(List<Token> tokens) {
            if (capture) {
                throw new IllegalStateException("Expression source required when capturing supported terms");
            }
            return analyze(tokens, null);
        }

        ExpressionAnalysis analyze(List<Token> tokens, Expression source) {
            Map<String, String> variables = new LinkedHashMap<>();
            List<Expression.Operator> incompatibleOps = new ArrayList<>();
            Deque<AnalysisTerm> stack = new ArrayDeque<>();
            Deque<Value<?>> validationStack = validate ? new ArrayDeque<>() : null;
            String evaluationError = null;
            boolean compatible = true;
            try {
                for (Token token : tokens) {
                    if (validationStack != null && evaluationError == null) {
                        try {
                            validationStack.push(validationValue(token, validationStack));
                        } catch (RuntimeException ex) {
                            evaluationError = ex.getMessage();
                        }
                    }
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
                                    finalizeEvaluation(validationStack, evaluationError),
                                    supportedTerms(source));
                    }
                }
                evaluationError = finalizeEvaluation(validationStack, evaluationError);
                if (stack.size() != 1 || !compatible) {
                    return new ExpressionAnalysis(null,
                            Map.of(),
                            variables,
                            incompatibleOps,
                            evaluationError,
                            supportedTerms(source));
                }
                Map<String, Reachability> demands = new HashMap<>();
                AnalysisBoolean result = booleanTerm(stack.pop());
                result.collect(trueReach(), demands);
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
                        finalizeEvaluation(validationStack, evaluationError),
                        supportedTerms(source));
            }
        }

        SupportedTerms supportedTerms(Expression expr) {
            if (!capture || expr == null) {
                return null;
            }
            Reachability supported = trueReach();
            Expression unsupported = Expression.TRUE;
            boolean hasSupportedTerms = false;
            for (Expression term : conjunctionTerms(expr)) {
                ExpressionAnalyzer analyzer = new ExpressionAnalyzer(scope, bindings, definitions, false, false);
                ExpressionAnalysis analysis = analyzer.analyze(term);
                if (analysis.reach != null) {
                    supported = supported.and(analysis.reach);
                    hasSupportedTerms = true;
                } else {
                    unsupported = unsupported.and(term);
                }
            }
            return new SupportedTerms(supported, unsupported.reduce(), hasSupportedTerms);
        }

        SupportedTerms supportedTerms(Reachability reach) {
            return capture ? SupportedTerms.fullySupported(reach) : null;
        }

        Value<?> validationValue(Token token, Deque<Value<?>> validationStack) {
            if (token.isOperator()) {
                return validateExpressionOperator(token.operator(), validationStack);
            }
            if (token.isOperand()) {
                return token.operand();
            }
            if (token.isVariable()) {
                return ScriptCompiler.this.validationValue(scope, token.variable());
            }
            throw new IllegalStateException("Invalid token");
        }

        String finalizeEvaluation(Deque<Value<?>> validationStack, String evaluationError) {
            if (validationStack == null || evaluationError != null) {
                return evaluationError;
            }
            try {
                validationStack.pop().asBoolean().get();
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
                    Reachability truthy = value.getBoolean() ? trueReach() : falseReach();
                    return new AnalysisBoolean(truthy, truthy, (demand, output) -> {
                    });
                }
                return new AnalysisBoolean(null, null, term::collect);
            }
            if (term instanceof AnalysisRef) {
                String key = ((AnalysisRef) term).key;
                return new AnalysisBoolean(translatedBooleanReachability(key),
                        demandBooleanReachability(key),
                        term::collect);
            }
            return new AnalysisBoolean(null, null, term::collect);
        }

        AnalysisBoolean equality(AnalysisTerm right, AnalysisTerm left, boolean negate) {
            Reachability translated = translatedEquality(left, right);
            Reachability demand = demandEquality(left, right);
            if (negate) {
                translated = negate(translated);
                demand = negate(demand);
            }
            return new AnalysisBoolean(translated, demand, (d, o) -> {
                left.collect(d, o);
                right.collect(d, o);
            });
        }

        Reachability translatedEquality(AnalysisTerm left, AnalysisTerm right) {
            if (left instanceof AnalysisValue && right instanceof AnalysisValue) {
                Value<?> leftValue = ((AnalysisValue) left).value;
                Value<?> rightValue = ((AnalysisValue) right).value;
                return Value.isEqual(leftValue, rightValue) ? trueReach() : falseReach();
            }
            if (left instanceof AnalysisRef && right instanceof AnalysisValue) {
                String leftKey = ((AnalysisRef) left).key;
                Value<?> rightValue = ((AnalysisValue) right).value;
                return translatedEquality(leftKey, rightValue);
            }
            if (left instanceof AnalysisValue && right instanceof AnalysisRef) {
                String rightKey = ((AnalysisRef) right).key;
                Value<?> leftValue = ((AnalysisValue) left).value;
                return translatedEquality(rightKey, leftValue);
            }
            return null;
        }

        Reachability demandEquality(AnalysisTerm left, AnalysisTerm right) {
            if (left instanceof AnalysisValue && right instanceof AnalysisValue) {
                Value<?> leftValue = ((AnalysisValue) left).value;
                Value<?> rightValue = ((AnalysisValue) right).value;
                return Value.isEqual(leftValue, rightValue) ? trueReach() : falseReach();
            }
            if (left instanceof AnalysisRef && right instanceof AnalysisValue) {
                String leftKey = ((AnalysisRef) left).key;
                Value<?> rightValue = ((AnalysisValue) right).value;
                return demandEquality(leftKey, rightValue);
            }
            if (left instanceof AnalysisValue && right instanceof AnalysisRef) {
                String rightKey = ((AnalysisRef) right).key;
                Value<?> leftValue = ((AnalysisValue) left).value;
                return demandEquality(rightKey, leftValue);
            }
            return null;
        }

        AnalysisBoolean contains(AnalysisTerm right, AnalysisTerm left) {
            Reachability translatedTruthy = translatedContains(left, right);
            Reachability demandTruthy = demandContains(left, right);
            return new AnalysisBoolean(translatedTruthy, demandTruthy, (demand, output) -> {
                left.collect(demand, output);
                right.collect(demand, output);
            });
        }

        Reachability translatedContains(AnalysisTerm left, AnalysisTerm right) {
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
                        return values.containsAll(required) ? trueReach() : falseReach();
                    }
                }
            }
            return null;
        }

        Reachability demandContains(AnalysisTerm left, AnalysisTerm right) {
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
                        return values.containsAll(required) ? trueReach() : falseReach();
                    }
                }
            }
            return null;
        }

        Reachability translatedBooleanReachability(String key) {
            ConstantBindings binding = bindings.get(key);
            if (binding != null) {
                if (binding.type != Type.BOOLEAN) {
                    return null;
                }
            } else {
                InputDomain domain = domains.get(key);
                if (domain == null || !domain.isBoolean()) {
                    return null;
                }
            }
            return translatedEquality(key, Value.TRUE);
        }

        Reachability demandBooleanReachability(String key) {
            InputDomain domain = domains.get(key);
            if (domain == null || !domain.isBoolean()) {
                return null;
            }
            Reachability raw = Reachability.scalar(key, Set.of("true"), domains);
            Reachability defined = definitions.get(key);
            return defined == null ? raw : defined.and(raw);
        }

        Reachability translatedEquality(String key, Value<?> value) {
            ConstantBindings binding = bindings.get(key);
            Reachability bound = binding != null ? binding.match(value) : null;
            Reachability raw = rawEquality(key, value);
            return combine(key, bound, raw);
        }

        Reachability demandEquality(String key, Value<?> value) {
            Reachability raw = rawEquality(key, value);
            Reachability defined = definitions.get(key);
            if (raw == null) {
                return null;
            }
            return defined == null ? raw : defined.and(raw);
        }

        Reachability translatedScalarAny(String key, Set<String> values) {
            ConstantBindings binding = bindings.get(key);
            Reachability bound = binding != null ? binding.scalarAny(values) : null;
            Reachability raw = rawScalarAny(key, values);
            return combine(key, bound, raw);
        }

        Reachability demandScalarAny(String key, Set<String> values) {
            Reachability raw = rawScalarAny(key, values);
            Reachability defined = definitions.get(key);
            if (raw == null) {
                return null;
            }
            return defined == null ? raw : defined.and(raw);
        }

        Reachability translatedListContains(String key, Value<?> value) {
            Set<String> required = containsValues(value);
            if (required == null) {
                return null;
            }
            ConstantBindings binding = bindings.get(key);
            Reachability bound = binding != null ? binding.listContains(required) : null;
            Reachability raw = rawListContains(key, required);
            return combine(key, bound, raw);
        }

        Reachability demandListContains(String key, Value<?> value) {
            Set<String> required = containsValues(value);
            if (required == null) {
                return null;
            }
            Reachability raw = rawListContains(key, required);
            Reachability defined = definitions.get(key);
            if (raw == null) {
                return null;
            }
            return defined == null ? raw : defined.and(raw);
        }

        Reachability combine(String key, Reachability bound, Reachability raw) {
            ConstantBindings binding = bindings.get(key);
            Reachability defined = definitions.get(key);
            if (binding == null) {
                if (raw == null) {
                    return bound;
                }
                return defined == null ? raw : defined.and(raw);
            }
            if (raw == null) {
                return bound;
            }
            Reachability available = defined == null ? trueReach() : defined;
            Reachability unresolved = available.subtract(binding.defined()).and(raw);
            return bound == null ? unresolved : bound.or(unresolved);
        }

        Reachability rawEquality(String key, Value<?> value) {
            String scalarValue = scalarLiteral(value);
            if (scalarValue == null) {
                return null;
            }
            InputDomain domain = domains.get(key);
            if (domain == null || domain.kind != InputDomain.Kind.SCALAR) {
                return null;
            }
            if (!domain.scalarValues.contains(scalarValue)) {
                return falseReach();
            }
            return Reachability.scalar(key, Set.of(scalarValue), domains);
        }

        Reachability rawScalarAny(String key, Set<String> values) {
            InputDomain domain = domains.get(key);
            if (domain == null || domain.kind != InputDomain.Kind.SCALAR) {
                return null;
            }
            Set<String> allowed = new TreeSet<>(values);
            allowed.retainAll(domain.scalarValues);
            return allowed.isEmpty() ? falseReach() : Reachability.scalar(key, allowed, domains);
        }

        Reachability rawListContains(String key, Set<String> required) {
            InputDomain domain = domains.get(key);
            if (domain == null || domain.kind != InputDomain.Kind.LIST) {
                return null;
            }
            if (!domain.listItems.containsAll(required)) {
                return falseReach();
            }
            return Reachability.listContains(key, required, domains);
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

        void addDemand(String key, Reachability demand, Map<String, Reachability> output) {
            if (demand == null || demand.isFalse()) {
                return;
            }
            output.compute(key, (k, current) -> current == null ? demand : current.or(demand));
        }

        Reachability negate(Reachability reach) {
            return reach == null ? null : trueReach().subtract(reach);
        }

        private abstract class AnalysisTerm {
            abstract void collect(Reachability demand, Map<String, Reachability> output);
        }

        private final class AnalysisValue extends AnalysisTerm {
            private final Value<?> value;

            AnalysisValue(Value<?> value) {
                this.value = value;
            }

            @Override
            public void collect(Reachability demand, Map<String, Reachability> output) {
            }
        }

        private final class AnalysisRef extends AnalysisTerm {
            private final String key;

            AnalysisRef(String key) {
                this.key = key;
            }

            @Override
            public void collect(Reachability demand, Map<String, Reachability> output) {
                addDemand(key, demand, output);
            }
        }

        private final class AnalysisOpaque extends AnalysisTerm {
            private final List<AnalysisTerm> terms;

            AnalysisOpaque(AnalysisTerm... terms) {
                this.terms = List.of(terms);
            }

            @Override
            void collect(Reachability demand, Map<String, Reachability> output) {
                for (AnalysisTerm term : terms) {
                    term.collect(demand, output);
                }
            }
        }

        private final class AnalysisBoolean extends AnalysisTerm {
            private final Reachability translated;
            private final Reachability demand;
            private final BiConsumer<Reachability, Map<String, Reachability>> collector;

            AnalysisBoolean(Reachability translated,
                            Reachability demand,
                            BiConsumer<Reachability, Map<String, Reachability>> collector) {
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
                Reachability translated = left.translated == null || this.translated == null
                        ? null
                        : left.translated.and(this.translated);
                Reachability demand = left.demand == null || this.demand == null
                        ? null
                        : left.demand.and(this.demand);
                return new AnalysisBoolean(translated, demand, (d, o) -> {
                    left.collect(d, o);
                    Reachability right = left.demand == null ? d : d.and(left.demand);
                    collect(right, o);
                });
            }

            AnalysisBoolean or(AnalysisBoolean left) {
                Reachability translated = left.translated == null || this.translated == null
                        ? null
                        : left.translated.or(this.translated);
                Reachability demand = left.demand == null || this.demand == null
                        ? null
                        : left.demand.or(this.demand);
                return new AnalysisBoolean(translated, demand, (d, o) -> {
                    left.collect(d, o);
                    Reachability right = left.demand == null
                            ? d
                            : d.and(trueReach().subtract(left.demand));
                    collect(right, o);
                });
            }

            @Override
            public void collect(Reachability demand, Map<String, Reachability> output) {
                collector.accept(demand, output);
            }
        }
    }

    private static final class ExpressionAnalysis {
        private final Reachability reach;
        private final Map<String, Reachability> variableDemands;
        private final Map<String, String> variables;
        private final List<Expression.Operator> incompatibleOps;
        private final String evaluationError;
        private final SupportedTerms supportedTerms;

        ExpressionAnalysis(Reachability reach,
                           Map<String, Reachability> variableDemands,
                           Map<String, String> variables,
                           List<Expression.Operator> incompatibleOps,
                           String evaluationError,
                           SupportedTerms supportedTerms) {
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

    private String scalarLiteral(Value<?> value) {
        switch (value.type()) {
            case STRING:
            case DYNAMIC:
                return value.getString();
            case BOOLEAN:
                return String.valueOf(value.getBoolean());
            default:
                return null;
        }
    }

    private Reachability optionReachability(Node node) {
        Node input = node.ancestor(Kind::isInput).orElseThrow();
        String key = scopeId(input);
        switch (input.kind()) {
            case INPUT_ENUM:
                return Reachability.scalar(key, Set.of(node.value().getString()), domains);
            case INPUT_LIST:
                return Reachability.listContains(key, Set.of(node.value().getString()), domains);
            default:
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

    private Expression pruningContribution(Node node) {
        switch (node.kind()) {
            case INPUT_BOOLEAN:
            case INPUT_OPTION:
                return expressionContribution(node, this::scopeId);
            default:
                return Expression.TRUE;
        }
    }

    private final class InlineInvoker extends ScriptInvoker {
        private final Deque<Node> callStack = new ArrayDeque<>();
        private final Map<String, Node> methods = new HashMap<>();

        InlineInvoker() {
            super(ctx);
        }

        @Override
        protected Node load(Script.Loader loader, Script.Source source) {
            // disable caching to get unique nodes for each invocation
            return source.readScript(false, Script.Loader.EMPTY);
        }

        @Override
        public boolean visit(Node node) {
            switch (node.kind()) {
                case CALL:
                    Script script = node.script();
                    String method = node.attribute("method").getString();
                    Node prototype = methods.computeIfAbsent(script.path() + ":" + method, k -> script.methods().get(method));
                    if (prototype == null) {
                        throw new IllegalStateException("Method not found: " + method);
                    }
                    String hash = md5(node.location());
                    script.methods().put(hash, prototype.deepCopy().attribute("name", hash));
                    callStack.push(node.attribute("method", hash));
                    break;
                case SOURCE:
                case EXEC:
                    if (node.attribute("url").isPresent()) {
                        // skip url invocation
                        return false;
                    }
                    callStack.push(node);
                    break;
                case FILE:
                case TEMPLATE:
                case FILES:
                case TEMPLATES:
                case OUTPUT:
                    workDirs.put(node, ctx.cwd());
                    break;
                case CONDITION:
                case VARIABLE_TEXT:
                case VARIABLE_ENUM:
                case VARIABLE_BOOLEAN:
                case VARIABLE_LIST:
                case PRESET_TEXT:
                case PRESET_ENUM:
                case PRESET_BOOLEAN:
                case PRESET_LIST:
                    return true;
                default:
            }
            return super.visit(node);
        }

        @Override
        public void postVisit(Node node) {
            switch (node.kind()) {
                case CALL:
                    String method = node.attribute("method").getString();
                    node.script().methods().remove(method);
                    break;
                case SOURCE:
                case EXEC:
                    if (node.attribute("url").isPresent()) {
                        // skip url invocation
                        return;
                    }
                    break;
                case METHOD:
                case SCRIPT:
                    if (!callStack.isEmpty()) {
                        callStack.pop().replace(node.children());
                    }
                    break;
                default:
            }
            super.postVisit(node);
        }
    }

    private final class RefsInvoker extends ScriptInvoker {

        private final Map<String, Reachability> currentReachByRef = new HashMap<>();
        private final Map<String, ConstantBindings> currentBindings = new HashMap<>();
        private final Deque<ReachabilityState> reachStack = new ArrayDeque<>(
                List.of(ReachabilityState.of(trueReach())));
        private final Set<Node> modifiedSteps = new HashSet<>();
        private int nextId = 0;

        RefsInvoker() {
            super(ctx);
        }

        void snapshotNodeContext(Node node) {
            refReachByNode.put(node, Map.copyOf(currentReachByRef));
            refBindingsByNode.put(node, snapshotBindings(currentBindings));
        }

        void cacheConditionSemantics(Node node,
                                             ReachabilityState nodeState,
                                             Expression localPruning,
                                             Expression localUnsupported) {
            if (node.parent() == null) {
                conditionSemanticsByNode.put(node, ConditionSemantics.root(nodeState.known));
                return;
            }
            ConditionSemantics parentSemantics = conditionSemantics(node.parent());
            conditionSemanticsByNode.put(node,
                    parentSemantics.and(nodeState.known,
                            expressionTerms(localPruning),
                            expressionTerms(localUnsupported)));
        }

        @Override
        public boolean visit(Node node) {
            node.id(nextId++);
            Scope scope = ctx.scope();
            ReachabilityState currentState = reachStack.peek();
            ReachabilityState nodeState = currentState;
            Expression pruning = Expression.TRUE;
            Expression unsupported = Expression.TRUE;
            switch (node.kind()) {
                case SOURCE:
                case EXEC:
                    scopes.put(node, scope.key());
                    cacheConditionSemantics(node, nodeState, pruning, unsupported);
                    if (node.attribute("url").isPresent()) {
                        // skip url invocation
                        reachStack.push(nodeState);
                        return false;
                    }
                    break;
                case CONDITION:
                    scopes.put(node, scope.key());
                    snapshotNodeContext(node);
                    Expression expr = node.expression();
                    pruning = normalize(expr, scope);
                    ExpressionAnalyzer analyzer = new ExpressionAnalyzer(scope, currentBindings, currentReachByRef, false, true);
                    ExpressionAnalysis analysis = analyzer.analyze(expr);
                    SupportedTerms conditionTerms = analysis.supportedTerms;
                    if (dependsOnTextInput(analysis.refs(), new HashSet<>())) {
                        errors.add(String.format(
                                "%s %s: '%s'",
                                node.location(),
                                EXPR_TEXT_INPUT_CONTROL_FLOW,
                                expr.literal()));
                    }
                    Reachability conditionReachability = analysis.reach;
                    if (conditionReachability == null) {
                        if (conditionTerms.hasSupportedTerms) {
                            // Keep the exact translatable slice that survives on the
                            // residual-ized path so post-visit pruning can re-evaluate
                            // it under later bindings without reparsing the full path.
                            pruning = residualExpression(conditionTerms.supported,
                                    conditionReach(node, currentState),
                                    scope);
                            expr = pruning.and(conditionTerms.unsupported).reduce();
                            analyzer = new ExpressionAnalyzer(scope, currentBindings, currentReachByRef, false, false);
                            analysis = analyzer.analyze(expr);
                            conditionReachability = analysis.reach;
                        } else {
                            pruning = Expression.TRUE;
                        }
                        if (conditionReachability == null) {
                            unsupported = conditionTerms.unsupported;
                        } else {
                            pruning = normalize(expr, scope);
                        }
                    }
                    if (expr != Expression.FALSE) {
                        node.expression(expr);
                        if (conditionReachability != null) {
                            nodeState = currentState.and(conditionReachability);
                        } else {
                            Reachability knownConditionReachability = conditionTerms.hasSupportedTerms
                                            ? conditionTerms.supported
                                            : null;
                            nodeState = currentState.andKnown(knownConditionReachability);
                        }
                        cacheConditionSemantics(node, nodeState, pruning, unsupported);
                        if (nodeState.isFalse()) {
                            node.expression(Expression.FALSE);
                            reachByNode.put(node, nodeState.reach);
                            reachStack.push(nodeState);
                            return false;
                        }
                        if (nodeState.supported()) {
                            reachByNode.put(node, nodeState.reach);
                        }
                        reachStack.push(nodeState);
                        return true;
                    }
                    // condition is always false
                    // skip traversal and prune (postVisit)
                    node.expression(Expression.FALSE);
                    ReachabilityState falseState = ReachabilityState.of(falseReach());
                    cacheConditionSemantics(node, falseState, Expression.FALSE, Expression.TRUE);
                    reachByNode.put(node, falseState.reach);
                    reachStack.push(falseState);
                    return false;
                case INPUT_TEXT:
                case INPUT_BOOLEAN:
                case INPUT_LIST:
                case INPUT_ENUM:
                    scope = ctx.pushScope(s -> s.getOrCreate(node));
                    scopes.put(node, scope.key());
                    pruning = pruningContribution(node);
                    snapshotNodeContext(node);
                    definedRefs.computeIfAbsent(scope.key(), k -> new LinkedHashSet<>()).add(node);
                    refTypes.putIfAbsent(scope.key(), node.kind().valueType());
                    if (node.kind() == Kind.INPUT_TEXT) {
                        textInputRefs.add(scope.key());
                    }
                    registerDomain(node, scope.key());
                    if (currentState.supported()) {
                        Reachability parentReachability = currentState.reach;
                        currentReachByRef.compute(scope.key(),
                                (k, v) -> v == null ? parentReachability : v.or(parentReachability));
                        if (currentState.isFalse()) {
                            return skipPrunedNode(node, currentState);
                        }
                    }
                    if (node.kind() == Kind.INPUT_BOOLEAN) {
                        nodeState = currentState.and(Reachability.scalar(scope.key(), Set.of("true"), domains));
                    }
                    if (shouldPrune(nodeState)) {
                        return skipPrunedNode(node, nodeState);
                    }
                    break;
                case INPUT_OPTION:
                    scopes.put(node, scope.key());
                    pruning = pruningContribution(node);
                    snapshotNodeContext(node);
                    nodeState = currentState.and(optionReachability(node));
                    if (shouldPrune(nodeState)) {
                        return skipPrunedNode(node, nodeState);
                    }
                    break;
                case VARIABLE_TEXT:
                case VARIABLE_ENUM:
                case VARIABLE_BOOLEAN:
                case VARIABLE_LIST:
                case PRESET_TEXT:
                case PRESET_ENUM:
                case PRESET_BOOLEAN:
                case PRESET_LIST:
                    scope = scope.getOrCreate("~" + Context.Key.normalize(node.attribute("path").getString()));
                    scopes.put(node, scope.key());
                    definedRefs.computeIfAbsent(scope.key(), k -> new LinkedHashSet<>()).add(node);
                    declaredValues.computeIfAbsent(scope.key(), k -> new LinkedHashSet<>()).add(node);
                    refTypes.putIfAbsent(scope.key(), node.kind().valueType());
                    registerImplicitDomain(scope.key(), node.kind().valueType());
                    if (currentState.supported()) {
                        Reachability parentReachability = currentState.reach;
                        currentReachByRef.compute(scope.key(),
                                (k, v) -> v == null ? parentReachability : v.or(parentReachability));
                        Value<?> value = constantValue(node);
                        if (value != null) {
                            currentBindings.computeIfAbsent(scope.key(),
                                    k -> new ConstantBindings(node.kind().valueType()))
                                    .add(value, parentReachability);
                        }
                    }
                    if (nodeState.supported()) {
                        reachByNode.put(node, nodeState.reach);
                    }
                    cacheConditionSemantics(node, nodeState, pruning, unsupported);
                    reachStack.push(nodeState);
                    return true;
                default:
            }
            if (nodeState.supported()) {
                reachByNode.put(node, nodeState.reach);
            }
            cacheConditionSemantics(node, nodeState, pruning, unsupported);
            reachStack.push(nodeState);
            return super.visit(node);
        }

        @Override
        public void postVisit(Node node) {
            switch (node.kind()) {
                case SOURCE:
                case EXEC:
                    if (node.attribute("url").isPresent()) {
                        // skip url invocation
                        reachStack.pop();
                        return;
                    }
                    break;
                case CONDITION:
                    if (node.expression() == Expression.FALSE) {
                        remove(node);
                    }
                    break;
                case INPUT_BOOLEAN:
                case INPUT_TEXT:
                    if (prunedByReachability(node)) {
                        remove(node);
                        node.ancestor(Kind.STEP::equals).ifPresent(modifiedSteps::add);
                    }
                    ctx.popScope();
                    break;
                case INPUT_LIST:
                case INPUT_ENUM:
                    if (prunedByReachability(node) || node.children().isEmpty()) {
                        remove(node);
                        node.ancestor(Kind.STEP::equals).ifPresent(modifiedSteps::add);
                    }
                    ctx.popScope();
                    break;
                case INPUT_OPTION:
                    if (prunedByReachability(node)) {
                        remove(node);
                    }
                    break;
                case STEP:
                    if (modifiedSteps.remove(node)) {
                        // only remove steps that have been modified
                        // to detect steps without inputs during validation
                        List<Node> inputs = node.collect(Kind::isInput);
                        if (inputs.isEmpty()) {
                            node.replace(node.children());
                        }
                    }
                    break;
                default:
            }
            reachStack.pop();
            super.postVisit(node);
        }

        boolean shouldPrune(ReachabilityState nodeState) {
            return nodeState.supported() && nodeState.isFalse();
        }

        boolean skipPrunedNode(Node node, ReachabilityState nodeState) {
            Reachability reach = nodeState.supported() ? nodeState.reach : falseReach();
            ReachabilityState prunedState = nodeState.supported() ? nodeState : ReachabilityState.of(reach);
            reachByNode.put(node, reach);
            cacheConditionSemantics(node, prunedState, pruningContribution(node), Expression.TRUE);
            reachStack.push(prunedState);
            return false;
        }

        void remove(Node node) {
            Log.debug("Removing %s, path: %s, expression: %s",
                    node,
                    Maps.mapValue(path(node), v -> Value.toString(v)),
                    activationCondition(node.parent()));
            node.remove();
        }
    }

    private class InputVisitor implements Node.Visitor {
        private final Deque<Node> stack = new ArrayDeque<>();
        private final Map<Node, Node> mirrors;
        private final Map<Node, ConditionSemantics> renderedConditionSemantics;
        private final Map<Node, Reachability> reachableBlocks;
        private final Map<Node, Set<String>> conditionRefs;
        private final Image image;

        InputVisitor(Image image,
                     Map<Node, Node> mirrors,
                     Map<Node, ConditionSemantics> renderedConditionSemantics,
                     Map<Node, Reachability> reachableBlocks,
                     Map<Node, Set<String>> conditionRefs) {
            this.image = image;
            this.mirrors = mirrors;
            this.renderedConditionSemantics = renderedConditionSemantics;
            this.reachableBlocks = reachableBlocks;
            this.conditionRefs = conditionRefs;
            this.stack.push(image.node);
        }

        @Override
        public boolean visit(Node node) {
            switch (node.kind()) {
                case PRESET_BOOLEAN:
                case PRESET_ENUM:
                case PRESET_LIST:
                case PRESET_TEXT:
                    // use ~ to be parented at the root context node
                    // to maintain scope.key == scope.internalKey
                    Node preset = process(node, (b, n) -> Nodes.ensureLast(b, Kind.PRESETS).append(n));
                    preset.attribute("path", "~" + scopeId(node));
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
                        variable.attribute("path", "~" + scopeId(node));
                        stack.push(variable);
                    }
                    break;
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                case INPUT_TEXT:
                case INPUT_LIST:
                    // flatten inputs to maintain scope.key == scope.internalKey (!)
                    // I.e. inputs are only nested to produce a dotted path

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
                            refs.computeIfAbsent(scopeId(mirror(input)), k -> new LinkedHashSet<>()).add(step);
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
            Node blockCopy = stack.getFirst();
            Node block = mirrors.get(blockCopy);

            Scope scope = scope(block);
            ConditionSemantics blockSemantics = renderedConditionSemantics(blockCopy);
            ConditionSemantics nodeSemantics = conditionSemantics(node.parent());

            // "relativize" the expression within the block
            Reachability blockReach = reachability(block);
            Reachability nodeReach = reachability(node.parent());
            Expression expr;
            if (blockReach != null && nodeReach != null) {
                expr = residualExpression(nodeReach, blockReach, scope);
            } else {
                expr = nodeSemantics.relativeCondition(blockSemantics, scope, blockReach);
            }

            // create copy
            Node copy = node.copy();
            Node wrappedCopy = copy.wrap(expr);
            appender.accept(blockCopy, wrappedCopy);
            rememberConditionRefs(wrappedCopy, expr, conditionRefs);
            renderedConditionSemantics.put(copy, nodeSemantics);
            if (node.kind().isBlock()) {
                reachableBlocks.put(copy, reachability(node));
            }

            mirrors.put(node, copy);
            mirrors.put(copy, node);
            return copy;
        }

        ConditionSemantics renderedConditionSemantics(Node node) {
            ConditionSemantics semantics = renderedConditionSemantics.get(node);
            if (semantics != null) {
                return semantics;
            }
            throw new IllegalStateException("Rendered condition semantics not found: " + node);
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
        private final Map<String, Map<List<FileOp>, Reachability>> fileOps = new HashMap<>();
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
            switch (node.kind()) {
                case TRANSFORMATION:
                    List<FileOp> ops = new ArrayList<>();
                    for (Node n : node.traverse(Kind.REPLACE::equals)) {
                        ops.add(new FileOp(
                                n.attribute("regex").getString(),
                                n.attribute("replacement").getString()));
                    }
                    Reachability nodeReach = outputReachability(node);
                    fileOps.computeIfAbsent(node.attribute("id").getString(), k -> new HashMap<>())
                            .compute(ops, (k, v) -> {
                                Reachability reach = v != null ? v : falseReach();
                                return reach.or(nodeReach);
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
            Path path = workDirs.get(node).resolve(source);
            String checksum = checksum(path);
            image.blobs.putIfAbsent(checksum, readAllBytes(path));
            List<FileOp> fileOps = List.of(new FileOp(Pattern.quote(checksum), target));
            return new FileObject(checksum, fileOps, outputReachability(node), scope(node));
        }

        Set<FileObject> resolveFiles(Node node) {
            Set<FileObject> fileObjects = new TreeSet<>();

            // resolve variations of transformations
            List<List<FileOps>> allOps = Lists.filter(Combinatorics.cartesianProduct(fileOps(node)), l -> !l.isEmpty());
            Set<FileOps> resolvedOps = new TreeSet<>(Lists.map(allOps, FileOps::combine));

            Map<String, Reachability> includes = new HashMap<>();
            Map<String, Reachability> excludes = new HashMap<>();

            Scope scope = scope(node);
            Reachability nodeReach = outputReachability(node);
            for (Node n : node.traverse()) {
                switch (n.kind()) {
                    case INCLUDE:
                    case EXCLUDE:
                        Map<String, Reachability> map = n.kind() == Kind.INCLUDE ? includes : excludes;
                        Reachability filterReach = outputReachability(n);
                        map.compute(n.value().getString(), (k, v) -> v == null ? filterReach : v.or(filterReach));
                        break;
                    default:
                }
            }

            Path directory = workDirs.get(node).resolve(node.attribute("directory").getString());
            for (SourcePath file : SourcePath.scan(directory)) {

                Reachability includeReach = includes.isEmpty() ? trueReach() : falseReach();
                for (Reachability v : Maps.filterKey(includes, file::matches).values()) {
                    includeReach = includeReach.or(v);
                }
                Reachability excludeReach = falseReach();
                for (Reachability v : Maps.filterKey(excludes, file::matches).values()) {
                    excludeReach = excludeReach.or(v);
                }

                Reachability blobReach = nodeReach.and(includeReach).subtract(excludeReach);
                if (!blobReach.isFalse()) {
                    String source = file.asString(false);
                    Path path = directory.resolve(source);

                    // record blob
                    String checksum = checksum(path);
                    image.blobs.putIfAbsent(checksum, readAllBytes(path));

                    if (resolvedOps.isEmpty()) {
                        List<FileOp> fileOps = List.of(new FileOp(Pattern.quote(checksum), source));
                        fileObjects.add(new FileObject(checksum, fileOps, blobReach, scope));
                    } else {
                        // create a unique file object for each transformation variation
                        for (FileOps e : resolvedOps) {
                            List<FileOp> fileOps = e.resolve(checksum, source);
                            Reachability fileReach = blobReach.and(e.reach);
                            fileObjects.add(new FileObject(checksum, fileOps, fileReach, scope));
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
                Map<List<FileOp>, Reachability> idOps = fileOps.getOrDefault(id, Map.of());
                ops.add(Lists.map(idOps.entrySet(), e -> new FileOps(e.getKey(), e.getValue())));
            }
            return ops;
        }

        Reachability outputReachability(Node node) {
            Reachability reach = reachability(node);
            if (reach == null) {
                throw new IllegalStateException("Missing reachability for output node: " + node);
            }
            return reach;
        }

        List<Node> renderModels() {
            List<Node> nodes = new ArrayList<>();
            for (Node node : sourceNode.traverse(Kind::isModel)) {
                Node parent = node.parent();
                if (parent != null && parent.kind() == Kind.CONDITION) {
                    parent = parent.parent();
                }
                if (parent != null && parent.kind() == Kind.MODEL) {
                    Path basedir = workDirs.get(node.ancestor(Kind.OUTPUT::equals).orElseThrow());
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
        private final Map<String, Reachability> currentReachByRef = new HashMap<>();
        private final Map<Node, Map<String, Reachability>> reachRefs = new LinkedHashMap<>();
        private final Map<Node, Node> mirrors;
        private final Map<Node, Reachability> reachableBlocks;
        private final Map<Node, Set<String>> conditionRefs;

        StubsVisitor(Map<Node, Node> mirrors, Map<Node, Reachability> reachableBlocks, Map<Node, Set<String>> conditionRefs) {
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
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                case INPUT_TEXT:
                case INPUT_LIST:
                    Node mirror = mirror(node);
                    Reachability reach = definitionReachability(mirror);
                    if (reach != null) {
                        currentReachByRef.compute(scopeId(mirror), (k, v) -> v == null ? reach : v.or(reach));
                    }
                    break;
                case CONDITION:
                    reachRefs.put(node, Map.copyOf(currentReachByRef));
                    break;
                default:
            }
            return true;
        }

        @Override
        public void postVisit(Node node) {
            if (node.kind() == Kind.SCRIPT) {
                reachRefs.forEach((n, snapshot) -> {
                    List<Node> stubs = resolveStubs(n, snapshot);
                    if (!stubs.isEmpty()) {
                        addVariables(n, stubs);
                    }
                });
            }
        }

        void addVariables(Node node, List<Node> nodes) {
            Node block = node.ancestor(Kind::isBlock).orElseThrow();

            // collect variables in the block
            Map<String, List<Token>> existing = new LinkedHashMap<>();
            for (Node n0 : block.children(Kind.VARIABLES::equals)) {
                for (Node n1 : n0.children()) {
                    existing.put(n1.unwrap().attribute("path").getString(), nodeConditionTokens(n1));
                }
            }

            // filter stubs that already exist
            List<Node> stubs = new ArrayList<>();
            for (Node n : nodes) {
                String path = n.unwrap().attribute("path").getString();
                List<Token> conditionTokens = existing.get(path);
                if (!nodeConditionTokens(n).equals(conditionTokens)) {
                    stubs.add(n);
                }
            }

            if (!stubs.isEmpty()) {
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
                            for (int j = index, size = parent.children().size(); j < size; j++) {
                                inputs.append(parent.children().remove(index));
                            }
                        }
                    } else if (parent.kind().isBlock()) {
                        container = Nodes.ensureBefore(n, Kind.VARIABLES);
                    }
                }
                if (container != null) {
                    for (Node stub : stubs) {
                        container.append(stub);
                    }
                } else {
                    throw new IllegalStateException("Unable to resolve stubs container");
                }
            }
        }

        List<Token> nodeConditionTokens(Node node) {
            return node.kind() == Kind.CONDITION
                    ? node.expression().tokens()
                    : Expression.TRUE.tokens();
        }

        List<Node> resolveStubs(Node node, Map<String, Reachability> snapshots) {
            Set<String> variables = conditionRefs.get(node);
            if (variables == null) {
                throw new IllegalStateException("Condition refs not found: " + node);
            }
            if (variables.isEmpty()) {
                return List.of();
            }

            List<Node> nodes = new ArrayList<>();
            Node block = node.ancestor(Kind::isBlock).orElseThrow();
            Scope scope = scope(mirror(block));
            Reachability reach = reachability(block);
            if (reach == null) {
                // validation already reports unsupported condition shapes; do not reintroduce expression fallback here
                return List.of();
            }
            for (String ref : variables) {

                // normalize the ref
                String key = scope.key(ref);

                Reachability refReach = snapshots.getOrDefault(key, falseReach());
                Reachability missing = reach.subtract(refReach);
                if (missing.isFalse()) {
                    continue;
                }
                Type type = refTypes.getOrDefault(key, Type.EMPTY);
                nodes.add(Nodes.variable(type, "~" + key).wrap(residualExpression(missing, reach, scope)));
            }
            return nodes;
        }

        Reachability reachability(Node node) {
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
            Set<Node> anchors = mergedActivationAnchors(group);
            if (anchors.size() == group.size()) {
                Expression condition = currentCondition(group.get(0));
                for (int i = 1; i < group.size(); i++) {
                    condition = condition.or(activationCondition(mirror(group.get(i)).parent()));
                }
                return condition;
            }

            Node firstAnchor = anchors.iterator().next();
            if (anchors.size() <= 4) {
                Reachability mergedReach = reachability(firstAnchor);
                if (mergedReach != null) {
                    for (Node anchor : anchors) {
                        if (anchor == firstAnchor) {
                            continue;
                        }
                        Reachability reach = reachability(anchor);
                        if (reach == null) {
                            mergedReach = null;
                            break;
                        }
                        mergedReach = mergedReach.or(reach);
                    }
                    if (mergedReach != null) {
                        return reachabilityExpression(mergedReach, scope(firstAnchor));
                    }
                }
            }

            Expression condition = activationCondition(firstAnchor);
            for (Node anchor : anchors) {
                if (anchor != firstAnchor) {
                    condition = condition.or(activationCondition(anchor));
                }
            }
            return condition;
        }

        Expression currentCondition(Node step) {
            Node parent = step.parent();
            return parent.kind() == Kind.CONDITION ? parent.expression() : Expression.TRUE;
        }

        Set<Node> mergedActivationAnchors(List<Node> group) {
            LinkedHashSet<Node> anchors = new LinkedHashSet<>();
            for (Node step : group) {
                Node anchor = mirror(step).parent();
                if (anchor == null) {
                    throw new IllegalStateException("Merged step is missing an activation anchor: " + step);
                }
                anchors.add(anchor);
            }
            collapseEnumCoverage(anchors);
            return anchors;
        }

        void collapseEnumCoverage(Set<Node> anchors) {
            boolean changed;
            do {
                changed = false;

                Map<Node, Set<Node>> optionsByInput = new LinkedHashMap<>();
                for (Node anchor : anchors) {
                    if (anchor.kind() == Kind.INPUT_OPTION) {
                        Node input = anchor.ancestor(Kind::isInput).orElse(null);
                        if (input != null && input.kind() == Kind.INPUT_ENUM) {
                            optionsByInput.computeIfAbsent(input, k -> new LinkedHashSet<>()).add(anchor);
                        }
                    }
                }

                for (Entry<Node, Set<Node>> entry : optionsByInput.entrySet()) {
                    Node input = entry.getKey();
                    Set<Node> options = entry.getValue();
                    if (anchors.contains(input)) {
                        changed = anchors.removeAll(options) || changed;
                        continue;
                    }
                    boolean coversAllOptions = true;
                    for (Node option : input.children(Kind.INPUT_OPTION::equals)) {
                        if (!options.contains(option)) {
                            coversAllOptions = false;
                            break;
                        }
                    }
                    if (coversAllOptions) {
                        changed = anchors.removeAll(options) || changed;
                        changed = anchors.add(input) || changed;
                    }
                }
            } while (changed);
        }

        Node mirror(Node node) {
            Node mirror = mirrors.get(node);
            if (mirror != null) {
                return mirror;
            }
            throw new IllegalStateException("Mirror not found: " + node);
        }
    }

    private static final class ReachabilityState {
        private final Reachability reach;
        private final Reachability known;

        ReachabilityState(Reachability reach, Reachability known) {
            this.reach = reach;
            this.known = Objects.requireNonNull(known);
        }

        static ReachabilityState of(Reachability reach) {
            Reachability known = Objects.requireNonNull(reach);
            return new ReachabilityState(reach, known);
        }

        static ReachabilityState unsupported(Reachability known) {
            return new ReachabilityState(null, Objects.requireNonNull(known));
        }

        boolean supported() {
            return reach != null;
        }

        boolean isFalse() {
            return supported() && reach.isFalse();
        }

        ReachabilityState and(Reachability other) {
            Reachability next = other == null ? known : known.and(other);
            if (!supported() || other == null) {
                return unsupported(next);
            }
            return new ReachabilityState(reach.and(other), next);
        }

        ReachabilityState andKnown(Reachability other) {
            Reachability next = other == null ? known : known.and(other);
            return unsupported(next);
        }
    }

    private static final class ConditionSemantics {
        private final Reachability known;
        private final List<ConditionTerm> pruning;
        private final List<ConditionTerm> unsupported;

        ConditionSemantics(Reachability known, List<ConditionTerm> pruning, List<ConditionTerm> unsupported) {
            this.known = Objects.requireNonNull(known);
            this.pruning = List.copyOf(pruning);
            this.unsupported = List.copyOf(unsupported);
        }

        static ConditionSemantics root(Reachability known) {
            return new ConditionSemantics(known, List.of(), List.of());
        }

        ConditionSemantics and(Reachability next, List<ConditionTerm> pruning, List<ConditionTerm> unsupported) {
            return new ConditionSemantics(next,
                    mergeTerms(this.pruning, pruning),
                    mergeTerms(this.unsupported, unsupported));
        }

        Expression emittedCondition(Scope scope) {
            return reachabilityExpression(known, scope)
                    .and(expression(unsupported))
                    .reduce();
        }

        Expression relativeCondition(ConditionSemantics base, Scope scope, Reachability reach) {
            Reachability relative = reach != null ? reach : base.known;
            return residualExpression(known, relative, scope)
                    .and(expression(relativeTerms(unsupported, base.unsupported)))
                    .reduce();
        }

        static List<ConditionTerm> mergeTerms(List<ConditionTerm> current, List<ConditionTerm> local) {
            if (current.size() == 1 && current.get(0).isFalse() || local.isEmpty()) {
                return current;
            }
            if (local.size() == 1 && local.get(0).isFalse() || current.isEmpty()) {
                return local;
            }
            LinkedHashSet<ConditionTerm> merged = new LinkedHashSet<>(current);
            merged.addAll(local);
            return merged.size() == current.size() ? current : List.copyOf(merged);
        }

        static List<ConditionTerm> relativeTerms(List<ConditionTerm> terms, List<ConditionTerm> baseTerms) {
            if (terms.isEmpty() || baseTerms.isEmpty()) {
                return terms;
            }
            Set<ConditionTerm> base = Set.copyOf(baseTerms);
            List<ConditionTerm> relative = new ArrayList<>();
            for (ConditionTerm term : terms) {
                if (!base.contains(term)) {
                    relative.add(term);
                }
            }
            return relative;
        }

        static Expression expression(List<ConditionTerm> terms) {
            Expression expr = Expression.TRUE;
            for (ConditionTerm term : terms) {
                expr = expr.and(term.expression());
            }
            return expr.reduce();
        }
    }

    private static final class ConditionTerm {
        private static final ConditionTerm TRUE = new ConditionTerm(Expression.TRUE.tokens());
        private static final ConditionTerm FALSE = new ConditionTerm(Expression.FALSE.tokens());

        private final List<Token> tokens;

        ConditionTerm(List<Token> tokens) {
            this.tokens = List.copyOf(tokens);
        }

        static ConditionTerm of(Expression expression) {
            Expression reduced = expression.reduce();
            if (reduced == Expression.TRUE) {
                return TRUE;
            }
            if (reduced == Expression.FALSE) {
                return FALSE;
            }
            return new ConditionTerm(reduced.tokens());
        }

        boolean isTrue() {
            return tokens.equals(Expression.TRUE.tokens());
        }

        boolean isFalse() {
            return tokens.equals(Expression.FALSE.tokens());
        }

        Expression expression() {
            if (isTrue()) {
                return Expression.TRUE;
            }
            if (isFalse()) {
                return Expression.FALSE;
            }
            return new Expression(tokens, true);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ConditionTerm)) {
                return false;
            }
            ConditionTerm other = (ConditionTerm) o;
            return tokens.equals(other.tokens);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tokens);
        }
    }

    private static final class InputDomain {
        enum Kind {
            SCALAR,
            LIST
        }

        private final Kind kind;
        private final Set<String> scalarValues;
        private final Set<String> listItems;

        InputDomain(Kind kind, Set<String> scalarValues, Set<String> listItems) {
            this.kind = kind;
            this.scalarValues = Set.copyOf(new TreeSet<>(scalarValues));
            this.listItems = Set.copyOf(new TreeSet<>(listItems));
        }

        static InputDomain booleanDomain() {
            return scalar(Set.of("false", "true"));
        }

        static InputDomain scalar(Set<String> values) {
            return new InputDomain(Kind.SCALAR, values, Set.of());
        }

        static InputDomain list(Set<String> values) {
            return new InputDomain(Kind.LIST, Set.of(), values);
        }

        boolean isBoolean() {
            return kind == Kind.SCALAR && scalarValues.equals(Set.of("false", "true"));
        }
    }

    private final class ConstantBindings {
        private final Type type;
        private final List<ConstantCase> cases = new ArrayList<>();
        private Reachability defined = falseReach();

        ConstantBindings(Type type) {
            this.type = type;
        }

        Reachability defined() {
            return defined;
        }

        ConstantBindings copy() {
            ConstantBindings copy = new ConstantBindings(type);
            copy.defined = defined;
            for (ConstantCase constantCase : cases) {
                copy.cases.add(new ConstantCase(constantCase.value, constantCase.reach));
            }
            return copy;
        }

        void add(Value<?> value, Reachability reach) {
            if (reach == null || reach.isFalse()) {
                return;
            }
            ListIterator<ConstantCase> it = cases.listIterator();
            while (it.hasNext()) {
                ConstantCase next = it.next();
                Reachability remaining = next.reach.subtract(reach);
                if (remaining.isFalse()) {
                    it.remove();
                } else {
                    next.reach = remaining;
                }
            }
            for (ConstantCase constantCase : cases) {
                if (Value.isEqual(constantCase.value, value)) {
                    constantCase.reach = constantCase.reach.or(reach);
                    defined = defined.or(reach);
                    return;
                }
            }
            cases.add(new ConstantCase(value, reach));
            defined = defined.or(reach);
        }

        Reachability match(Value<?> value) {
            Reachability reach = falseReach();
            for (ConstantCase constantCase : cases) {
                if (Value.isEqual(constantCase.value, value)) {
                    reach = reach.or(constantCase.reach);
                }
            }
            return reach;
        }

        Reachability scalarAny(Set<String> values) {
            Reachability reach = falseReach();
            for (ConstantCase constantCase : cases) {
                String value = scalarLiteral(constantCase.value);
                if (value != null && values.contains(value)) {
                    reach = reach.or(constantCase.reach);
                }
            }
            return reach;
        }

        Reachability listContains(Set<String> required) {
            Reachability reach = falseReach();
            if (type != Type.LIST) {
                return reach;
            }
            for (ConstantCase constantCase : cases) {
                if (constantCase.value.type() == Type.LIST) {
                    Set<String> values = new HashSet<>(constantCase.value.getList());
                    if (values.containsAll(required)) {
                        reach = reach.or(constantCase.reach);
                    }
                }
            }
            return reach;
        }
    }

    private static final class ConstantCase {
        private final Value<?> value;
        private Reachability reach;

        ConstantCase(Value<?> value, Reachability reach) {
            this.value = value;
            this.reach = reach;
        }
    }

    private static final class SupportedTerms {
        private final Reachability supported;
        private final Expression unsupported;
        private final boolean hasSupportedTerms;

        SupportedTerms(Reachability supported, Expression unsupported, boolean hasSupportedTerms) {
            this.supported = supported;
            this.unsupported = unsupported;
            this.hasSupportedTerms = hasSupportedTerms;
        }

        static SupportedTerms fullySupported(Reachability reach) {
            return new SupportedTerms(reach, Expression.TRUE, true);
        }
    }

    private static final class Reachability {
        private final Map<String, InputDomain> domains;
        private final List<ConstraintSet> constraintSets;

        Reachability(Map<String, InputDomain> domains, List<ConstraintSet> constraintSets) {
            this.domains = domains;
            this.constraintSets = List.copyOf(constraintSets);
        }

        static Reachability trueValue(Map<String, InputDomain> domains) {
            return new Reachability(domains, List.of(new ConstraintSet()));
        }

        static Reachability falseValue(Map<String, InputDomain> domains) {
            return new Reachability(domains, List.of());
        }

        static Reachability scalar(String key, Set<String> allowed, Map<String, InputDomain> domains) {
            InputDomain domain = domains.get(key);
            if (domain == null || domain.kind != InputDomain.Kind.SCALAR) {
                return null;
            }
            Set<String> actual = new TreeSet<>(allowed);
            actual.retainAll(domain.scalarValues);
            if (actual.isEmpty()) {
                return falseValue(domains);
            }
            if (actual.equals(domain.scalarValues)) {
                return trueValue(domains);
            }
            return of(List.of(new ConstraintSet(
                    Map.of(key, new ScalarConstraint(actual)),
                    Map.of())), domains);
        }

        static Reachability listContains(String key, Set<String> required, Map<String, InputDomain> domains) {
            InputDomain domain = domains.get(key);
            if (domain == null || domain.kind != InputDomain.Kind.LIST) {
                return null;
            }
            if (!domain.listItems.containsAll(required)) {
                return falseValue(domains);
            }
            if (required.isEmpty()) {
                return trueValue(domains);
            }
            ListConstraint listConstraint = new ListConstraint(required, Set.of());
            ConstraintSet constraintSet = new ConstraintSet(Map.of(), Map.of(key, listConstraint));
            return of(List.of(constraintSet), domains);
        }

        static Reachability of(List<ConstraintSet> constraintSets, Map<String, InputDomain> domains) {
            if (constraintSets.isEmpty()) {
                return falseValue(domains);
            }
            List<ConstraintSet> normalized = new ArrayList<>();
            for (ConstraintSet set : constraintSets) {
                ConstraintSet next = set.normalized(domains);
                if (next.isTrue()) {
                    return trueValue(domains);
                }
                if (!normalized.contains(next)) {
                    normalized.add(next);
                }
            }
            boolean changed = true;
            while (changed) {
                int removeIndex = -1;
                int mergeIndex = -1;
                ConstraintSet merged = null;
                for (int i = 0; i < normalized.size() && removeIndex < 0; i++) {
                    for (int j = i + 1; j < normalized.size(); j++) {
                        ConstraintSet left = normalized.get(i);
                        ConstraintSet right = normalized.get(j);
                        if (left.subsetOf(right)) {
                            removeIndex = i;
                            break;
                        }
                        if (right.subsetOf(left)) {
                            removeIndex = j;
                            break;
                        }
                        ConstraintSet candidate = left.tryMerge(right, domains);
                        if (candidate != null) {
                            merged = candidate;
                            mergeIndex = i;
                            removeIndex = j;
                            break;
                        }
                    }
                }
                changed = removeIndex >= 0;
                if (changed) {
                    if (mergeIndex >= 0) {
                        normalized.set(mergeIndex, merged);
                    }
                    normalized.remove(removeIndex);
                }
            }
            normalized.sort(Comparator.comparing(ConstraintSet::literal));
            return normalized.isEmpty() ? falseValue(domains) : new Reachability(domains, normalized);
        }

        boolean isFalse() {
            return constraintSets.isEmpty();
        }

        Reachability and(Reachability other) {
            if (isFalse() || other.isFalse()) {
                return falseValue(domains);
            }
            if (constraintSets.size() == 1 && constraintSets.get(0).isTrue()) {
                return other;
            }
            if (other.constraintSets.size() == 1 && other.constraintSets.get(0).isTrue()) {
                return this;
            }
            List<ConstraintSet> result = new ArrayList<>();
            for (ConstraintSet left : constraintSets) {
                for (ConstraintSet right : other.constraintSets) {
                    ConstraintSet intersected = left.intersect(right, domains);
                    if (intersected != null) {
                        result.add(intersected);
                    }
                }
            }
            return of(result, domains);
        }

        Reachability or(Reachability other) {
            if (isFalse()) {
                return other;
            }
            if (other.isFalse()) {
                return this;
            }
            List<ConstraintSet> result = new ArrayList<>(constraintSets);
            result.addAll(other.constraintSets);
            return of(result, domains);
        }

        Reachability subtract(Reachability other) {
            if (isFalse() || other.isFalse()) {
                return this;
            }
            if (other.constraintSets.size() == 1 && other.constraintSets.get(0).isTrue()) {
                return falseValue(domains);
            }
            List<ConstraintSet> remaining = new ArrayList<>(constraintSets);
            for (ConstraintSet right : other.constraintSets) {
                List<ConstraintSet> next = new ArrayList<>();
                for (ConstraintSet left : remaining) {
                    next.addAll(left.subtract(right, domains));
                }
                remaining = next;
                if (remaining.isEmpty()) {
                    return falseValue(domains);
                }
            }
            return of(remaining, domains);
        }

        boolean contains(Reachability other) {
            return other.subtract(this).isFalse();
        }

        Expression toExpression(Scope scope) {
            if (isFalse()) {
                return Expression.FALSE;
            }
            Expression expr = Expression.FALSE;
            for (ConstraintSet constraintSet : constraintSets) {
                expr = expr.or(constraintSet.toExpression(scope, domains));
            }
            return expr;
        }

        String literal() {
            StringBuilder builder = new StringBuilder();
            for (ConstraintSet constraintSet : constraintSets) {
                if (builder.length() > 0) {
                    builder.append("||");
                }
                builder.append(constraintSet.literal());
            }
            return builder.toString();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Reachability
                   && constraintSets.equals(((Reachability) o).constraintSets);
        }

        @Override
        public int hashCode() {
            return Objects.hash(constraintSets);
        }
    }

    private static final class ConstraintSet {
        private final Map<String, ScalarConstraint> scalars;
        private final Map<String, ListConstraint> lists;

        ConstraintSet() {
            this(Map.of(), Map.of());
        }

        ConstraintSet(Map<String, ScalarConstraint> scalars, Map<String, ListConstraint> lists) {
            this.scalars = new TreeMap<>(scalars);
            this.lists = new TreeMap<>(lists);
        }

        boolean isTrue() {
            return scalars.isEmpty() && lists.isEmpty();
        }

        ConstraintSet normalized(Map<String, InputDomain> domains) {
            Map<String, ScalarConstraint> normalizedScalars = new TreeMap<>();
            for (Entry<String, ScalarConstraint> entry : scalars.entrySet()) {
                InputDomain domain = domains.get(entry.getKey());
                if (domain == null || domain.kind != InputDomain.Kind.SCALAR
                    || !domain.scalarValues.equals(entry.getValue().allowed)) {
                    normalizedScalars.put(entry.getKey(), entry.getValue());
                }
            }
            Map<String, ListConstraint> normalizedLists = new TreeMap<>();
            for (Entry<String, ListConstraint> entry : lists.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    normalizedLists.put(entry.getKey(), entry.getValue());
                }
            }
            return new ConstraintSet(normalizedScalars, normalizedLists);
        }

        ConstraintSet intersect(ConstraintSet other, Map<String, InputDomain> domains) {
            Map<String, ScalarConstraint> mergedScalars = new TreeMap<>(scalars);
            for (Entry<String, ScalarConstraint> entry : other.scalars.entrySet()) {
                ScalarConstraint current = mergedScalars.get(entry.getKey());
                if (current == null) {
                    mergedScalars.put(entry.getKey(), entry.getValue());
                } else {
                    ScalarConstraint intersected = current.intersect(entry.getValue());
                    if (intersected == null) {
                        return null;
                    }
                    mergedScalars.put(entry.getKey(), intersected);
                }
            }
            Map<String, ListConstraint> mergedLists = new TreeMap<>(lists);
            for (Entry<String, ListConstraint> entry : other.lists.entrySet()) {
                ListConstraint current = mergedLists.get(entry.getKey());
                if (current == null) {
                    mergedLists.put(entry.getKey(), entry.getValue());
                } else {
                    ListConstraint intersected = current.intersect(entry.getValue());
                    if (intersected == null) {
                        return null;
                    }
                    mergedLists.put(entry.getKey(), intersected);
                }
            }
            return new ConstraintSet(mergedScalars, mergedLists).normalized(domains);
        }

        boolean subsetOf(ConstraintSet other) {
            Set<String> scalarKeys = new HashSet<>(scalars.keySet());
            scalarKeys.addAll(other.scalars.keySet());
            for (String key : scalarKeys) {
                ScalarConstraint left = scalars.get(key);
                ScalarConstraint right = other.scalars.get(key);
                if (left == null) {
                    if (right != null) {
                        return false;
                    }
                } else if (right != null && !right.allowed.containsAll(left.allowed)) {
                    return false;
                }
            }
            Set<String> listKeys = new HashSet<>(lists.keySet());
            listKeys.addAll(other.lists.keySet());
            for (String key : listKeys) {
                ListConstraint left = lists.getOrDefault(key, ListConstraint.EMPTY);
                ListConstraint right = other.lists.getOrDefault(key, ListConstraint.EMPTY);
                if (!left.required.containsAll(right.required) || !left.forbidden.containsAll(right.forbidden)) {
                    return false;
                }
            }
            return true;
        }

        ConstraintSet tryMerge(ConstraintSet other, Map<String, InputDomain> domains) {
            if (!lists.equals(other.lists)) {
                return null;
            }
            String diffKey = null;
            Map<String, ScalarConstraint> mergedScalars = new TreeMap<>();
            Set<String> keys = new TreeSet<>(scalars.keySet());
            keys.addAll(other.scalars.keySet());
            for (String key : keys) {
                InputDomain domain = domains.get(key);
                if (domain == null || domain.kind != InputDomain.Kind.SCALAR) {
                    return null;
                }
                Set<String> leftAllowed = allowed(key, domain);
                Set<String> rightAllowed = other.allowed(key, domain);
                if (!leftAllowed.equals(rightAllowed)) {
                    if (diffKey != null) {
                        return null;
                    }
                    diffKey = key;
                    Set<String> union = new TreeSet<>(leftAllowed);
                    union.addAll(rightAllowed);
                    if (!union.equals(domain.scalarValues)) {
                        mergedScalars.put(key, new ScalarConstraint(union));
                    }
                } else if (!leftAllowed.equals(domain.scalarValues)) {
                    mergedScalars.put(key, new ScalarConstraint(leftAllowed));
                }
            }
            return diffKey == null ? null : new ConstraintSet(mergedScalars, lists).normalized(domains);
        }

        List<ConstraintSet> subtract(ConstraintSet other, Map<String, InputDomain> domains) {
            ConstraintSet intersection = intersect(other, domains);
            if (intersection == null) {
                return List.of(this);
            }
            if (subsetOf(other)) {
                return List.of();
            }
            Split split = splitAgainst(other, domains);
            if (split == null) {
                return List.of(this);
            }
            List<ConstraintSet> result = new ArrayList<>();
            result.add(split.outside);
            result.addAll(split.overlap.subtract(other, domains));
            return result;
        }

        Expression toExpression(Scope scope, Map<String, InputDomain> domains) {
            Expression expr = Expression.TRUE;
            for (Entry<String, ScalarConstraint> entry : scalars.entrySet()) {
                expr = expr.and(entry.getValue().toExpression(scope.key(entry.getKey()), domains.get(entry.getKey())));
            }
            for (Entry<String, ListConstraint> entry : lists.entrySet()) {
                expr = expr.and(entry.getValue().toExpression(scope.key(entry.getKey())));
            }
            return expr;
        }

        String literal() {
            return scalars + "|" + lists;
        }

        Split splitAgainst(ConstraintSet other, Map<String, InputDomain> domains) {
            for (Entry<String, ScalarConstraint> entry : other.scalars.entrySet()) {
                InputDomain domain = domains.get(entry.getKey());
                if (domain == null || domain.kind != InputDomain.Kind.SCALAR) {
                    continue;
                }
                Set<String> leftAllowed = allowed(entry.getKey(), domain);
                Set<String> rightAllowed = other.allowed(entry.getKey(), domain);
                if (!rightAllowed.containsAll(leftAllowed)) {
                    Set<String> outside = new TreeSet<>(leftAllowed);
                    outside.removeAll(rightAllowed);
                    Set<String> overlap = new TreeSet<>(leftAllowed);
                    overlap.retainAll(rightAllowed);
                    return new Split(withScalar(entry.getKey(), outside, domain, domains),
                            withScalar(entry.getKey(), overlap, domain, domains));
                }
            }
            for (Entry<String, ListConstraint> entry : other.lists.entrySet()) {
                String key = entry.getKey();
                ListConstraint left = lists.getOrDefault(key, ListConstraint.EMPTY);
                for (String required : entry.getValue().required) {
                    if (!left.required.contains(required) && !left.forbidden.contains(required)) {
                        return new Split(withListForbidden(key, required, domains),
                                withListRequired(key, required, domains));
                    }
                }
                for (String forbidden : entry.getValue().forbidden) {
                    if (!left.forbidden.contains(forbidden) && !left.required.contains(forbidden)) {
                        return new Split(withListRequired(key, forbidden, domains),
                                withListForbidden(key, forbidden, domains));
                    }
                }
            }
            return null;
        }

        ConstraintSet withScalar(String key, Set<String> allowed, InputDomain domain, Map<String, InputDomain> domains) {
            Map<String, ScalarConstraint> next = new TreeMap<>(scalars);
            if (allowed.equals(domain.scalarValues)) {
                next.remove(key);
            } else {
                next.put(key, new ScalarConstraint(allowed));
            }
            return new ConstraintSet(next, lists).normalized(domains);
        }

        ConstraintSet withListRequired(String key, String value, Map<String, InputDomain> domains) {
            Map<String, ListConstraint> next = new TreeMap<>(lists);
            ListConstraint constraint = next.getOrDefault(key, ListConstraint.EMPTY);
            next.put(key, constraint.withRequired(value));
            return new ConstraintSet(scalars, next).normalized(domains);
        }

        ConstraintSet withListForbidden(String key, String value, Map<String, InputDomain> domains) {
            Map<String, ListConstraint> next = new TreeMap<>(lists);
            ListConstraint constraint = next.getOrDefault(key, ListConstraint.EMPTY);
            next.put(key, constraint.withForbidden(value));
            return new ConstraintSet(scalars, next).normalized(domains);
        }

        Set<String> allowed(String key, InputDomain domain) {
            ScalarConstraint constraint = scalars.get(key);
            return constraint == null ? domain.scalarValues : constraint.allowed;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ConstraintSet)) {
                return false;
            }
            ConstraintSet other = (ConstraintSet) o;
            return scalars.equals(other.scalars) && lists.equals(other.lists);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scalars, lists);
        }
    }

    private static final class ScalarConstraint {
        private final Set<String> allowed;

        ScalarConstraint(Set<String> allowed) {
            this.allowed = Set.copyOf(new TreeSet<>(allowed));
        }

        ScalarConstraint intersect(ScalarConstraint other) {
            Set<String> intersection = new TreeSet<>(allowed);
            intersection.retainAll(other.allowed);
            return intersection.isEmpty() ? null : new ScalarConstraint(intersection);
        }

        Expression toExpression(String key, InputDomain domain) {
            if (domain != null && domain.isBoolean()) {
                if (allowed.equals(Set.of("true"))) {
                    return Expression.create(String.format("${%s}", key));
                }
                if (allowed.equals(Set.of("false"))) {
                    return Expression.create(String.format("!${%s}", key));
                }
            }
            if (domain != null && domain.kind == InputDomain.Kind.SCALAR) {
                Set<String> excluded = new TreeSet<>(domain.scalarValues);
                excluded.removeAll(allowed);
                if (excluded.size() == 1) {
                    return Expression.create(String.format("${%s} != '%s'", key, excluded.iterator().next()));
                }
            }
            Expression expr = Expression.FALSE;
            for (String value : allowed) {
                expr = expr.or(Expression.create(String.format("${%s} == '%s'", key, value)));
            }
            return expr.reduce();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ScalarConstraint && allowed.equals(((ScalarConstraint) o).allowed);
        }

        @Override
        public int hashCode() {
            return Objects.hash(allowed);
        }

        @Override
        public String toString() {
            return allowed.toString();
        }
    }

    private static final class ListConstraint {
        private static final ListConstraint EMPTY = new ListConstraint(Set.of(), Set.of());

        private final Set<String> required;
        private final Set<String> forbidden;

        ListConstraint(Set<String> required, Set<String> forbidden) {
            this.required = Set.copyOf(new TreeSet<>(required));
            this.forbidden = Set.copyOf(new TreeSet<>(forbidden));
        }

        boolean isEmpty() {
            return required.isEmpty() && forbidden.isEmpty();
        }

        ListConstraint intersect(ListConstraint other) {
            Set<String> nextRequired = new TreeSet<>(required);
            nextRequired.addAll(other.required);
            Set<String> nextForbidden = new TreeSet<>(forbidden);
            nextForbidden.addAll(other.forbidden);
            for (String value : nextRequired) {
                if (nextForbidden.contains(value)) {
                    return null;
                }
            }
            return new ListConstraint(nextRequired, nextForbidden);
        }

        ListConstraint withRequired(String value) {
            Set<String> next = new TreeSet<>(required);
            next.add(value);
            return new ListConstraint(next, forbidden);
        }

        ListConstraint withForbidden(String value) {
            Set<String> next = new TreeSet<>(forbidden);
            next.add(value);
            return new ListConstraint(required, next);
        }

        Expression toExpression(String key) {
            Expression expr = Expression.TRUE;
            for (String value : required) {
                expr = expr.and(Expression.create(String.format("${%s} contains '%s'", key, value)));
            }
            for (String value : forbidden) {
                expr = expr.and(Expression.create(String.format("!(${%s} contains '%s')", key, value)));
            }
            return expr;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ListConstraint)) {
                return false;
            }
            ListConstraint other = (ListConstraint) o;
            return required.equals(other.required) && forbidden.equals(other.forbidden);
        }

        @Override
        public int hashCode() {
            return Objects.hash(required, forbidden);
        }

        @Override
        public String toString() {
            return required + "!" + forbidden;
        }
    }

    private static final class Split {
        private final ConstraintSet outside;
        private final ConstraintSet overlap;

        Split(ConstraintSet outside, ConstraintSet overlap) {
            this.outside = outside;
            this.overlap = overlap;
        }
    }

    private static final class FileObject implements Comparable<FileObject> {
        private final String checksum;
        private final List<FileOp> ops;
        private final Reachability reachability;
        private final Scope scope;
        private String conditionLiteral;
        private List<Expression.Token> conditionTokens;

        FileObject(String checksum, List<FileOp> ops, Reachability reachability, Scope scope) {
            this.checksum = checksum;
            this.ops = ops;
            this.reachability = Objects.requireNonNull(reachability);
            this.scope = Objects.requireNonNull(scope);
        }

        Expression condition() {
            if (reachability.isFalse()) {
                return Expression.FALSE;
            }
            String literal = conditionLiteral();
            return Expression.TRUE.literal().equals(literal)
                    ? Expression.TRUE
                    : Expression.create(literal);
        }

        String conditionLiteral() {
            String literal = conditionLiteral;
            if (literal == null) {
                literal = reachabilityExpression(reachability, scope).literal();
                conditionLiteral = literal;
            }
            return literal;
        }

        List<Expression.Token> conditionTokens() {
            if (reachability.isFalse()) {
                return Expression.FALSE.tokens();
            }
            List<Expression.Token> tokens = conditionTokens;
            if (tokens == null) {
                String literal = conditionLiteral();
                tokens = Expression.TRUE.literal().equals(literal)
                        ? Expression.TRUE.tokens()
                        : Expression.parseTokens(literal);
                conditionTokens = tokens;
            }
            return tokens;
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
        private final Reachability reach;
        private final List<FileOp> ops;

        FileOps(List<FileOp> ops, Reachability reach) {
            this.reach = reach;
            this.ops = ops;
        }

        @Override
        public int compareTo(FileOps o) {
            int r = reach.literal().compareTo(o.reach.literal());
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

        static FileOps combine(List<FileOps> list) {
            List<FileOp> ops = new ArrayList<>();
            FileOps first = list.get(0);
            Reachability reach = first.reach;
            for (FileOps e : list) {
                ops.addAll(e.ops);
            }
            for (int i = 1; i < list.size(); i++) {
                reach = reach.and(list.get(i).reach);
            }
            return new FileOps(ops, reach);
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
