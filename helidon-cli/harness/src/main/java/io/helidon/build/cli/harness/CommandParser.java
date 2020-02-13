package io.helidon.build.cli.harness;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;

import io.helidon.build.cli.harness.CommandModel.ArgumentInfo;
import io.helidon.build.cli.harness.CommandModel.OptionInfo;
import io.helidon.build.cli.harness.CommandParameters.ParameterInfo;

/**
 * Command parser.
 */
public final class CommandParser {

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
    public static CommandParser create(String[] rawArgs) {
        String error = null;
        String commandName = null;
        Map<String, Parameter> params = new HashMap<>();
        boolean global = true;
        for (int i = 0; i < rawArgs.length; i++) {
            String arg = rawArgs[i];
            if (arg == null || arg.isEmpty()) {
                continue;
            }
            if (arg.charAt(0) == '-') {
                String optionName = arg.substring(1);
                if (!optionName.isEmpty() && optionName.charAt(0) == '-') {
                    optionName = optionName.substring(1);
                }
                if (!Option.NAME_PREDICATE.test(optionName)) {
                    error = "Invalid option name: " + optionName;
                    break;
                }
                Parameter param = params.get(optionName);
                if (i + 1 < rawArgs.length) {
                    // key-value(s)
                    String value = rawArgs[i + 1];
                    if (value.charAt(0) != '-') {
                        if (param == null) {
                            params.put(optionName, new KeyValueParam(optionName, global, value));
                        } else if (param instanceof KeyValueParam) {
                            LinkedList<String> values = new LinkedList<>();
                            values.add(((KeyValueParam) param).value);
                            values.add(value);
                            params.put(optionName, new KeyValuesParam(optionName, global, values));
                        } else if (param instanceof KeyValuesParam) {
                            ((KeyValuesParam) param).values.add(value);
                        } else {
                            error = "Invalid repeating option: " + optionName;
                            break;
                        }
                        continue;
                    }
                }
                // flag
                if (param == null) {
                    params.put(optionName, new FlagParam(optionName, global));
                } else {
                    error = "Invalid repeating option: " + optionName;
                    break;
                }
            } else if (commandName == null) {
                if (!Command.NAME_PREDICATE.test(arg)) {
                    error = "Invalid command name: " + arg;
                    break;
                }
                commandName = arg;
                global = false;
            } else if (params.containsKey("")) {
                error = "Too many arguments";
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

    private <T> T resolveValue(Class<T> type, String rawValue) {
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

    /**
     * Resolve the given parameter.
     * @param <T> parameter type
     * @param param the parameter to resolve
     * @return resolve value for the parameter
     * @throws ParameterResolutionException if an error occurs while resolving the parameter
     */
    public <T> T resolve(ParameterInfo<T> param) throws ParameterResolutionException {
        Class<T> type = param.type();
        if (param instanceof OptionInfo) {
            OptionInfo option = (OptionInfo) param;
            Parameter resolved = params.get(option.name());
            if (resolved == null && option.required()) {
                throw new ParameterResolutionException("Missing required option: " + option.name());
            }
            if (Boolean.class.equals(type)) {
                if (resolved == null) {
                    return (T) null;
                } else if (resolved instanceof FlagParam) {
                    return type.cast(Boolean.TRUE);
                }
            } else if (Option.VALUE_TYPES.contains(type)) {
                if (resolved == null) {
                    return resolveValue(type, null);
                } else if (resolved instanceof KeyValueParam) {
                    return resolveValue(type, ((KeyValueParam) resolved).value);
                }
            } else if (Option.MULTI_TYPES.contains(type)) {
                throw new UnsupportedOperationException("multi values are not supported yet");
            }
            throw new ParameterResolutionException("Invalid option value: " + option.name());
        } else if (param instanceof ArgumentInfo) {
            ArgumentInfo argInfo = (ArgumentInfo) param;
            Parameter resolved = params.get("");
            if (resolved == null && argInfo.required()) {
                throw new ParameterResolutionException("Missing required argument");
            }
            if (Argument.VALUE_TYPES.contains(type)) {
                if (resolved == null) {
                    return (T) null;
                } else if (resolved instanceof ArgumentParam) {
                    return type.cast(((ArgumentParam) resolved).value);
                }
            }
            throw new ParameterResolutionException("Invalid argument value");
        }
        throw new IllegalArgumentException("Unresolveable parameter: " + param);
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
     * Parameter resolution error.
     */
    public static final class ParameterResolutionException extends RuntimeException {

        private ParameterResolutionException(String message) {
            super(message);
        }
    }

    /**
     * Base class for all parsed parameters.
     */
    public static abstract class Parameter {

        final String name;
        final boolean global;

        private Parameter(String name, boolean global) {
            this.name = name;
            this.global = global;
        }
    }

    /**
     * Named option with no explicit value, if present implies {@code true} value, if not present implies {@code false} value.
     */
    public static class FlagParam extends Parameter {

        private FlagParam(String name, boolean global) {
            super(name, global);
        }
    }

    /**
     * Named option with only one value.
     */
    public static class KeyValueParam extends Parameter {

        private final String value;

        private KeyValueParam(String name, boolean global, String value) {
            super(name, global);
            this.value = value;
        }
    }

    /**
     * Named option with one or more values.
     */
    public static class KeyValuesParam extends Parameter {

        private final LinkedList<String> values;

        private KeyValuesParam(String name, boolean global, LinkedList<String> values) {
            super(name, global);
            this.values = values;
        }
    }

    /**
     * No-name local option with one value.
     */
    public static class ArgumentParam extends Parameter {

        private final String value;

        private ArgumentParam(String value) {
            super("", /* global */ false);
            this.value = value;
        }
    }
}
