package io.helidon.build.cli.harness;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.build.cli.harness.CommandModel.ArgumentInfo;
import io.helidon.build.cli.harness.CommandModel.OptionInfo;
import io.helidon.build.cli.harness.CommandModel.RepeatableOptionInfo;
import io.helidon.build.cli.harness.CommandParameters.ParameterInfo;

/**
 * Command parser.
 */
public final class CommandParser {

    private static final List<String> GLOBAL_OPTIONS = List.of(
            "--" + CommandModel.HELP_OPTION.name(),
            "--" + CommandModel.VERBOSE_OPTION.name(),
            "--" + CommandModel.DEBUG_OPTION.name());

    static final String TOO_MANY_ARGUMENTS = "Too many arguments";
    static final String INVALID_REPEATING_OPTION = "Invalid repeating option";
    static final String INVALID_COMMAND_NAME = "Invalid command name";
    static final String INVALID_OPTION_NAME = "Invalid option name";
    static final String MISSING_REQUIRED_ARGUMENT = "Missing required argument";
    static final String INVALID_ARGUMENT_VALUE = "Invalid argument value";
    static final String INVALID_OPTION_VALUE = "Invalid option value";
    static final String UNREPEATABLE_OPTION = "Option cannot be repeated";
    static final String MISSING_REQUIRED_OPTION = "Missing required option";

    private final String[] rawArgs;
    private final Map<String, Parameter> params;
    private final String error;
    private final String commandName;

    private CommandParser(String[] rawArgs, String commandName, Map<String, Parameter> params, String error) {
        this.rawArgs = rawArgs;
        this.commandName = commandName;
        this.params = params;
        this.error = error;
    }

    /**
     * Parse the command line arguments.
     * @param rawArgs arguments to parse
     * @return parser
     */
    public static CommandParser create(String ... rawArgs) {
        Objects.requireNonNull(rawArgs, "rawArgs is null");
        String error = null;
        String commandName = null;
        Map<String, Parameter> params = new HashMap<>();
        for (int i = 0; i < rawArgs.length; i++) {
            String arg = rawArgs[i];
            if (arg == null || arg.isEmpty()) {
                continue;
            }
            arg = arg.trim().toLowerCase();
            if (!GLOBAL_OPTIONS.contains(arg) && commandName == null) {
                if (!Command.NAME_PREDICATE.test(arg)) {
                    error = INVALID_COMMAND_NAME + ": " + arg;
                    break;
                }
                commandName = arg;
            } else if (arg.length() > 2 && arg.charAt(0) == '-' && arg.charAt(1) == '-') {
                String optionName = arg.substring(2);
                if (!Option.NAME_PREDICATE.test(optionName)) {
                    error = INVALID_OPTION_NAME + ": " + optionName;
                    break;
                }
                Parameter param = params.get(optionName);
                if (i + 1 < rawArgs.length) {
                    // key-value(s)
                    String value = rawArgs[i + 1].trim().toLowerCase();
                    if (value.charAt(0) != '-') {
                        String[] splitValues = value.split(",");
                        if (param == null && splitValues.length == 1) {
                            params.put(optionName, new KeyValueParam(optionName, value));
                        } else if (param == null) {
                            LinkedList<String> values = new LinkedList<>();
                            for (String splitValue : splitValues) {
                                values.add(splitValue);
                            }
                            params.put(optionName, new KeyValuesParam(optionName, values));
                        } else if (param instanceof KeyValueParam) {
                            LinkedList<String> values = new LinkedList<>();
                            values.add(((KeyValueParam) param).value);
                            values.add(value);
                            params.put(optionName, new KeyValuesParam(optionName, values));
                        } else if (param instanceof KeyValuesParam) {
                            for (String splitValue : splitValues) {
                                ((KeyValuesParam) param).values.add(splitValue);
                            }
                        } else {
                            error = INVALID_REPEATING_OPTION + ": " + optionName;
                            break;
                        }
                        i++;
                        continue;
                    }
                }
                // flag
                if (param == null) {
                    params.put(optionName, new FlagParam(optionName));
                } else {
                    error = INVALID_REPEATING_OPTION + ": " + optionName;
                    break;
                }
            } else if (params.containsKey("")) {
                error = TOO_MANY_ARGUMENTS;
                break;
            } else {
                params.put("", new ArgumentParam(arg));
            }
        }
        return new CommandParser(rawArgs, commandName, params, error);
    }

    /**
     * Get the first parsing error if any.
     * @return parsing error, or {@code null} if there is no error.
     */
    public Optional<String> error() {
        return Optional.ofNullable(error);
    }

    /**
     * Get the parsed command name.
     * @return command name
     */
    public Optional<String> commandName() {
        return Optional.ofNullable(commandName);
    }

    /**
     * Get the parsed parameters.
     * @return map of parameter
     */
    Map<String, Parameter> params() {
        return params;
    }

    private <T> T resolveValue(Class<T> type, String rawValue) {
        Objects.requireNonNull(rawValue, "rawValue is null");
        if (String.class.equals(type)) {
            return type.cast(rawValue);
        }
        if (Integer.class.equals(type)) {
            return type.cast(Integer.parseInt(rawValue));
        }
        if (File.class.equals(type)) {
            return type.cast(new File(rawValue));
        }
        throw new IllegalArgumentException("Invalid value type: " + type);
    }

    private <T> T resolveDefaultValue(Class<T> type) {
        if (!type.isPrimitive()) {
            return type.cast(null);
        }
        if (Integer.class.equals(type)) {
            return type.cast((Integer) 0);
        }
        if (Boolean.class.equals(type)) {
            return type.cast(Boolean.FALSE);
        }
        throw new IllegalArgumentException("Invalid value type: " + type);
    }

    /**
     * Resolve the given option parameter.
     * @param <T> option type
     * @param optionInfo the option to resolve
     * @return resolved value for the option
     * @throws CommandParserException if an error occurs while resolving the parameter
     */
    public <T> T resolve(OptionInfo<T> optionInfo) throws CommandParserException {
        Class<T> type = optionInfo.type();
        Parameter resolved = params.get(optionInfo.name());
        if (resolved == null && optionInfo.required()) {
            throw new CommandParserException(MISSING_REQUIRED_OPTION + ": " + optionInfo.name());
        }
        if (Boolean.class.equals(type)) {
            if (resolved == null) {
                return type.cast(Boolean.FALSE);
            } else if (resolved instanceof FlagParam) {
                return type.cast(Boolean.TRUE);
            }
        } else if (Option.VALUE_TYPES.contains(type)) {
            if (resolved == null) {
                return (T) resolveDefaultValue(type);
            } else if (resolved instanceof KeyValueParam) {
                return resolveValue(type, ((KeyValueParam) resolved).value);
            } else if (resolved instanceof KeyValuesParam) {
                throw new CommandParserException(UNREPEATABLE_OPTION + ": " + optionInfo.name());
            }
        }
        throw new CommandParserException(INVALID_OPTION_VALUE + ": " + optionInfo.name());
    }

    /**
     * Resolve the given repeatable option parameter.
     * @param <T> repeated parameter type
     * @param optionInfo the option to resolve
     * @return collection of resolved values for the option
     * @throws CommandParserException if an error occurs while resolving the parameter
     */
    public <T> Collection<T> resolve(RepeatableOptionInfo<T> optionInfo) throws CommandParserException {
        Class<T> type = optionInfo.paramType();
        Parameter resolved = params.get(optionInfo.name());
        if (resolved == null && optionInfo.required()) {
            throw new CommandParserException(MISSING_REQUIRED_OPTION + ": " + optionInfo.name());
        }
        if (Option.VALUE_TYPES.contains(type)) {
            if (resolved == null) {
                return List.of();
            } else if (resolved instanceof KeyValueParam) {
                return List.of(resolveValue(type, ((KeyValueParam) resolved).value));
            } else if (resolved instanceof KeyValuesParam) {
                LinkedList<T> resolvedValues = new LinkedList<>();
                for (String value : ((KeyValuesParam) resolved).values) {
                    resolvedValues.add(resolveValue(type, value));
                }
                return resolvedValues;
            }
        }
        throw new CommandParserException(INVALID_OPTION_VALUE + ": " + optionInfo.name());
    }

    /**
     * Resolve the given argument parameter.
     * @param <T> argument type
     * @param argInfo the argument to resolve
     * @return resolved value for the argument
     * @throws CommandParserException if an error occurs while resolving the parameter
     */
    @SuppressWarnings("unchecked")
    public <T> T resolve(ArgumentInfo<T> argInfo) throws CommandParserException {
        Class<T> type = argInfo.type();
        Parameter resolved = params.get("");
        if (resolved == null && argInfo.required()) {
            throw new CommandParserException(MISSING_REQUIRED_ARGUMENT);
        }
        if (Argument.VALUE_TYPES.contains(type)) {
            if (resolved == null) {
                return (T) resolveDefaultValue(type);
            } else if (resolved instanceof ArgumentParam) {
                return type.cast(((ArgumentParam) resolved).value);
            }
        }
        throw new CommandParserException(INVALID_ARGUMENT_VALUE);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 73 * hash + Arrays.deepHashCode(this.rawArgs);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CommandParser other = (CommandParser) obj;
        return Arrays.deepEquals(this.rawArgs, other.rawArgs);
    }

    /**
     * Parser error.
     */
    public static final class CommandParserException extends RuntimeException {

        private CommandParserException(String message) {
            super(message);
        }
    }

    /**
     * Base class for all parsed parameters.
     */
    public static abstract class Parameter {

        final String name;

        private Parameter(String name) {
            this.name = name;
        }
    }

    /**
     * Named option with no explicit value, if present implies {@code true} value, if not present implies {@code false} value.
     */
    public static class FlagParam extends Parameter {

        private FlagParam(String name) {
            super(name);
        }
    }

    /**
     * Named option with only one value.
     */
    public static class KeyValueParam extends Parameter {

        final String value;

        private KeyValueParam(String name, String value) {
            super(name);
            this.value = value;
        }
    }

    /**
     * Named option with one or more values.
     */
    public static class KeyValuesParam extends Parameter {

        final LinkedList<String> values;

        private KeyValuesParam(String name, LinkedList<String> values) {
            super(name);
            this.values = values;
        }
    }

    /**
     * No-name local option with one value.
     */
    public static class ArgumentParam extends Parameter {

        final String value;

        private ArgumentParam(String value) {
            super("");
            this.value = value;
        }
    }
}
