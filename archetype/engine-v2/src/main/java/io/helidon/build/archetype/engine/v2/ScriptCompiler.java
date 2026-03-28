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
    static final String OPTION_VALUE_ALREADY_DECLARED = "Option value is already declared";

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<Node, String> scopes = new HashMap<>();
    private final Map<Node, Map<String, Value<?>>> paths = new HashMap<>();
    private final Map<String, Set<Node>> declaredValues = new HashMap<>();
    private final Map<Node, Map<String, Value<?>>> declaredValueCache = new IdentityHashMap<>();
    private final Map<Node, Map<String, Value<?>>> inlineDeclaredValueCache = new IdentityHashMap<>();
    private final Map<Node, Expression> expressions = new HashMap<>();
    private final Map<Node, Map<String, Expression>> refs = new HashMap<>();
    private final Map<Node, Reachability> reachabilityByNode = new HashMap<>();
    private final Map<Node, Map<String, Reachability>> refReachabilityByNode = new HashMap<>();
    private final Map<String, InputDomain> domains = new LinkedHashMap<>();
    private final Map<String, Type> refTypes = new HashMap<>();
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
        mirrors.put(sourceNode, image.node);
        mirrors.put(image.node, sourceNode);
        sourceNode.visit(new InputVisitor(image, mirrors)); // render inputs
        if (!options.contains(Options.NO_OUTPUT)) {
            sourceNode.visit(new OutputVisitor(image)); // render outputs
        }
        image.node.visit(new StubsVisitor(mirrors)); // render stubs
        image.node.visit(new DedupVisitor()); // de-dup steps
    }

    private void validate() {
        validatePresets();
        validateInputTypes();
        validateExpressions();
        validateOptions();
        validateInputs();
        validateSteps();
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
            boolean compatible = true;

            // check operators compatibility
            for (Token token : expr.tokens()) {
                if (token.isOperator()) {
                    switch (token.operator()) {
                        case AS_INT:
                        case AS_LIST:
                        case AS_STRING:
                        case SIZEOF:
                        case GREATER_THAN:
                        case GREATER_OR_EQUAL:
                        case LOWER_THAN:
                        case LOWER_OR_EQUAL:
                            compatible = false;
                            errors.add(String.format("%s %s: '%s'",
                                    node.location(),
                                    EXPR_INCOMPATIBLE_OPERATOR,
                                    token.operator().symbol()));
                            break;
                        default:
                    }
                }
            }

            boolean resolved = true;
            Scope scope = scope(node);
            Reachability blockReachability = reachability(node.parent());
            Map<String, Reachability> refReachabilityMap = refReachabilityByNode.getOrDefault(node, Map.of());
            Map<String, Reachability> variableDemands = variableDemands(expr, scope, refReachabilityMap);
            Expression blockExpr = null;
            Map<String, Expression> refMap = null;
            for (String variable : expr.variables()) {

                // normalized variable
                String ref = scope.key(variable);

                boolean variableResolved = false;
                Reachability requiredReachability = blockReachability;
                Reachability variableDemand = variableDemands.get(ref);
                if (variableDemand != null) {
                    requiredReachability = requiredReachability == null
                            ? variableDemand
                            : requiredReachability.and(variableDemand);
                }
                Reachability refReachability = refReachabilityMap.get(ref);
                if (requiredReachability != null && refReachability != null) {
                    variableResolved = refReachability.contains(requiredReachability);
                }
                if (!variableResolved) {
                    if (blockExpr == null) {
                        blockExpr = expression(node.parent());
                        refMap = refs.getOrDefault(node, Map.of());
                    }
                    // reachability tracks declaration coverage, but it does not yet
                    // capture every implied binding recovered by inlining presets and
                    // defaults within the current block
                    Expression refExpr0 = refMap.getOrDefault(ref, Expression.FALSE);
                    Expression refExpr1 = blockExpr.relativize(refExpr0);
                    Expression refExpr2 = inline(node, refExpr1);
                    variableResolved = refExpr2 == Expression.TRUE;
                }
                if (!variableResolved) {
                    resolved = false;
                    errors.add(String.format("%s %s: '%s'",
                            node.location(),
                            EXPR_UNRESOLVED_VARIABLE,
                            variable));
                }
            }

            // check evaluation
            if (resolved) {
                Map<String, Value<?>> variables = new HashMap<>();
                for (String variable : expr.variables()) {
                    Type type = refTypes.getOrDefault(scope.key(variable), Type.EMPTY);
                    variables.put(variable, Value.typed(type));
                }
                try {
                    expr.eval(variables::get);
                } catch (RuntimeException ex) {
                    errors.add(String.format(
                            "%s %s: '%s'",
                            node.location(),
                            EXPR_EVAL_ERROR,
                            ex.getMessage()));
                    continue;
                }
                if (compatible && translate(expr, scope, Map.of(), refReachabilityMap) == null) {
                    errors.add(String.format(
                            "%s %s: '%s'",
                            node.location(),
                            EXPR_UNSUPPORTED_CONDITION,
                            expr.literal()));
                }
            }
        }
    }

    private Map<String, Reachability> variableDemands(Expression expr,
                                                      Scope scope,
                                                      Map<String, Reachability> definitions) {
        return new VariableDemandAnalyzer(scope, definitions).analyze(expr);
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
        if (node0 == null) {
            node0 = declaredValueByExpression(node, key);
        }
        if (node0 != null) {
            return Value.typed(node0.value(), node0.kind().valueType());
        }
        return Value.empty();
    }

    private Value<?> declaredValueForInlining(Node node, String key) {
        return inlineDeclaredValueCache
                .computeIfAbsent(node, n -> new HashMap<>())
                .computeIfAbsent(key, k -> declaredValueForInlining0(node, k));
    }

    private Value<?> declaredValueForInlining0(Node node, String key) {
        Node node0 = declaredValueByReachability(node, key);
        if (node0 != null) {
            return Value.typed(node0.value(), node0.kind().valueType());
        }
        return Value.empty();
    }

    private Node declaredValueByReachability(Node node, String key) {
        Reachability blockReachability = reachability(node.parent());
        if (blockReachability == null) {
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
            Reachability overlap = blockReachability.and(refReachability);
            if (overlap.isFalse()) {
                continue;
            }
            if (refReachability.contains(blockReachability)) {
                if (node0 == null || n.id() > node0.id()) {
                    node0 = n;
                }
            } else if (node0 != null && n.id() > node0.id()) {
                node0 = null;
            }
        }
        return node0;
    }

    private Node declaredValueByExpression(Node node, String key) {
        Node node0 = null;
        Expression blockExpr = expression(node.parent());
        for (Node n : declaredValues.getOrDefault(key, Set.of())) {
            if (!attached(n) || node.id() <= n.id()) {
                continue;
            }
            // "relativize" the expression within the block
            Expression refExpr = blockExpr.relativize(expression(n));
            if (refExpr == Expression.TRUE) {
                if (node0 == null || n.id() > node0.id()) {
                    node0 = n;
                }
            } else if (refExpr != Expression.FALSE) {
                if (node0 != null && n.id() > node0.id()) {
                    node0 = null;
                }
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

    private Expression inline(Node node, Expression expr) {
        return inline(node, expr, this::declaredValue);
    }

    private Expression inlineCondition(Node node, Expression expr) {
        return inline(node, expr, this::declaredValueForInlining);
    }

    private Expression inline(Node node,
                              Expression expr,
                              BiFunction<Node, String, Value<?>> declaredValueResolver) {
        try {
            Scope scope = scope(node);
            Map<String, Value<?>> values = path(node);
            return expr.inline(s -> {
                String key = scope.get(s).key();
                Value<?> value = values.get(key);
                if (value == null) {
                    value = declaredValueResolver.apply(node, key);
                }
                return value;
            });
        } catch (RuntimeException ex) {
            errors.add(String.format(
                    "%s %s: '%s'",
                    node.location(),
                    EXPR_EVAL_ERROR,
                    ex.getMessage()));
            return expr;
        }
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

    private Expression normalize(Expression expr, Scope scope) {
        return expr.map(t -> {
            if (t.isVariable()) {
                return Token.of(scope.key(t.variable()));
            }
            return t;
        }).reduce();
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

    private Reachability trueReachability() {
        return Reachability.trueValue(domains);
    }

    private Reachability falseReachability() {
        return Reachability.falseValue(domains);
    }

    private Reachability reachability(Node node) {
        return node == null ? trueReachability() : reachabilityByNode.get(node);
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

    private Expression reachabilityExpression(Reachability reachability, Scope scope) {
        return reachability.toExpression(scope).reduce();
    }

    private Expression residualExpression(Reachability reachability, Reachability base, Scope scope) {
        return reachability.toExpression(scope).reduce(base.toExpression(scope));
    }

    private Expression compiledExpression(Node node, Scope scope) {
        Reachability reachability = reachability(node);
        return reachability != null ? reachabilityExpression(reachability, scope) : normalize(expression(node), scope);
    }

    private Reachability translate(Expression expr,
                                   Scope scope,
                                   Map<String, ConstantBindings> bindings,
                                   Map<String, Reachability> definitions) {
        return new ReachabilityTranslator(scope, bindings, definitions).translate(expr);
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

    private final class VariableDemandAnalyzer {
        private final Scope scope;
        private final Map<String, Reachability> definitions;

        private VariableDemandAnalyzer(Scope scope, Map<String, Reachability> definitions) {
            this.scope = scope;
            this.definitions = definitions;
        }

        Map<String, Reachability> analyze(Expression expr) {
            Deque<DemandTerm> stack = new ArrayDeque<>();
            try {
                for (Token token : expr.tokens()) {
                    if (token.isOperand()) {
                        stack.push(new DemandValue(token.operand()));
                        continue;
                    }
                    if (token.isVariable()) {
                        stack.push(new DemandRef(scope.key(token.variable())));
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
                            return Map.of();
                    }
                }
                if (stack.size() != 1) {
                    return Map.of();
                }
                Map<String, Reachability> demands = new HashMap<>();
                booleanTerm(stack.pop()).collect(trueReachability(), demands);
                return demands;
            } catch (RuntimeException ex) {
                return Map.of();
            }
        }

        private DemandBoolean booleanTerm(DemandTerm term) {
            if (term instanceof DemandBoolean) {
                return (DemandBoolean) term;
            }
            if (term instanceof DemandValue) {
                Value<?> value = ((DemandValue) term).value;
                if (value.type() == Type.BOOLEAN) {
                    return new DemandBoolean(value.getBoolean() ? trueReachability() : falseReachability(),
                            (demand, output) -> {
                            });
                }
                return new DemandBoolean(null, term::collect);
            }
            if (term instanceof DemandRef) {
                String key = ((DemandRef) term).key;
                return new DemandBoolean(booleanReachability(key), term::collect);
            }
            throw new IllegalStateException("Unsupported demand term: " + term);
        }

        private DemandBoolean equality(DemandTerm right, DemandTerm left, boolean negate) {
            Reachability reachability = equality(left, right);
            if (negate && reachability != null) {
                reachability = trueReachability().subtract(reachability);
            }
            return new DemandBoolean(reachability, (demand, output) -> {
                left.collect(demand, output);
                right.collect(demand, output);
            });
        }

        private Reachability equality(DemandTerm left, DemandTerm right) {
            if (left instanceof DemandValue && right instanceof DemandValue) {
                return Value.isEqual(((DemandValue) left).value, ((DemandValue) right).value)
                        ? trueReachability()
                        : falseReachability();
            }
            if (left instanceof DemandRef && right instanceof DemandValue) {
                return equality(((DemandRef) left).key, ((DemandValue) right).value);
            }
            if (left instanceof DemandValue && right instanceof DemandRef) {
                return equality(((DemandRef) right).key, ((DemandValue) left).value);
            }
            return null;
        }

        private DemandBoolean contains(DemandTerm right, DemandTerm left) {
            Reachability reachability = containsReachability(left, right);
            return new DemandBoolean(reachability, (demand, output) -> {
                left.collect(demand, output);
                right.collect(demand, output);
            });
        }

        private Reachability containsReachability(DemandTerm left, DemandTerm right) {
            if (left instanceof DemandRef && right instanceof DemandValue) {
                return listContains(((DemandRef) left).key, ((DemandValue) right).value);
            }
            if (left instanceof DemandValue && right instanceof DemandRef) {
                Value<?> value = ((DemandValue) left).value;
                if (value.type() == Type.LIST) {
                    return scalarAny(((DemandRef) right).key, new TreeSet<>(value.getList()));
                }
            }
            if (left instanceof DemandValue && right instanceof DemandValue) {
                Value<?> leftValue = ((DemandValue) left).value;
                Value<?> rightValue = ((DemandValue) right).value;
                if (leftValue.type() == Type.LIST) {
                    Set<String> required = containsValues(rightValue);
                    if (required != null) {
                        Set<String> values = new HashSet<>(leftValue.getList());
                        return values.containsAll(required) ? trueReachability() : falseReachability();
                    }
                }
            }
            return null;
        }

        private Reachability booleanReachability(String key) {
            InputDomain domain = domains.get(key);
            if (domain == null || !domain.isBoolean()) {
                return null;
            }
            Reachability raw = Reachability.scalar(key, Set.of("true"), domains);
            Reachability defined = definitions.get(key);
            return defined == null ? raw : defined.and(raw);
        }

        private Reachability equality(String key, Value<?> value) {
            Reachability raw = rawEquality(key, value);
            Reachability defined = definitions.get(key);
            if (raw == null) {
                return null;
            }
            return defined == null ? raw : defined.and(raw);
        }

        private Reachability scalarAny(String key, Set<String> values) {
            Reachability raw = rawScalarAny(key, values);
            Reachability defined = definitions.get(key);
            if (raw == null) {
                return null;
            }
            return defined == null ? raw : defined.and(raw);
        }

        private Reachability listContains(String key, Value<?> value) {
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

        private Reachability rawEquality(String key, Value<?> value) {
            String scalarValue = scalarLiteral(value);
            if (scalarValue == null) {
                return null;
            }
            InputDomain domain = domains.get(key);
            if (domain == null || domain.kind != InputDomain.Kind.SCALAR) {
                return null;
            }
            if (!domain.scalarValues.contains(scalarValue)) {
                return falseReachability();
            }
            return Reachability.scalar(key, Set.of(scalarValue), domains);
        }

        private Reachability rawScalarAny(String key, Set<String> values) {
            InputDomain domain = domains.get(key);
            if (domain == null || domain.kind != InputDomain.Kind.SCALAR) {
                return null;
            }
            Set<String> allowed = new TreeSet<>(values);
            allowed.retainAll(domain.scalarValues);
            return allowed.isEmpty() ? falseReachability() : Reachability.scalar(key, allowed, domains);
        }

        private Reachability rawListContains(String key, Set<String> required) {
            InputDomain domain = domains.get(key);
            if (domain == null || domain.kind != InputDomain.Kind.LIST) {
                return null;
            }
            if (!domain.listItems.containsAll(required)) {
                return falseReachability();
            }
            return Reachability.listContains(key, required, domains);
        }

        private Set<String> containsValues(Value<?> value) {
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

        private void addDemand(String key, Reachability demand, Map<String, Reachability> output) {
            if (demand == null || demand.isFalse()) {
                return;
            }
            output.compute(key, (k, current) -> current == null ? demand : current.or(demand));
        }

        private abstract class DemandTerm {
            abstract void collect(Reachability demand, Map<String, Reachability> output);
        }

        private final class DemandValue extends DemandTerm {
            private final Value<?> value;

            private DemandValue(Value<?> value) {
                this.value = value;
            }

            @Override
            public void collect(Reachability demand, Map<String, Reachability> output) {
            }
        }

        private final class DemandRef extends DemandTerm {
            private final String key;

            private DemandRef(String key) {
                this.key = key;
            }

            @Override
            public void collect(Reachability demand, Map<String, Reachability> output) {
                addDemand(key, demand, output);
            }
        }

        private final class DemandBoolean extends DemandTerm {
            private final Reachability truthy;
            private final BiConsumer<Reachability, Map<String, Reachability>> collector;

            private DemandBoolean(Reachability truthy,
                                  BiConsumer<Reachability, Map<String, Reachability>> collector) {
                this.truthy = truthy;
                this.collector = collector;
            }

            DemandBoolean negate() {
                Reachability reachability = truthy == null ? null : trueReachability().subtract(truthy);
                return new DemandBoolean(reachability, collector);
            }

            DemandBoolean and(DemandBoolean left) {
                Reachability reachability = left.truthy == null || truthy == null ? null : left.truthy.and(truthy);
                return new DemandBoolean(reachability, (demand, output) -> {
                    left.collect(demand, output);
                    Reachability rightDemand = left.truthy == null ? demand : demand.and(left.truthy);
                    collect(rightDemand, output);
                });
            }

            DemandBoolean or(DemandBoolean left) {
                Reachability reachability = left.truthy == null || truthy == null ? null : left.truthy.or(truthy);
                return new DemandBoolean(reachability, (demand, output) -> {
                    left.collect(demand, output);
                    Reachability rightDemand = left.truthy == null
                            ? demand
                            : demand.and(trueReachability().subtract(left.truthy));
                    collect(rightDemand, output);
                });
            }

            @Override
            public void collect(Reachability demand, Map<String, Reachability> output) {
                collector.accept(demand, output);
            }
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

    Expression expression(Node node) {
        return expressions.computeIfAbsent(node, k -> expression(k, this::scopeId));
    }

    private Expression expression(Node node, Function<Node, String> key) {
        Expression expr = Expression.TRUE;
        for (Node n = node; n != null; n = n.parent()) {
            switch (n.kind()) {
                case CONDITION:
                    expr = expr.and(n.expression());
                    break;
                case INPUT_BOOLEAN:
                    expr = expr.and(Expression.create(String.format("${%s}", key.apply(n))));
                    break;
                case INPUT_TEXT:
                    expr = expr.and(Expression.create(String.format("${%s} != ''", key.apply(n))));
                    break;
                case INPUT_OPTION:
                    Node input = n.ancestor(Kind::isInput).orElseThrow();
                    switch (input.kind()) {
                        case INPUT_ENUM:
                            expr = expr.and(Expression.create(String.format(
                                    "${%s} == '%s'",
                                    key.apply(input),
                                    n.value().getString())));
                            break;
                        case INPUT_LIST:
                            expr = expr.and(Expression.create(String.format(
                                    "${%s} contains '%s'",
                                    key.apply(input),
                                    n.value().getString())));
                            break;
                        default:
                    }
                    break;
                default:
            }
        }
        return expr.reduce();
    }

    private final class InlineInvoker extends ScriptInvoker {
        private final Deque<Node> callStack = new ArrayDeque<>();
        private final Map<String, Node> methods = new HashMap<>();

        private InlineInvoker() {
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

        private final Map<String, Expression> currentRefs = new HashMap<>();
        private final Map<String, Reachability> currentReachabilityByRef = new HashMap<>();
        private final Map<String, ConstantBindings> currentBindings = new HashMap<>();
        private final Deque<ReachabilityState> reachabilityStack = new ArrayDeque<>(
                List.of(ReachabilityState.of(trueReachability())));
        private final Set<Node> modifiedSteps = new HashSet<>();
        private int nextId = 0;

        private RefsInvoker() {
            super(ctx);
        }

        @Override
        public boolean visit(Node node) {
            node.id(nextId++);
            Scope scope = ctx.scope();
            ReachabilityState currentState = reachabilityStack.peek();
            ReachabilityState nodeState = currentState;
            switch (node.kind()) {
                case SOURCE:
                case EXEC:
                    scopes.put(node, scope.key());
                    if (node.attribute("url").isPresent()) {
                        // skip url invocation
                        reachabilityStack.push(nodeState);
                        return false;
                    }
                    break;
                case CONDITION:
                    scopes.put(node, scope.key());
                    Expression expr = inlineCondition(node, node.expression());
                    if (expr != Expression.FALSE) {
                        node.expression(expr);
                        refs.put(node, Map.copyOf(currentRefs));
                        refReachabilityByNode.put(node, Map.copyOf(currentReachabilityByRef));
                        Expression reachabilityExpr = inline(node, expr);
                        Reachability conditionReachability = translate(reachabilityExpr, scope, currentBindings,
                                currentReachabilityByRef);
                        nodeState = currentState.and(conditionReachability);
                        if (nodeState.isFalse()) {
                            node.expression(Expression.FALSE);
                            reachabilityByNode.put(node, nodeState.reachability());
                            reachabilityStack.push(nodeState);
                            return false;
                        }
                        if (nodeState.supported()) {
                            reachabilityByNode.put(node, nodeState.reachability());
                        }
                        reachabilityStack.push(nodeState);
                        return true;
                    }
                    // condition is always false
                    // skip traversal and prune (postVisit)
                    node.expression(Expression.FALSE);
                    reachabilityByNode.put(node, falseReachability());
                    reachabilityStack.push(ReachabilityState.of(falseReachability()));
                    return false;
                case INPUT_TEXT:
                case INPUT_BOOLEAN:
                case INPUT_LIST:
                case INPUT_ENUM:
                    scope = ctx.pushScope(s -> s.getOrCreate(node));
                    scopes.put(node, scope.key());
                    refTypes.putIfAbsent(scope.key(), node.kind().valueType());
                    registerDomain(node, scope.key());
                    currentRefs.compute(scope.key(), (k, v) -> expression(node.parent()).or(v).reduce());
                    Expression inputExpr = inline(node, expression(node));
                    if (currentState.supported()) {
                        Reachability parentReachability = currentState.reachability();
                        currentReachabilityByRef.compute(scope.key(),
                                (k, v) -> v == null ? parentReachability : v.or(parentReachability));
                        if (currentState.isFalse()) {
                            reachabilityByNode.put(node, falseReachability());
                            reachabilityStack.push(ReachabilityState.of(falseReachability()));
                            return false;
                        }
                    }
                    switch (node.kind()) {
                        case INPUT_BOOLEAN:
                            nodeState = currentState.and(Reachability.scalar(scope.key(), Set.of("true"), domains));
                            break;
                        case INPUT_TEXT:
                            nodeState = ReachabilityState.unsupported();
                            break;
                        default:
                    }
                    if (nodeState.supported() && nodeState.isFalse()) {
                        reachabilityByNode.put(node, nodeState.reachability());
                        reachabilityStack.push(nodeState);
                        return false;
                    }
                    if (!nodeState.supported() && inputExpr == Expression.FALSE) {
                        reachabilityByNode.put(node, falseReachability());
                        reachabilityStack.push(ReachabilityState.of(falseReachability()));
                        return false;
                    }
                    break;
                case INPUT_OPTION:
                    scopes.put(node, scope.key());
                    nodeState = currentState.and(optionReachability(node));
                    if (nodeState.supported() && nodeState.isFalse()) {
                        reachabilityByNode.put(node, nodeState.reachability());
                        reachabilityStack.push(nodeState);
                        return false;
                    }
                    if (!nodeState.supported() && inline(node, expression(node)) == Expression.FALSE) {
                        reachabilityByNode.put(node, falseReachability());
                        reachabilityStack.push(ReachabilityState.of(falseReachability()));
                        return false;
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
                    declaredValues.computeIfAbsent(scope.key(), k -> new LinkedHashSet<>()).add(node);
                    refTypes.putIfAbsent(scope.key(), node.kind().valueType());
                    registerImplicitDomain(scope.key(), node.kind().valueType());
                    currentRefs.compute(scope.key(), (k, v) -> expression(node.parent()).or(v).reduce());
                    if (currentState.supported()) {
                        Reachability parentReachability = currentState.reachability();
                        currentReachabilityByRef.compute(scope.key(),
                                (k, v) -> v == null ? parentReachability : v.or(parentReachability));
                        Value<?> value = constantValue(node);
                        if (value != null) {
                            currentBindings.computeIfAbsent(scope.key(),
                                    k -> new ConstantBindings(node.kind().valueType()))
                                    .add(value, parentReachability);
                        }
                    }
                    if (nodeState.supported()) {
                        reachabilityByNode.put(node, nodeState.reachability());
                    }
                    reachabilityStack.push(nodeState);
                    return true;
                default:
            }
            if (nodeState.supported()) {
                reachabilityByNode.put(node, nodeState.reachability());
            }
            reachabilityStack.push(nodeState);
            return super.visit(node);
        }

        @Override
        public void postVisit(Node node) {
            switch (node.kind()) {
                case SOURCE:
                case EXEC:
                    if (node.attribute("url").isPresent()) {
                        // skip url invocation
                        reachabilityStack.pop();
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
                    Expression expr = inline(node, expression(node));
                    if (expr == Expression.FALSE) {
                        remove(node);
                        node.ancestor(Kind.STEP::equals).ifPresent(modifiedSteps::add);
                    }
                    ctx.popScope();
                    break;
                case INPUT_LIST:
                case INPUT_ENUM:
                    expr = inline(node, expression(node));
                    if (expr == Expression.FALSE || node.children().isEmpty()) {
                        remove(node);
                        node.ancestor(Kind.STEP::equals).ifPresent(modifiedSteps::add);
                    }
                    ctx.popScope();
                    break;
                case INPUT_OPTION:
                    expr = inline(node, expression(node));
                    if (expr == Expression.FALSE) {
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
            reachabilityStack.pop();
            super.postVisit(node);
        }

        void remove(Node node) {
            Log.debug("Removing %s, path: %s, expression: %s",
                    node,
                    Maps.mapValue(path(node), v -> Value.toString(v)),
                    expression(node.parent()));
            node.remove();
        }
    }

    private class InputVisitor implements Node.Visitor {
        private final Deque<Node> stack = new ArrayDeque<>();
        private final Map<Node, Node> mirrors;
        private final Image image;

        InputVisitor(Image image, Map<Node, Node> mirrors) {
            this.image = image;
            this.mirrors = mirrors;
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

            // "relativize" the expression within the block
            Reachability blockReachability = reachability(block);
            Reachability nodeReachability = reachability(node.parent());
            Expression expr;
            if (blockReachability != null && nodeReachability != null) {
                expr = residualExpression(nodeReachability, blockReachability, scope);
            } else {
                expr = normalize(expression(block).relativize(expression(node.parent())), scope);
            }

            // create copy
            Node copy = node.copy();
            appender.accept(blockCopy, copy.wrap(expr));

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
        private final Map<String, Map<List<FileOp>, Reachability>> fileOps = new HashMap<>();
        private final Set<FileObject> files = new TreeSet<>();
        private final Set<FileObject> templates = new TreeSet<>();
        private final Image image;

        private OutputVisitor(Image image) {
            this.image = image;
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
                    Reachability nodeReachability = outputReachability(node);
                    fileOps.computeIfAbsent(node.attribute("id").getString(), k -> new HashMap<>())
                            .compute(ops, (k, v) -> {
                                Reachability reachability = v != null ? v : falseReachability();
                                return reachability.or(nodeReachability);
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
            Scope scope = scope(node);
            Expression expr = reachabilityExpression(outputReachability(node), scope);
            return new FileObject(checksum, fileOps, expr);
        }

        Set<FileObject> resolveFiles(Node node) {
            Set<FileObject> fileObjects = new TreeSet<>();

            // resolve variations of transformations
            List<List<FileOps>> allOps = Lists.filter(Combinatorics.cartesianProduct(fileOps(node)), l -> !l.isEmpty());
            Set<FileOps> resolvedOps = new TreeSet<>(Lists.map(allOps, FileOps::combine));

            Map<String, Reachability> includes = new HashMap<>();
            Map<String, Reachability> excludes = new HashMap<>();

            Scope scope = scope(node);
            Reachability nodeReachability = outputReachability(node);
            for (Node n : node.traverse()) {
                switch (n.kind()) {
                    case INCLUDE:
                    case EXCLUDE:
                        Map<String, Reachability> map = n.kind() == Kind.INCLUDE ? includes : excludes;
                        Reachability filterReachability = outputReachability(n);
                        map.compute(n.value().getString(), (k, v) -> {
                            return v == null ? filterReachability : v.or(filterReachability);
                        });
                        break;
                    default:
                }
            }

            Path directory = workDirs.get(node).resolve(node.attribute("directory").getString());
            for (SourcePath file : SourcePath.scan(directory)) {

                Reachability includeReachability = includes.isEmpty() ? trueReachability() : falseReachability();
                for (Reachability v : Maps.filterKey(includes, file::matches).values()) {
                    includeReachability = includeReachability.or(v);
                }
                Reachability excludeReachability = falseReachability();
                for (Reachability v : Maps.filterKey(excludes, file::matches).values()) {
                    excludeReachability = excludeReachability.or(v);
                }

                Reachability blobReachability = nodeReachability.and(includeReachability).subtract(excludeReachability);
                if (!blobReachability.isFalse()) {
                    String source = file.asString(false);
                    Path path = directory.resolve(source);

                    // record blob
                    String checksum = checksum(path);
                    image.blobs.putIfAbsent(checksum, readAllBytes(path));

                    if (resolvedOps.isEmpty()) {
                        List<FileOp> fileOps = List.of(new FileOp(Pattern.quote(checksum), source));
                        Expression blobExpr = reachabilityExpression(blobReachability, scope);
                        fileObjects.add(new FileObject(checksum, fileOps, blobExpr));
                    } else {
                        // create a unique file object for each transformation variation
                        for (FileOps e : resolvedOps) {
                            List<FileOp> fileOps = e.resolve(checksum, source);
                            Expression fileExpr = reachabilityExpression(blobReachability.and(e.reachability), scope);
                            fileObjects.add(new FileObject(checksum, fileOps, fileExpr));
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
                    if (f.expression != Expression.FALSE) {
                        includes.append(Nodes.include(f.checksum).wrap(f.expression));
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

        private Reachability outputReachability(Node node) {
            Reachability reachability = reachability(node);
            if (reachability == null) {
                throw new IllegalStateException("Missing reachability for output node: " + node);
            }
            return reachability;
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
                    Expression expr = compiledExpression(node, scope);
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
                        nodes.add(copy.wrap(expr));
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
        private final Map<String, Reachability> currentReachabilityByRef = new HashMap<>();
        private final Map<Node, Map<String, Reachability>> reachabilityRefs = new LinkedHashMap<>();
        private final Map<Node, Node> mirrors;

        StubsVisitor(Map<Node, Node> mirrors) {
            this.mirrors = mirrors;
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
                    Reachability reachability = definitionReachability(mirror);
                    if (reachability != null) {
                        currentReachabilityByRef.compute(scopeId(mirror),
                                (k, v) -> v == null ? reachability : v.or(reachability));
                    }
                    break;
                case CONDITION:
                    reachabilityRefs.put(node, Map.copyOf(currentReachabilityByRef));
                    break;
                default:
            }
            return true;
        }

        @Override
        public void postVisit(Node node) {
            if (node.kind() == Kind.SCRIPT) {
                reachabilityRefs.forEach((n, snapshot) -> {
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
            Map<String, Expression> existing = new LinkedHashMap<>();
            for (Node n0 : block.children(Kind.VARIABLES::equals)) {
                for (Node n1 : n0.children()) {
                    existing.put(n1.unwrap().attribute("path").getString(), n1.expression());
                }
            }

            // filter stubs that already exist
            List<Node> stubs = new ArrayList<>();
            for (Node n : nodes) {
                String path = n.unwrap().attribute("path").getString();
                Expression expr = existing.get(path);
                if (!n.expression().equals(expr)) {
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

        List<Node> resolveStubs(Node node, Map<String, Reachability> reachabilitySnapshot) {
            Set<String> variables = node.expression().variables();
            if (variables.isEmpty()) {
                return List.of();
            }

            List<Node> nodes = new ArrayList<>();
            Node block = node.ancestor(Kind::isBlock).orElseThrow();
            Scope scope = ctx.scope().get("~" + scopes.getOrDefault(mirror(block), ""));
            Expression blockExpr = expression(block, n -> scopeId(mirror(n)));
            Reachability blockReachability = translate(blockExpr, scope, Map.of(), reachabilitySnapshot);
            if (blockReachability == null) {
                // validation already reports unsupported condition shapes; do not reintroduce expression fallback here
                return List.of();
            }
            for (String ref : variables) {

                // normalize the ref
                String key = scope.key(ref);

                Reachability refReachability = reachabilitySnapshot.getOrDefault(key, falseReachability());
                Reachability missing = blockReachability.subtract(refReachability);
                if (missing.isFalse()) {
                    continue;
                }
                Type type = refTypes.getOrDefault(key, Type.EMPTY);
                nodes.add(Nodes.variable(type, "~" + key).wrap(residualExpression(missing, blockReachability, scope)));
            }
            return nodes;
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
                        Node first = null;
                        for (Node step : group) {
                            if (first == null) {
                                first = step;
                            } else {
                                Node p = first.parent();
                                if (p.kind() == Kind.CONDITION) {
                                    p.expression(p.expression().or(expression(step)));
                                }
                                step.remove();
                            }
                        }
                    }
                }
            }
        }
    }

    private static final class ReachabilityState {
        private static final ReachabilityState UNSUPPORTED = new ReachabilityState(null);

        private final Reachability reachability;

        private ReachabilityState(Reachability reachability) {
            this.reachability = reachability;
        }

        static ReachabilityState of(Reachability reachability) {
            return reachability == null ? UNSUPPORTED : new ReachabilityState(reachability);
        }

        static ReachabilityState unsupported() {
            return UNSUPPORTED;
        }

        boolean supported() {
            return reachability != null;
        }

        boolean isFalse() {
            return supported() && reachability.isFalse();
        }

        Reachability reachability() {
            return reachability;
        }

        ReachabilityState and(Reachability other) {
            if (!supported() || other == null) {
                return unsupported();
            }
            return of(reachability.and(other));
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

        private InputDomain(Kind kind, Set<String> scalarValues, Set<String> listItems) {
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
        private Reachability defined = falseReachability();

        private ConstantBindings(Type type) {
            this.type = type;
        }

        Reachability defined() {
            return defined;
        }

        void add(Value<?> value, Reachability reachability) {
            if (reachability == null || reachability.isFalse()) {
                return;
            }
            ListIterator<ConstantCase> it = cases.listIterator();
            while (it.hasNext()) {
                ConstantCase next = it.next();
                Reachability remaining = next.reachability.subtract(reachability);
                if (remaining.isFalse()) {
                    it.remove();
                } else {
                    next.reachability = remaining;
                }
            }
            for (ConstantCase constantCase : cases) {
                if (Value.isEqual(constantCase.value, value)) {
                    constantCase.reachability = constantCase.reachability.or(reachability);
                    defined = defined.or(reachability);
                    return;
                }
            }
            cases.add(new ConstantCase(value, reachability));
            defined = defined.or(reachability);
        }

        Reachability match(Value<?> value) {
            Reachability reachability = falseReachability();
            for (ConstantCase constantCase : cases) {
                if (Value.isEqual(constantCase.value, value)) {
                    reachability = reachability.or(constantCase.reachability);
                }
            }
            return reachability;
        }

        Reachability scalarAny(Set<String> values) {
            Reachability reachability = falseReachability();
            for (ConstantCase constantCase : cases) {
                String value = scalarLiteral(constantCase.value);
                if (value != null && values.contains(value)) {
                    reachability = reachability.or(constantCase.reachability);
                }
            }
            return reachability;
        }

        Reachability listContains(Set<String> required) {
            Reachability reachability = falseReachability();
            if (type != Type.LIST) {
                return reachability;
            }
            for (ConstantCase constantCase : cases) {
                if (constantCase.value.type() == Type.LIST) {
                    Set<String> values = new HashSet<>(constantCase.value.getList());
                    if (values.containsAll(required)) {
                        reachability = reachability.or(constantCase.reachability);
                    }
                }
            }
            return reachability;
        }
    }

    private static final class ConstantCase {
        private final Value<?> value;
        private Reachability reachability;

        private ConstantCase(Value<?> value, Reachability reachability) {
            this.value = value;
            this.reachability = reachability;
        }
    }

    private final class ReachabilityTranslator {
        private final Scope scope;
        private final Map<String, ConstantBindings> bindings;
        private final Map<String, Reachability> definitions;

        private ReachabilityTranslator(Scope scope,
                                      Map<String, ConstantBindings> bindings,
                                      Map<String, Reachability> definitions) {
            this.scope = scope;
            this.bindings = bindings;
            this.definitions = definitions;
        }

        Reachability translate(Expression expr) {
            Deque<TranslationTerm> stack = new ArrayDeque<>();
            try {
                for (Token token : expr.tokens()) {
                    if (token.isOperand()) {
                        stack.push(new TranslationTerm.Value(token.operand()));
                        continue;
                    }
                    if (token.isVariable()) {
                        stack.push(new TranslationTerm.Ref(scope.key(token.variable())));
                        continue;
                    }
                    switch (token.operator()) {
                        case NOT:
                            Reachability negated = booleanReachability(stack.pop());
                            if (negated == null) {
                                return null;
                            }
                            stack.push(new TranslationTerm.Boolean(trueReachability().subtract(negated)));
                            break;
                        case AND:
                        case OR:
                            Reachability right = booleanReachability(stack.pop());
                            Reachability left = booleanReachability(stack.pop());
                            if (left == null || right == null) {
                                return null;
                            }
                            stack.push(new TranslationTerm.Boolean(token.operator() == Expression.Operator.AND
                                    ? left.and(right)
                                    : left.or(right)));
                            break;
                        case EQUAL:
                        case NOT_EQUAL:
                            Reachability equality = equality(stack.pop(), stack.pop());
                            if (equality == null) {
                                return null;
                            }
                            stack.push(new TranslationTerm.Boolean(token.operator() == Expression.Operator.NOT_EQUAL
                                    ? trueReachability().subtract(equality)
                                    : equality));
                            break;
                        case CONTAINS:
                            Reachability contains = contains(stack.pop(), stack.pop());
                            if (contains == null) {
                                return null;
                            }
                            stack.push(new TranslationTerm.Boolean(contains));
                            break;
                        default:
                            return null;
                    }
                }
                if (stack.size() != 1) {
                    return null;
                }
                return booleanReachability(stack.pop());
            } catch (RuntimeException ex) {
                return null;
            }
        }

        private Reachability booleanReachability(TranslationTerm term) {
            if (term instanceof TranslationTerm.Boolean) {
                return ((TranslationTerm.Boolean) term).reachability;
            }
            if (term instanceof TranslationTerm.Value) {
                Value<?> value = ((TranslationTerm.Value) term).value;
                if (value.type() == Type.BOOLEAN) {
                    return value.getBoolean() ? trueReachability() : falseReachability();
                }
                return null;
            }
            if (term instanceof TranslationTerm.Ref) {
                String key = ((TranslationTerm.Ref) term).key;
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
                return equality(key, Value.TRUE);
            }
            return null;
        }

        private Reachability equality(TranslationTerm right, TranslationTerm left) {
            if (left instanceof TranslationTerm.Value && right instanceof TranslationTerm.Value) {
                return Value.isEqual(((TranslationTerm.Value) left).value, ((TranslationTerm.Value) right).value)
                        ? trueReachability()
                        : falseReachability();
            }
            if (left instanceof TranslationTerm.Ref && right instanceof TranslationTerm.Value) {
                return equality(((TranslationTerm.Ref) left).key, ((TranslationTerm.Value) right).value);
            }
            if (left instanceof TranslationTerm.Value && right instanceof TranslationTerm.Ref) {
                return equality(((TranslationTerm.Ref) right).key, ((TranslationTerm.Value) left).value);
            }
            return null;
        }

        private Reachability contains(TranslationTerm right, TranslationTerm left) {
            if (left instanceof TranslationTerm.Ref && right instanceof TranslationTerm.Value) {
                return listContains(((TranslationTerm.Ref) left).key, ((TranslationTerm.Value) right).value);
            }
            if (left instanceof TranslationTerm.Value && right instanceof TranslationTerm.Ref) {
                Value<?> value = ((TranslationTerm.Value) left).value;
                if (value.type() == Type.LIST) {
                    return scalarAny(((TranslationTerm.Ref) right).key, new TreeSet<>(value.getList()));
                }
            }
            if (left instanceof TranslationTerm.Value && right instanceof TranslationTerm.Value) {
                Value<?> leftValue = ((TranslationTerm.Value) left).value;
                Value<?> rightValue = ((TranslationTerm.Value) right).value;
                if (leftValue.type() == Type.LIST) {
                    Set<String> required = containsValues(rightValue);
                    if (required != null) {
                        Set<String> values = new HashSet<>(leftValue.getList());
                        return values.containsAll(required) ? trueReachability() : falseReachability();
                    }
                }
            }
            return null;
        }

        private Reachability equality(String key, Value<?> value) {
            ConstantBindings binding = bindings.get(key);
            Reachability bound = binding != null ? binding.match(value) : null;
            Reachability raw = rawEquality(key, value);
            return combine(key, bound, raw);
        }

        private Reachability scalarAny(String key, Set<String> values) {
            ConstantBindings binding = bindings.get(key);
            Reachability bound = binding != null ? binding.scalarAny(values) : null;
            Reachability raw = rawScalarAny(key, values);
            return combine(key, bound, raw);
        }

        private Reachability listContains(String key, Value<?> value) {
            Set<String> required = containsValues(value);
            if (required == null) {
                return null;
            }
            ConstantBindings binding = bindings.get(key);
            Reachability bound = binding != null ? binding.listContains(required) : null;
            Reachability raw = rawListContains(key, required);
            return combine(key, bound, raw);
        }

        private Reachability combine(String key, Reachability bound, Reachability raw) {
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
            Reachability available = defined == null ? trueReachability() : defined;
            Reachability unresolved = available.subtract(binding.defined()).and(raw);
            return bound == null ? unresolved : bound.or(unresolved);
        }

        private Reachability rawEquality(String key, Value<?> value) {
            String scalarValue = scalarLiteral(value);
            if (scalarValue == null) {
                return null;
            }
            InputDomain domain = domains.get(key);
            if (domain == null || domain.kind != InputDomain.Kind.SCALAR) {
                return null;
            }
            if (!domain.scalarValues.contains(scalarValue)) {
                return falseReachability();
            }
            return Reachability.scalar(key, Set.of(scalarValue), domains);
        }

        private Reachability rawScalarAny(String key, Set<String> values) {
            InputDomain domain = domains.get(key);
            if (domain == null || domain.kind != InputDomain.Kind.SCALAR) {
                return null;
            }
            Set<String> allowed = new TreeSet<>(values);
            allowed.retainAll(domain.scalarValues);
            return allowed.isEmpty() ? falseReachability() : Reachability.scalar(key, allowed, domains);
        }

        private Reachability rawListContains(String key, Set<String> required) {
            InputDomain domain = domains.get(key);
            if (domain == null || domain.kind != InputDomain.Kind.LIST) {
                return null;
            }
            if (!domain.listItems.containsAll(required)) {
                return falseReachability();
            }
            return Reachability.listContains(key, required, domains);
        }

        private Set<String> containsValues(Value<?> value) {
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
    }

    private interface TranslationTerm {
        final class Boolean implements TranslationTerm {
            private final Reachability reachability;

            private Boolean(Reachability reachability) {
                this.reachability = reachability;
            }
        }

        final class Ref implements TranslationTerm {
            private final String key;

            private Ref(String key) {
                this.key = key;
            }
        }

        final class Value implements TranslationTerm {
            private final io.helidon.build.archetype.engine.v2.Value<?> value;

            private Value(io.helidon.build.archetype.engine.v2.Value<?> value) {
                this.value = value;
            }
        }
    }

    private static final class Reachability {
        private final Map<String, InputDomain> domains;
        private final List<ConstraintSet> constraintSets;

        private Reachability(Map<String, InputDomain> domains, List<ConstraintSet> constraintSets) {
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

        Reachability relativize(Reachability base) {
            if (isFalse() || base.isFalse()) {
                return falseValue(domains);
            }
            Reachability expected = and(base);
            if (expected.isFalse()) {
                return falseValue(domains);
            }
            Reachability candidate = expected;
            boolean changed = true;
            while (changed) {
                changed = false;
                for (int i = 0; i < candidate.constraintSets.size() && !changed; i++) {
                    for (ConstraintSet weakened : candidate.constraintSets.get(i).weakenings(domains)) {
                        Reachability next = candidate.withConstraintSet(i, weakened);
                        if (next.constraintSets.equals(candidate.constraintSets)) {
                            continue;
                        }
                        Reachability projected = base.and(next);
                        if (projected.subtract(expected).isFalse() && expected.subtract(projected).isFalse()) {
                            candidate = next;
                            changed = true;
                            break;
                        }
                    }
                }
            }
            return candidate;
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

        private Reachability withConstraintSet(int index, ConstraintSet replacement) {
            List<ConstraintSet> next = new ArrayList<>(constraintSets);
            next.set(index, replacement);
            return of(next, domains);
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
        private final Map<String, ListConstraint> listConstraints;

        private ConstraintSet() {
            this(Map.of(), Map.of());
        }

        private ConstraintSet(Map<String, ScalarConstraint> scalars, Map<String, ListConstraint> listConstraints) {
            this.scalars = new TreeMap<>(scalars);
            this.listConstraints = new TreeMap<>(listConstraints);
        }

        boolean isTrue() {
            return scalars.isEmpty() && listConstraints.isEmpty();
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
            for (Entry<String, ListConstraint> entry : listConstraints.entrySet()) {
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
            Map<String, ListConstraint> mergedLists = new TreeMap<>(listConstraints);
            for (Entry<String, ListConstraint> entry : other.listConstraints.entrySet()) {
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
            Set<String> listKeys = new HashSet<>(listConstraints.keySet());
            listKeys.addAll(other.listConstraints.keySet());
            for (String key : listKeys) {
                ListConstraint left = listConstraints.getOrDefault(key, ListConstraint.EMPTY);
                ListConstraint right = other.listConstraints.getOrDefault(key, ListConstraint.EMPTY);
                if (!left.required.containsAll(right.required) || !left.forbidden.containsAll(right.forbidden)) {
                    return false;
                }
            }
            return true;
        }

        ConstraintSet tryMerge(ConstraintSet other, Map<String, InputDomain> domains) {
            if (!listConstraints.equals(other.listConstraints)) {
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
            return diffKey == null ? null : new ConstraintSet(mergedScalars, listConstraints).normalized(domains);
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
            for (Entry<String, ListConstraint> entry : listConstraints.entrySet()) {
                expr = expr.and(entry.getValue().toExpression(scope.key(entry.getKey())));
            }
            return expr;
        }

        String literal() {
            return scalars + "|" + listConstraints;
        }

        List<ConstraintSet> weakenings(Map<String, InputDomain> domains) {
            List<ConstraintSet> weakened = new ArrayList<>();
            for (String key : scalars.keySet()) {
                weakened.add(withoutScalar(key, domains));
            }
            for (Entry<String, ListConstraint> entry : listConstraints.entrySet()) {
                String key = entry.getKey();
                for (String value : entry.getValue().required) {
                    weakened.add(withoutListRequired(key, value, domains));
                }
                for (String value : entry.getValue().forbidden) {
                    weakened.add(withoutListForbidden(key, value, domains));
                }
            }
            return weakened;
        }

        private Split splitAgainst(ConstraintSet other, Map<String, InputDomain> domains) {
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
            for (Entry<String, ListConstraint> entry : other.listConstraints.entrySet()) {
                String key = entry.getKey();
                ListConstraint left = listConstraints.getOrDefault(key, ListConstraint.EMPTY);
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

        private ConstraintSet withScalar(String key, Set<String> allowed, InputDomain domain, Map<String, InputDomain> domains) {
            Map<String, ScalarConstraint> next = new TreeMap<>(scalars);
            if (allowed.equals(domain.scalarValues)) {
                next.remove(key);
            } else {
                next.put(key, new ScalarConstraint(allowed));
            }
            return new ConstraintSet(next, listConstraints).normalized(domains);
        }

        private ConstraintSet withoutScalar(String key, Map<String, InputDomain> domains) {
            Map<String, ScalarConstraint> next = new TreeMap<>(scalars);
            next.remove(key);
            return new ConstraintSet(next, listConstraints).normalized(domains);
        }

        private ConstraintSet withListRequired(String key, String value, Map<String, InputDomain> domains) {
            Map<String, ListConstraint> next = new TreeMap<>(listConstraints);
            ListConstraint constraint = next.getOrDefault(key, ListConstraint.EMPTY);
            next.put(key, constraint.withRequired(value));
            return new ConstraintSet(scalars, next).normalized(domains);
        }

        private ConstraintSet withListForbidden(String key, String value, Map<String, InputDomain> domains) {
            Map<String, ListConstraint> next = new TreeMap<>(listConstraints);
            ListConstraint constraint = next.getOrDefault(key, ListConstraint.EMPTY);
            next.put(key, constraint.withForbidden(value));
            return new ConstraintSet(scalars, next).normalized(domains);
        }

        private ConstraintSet withoutListRequired(String key, String value, Map<String, InputDomain> domains) {
            Map<String, ListConstraint> next = new TreeMap<>(listConstraints);
            ListConstraint constraint = next.getOrDefault(key, ListConstraint.EMPTY);
            next.put(key, constraint.withoutRequired(value));
            return new ConstraintSet(scalars, next).normalized(domains);
        }

        private ConstraintSet withoutListForbidden(String key, String value, Map<String, InputDomain> domains) {
            Map<String, ListConstraint> next = new TreeMap<>(listConstraints);
            ListConstraint constraint = next.getOrDefault(key, ListConstraint.EMPTY);
            next.put(key, constraint.withoutForbidden(value));
            return new ConstraintSet(scalars, next).normalized(domains);
        }

        private Set<String> allowed(String key, InputDomain domain) {
            ScalarConstraint constraint = scalars.get(key);
            return constraint == null ? domain.scalarValues : constraint.allowed;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ConstraintSet)) {
                return false;
            }
            ConstraintSet other = (ConstraintSet) o;
            return scalars.equals(other.scalars) && listConstraints.equals(other.listConstraints);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scalars, listConstraints);
        }
    }

    private static final class ScalarConstraint {
        private final Set<String> allowed;

        private ScalarConstraint(Set<String> allowed) {
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

        private ListConstraint(Set<String> required, Set<String> forbidden) {
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

        ListConstraint withoutRequired(String value) {
            Set<String> next = new TreeSet<>(required);
            next.remove(value);
            return new ListConstraint(next, forbidden);
        }

        ListConstraint withoutForbidden(String value) {
            Set<String> next = new TreeSet<>(forbidden);
            next.remove(value);
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

        private Split(ConstraintSet outside, ConstraintSet overlap) {
            this.outside = outside;
            this.overlap = overlap;
        }
    }

    private static final class FileObject implements Comparable<FileObject> {
        private final String checksum;
        private final List<FileOp> ops;
        private final Expression expression;

        FileObject(String checksum, List<FileOp> ops, Expression expression) {
            this.checksum = checksum;
            this.ops = ops;
            this.expression = expression.reduce();
        }

        @Override
        public int compareTo(FileObject o) {
            int r = expression.compareTo(o.expression);
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
                   && Objects.equals(expression, other.expression);
        }

        @Override
        public int hashCode() {
            return Objects.hash(checksum, ops, expression);
        }
    }

    private static final class FileOps implements Comparable<FileOps> {
        private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{[^}]+}");
        private final Reachability reachability;
        private final List<FileOp> ops;

        FileOps(List<FileOp> ops, Reachability reachability) {
            this.reachability = reachability;
            this.ops = ops;
        }

        @Override
        public int compareTo(FileOps o) {
            int r = reachability.literal().compareTo(o.reachability.literal());
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
            return Objects.equals(reachability, other.reachability)
                   && Objects.equals(ops, other.ops);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reachability, ops);
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
            Reachability reachability = first.reachability;
            for (FileOps e : list) {
                ops.addAll(e.ops);
            }
            for (int i = 1; i < list.size(); i++) {
                reachability = reachability.and(list.get(i).reachability);
            }
            return new FileOps(ops, reachability);
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
