/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.archetype.engine.v2.Context.ScopeValue;
import io.helidon.build.archetype.engine.v2.Context.ValueKind;
import io.helidon.build.archetype.engine.v2.Node.Kind;
import io.helidon.build.common.InputStreams;
import io.helidon.build.common.Lists;
import io.helidon.build.common.Maps;
import io.helidon.build.common.Permutations;
import io.helidon.build.common.SourcePath;

import static io.helidon.build.common.Checksum.md5;
import static io.helidon.build.common.FileUtils.ensureDirectory;
import static io.helidon.build.common.FileUtils.readAllBytes;
import static java.util.Objects.requireNonNull;

/**
 * Script compiler.
 */
public class ScriptCompiler {

    /**
     * Compiler options.
     */
    public static final class Options {
        private final Set<Feature> features = new HashSet<>();
        private Script.Source source;
        private Path cwd;
        private Path outputDir;

        /**
         * Set the stream source.
         *
         * @param source stream source
         * @return this instance
         */
        public Options source(Script.Source source) {
            this.source = source;
            return this;
        }

        /**
         * Set the stream source.
         *
         * @param path path
         * @return this instance
         */
        public Options source(Path path) {
            this.source = () -> path.toAbsolutePath().normalize();
            return this;
        }

        /**
         * Set the working directory.
         *
         * @param cwd working directory
         * @return this instance
         */
        public Options cwd(Path cwd) {
            this.cwd = cwd.toAbsolutePath().normalize();
            return this;
        }

        /**
         * Set the output directory.
         *
         * @param outputDir output directory
         * @return this instance
         */
        public Options outputDir(Path outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        /**
         * Set the features.
         *
         * @param features features
         * @return this instance
         */
        public Options features(Feature... features) {
            Collections.addAll(this.features, features);
            return this;
        }
    }

    /**
     * Compiler feature.
     */
    public interface Feature {
        /**
         * Run the feature.
         *
         * @param compiler compiler
         */
        void run(ScriptCompiler compiler);

        /**
         * Weight.
         *
         * @return weight
         */
        int weight();
    }

    /**
     * Compiler features.
     */
    public enum Features implements Feature {
        /**
         * Validate scripts.
         */
        VALIDATE(ScriptCompiler::validate, 0),
        /**
         * Remove outputs.
         */
        NO_OUTPUT(null, 1),
        /**
         * Remove transient variables.
         */
        NO_TRANSIENT(null, 1);

        private final Consumer<ScriptCompiler> consumer;
        private final int weight;

        Features(Consumer<ScriptCompiler> consumer, int weight) {
            this.consumer = consumer;
            this.weight = weight;
        }

        @Override
        public void run(ScriptCompiler compiler) {
            if (consumer != null) {
                consumer.accept(compiler);
            }
        }

        @Override
        public int weight() {
            return weight;
        }
    }

    static final String PRESET_UNRESOLVED = "Preset input cannot be resolved";
    static final String PRESET_TYPE_MISMATCH = "Preset type mismatch";
    static final String EXPR_INCOMPATIBLE_OPERATOR = "Expression contains a non compatible operator";
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

    private final Map<Node, Frame> frames = new HashMap<>();
    private final Map<Node, Expression> expressions = new HashMap<>();
    private final Map<String, Set<Node>> refs = new HashMap<>();
    private final Map<Node, Path> workDirs = new HashMap<>();
    private final Map<String, byte[]> blobs = new HashMap<>();
    private final Deque<Map<String, List<FileOps>>> transformations = new ArrayDeque<>();
    private final Set<FileObject> files = new TreeSet<>();
    private final Set<FileObject> templates = new TreeSet<>();
    private final Set<String> errors = new LinkedHashSet<>();
    private final Node sourceNode;
    private final Node targetNode = Nodes.script();
    private final Context ctx = new Context();
    private final List<Feature> features;
    private final Path outputDir;

    /**
     * Create a new instance.
     *
     * @param options options
     */
    public ScriptCompiler(Options options) {
        this.ctx.pushCwd(requireNonNull(options.cwd, "workDir is null"));
        this.outputDir = requireNonNull(options.outputDir, "outputDir is null");
        this.sourceNode = Script.load(requireNonNull(options.source, "dataSource is null"), false);
        this.features = Lists.sorted(options.features, Comparator.comparingInt(Feature::weight));
    }

    /**
     * Compile the given script.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean compile() {
        new Invoker1().invoke(sourceNode); // inline call sites
        new Invoker2().invoke(sourceNode); // capture frames
        for (Feature feature : features) {
            feature.run(this);
        }
        if (errors.isEmpty()) {
            compile0();
            try {
                ensureDirectory(outputDir);

                // write entrypoint
                Path entrypoint = outputDir.resolve("main.xml");
                try (XMLScriptWriter writer = new XMLScriptWriter(Files.newBufferedWriter(entrypoint), true)) {
                    writer.writeScript(targetNode);
                }

                // write blobs
                if (!blobs.isEmpty()) {
                    Path blobsDir = ensureDirectory(outputDir.resolve("blobs"));
                    for (Map.Entry<String, byte[]> entry : blobs.entrySet()) {
                        Files.write(blobsDir.resolve(entry.getKey()), entry.getValue());
                    }
                }
                return true;
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        } else {
            return false;
        }
    }

    /**
     * Get the validation errors.
     *
     * @return errors
     */
    public Collection<String> errors() {
        return errors;
    }

    /**
     * Get the source node.
     *
     * @return Node
     */
    public Node node() {
        return sourceNode;
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
            inputs.computeIfAbsent(frame(node).key, k -> new ArrayList<>()).add(node);
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
                Value.Type valueType = node.kind().valueType();
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
            Frame frame = frame(node);
            Kind expected = kinds.computeIfAbsent(frame.key, k -> node.kind());
            if (expected != node.kind()) {
                errors.add(String.format("%s %s: '%s'",
                        node.location(),
                        INPUT_TYPE_MISMATCH,
                        frame.key));
            }
        }
    }

    private void validateExpressions() {
        for (Node node : sourceNode.traverse(Kind.CONDITION::equals)) {
            Expression expr = node.expression();

            // check operators compatibility
            for (Expression.Token token : expr.tokens()) {
                if (token.isOperator()) {
                    switch (token.operator()) {
                        case AS_INT:
                        case AS_LIST:
                        case AS_STRING:
                        case SIZEOF:
                            errors.add(String.format("%s %s: '%s'",
                                    node.location(),
                                    EXPR_INCOMPATIBLE_OPERATOR,
                                    token.operator().symbol()));
                            break;
                        default:
                    }
                }
            }

            // check evaluation
            try {
                Frame frame = frame(node);
                Map<String, ScopeValue<?>> variables = new HashMap<>();
                List<String> refs = Lists.map(expr.variables(), frame.scope::key);
                for (String ref : refs) {
                    for (ScopeValue<?> value : resolve(node, ref)) {
                        variables.put(ref, value);
                        break;
                    }
                }
                expr.eval(variables::get);
            } catch (Expression.UnresolvedVariableException ex) {
                errors.add(String.format("%s %s: '%s'",
                        node.location(),
                        EXPR_UNRESOLVED_VARIABLE,
                        ex.variable()));
            } catch (RuntimeException ex) {
                errors.add(String.format(
                        "%s %s: '%s'",
                        node.location(),
                        EXPR_EVAL_ERROR,
                        ex.getMessage()));
            }
        }
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
            Frame frame = frame(input);

            // optional no default
            if (input.attribute("optional").asBoolean().orElse(false)
                && input.attribute("default").isEmpty()) {
                switch (input.kind()) {
                    case INPUT_ENUM:
                    case INPUT_TEXT:
                        errors.add(String.format("%s %s: '%s'",
                                input.location(),
                                INPUT_OPTIONAL_NO_DEFAULT,
                                frame.key));
                        break;
                    default:
                }
            }

            if (input.ancestor(Kind.STEP::equals).isEmpty()) {
                // input not in a step
                errors.add(String.format("%s %s: '%s'",
                        input.location(),
                        INPUT_NOT_IN_STEP,
                        frame.key));
            } else {
                // input already declared
                Iterable<Node> actual = input.ancestors(Kind::isInput);
                for (Node n : inputs.getOrDefault(frame.key, List.of())) {
                    // only allow duplicates if the common ancestor is an enum
                    Node firstMatch = Lists.firstMatch(actual, n.ancestors(Kind::isInput));
                    if (firstMatch == null || firstMatch.kind() != Kind.INPUT_ENUM) {
                        errors.add(String.format("%s %s: '%s'",
                                input.location(),
                                INPUT_ALREADY_DECLARED,
                                frame.key));
                        break;
                    }
                }
                inputs.computeIfAbsent(frame.key, k -> new ArrayList<>()).add(input);
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

    private void compile0() {
        sourceNode.visit(new Visitor1());
        sourceNode.visit(new Visitor2());

        // TODO re-implement the Frame tree to model all variations
        //  traverse all variations to resolve all resources (extend Generator?)
        //  index resources by frame expressions to remove duplicates
        //  resolve blob groups (identical files with varying paths)

        Set<String> refs = new TreeSet<>();
        List<Node> resources = new ArrayList<>();
        List<Node> models = new ArrayList<>();

        // process files
        for (Node node : renderFiles(files, "file", Nodes::files)) {
            for (Node n : node.collect(Kind.CONDITION::equals)) {
                refs.addAll(n.expression().variables());
            }
            resources.add(node);
        }

        // process templates
        for (Node node : renderFiles(templates, "template", Nodes::templates)) {
            for (Node n : node.collect(Kind.CONDITION::equals)) {
                refs.addAll(n.expression().variables());
            }
            resources.add(node);
        }

        // process models
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
                Expression expr = expression(node);
                if (expr != Expression.FALSE) {
                    Node copy = node.deepCopy();
                    for (Node n : copy.traverse(Kind.MODEL_VALUE::equals)) {
                        String value = n.value().asString().orElse("");
                        if (value.matches("^\\s+") || value.matches("\\s+$") || value.contains("\n")) {
                            // move text model with leading, trailing whitespaces or newlines to blobs
                            String id = md5(n.location());
                            blobs.put(id, value.getBytes(StandardCharsets.UTF_8));
                            n.attribute("file", "blobs/" + id);
                        } else {
                            String file = n.attribute("file").asString().orElse(null);
                            if (file != null) {
                                Path path = basedir.resolve(file);
                                String checksum = checksum(path);
                                blobs.putIfAbsent(checksum, readAllBytes(path));
                                n.attribute("file", "blobs/" + checksum);
                            } else if (value.isEmpty()) {
                                // force an empty CDATA
                                n.value(Value.EMPTY_STRING);
                            }
                        }
                    }
                    refs.addAll(expr.variables());
                    models.add(copy.wrap(expr));
                }
            }
        }

        // stub refs
        if (!refs.isEmpty()) {
            ensureRefs(targetNode, refs);
        }

        if (!resources.isEmpty()) {
            Node output = Nodes.output();
            for (Node node : resources) {
                output.append(node);
            }
            if (!models.isEmpty()) {
                Node model = Nodes.model();
                for (Node node : models) {
                    model.append(node);
                }
                output.append(model);
            }
            targetNode.append(output);
        }
    }

    private Set<ScopeValue<?>> resolve(Node node, String key) {
        Set<ScopeValue<?>> values = new LinkedHashSet<>();
        for (Node n : node.rewind()) {
            for (Frame f = frames.get(n); f != null; f = f.parent) {
                ScopeValue<?> value = f.values.get(key);
                if (value != null && value.isPresent()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private Frame frame(Node node) {
        Frame frame = frames.get(node);
        if (frame == null) {
            throw new IllegalStateException("Frame not found: " + node);
        }
        return frame;
    }

    private void ensureRefs(Node block, Set<String> refs) {
        // collect variables in the block
        Map<String, Expression> variables = new LinkedHashMap<>();
        Node node = block.lastChild(Kind.VARIABLES::equals).orElse(Node.EMPTY);
        for (Node n : node.traverse(Kind.CONDITION::equals)) {
            variables.put(n.unwrap().attribute("path").getString(), n.expression());
        }

        // process references
        Node parent = null;
        for (String ref : refs) {
            String path = "~" + ref;
            Expression e = expression(ref);
            if (e != Expression.TRUE) {
                Expression e0 = variables.get(path);
                Expression e1 = Expression.not(e).reduce();
                if (!e1.equals(e0)) {
                    if (parent == null) {
                        parent = Nodes.ensureLast(block, Kind.VARIABLES);
                    }
                    parent.append(Nodes.variable(refType(ref), path).wrap(e1));
                    variables.put(path, e1);
                }
            }
        }
    }

    private Value.Type refType(String ref) {
        for (Node n : refs.getOrDefault(ref, Set.of())) {
            Kind kind = n.kind();
            if (kind == Kind.INPUT_OPTION) {
                kind = n.ancestor(Kind::isInput).orElseThrow().kind();
            }
            return kind.valueType();
        }
        return Value.Type.EMPTY;
    }

    private Expression expression(String ref) {
        Expression expr = Expression.TRUE;
        for (Node n : refs.getOrDefault(ref, Set.of())) {
            Expression e = expression(n.parent());
            if (e != Expression.TRUE) {
                expr = expr == Expression.TRUE ? e : expr.or(e);
            } else {
                return Expression.TRUE;
            }
        }
        return expr.reduce();
    }

    private Expression expression(Node node) {
        return expressions.computeIfAbsent(node, this::expression0);
    }

    private Expression expression0(Node node) {
        Expression expr = Expression.TRUE;
        for (Node n = node; n != null; n = n.parent()) {
            switch (n.kind()) {
                case CONDITION:
                    expr = expr.and(n.expression());
                    break;
                case INPUT_BOOLEAN:
                    expr = expr.and(Expression.create(String.format("${%s}", frame(n).key)));
                    break;
                case INPUT_TEXT:
                    expr = expr.and(Expression.create(String.format("${%s} != ''", frame(n).key)));
                    break;
                case INPUT_OPTION:
                    Node input = n.ancestor(Kind::isInput).orElseThrow();
                    switch (input.kind()) {
                        case INPUT_ENUM:
                            expr = expr.and(Expression.create(String.format(
                                    "${%s} == '%s'",
                                    frame(input).key,
                                    n.value().getString())));
                            break;
                        case INPUT_LIST:
                            expr = expr.and(Expression.create(String.format(
                                    "${%s} contains '%s'",
                                    frame(input).key,
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

    private static List<Node> renderFiles(Set<FileObject> files, String prefix, BiFunction<String, List<String>, Node> func) {
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
                transformations.computeIfAbsent(id, k -> new TreeSet<>()).addAll(file.ops);
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

    private static List<List<FileOps>> fileOps(Map<String, List<FileOps>> map, Node node) {
        List<List<FileOps>> ops = new ArrayList<>();
        List<String> ids = node.attribute("transformations").asList().orElse(List.of());
        for (String id : ids) {
            List<FileOps> value = map.get(id);
            if (value != null) {
                ops.add(value);
            }
        }
        return ops;
    }

    private static String checksum(Path path) {
        try {
            return md5(InputStreams.normalizeNewLines(Files.newInputStream(path)));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private final class Invoker1 extends ScriptInvoker {
        private final Deque<Node> callStack = new ArrayDeque<>();
        private final Map<String, Node> methods = new HashMap<>();

        private Invoker1() {
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
                    String name = node.attribute("method").getString();
                    String key = script.path() + ":" + name;
                    Node prototype = methods.computeIfAbsent(key, k -> script.methods().get(name));
                    if (prototype == null) {
                        throw new IllegalStateException("Method not found: " + name);
                    }
                    String hash = md5(node.location());
                    Node method = prototype.deepCopy();
                    method.attribute("name", hash);
                    node.attribute("method", hash);
                    script.methods().put(hash, method);
                    callStack.push(node);
                    break;
                case SOURCE:
                case EXEC:
                    if (node.attribute("url").isPresent()) {
                        // skip url invocation
                        return false;
                    }
                    callStack.push(node);
                    break;
                case CONDITION:
                    return true;
                case FILE:
                case TEMPLATE:
                case FILES:
                case TEMPLATES:
                case OUTPUT:
                    workDirs.put(node, ctx.cwd());
                    break;
                case PRESET_BOOLEAN:
                case PRESET_TEXT:
                case PRESET_LIST:
                case PRESET_ENUM:
                case VARIABLE_BOOLEAN:
                case VARIABLE_TEXT:
                case VARIABLE_LIST:
                case VARIABLE_ENUM:
                    // use local var instead of preset to allow overrides
                    ctx.scope().getOrCreate(node.attribute("path").getString())
                            .value(node.value(), ValueKind.LOCAL_VAR);
                    return true;
                default:
            }
            return super.visit(node);
        }

        @Override
        public void postVisit(Node node) {
            switch (node.kind()) {
                case CALL:
                    node.script().methods().remove(node.attribute("method").getString());
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

    private final class Invoker2 extends ScriptInvoker {
        private Frame frame = new Frame(null, ctx.scope());

        private Invoker2() {
            super(ctx);
        }

        @Override
        public boolean visit(Node node) {
            Kind kind = node.kind();
            switch (kind) {
                case SCRIPT:
                    frames.put(node, frame);
                    break;
                case EXEC:
                    if (node.attribute("url").isPresent()) {
                        // skip url invocation
                        return false;
                    }
                    break;
                case CONDITION:
                    frame = new Frame(frame, ctx.scope());
                    frames.put(node, frame);
                    node.expression(node.expression().reduce());
                    return true;
                case INPUT_TEXT:
                    frame = new Frame(frame, ctx.pushScope(s -> s.getOrCreate(node)));
                    frame.values.put(frame.key, new ScopeValue<>(frame.scope, Value.of("some text"), ValueKind.USER));
                    frames.put(node, frame);
                    refs.computeIfAbsent(frame.key, k -> new HashSet<>()).add(node);
                    break;
                case INPUT_BOOLEAN:
                    frame = new Frame(frame, ctx.pushScope(s -> s.getOrCreate(node)));
                    frames.put(node, frame);
                    List<String> resolved = Lists.map(resolve(node, frame.key), v -> Value.toString(v));
                    if (resolved.isEmpty() || Lists.anyMatch(resolved, v -> !"false".equals(v))) {
                        refs.computeIfAbsent(frame.key, k -> new HashSet<>()).add(node);
                        frame.values.put(frame.key, new ScopeValue<>(frame.scope, Value.TRUE, ValueKind.USER));
                    }
                    break;
                case INPUT_LIST:
                case INPUT_ENUM:
                    frame = new Frame(frame, ctx.pushScope(s -> s.getOrCreate(node)));
                    frames.put(node, frame);
                    break;
                case INPUT_OPTION:
                    frame = new Frame(frame, frame.scope);
                    frames.put(node, frame);
                    Node input = node.ancestor(Kind::isInput).orElseThrow();
                    List<String> rawValues = Lists.map(resolve(input, frame.key), v -> Value.toString(v));
                    String option = node.value().getString();
                    switch (input.kind()) {
                        case INPUT_ENUM:
                            if (rawValues.isEmpty() || Lists.anyMatch(rawValues, option::equalsIgnoreCase)) {
                                refs.computeIfAbsent(frame.key, k -> new HashSet<>()).add(node);
                                frame.values.put(frame.key,
                                        new ScopeValue<>(frame.scope, Value.of(option), ValueKind.USER));
                            }
                            break;
                        case INPUT_LIST:
                            List<List<String>> values = Lists.map(rawValues, v -> Value.parseList(v).get());
                            if (values.isEmpty() || Lists.anyMatch(values, v -> Lists.containsIgnoreCase(v, option))) {
                                refs.computeIfAbsent(frame.key, k -> new HashSet<>()).add(node);
                                frame.values.put(frame.key,
                                        new ScopeValue<>(frame.scope, Value.of(List.of(option)), ValueKind.USER));
                            }
                            break;
                        default:
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
                    String path = node.attribute("path").getString();
                    refs.computeIfAbsent(path, k -> new HashSet<>()).add(node);
                    frame.values.put(path, new ScopeValue<>(frame.scope, Value.typed(node.value(), kind.valueType()),
                            kind.isPreset() ? ValueKind.PRESET : ValueKind.LOCAL_VAR));
                    return true;
                default:
            }
            return super.visit(node);
        }

        @Override
        public void postVisit(Node node) {
            switch (node.kind()) {
                case EXEC:
                    if (node.attribute("url").isPresent()) {
                        // skip url invocation
                        return;
                    }
                    break;
                case CONDITION:
                    if (node.expression() == Expression.TRUE) {
                        node.replace(node.firstChild().orElseThrow());
                    }
                    frame = frame.parent;
                    break;
                case INPUT_OPTION:
                    frame = frame.parent;
                    break;
                case INPUT_BOOLEAN:
                case INPUT_TEXT:
                case INPUT_LIST:
                case INPUT_ENUM:
                    frame = frame.parent;
                    ctx.popScope();
                    break;
                default:
            }
            super.postVisit(node);
        }
    }

    private final class Visitor1 implements Node.Visitor {
        private final Deque<Node> stack = new ArrayDeque<>();
        private final Map<Node, Node> mirrors = new HashMap<>();

        Visitor1() {
            stack.push(targetNode);
            mirrors.put(sourceNode, targetNode);
            mirrors.put(targetNode, sourceNode);
        }

        @Override
        public boolean visit(Node node) {
            switch (node.kind()) {
                case PRESET_BOOLEAN:
                case PRESET_ENUM:
                case PRESET_LIST:
                case PRESET_TEXT:
                    Node preset = process(node, (b, n) -> Nodes.ensureLast(b, Kind.PRESETS).append(n));
                    preset.attribute("path", v -> v.asString().map(s -> "~" + Context.Key.normalize(s)));
                    stack.push(preset);
                    break;
                case VARIABLE_BOOLEAN:
                case VARIABLE_ENUM:
                case VARIABLE_LIST:
                case VARIABLE_TEXT:
                    if (!features.contains(Features.NO_TRANSIENT)
                        || !node.attribute("transient").asBoolean().orElse(false)) {

                        Node variable = process(node, (b, n) -> Nodes.ensureLast(b, Kind.VARIABLES).append(n));
                        variable.attribute("path", v -> v.asString().map(s -> "~" + Context.Key.normalize(s)));
                        stack.push(variable);
                    }
                    break;
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                case INPUT_TEXT:
                case INPUT_LIST:

                    // flatten if enclosing input is effectively global
                    // I.e., input is global or enclosing input is global
                    boolean global = node.attribute("global").asBoolean().orElse(false);
                    boolean discrete = global || node.ancestor(Kind::isInput)
                            .map(n -> n.attribute("global").asBoolean().orElse(false))
                            .orElse(false);

                    // process steps lazily
                    Node block = node.ancestor(Kind::isBlock).orElseThrow();
                    if (block.kind() == Kind.STEP || discrete) {
                        Node step = node.ancestor(Kind.STEP::equals).orElseThrow();
                        Node step0 = mirrors.get(step);
                        if (step0 == null) {
                            if (discrete && targetNode != stack.peek()) {
                                // discrete step
                                stack.push(targetNode);
                            }
                            step0 = process(step, Node::append);
                            stack.push(step0);
                        } else if (discrete && step0 != stack.peek()) {
                            // discrete input
                            stack.push(step0);
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
            Node node0 = mirrors.get(node);
            if (node0 != null) {
                while (!stack.isEmpty()) {
                    Node n = stack.pop();
                    if (node0 == n) {
                        break;
                    }
                }
            }
        }

        Node process(Node node, BiConsumer<Node, Node> appender) {
            Node block0 = stack.getFirst();
            Node block = mirrors.get(block0);

            Expression expr = expression(node.parent()).sub(expression(block));
            ensureRefs(block0, expr.variables());

            // create copy
            Node node0 = node.copy();
            appender.accept(block0, node0.wrap(expr));

            mirrors.put(node, node0);
            mirrors.put(node0, node);
            return node0;
        }
    }

    private final class Visitor2 implements Node.Visitor {

        @Override
        public boolean visit(Node node) {
            switch (node.kind()) {
                case INPUT_OPTION:
                    if (isEnumOption(node)) {
                        transformations.push(new HashMap<>(transformations.getFirst()));
                    }
                    break;
                case SCRIPT:
                    transformations.push(new HashMap<>());
                    break;
                case TRANSFORMATION:
                    List<FileOp> ops = new ArrayList<>();
                    for (Node n : node.traverse(Kind.REPLACE::equals)) {
                        ops.add(new FileOp(
                                n.attribute("regex").getString(),
                                n.attribute("replacement").getString()));
                    }
                    transformations.getFirst().computeIfAbsent(node.attribute("id").getString(),
                            k -> new ArrayList<>()).add(new FileOps(ops, expression(node)));
                    break;
                case FILE:
                    files.add(resolveFile(workDirs.get(node), node, expression(node)));
                    break;
                case TEMPLATE:
                    templates.add(resolveFile(workDirs.get(node), node, expression(node)));
                    break;
                case FILES:
                    files.addAll(resolveFiles(workDirs.get(node), node, transformations.getFirst(), expression(node)));
                    break;
                case TEMPLATES:
                    templates.addAll(resolveFiles(workDirs.get(node), node, transformations.getFirst(), expression(node)));
                    break;
                default:
            }
            return true;
        }

        @Override
        public void postVisit(Node node) {
            if (isEnumOption(node)) {
                transformations.pop();
            }
        }

        boolean isEnumOption(Node node) {
            if (node.kind() == Kind.INPUT_OPTION) {
                return node.ancestor(Kind::isInput)
                        .map(Node::kind)
                        .filter(Kind.INPUT_ENUM::equals)
                        .isPresent();
            }
            return false;
        }

        private FileObject resolveFile(Path basedir, Node node, Expression expression) {
            String source = node.attribute("source").getString();
            String target = node.attribute("target").getString();
            Path path = basedir.resolve(source);
            String checksum = checksum(path);
            blobs.putIfAbsent(checksum, readAllBytes(path));
            List<FileOp> fileOps = List.of(new FileOp(Pattern.quote(checksum), target));
            return new FileObject(checksum, fileOps, expression.and(node.expression()));
        }

        private Set<FileObject> resolveFiles(Path dir, Node node, Map<String, List<FileOps>> opsMap, Expression frameExpr) {
            Set<FileObject> fileObjects = new TreeSet<>();

            // resolve variations of transformations
            List<List<FileOps>> allOps = Lists.filter(Permutations.ofList(fileOps(opsMap, node)), l -> !l.isEmpty());
            Set<FileOps> resolvedOps = new TreeSet<>(Lists.map(allOps, FileOps::combine));

            Map<String, Expression> includes = new HashMap<>();
            Map<String, Expression> excludes = new HashMap<>();
            for (Node n : node.traverse()) {
                switch (n.kind()) {
                    case INCLUDE:
                    case EXCLUDE:
                        Map<String, Expression> map = n.kind() == Kind.INCLUDE ? includes : excludes;
                        map.compute(n.value().getString(), (k, v) -> {
                            Expression expr = v == null ? Expression.TRUE : v;
                            if (n.parent().kind() == Kind.CONDITION) {
                                expr = expr.and(n.parent().expression());
                            }
                            return expr;
                        });
                        break;
                    default:
                }
            }

            Path directory = dir.resolve(node.attribute("directory").getString());
            for (SourcePath file : SourcePath.scan(directory)) {

                // filter manually to collect expressions
                Expression filterExpr = Expression.FALSE;
                for (Expression v : Maps.filterKey(includes, file::matches).values()) {
                    filterExpr = filterExpr.or(v);
                }
                for (Expression v : Maps.filterKey(excludes, file::matches).values()) {
                    filterExpr = filterExpr.and(Expression.not(v));
                }

                Expression blobExpr = frameExpr.and(filterExpr).reduce();
                if (includes.isEmpty() || blobExpr != Expression.FALSE) {
                    String source = file.asString(false);
                    Path path = directory.resolve(source);

                    // record blob
                    String checksum = checksum(path);
                    blobs.putIfAbsent(checksum, readAllBytes(path));

                    if (resolvedOps.isEmpty()) {
                        List<FileOp> fileOps = List.of(new FileOp(Pattern.quote(checksum), source));
                        fileObjects.add(new FileObject(checksum, fileOps, blobExpr));
                    } else {
                        // create a unique file object for each transformation variation
                        for (FileOps e : resolvedOps) {
                            List<FileOp> fileOps = e.resolve(checksum, source);
                            Expression fileExpr = blobExpr.and(e.expression).reduce();
                            fileObjects.add(new FileObject(checksum, fileOps, fileExpr));
                        }
                    }
                }
            }
            return fileObjects;
        }
    }

    private static final class Frame {
        private final Frame parent;
        private final Scope scope;
        private final String key;
        private final Map<String, ScopeValue<?>> values = new HashMap<>();

        Frame(Frame parent, Scope scope) {
            this.parent = parent;
            this.scope = scope;
            this.key = scope.key();
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
        private final Expression expression;
        private final List<FileOp> ops;

        FileOps(List<FileOp> ops, Expression expression) {
            this.expression = expression;
            this.ops = ops;
        }

        @Override
        public int compareTo(FileOps o) {
            int r = expression.compareTo(o.expression);
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
            return Objects.equals(expression, other.expression)
                   && Objects.equals(ops, other.ops);
        }

        @Override
        public int hashCode() {
            return Objects.hash(expression, ops);
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
            Expression expr = Expression.TRUE;
            for (FileOps e : list) {
                ops.addAll(e.ops);
                expr = expr.and(e.expression);
            }
            return new FileOps(ops, expr.reduce());
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
