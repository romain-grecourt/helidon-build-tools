package io.helidon.build.cli.harness;

import java.util.Collection;
import java.util.Objects;

/**
 * Command model.
 */
public abstract class CommandModel extends CommandParameters {

    static FlagInfo HELP_OPTION = new FlagInfo("help", "Display help information", false);
    static FlagInfo VERBOSE_OPTION = new FlagInfo("verbose", "Produce verbose output", false);
    static FlagInfo DEBUG_OPTION = new FlagInfo("debug", "Produce debug output", false);

    private final CommandInfo commandInfo;

    protected CommandModel(CommandInfo commandInfo) {
        this.commandInfo = Objects.requireNonNull(commandInfo, "commandInfo is null");
        // built-in options
        addParameter(HELP_OPTION);
        addParameter(VERBOSE_OPTION);
    }

    /**
     * Indicate if the command is visible.
     * @return {@code true} if visible, {@code false} if not visible.
     */
    boolean visible() {
        return true;
    }

    /**
     * Get the command for this model.
     *
     * @return {@link Command}, never {@code null}
     */
    public final CommandInfo command() {
        return commandInfo;
    }

    /**
     * Create a {@link CommandExecution} for this model.
     *
     * @param parser command parser
     * @return new {@link CommandExecution} instance
     */
    public abstract CommandExecution createExecution(CommandParser parser);

    /**
     * Meta model for the {@link Command} annotation.
     */
    public static final class CommandInfo {

        private final String name;
        private final String description;

        /**
         * Create a new command info.
         *
         * @param name command name.
         * @param description command description
         */
        public CommandInfo(String name, String description) {
            this.name = Objects.requireNonNull(name, "name is null");
            this.description = Objects.requireNonNull(description, "description is null");
        }

        /**
         * The command name.
         *
         * @return command name, never {@code null}
         */
        public String name() {
            return name;
        }

        /**
         * The command description.
         *
         * @return command description, never {@code null}
         */
        public String description() {
            return description;
        }
    }

    /**
     * Common meta-model for {@link Argument} and {@link Option}.
     * @param <T> mapped type
     */
    public static class OptionInfo<T> implements ParameterInfo<T> {

        protected final Class<T> type;
        protected final String description;

        protected OptionInfo(Class<T> type, String description) {
            this.type = type;
            this.description = Objects.requireNonNull(description, "description is null");
        }

        /**
         * The attribute description.
         *
         * @return option description, never {@code null}
         */
        public final String description() {
            return description;
        }

        @Override
        public final Class<T> type() {
            return type;
        }
    }

    /**
     * Meta model for the {@link Option.Argument} annotation.
     * @param <T> mapped type
     */
    public static final class ArgumentInfo<T> extends OptionInfo<T> {

        private final boolean required;

        /**
         * Create a new argument info.
         *
         * @param type argument field type
         * @param description argument description
         * @param required argument required flag
         */
        public ArgumentInfo(Class<T> type, String description, boolean required) {
            super(type, description);
            this.required = required;
        }

        /**
         * The attribute required flag.
         *
         * @return required flag
         */
        public boolean required() {
            return required;
        }
    }

    /**
     * Meta model for the {@link Option} annotation.
     * @param <T> mapped type
     */
    public static abstract class NamedOptionInfo<T> extends OptionInfo<T> {

        private final String name;
        private final boolean visible;

        protected NamedOptionInfo(Class<T> type, String name, String description, boolean visible) {
            super(type, description);
            this.name = Objects.requireNonNull(name, "name is null");
            this.visible = visible;
        }

        protected NamedOptionInfo(Class<T> type, String name, String description) {
            this(type, name, description, /* visible */ true);
        }

        /**
         * The option name.
         *
         * @return option name, never {@code null}
         */
        public String name() {
            return name;
        }

        @Override
        public boolean visible() {
            return visible;
        }
    }

    /**
     * Meta model for repeatable {@link Option.Flag} annotation.
     */
    public static final class FlagInfo extends NamedOptionInfo<Boolean> {

        /**
         * Create a new flag info.
         * @param name flag name
         * @param description flag description
         * @param visible flag visible, {@code false} to make it hidden
         */
        FlagInfo(String name, String description, boolean visible) {
            super(Boolean.class, name, description, visible);
        }

        /**
         * Create a new flag info.
         * @param name option name
         * @param description option description
         */
        public FlagInfo(String name, String description) {
            this(name, description, /* visible */ true);
        }
    }

    /**
     * Meta model for the {@link Option.KeyValue} annotation.
     * @param <T> item type
     */
    public static final class KeyValueInfo<T> extends NamedOptionInfo<T> {

        private final T defaultValue;

        /**
         * Create a new key value info.
         * @param type option type
         * @param name option name
         * @param description option description
         * @param defaultValue default value, may be {@code null} if the option is not required
         */
        public KeyValueInfo(Class<T> type, String name, String description, T defaultValue) {
            super(type, name, description);
            this.defaultValue = defaultValue;
        }

        /**
         * The default value for the option, if the option is not required.
         *
         * @return default value or {@code null} if the option is required
         */
        public T defaultValue() {
            return defaultValue;
        }
    }

    /**
     * Meta model for the {@link Option.KeyValues} annotation.
     * @param <T> item type
     */
    public static final class KeyValuesInfo<T> extends NamedOptionInfo<Collection<T>> {

        private final Class<T> paramType;
        private final boolean required;

        /**
         * Create a new key values info.
         * @param paramType option field type parameter type
         * @param name option name
         * @param description option description
         * @param required option required
         */
        public KeyValuesInfo(Class<T> paramType, String name, String description, boolean required) {
            super(null, name, description);
            this.required = required;
            this.paramType = Objects.requireNonNull(paramType, "paramType is null");
        }

        /**
         * Get the parameter type.
         * @return type, never {@code null}
         */
        public Class<T> paramType() {
            return paramType;
        }

        /**
         * The attribute required flag.
         *
         * @return required flag
         */
        public boolean required() {
            return required;
        }
    }
}
