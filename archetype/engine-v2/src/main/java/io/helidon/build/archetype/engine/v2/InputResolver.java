/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.helidon.build.archetype.engine.v2.Context.ValueKind;
import io.helidon.build.archetype.engine.v2.Node.Kind;
import io.helidon.build.common.Lists;
import io.helidon.build.common.logging.Log;
import io.helidon.build.common.logging.LogLevel;

import static io.helidon.build.common.Strings.padding;
import static io.helidon.build.common.ansi.AnsiTextStyles.Bold;
import static io.helidon.build.common.ansi.AnsiTextStyles.BoldBlue;
import static io.helidon.build.common.ansi.AnsiTextStyles.BoldRed;
import static java.util.Collections.emptyList;

/**
 * Input resolver.
 * Provides input resolution and controls the traversal of input nodes.
 */
public abstract class InputResolver implements Node.Visitor {

    private static final Value<String> EMPTY_LIST = Value.dynamic("none");
    private static final Value<String> FALSE = Value.dynamic("false");

    private final Map<String, List<Pattern>> validations = new HashMap<>();
    private final Deque<Node> parents = new ArrayDeque<>();
    private final Deque<Node> currentSteps = new ArrayDeque<>();
    private final Set<Node> visitedSteps = new HashSet<>();
    private final Context context;
    private List<Pattern> patterns;

    /**
     * Create a new instance.
     *
     * @param context context
     */
    protected InputResolver(Context context) {
        this.context = context;
    }

    /**
     * Visit an input value.
     *
     * @param node  node
     * @param value value
     * @return {@code true} if traversing, {@code false} otherwise
     */
    public static boolean visitValue(Node node, Value<?> value) {
        switch (node.kind()) {
            case INPUT_BOOLEAN:
                return value.asBoolean().orElse(false);
            case INPUT_LIST:
                return !value.asList().orElse(List.of()).isEmpty();
            default:
                return true;
        }
    }

    /**
     * Get the option nodes of an option input.
     *
     * @param node      node
     * @param predicate predicate
     * @return nodes
     */
    public static List<Node> optionNodes(Node node, Predicate<Node> predicate) {
        return node.children().stream()
                .filter(predicate)
                .map(Node::unwrap)
                .filter(n -> n.kind() == Kind.INPUT_OPTION)
                .collect(Collectors.toList());
    }

    /**
     * Get the index.
     *
     * @param name    option name
     * @param options the options to process
     * @return index
     */
    public static int optionIndex(String name, List<Node> options) {
        if (name != null) {
            for (ListIterator<Node> it = options.listIterator(); it.hasNext();) {
                if (it.next().value().getString().equalsIgnoreCase(name)) {
                    return it.previousIndex();
                }
            }
        }
        return -1;
    }

    @Override
    public final boolean visit(Node node) {
        switch (node.kind()) {
            case STEP:
                currentSteps.push(node);
                break;
            case INPUT_BOOLEAN:
                return visitScoped(node, this::visitBoolean);
            case INPUT_TEXT:
                return visitScoped(node, this::visitText);
            case INPUT_LIST:
                return visitScoped(node, this::visitList);
            case INPUT_ENUM:
                return visitScoped(node, this::visitEnum);
            case INPUT_OPTION:
                return visitOption(node);
            case VALIDATION:
                patterns = new ArrayList<>();
                validations.put(node.attribute("id").getString(), patterns);
                break;
            case REGEX:
                patterns.add(Pattern.compile(node.value().getString()));
                break;
            default:
        }
        return true;
    }

    @Override
    public final void postVisit(Node node) {
        switch (node.kind()) {
            case STEP:
                currentSteps.pop();
                break;
            case INPUT_TEXT:
            case INPUT_BOOLEAN:
            case INPUT_ENUM:
            case INPUT_LIST:
                parents.pop();
                context.popScope();
                break;
            default:
        }
    }

    /**
     * Visit a {@link Kind#INPUT_BOOLEAN} node.
     *
     * @param node node
     * @param scope scope
     * @return visit result
     */
    protected boolean visitBoolean(Node node, Context.Scope scope) {
        return visitInput(node, scope);
    }

    /**
     * Visit a {@link Kind#INPUT_TEXT} node.
     *
     * @param node node
     * @param scope scope
     * @return visit result
     */
    protected boolean visitText(Node node, Context.Scope scope) {
        return visitInput(node, scope);
    }

    /**
     * Visit a {@link Kind#INPUT_LIST} node.
     *
     * @param node node
     * @param scope scope
     * @return visit result
     */
    protected boolean visitList(Node node, Context.Scope scope) {
        return visitInput(node, scope);
    }

    /**
     * Visit a {@link Kind#INPUT_ENUM} node.
     *
     * @param node node
     * @param scope scope
     * @return visit result
     */
    protected boolean visitEnum(Node node, Context.Scope scope) {
        return visitInput(node, scope);
    }

    /**
     * Visit an input node.
     *
     * @param node  node
     * @param scope scope
     * @return visit result
     */
    protected boolean visitInput(Node node, Context.Scope scope) {
        return true;
    }

    /**
     * Invoked for every visited step.
     *
     * @param node node
     */
    protected void onVisitStep(Node node) {
    }

    /**
     * Get the context.
     *
     * @return context
     */
    protected Context context() {
        return context;
    }

    /**
     * Compute the default value for an input.
     *
     * @param node  node
     * @param scope scope
     * @return default value
     */
    protected Value<?> defaultValue(Node node, Context.Scope scope) {
        return context.defaultValue(scope.key())
                .or(() -> node.attribute("default").asString())
                .map(s -> {
                    String value = context.scope().interpolate(s);
                    switch (node.kind()) {
                        case INPUT_LIST:
                        case INPUT_ENUM:
                            return value.toLowerCase();
                        default:
                            return value;
                    }
                })
                .or(() -> {
                    switch (node.kind()) {
                        case INPUT_LIST:
                            return EMPTY_LIST;
                        case INPUT_BOOLEAN:
                            return FALSE;
                        default:
                            return Value.empty();
                    }
                });
    }

    /**
     * Resolve a declared input.
     *
     * @param node  node
     * @param scope scope
     * @return positive if traversing, zero if skipping, negative if not found
     */
    protected int resolveInput(Node node, Context.Scope scope) {
        Node currentStep = currentSteps.peek();
        if (currentStep == null) {
            throw new IllegalStateException(node.location() + " input not nested inside a step");
        }
        if (node.attribute("global").asBoolean().orElse(false)) {
            Node parent = parents.peek();
            if (parent != null && !parent.attribute("global").asBoolean().orElse(false)) {
                throw new IllegalStateException(node.location() + " parent input is not global");
            }
        }
        parents.push(node);
        Value<?> value = existingValue(node, scope);
        if (value == null) {
            if (!visitedSteps.contains(currentStep)) {
                visitedSteps.add(currentStep);
                onVisitStep(currentStep);
            }
            return -1;
        }
        validate(node, value, scope.key(true));
        return visitValue(node, value) ? 1 : 0;
    }

    private Value<?> existingValue(Node node, Context.Scope scope) {
        String id = node.attribute("id").getString();
        boolean model = node.attribute("model").asBoolean().orElse(false);
        Value<?> value = scope.get(id).value();
        if (value.isPresent()) {
            return value;
        }
        List<Node> options;
        switch (node.kind()) {
            case INPUT_LIST:
                // auto create a value for lists without options
                options = optionNodes(node, n -> n.expression().eval(s -> scope.get(s).value()));
                if (options.isEmpty()) {
                    return scope.getOrCreate(id, model).value(Value.of(List.of()), ValueKind.DEFAULT);
                }
                return null;
            case INPUT_ENUM:
                // auto create a value if there is only one option with a default value
                Value<?> defaultValue = defaultValue(node, scope);
                if (defaultValue.isPresent()) {
                    options = optionNodes(node, n -> n.expression().eval(s -> scope.get(s).value()));
                    int defaultIndex = optionIndex(defaultValue.getString(), options);
                    if (options.size() == 1 && defaultIndex >= 0) {
                        return scope.getOrCreate(id, model).value(defaultValue, ValueKind.DEFAULT);
                    }
                }
                return null;
            default:
                return null;
        }
    }

    private boolean visitOption(Node node) {
        if (parents.isEmpty()) {
            throw new IllegalStateException("parents is empty");
        }
        Node parent = parents.peek();
        if (parent != null) {
            Value<?> inputValue = context.scope().value();
            if (inputValue.isPresent()) {
                String option = node.value().getString();
                switch (parent.kind()) {
                    case INPUT_ENUM:
                        return option.equalsIgnoreCase(inputValue.getString());
                    case INPUT_LIST:
                        return Lists.containsIgnoreCase(inputValue.getList(), option);
                    default:
                        throw new IllegalStateException("Unexpected node kind " + parent.kind());
                }
            }
        }
        return false;
    }

    private void validate(Node node, Value<?> value, String path) {
        switch (node.kind()) {
            case INPUT_TEXT:
                String inputValue = value.getString();
                List<String> errors = node.attribute("validations").asList()
                        .stream()
                        .flatMap(Collection::stream)
                        .map(id -> Optional.ofNullable(validations.get(id))
                                .orElseThrow(() -> new IllegalArgumentException("Unresolved validation: " + id)))
                        .flatMap(Collection::stream)
                        .filter(p -> !p.matcher(inputValue).matches())
                        .map(Pattern::pattern)
                        .collect(Collectors.toList());
                if (!errors.isEmpty()) {
                    throw new InputValidationException(inputValue, path, errors);
                }
                break;
            case INPUT_ENUM:
                String optionValue = value.getString();
                if (optionIndex(optionValue, optionNodes(node, n -> true)) == -1) {
                    throw new InvalidInputException(optionValue, path);
                }
                break;
            default:
        }
    }

    private boolean visitScoped(Node node, BiFunction<Node, Context.Scope, Boolean> function) {
        Context.Scope scope = context.scope().getOrCreate(node);
        try {
            return function.apply(node, scope);
        } finally {
            context.pushScope(scope);
        }
    }

    private static boolean parseBoolean(String input) {
        switch (input.trim().toLowerCase()) {
            case "y":
            case "yes":
            case "true":
                return true;
            case "n":
            case "no":
            case "false":
                return false;
            default:
                throw new IllegalArgumentException(input + " is not boolean");
        }
    }

    private static String optionText(Node option, int maxKeyWidth, int index) {
        String value = option.value().getString();
        String name = option.attribute("name").asString().orElse(value);
        String text = BoldBlue.apply(String.format("  (%d) %s ", index + 1, value));
        if (name != null && !name.isBlank()) {
            return String.format("%s%s| %s", text, padding(" ", maxKeyWidth, value), name);
        }
        return text;
    }

    private static int maxKeyWidth(List<String> names) {
        int maxLen = 0;
        for (String name : names) {
            int len = name.length();
            if (len > maxLen) {
                maxLen = len;
            }
        }
        return maxLen;
    }

    private static List<String> optionValues(List<Node> options) {
        return options.stream()
                .map(n -> n.value().getString())
                .collect(Collectors.toList());
    }

    private static String parseEnumResponse(String response, List<Node> options) {
        int index = Integer.parseInt(response.trim());
        return options.get(index - 1).value().getString().toLowerCase();
    }

    private static List<String> parseListResponse(String response, List<Node> options) {
        response = response.trim();
        if ("none".equals(response)) {
            return emptyList();
        }
        return Arrays.stream(response.trim().split("\\s+"))
                .map(Integer::parseInt)
                .distinct()
                .map(i -> options.get(i - 1))
                .map(n -> n.value().getString())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    private static String defaultResponse(List<String> optionNames, List<Node> options) {
        return IntStream.range(0, options.size())
                .boxed()
                .filter(i -> containsIgnoreCase(optionNames, options.get(i).value().getString()))
                .map(i -> (i + 1) + "")
                .collect(Collectors.joining(", "));
    }

    private static boolean containsIgnoreCase(Collection<String> values, String expected) {
        for (String optionName : values) {
            if (optionName.equalsIgnoreCase(expected)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prompter that uses CLI for input/output.
     */
    public static class InteractiveResolver extends InputResolver {
        private static final String ENTER_SELECTION = Bold.apply("Enter selection");
        private static final String ENTER_LIST_SELECTION = ENTER_SELECTION + " (numbers separated by spaces or 'none')";

        private final InputStream in;
        private String lastName;

        /**
         * Create an interactive input resolver.
         *
         * @param context context
         */
        public InteractiveResolver(Context context) {
            this(context, System.in);
        }

        /**
         * Create a new interactive input resolver.
         *
         * @param context context
         * @param in      input stream to use for reading user input
         */
        public InteractiveResolver(Context context, InputStream in) {
            super(context);
            this.in = Objects.requireNonNull(in, "input stream is null");
        }

        @Override
        protected void onVisitStep(Node node) {
            System.out.printf("%n| %s%n%n", node.attribute("name").getString());
        }

        @Override
        protected boolean visitBoolean(Node node, Context.Scope scope) {
            String id = node.attribute("id").getString();
            String name = node.attribute("name").asString().orElse(id);
            boolean model = node.attribute("model").asBoolean().orElse(false);
            int code = resolveInput(node, scope);
            while (code < 0) {
                try {
                    Value<?> defaultValue = defaultValue(node, scope);
                    String defaultText = defaultValue.asBoolean()
                            .map(v -> BoldBlue.apply(v ? "yes" : "no"))
                            .orElse(null);
                    String question = String.format("%s (yes/no)", Bold.apply(name));
                    String response = prompt(question, defaultText);
                    if (response == null || response.trim().isEmpty()) {
                        scope.getOrCreate(id, model)
                                .value(defaultValue, ValueKind.DEFAULT);
                        code = defaultValue.asBoolean().orElse(false) ? 1 : 0;
                        break;
                    }
                    boolean value;
                    try {
                        value = parseBoolean(response);
                    } catch (Exception e) {
                        System.out.println(BoldRed.apply("Invalid response: " + response));
                        if (LogLevel.isDebug()) {
                            Log.debug(e.getMessage());
                        }
                        continue;
                    }
                    scope.getOrCreate(id, model)
                            .value(Value.of(value), ValueKind.USER);
                    code = value ? 1 : 0;
                } catch (IOException e) {
                    throw new UncheckedIOException(e.getMessage(), e);
                }
            }
            return code == 1;
        }

        @Override
        protected boolean visitText(Node node, Context.Scope scope) {
            String id = node.attribute("id").getString();
            String name = node.attribute("name").asString().orElse(id);
            boolean model = node.attribute("model").asBoolean().orElse(false);
            int code = resolveInput(node, scope);
            if (code < 0) {
                try {
                    Value<String> defaultValue = defaultValue(node, scope).asString();
                    String defaultText = defaultValue.map(BoldBlue::apply).orElse(null);
                    String response = prompt(Bold.apply(name), defaultText);
                    ValueKind valueKind;
                    Value<?> value;
                    if (response == null || response.trim().isEmpty()) {
                        value = defaultValue;
                        valueKind = ValueKind.DEFAULT;
                    } else {
                        value = Value.of(response);
                        valueKind = ValueKind.USER;
                    }
                    scope.getOrCreate(id, model).value(value, valueKind);
                    code = 1;
                } catch (IOException e) {
                    throw new UncheckedIOException(e.getMessage(), e);
                }
            }
            return code == 1;
        }

        @Override
        protected boolean visitEnum(Node node, Context.Scope scope) {
            String id = node.attribute("id").getString();
            String name = node.attribute("name").asString().orElse(id);
            boolean model = node.attribute("model").asBoolean().orElse(false);
            int code = resolveInput(node, scope);
            while (code < 0) {
                String response = null;
                try {
                    Value<?> defaultValue = defaultValue(node, scope);
                    List<Node> options = optionNodes(node, n -> n.expression().eval(s -> scope.get(s).value()));
                    int defaultIndex = optionIndex(defaultValue.getString(), options);
                    // skip prompting if there is only one option with a default value
                    if (options.size() == 1 && defaultIndex >= 0) {
                        scope.getOrCreate(id, model).value(defaultValue, ValueKind.DEFAULT);
                        code = 1;
                        break;
                    }

                    printName(name);
                    printOptions(options);

                    String defaultText = defaultIndex != -1
                            ? BoldBlue.apply(String.format("%s", defaultIndex + 1))
                            : null;

                    response = prompt(ENTER_SELECTION, defaultText);
                    lastName = name;
                    Value<?> value;
                    ValueKind valueKind;
                    if ((response == null || response.trim().isEmpty())) {
                        if (defaultIndex < 0) {
                            continue;
                        }
                        value = defaultValue;
                        valueKind = ValueKind.DEFAULT;
                    } else {
                        value = Value.of(parseEnumResponse(response, options));
                        valueKind = ValueKind.USER;
                    }
                    scope.getOrCreate(id, model).value(value, valueKind);
                    code = 1;
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    System.out.println(BoldRed.apply("Invalid response: " + response));
                    if (LogLevel.isDebug()) {
                        Log.debug(e.getMessage());
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e.getMessage(), e);
                }
            }
            return code == 1;
        }

        @Override
        protected boolean visitList(Node node, Context.Scope scope) {
            int code = resolveInput(node, scope);
            while (code < 0) {
                String response = null;
                try {
                    String id = node.attribute("id").getString();
                    lastName = node.attribute("name").asString().orElse(id);
                    List<Node> options = optionNodes(node, n -> n.expression().eval(s -> scope.get(s).value()));
                    Value<?> defaultValue = defaultValue(node, scope);

                    printName(lastName);
                    printOptions(options);

                    response = prompt(ENTER_LIST_SELECTION, defaultValue.asList()
                            .map(l -> defaultResponse(l, options))
                            .map(BoldBlue::apply).orElse("none"));

                    Value<?> value;
                    ValueKind valueKind;
                    if (response == null || response.trim().isEmpty()) {
                        value = defaultValue;
                        valueKind = ValueKind.DEFAULT;
                    } else {
                        value = Value.of(parseListResponse(response, options));
                        valueKind = ValueKind.USER;
                    }
                    scope.get(id).value(value, valueKind);
                    code = 1;
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    System.out.println(BoldRed.apply("Invalid response: " + response));
                    if (LogLevel.isDebug()) {
                        Log.debug(e.getMessage());
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e.getMessage(), e);
                }
            }
            return code == 1;
        }

        private void printName(String name) {
            if (name != null && !name.equals(lastName)) {
                System.out.println(Bold.apply(name));
            }
        }

        private static void printOptions(List<Node> options) {
            int index = 0;
            int maxKeyWidth = maxKeyWidth(optionValues(options));
            for (Node option : options) {
                System.out.println(optionText(option, maxKeyWidth, index));
                index++;
            }
        }

        private String prompt(String prompt, String defaultText) throws IOException {
            String promptText = defaultText != null
                    ? String.format("%s (default: %s): ", prompt, defaultText)
                    : String.format("%s: ", prompt);
            System.out.print(promptText);
            System.out.flush();
            return new BufferedReader(new InputStreamReader(in)).readLine();
        }
    }

    /**
     * Batch input resolver.
     * Only fails if a non-optional input is unresolved, or an optional input cannot be resolved with a default value.
     */
    public static class BatchResolver extends InputResolver {

        /**
         * Create a new instance.
         *
         * @param context context
         */
        public BatchResolver(Context context) {
            super(context);
        }

        @Override
        protected boolean visitInput(Node node, Context.Scope scope) {
            String id = node.attribute("id").getString();
            boolean model = node.attribute("model").asBoolean().orElse(false);
            int code = resolveInput(node, scope);
            if (code < 0) {
                if (node.attribute("optional").asBoolean().orElse(false)) {
                    Value<?> defaultValue = defaultValue(node, scope);
                    if (defaultValue.isPresent()) {
                        scope.getOrCreate(id, model).value(defaultValue, ValueKind.DEFAULT);
                        if (node.kind() == Kind.INPUT_BOOLEAN && !defaultValue.asBoolean().orElse(false)) {
                            code = 0;
                        } else {
                            code = 1;
                        }
                    } else {
                        throw new InputUnresolvedException(scope.key(true));
                    }
                } else {
                    switch (node.kind()) {
                        case INPUT_BOOLEAN:
                        case INPUT_LIST:
                            code = 0;
                            break;
                        default:
                            throw new InputUnresolvedException(scope.key(true));
                    }
                }
            }
            return code == 1;
        }
    }

    /**
     * Base input exception.
     */
    public abstract static class InputException extends RuntimeException {

        private final String path;

        private InputException(String message, String inputPath) {
            super(message);
            this.path = inputPath;
        }

        /**
         * Get the input path.
         *
         * @return The path.
         */
        public String inputPath() {
            return path;
        }
    }

    /**
     * Unresolved input exception.
     */
    public static final class InputUnresolvedException extends InputException {
        InputUnresolvedException(String path) {
            super("Unresolved input: " + path, path);
        }
    }

    /**
     * Input validation exception.
     */
    public static final class InputValidationException extends InputException {
        InputValidationException(String value, String inputPath, List<String> patterns) {
            super(String.format("Invalid input: %s='%s', rules:%n\t%s",
                    inputPath, value, String.join("\n\t", patterns)), inputPath);
        }
    }

    /**
     * Invalid input exception.
     */
    public static final class InvalidInputException extends InputException {
        InvalidInputException(String value, String inputPath) {
            super(String.format("Invalid input: %s='%s'", inputPath, value), inputPath);
        }
    }
}
