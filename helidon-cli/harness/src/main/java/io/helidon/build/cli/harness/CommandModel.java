package io.helidon.build.cli.harness;

import java.util.Collection;
import java.util.Objects;

/**
 * Command model.
 */
public abstract class CommandModel extends CommandParameters {

    static OptionInfo<Boolean> HELP_OPTION = new OptionInfo<>(Boolean.class, "help", "Display help information", false, false);
    static OptionInfo<Boolean> VERBOSE_OPTION = new OptionInfo<>(Boolean.class, "verbose", "Produce verbose output", false, false);
    static OptionInfo<Boolean> DEBUG_OPTION = new OptionInfo<>(Boolean.class, "debug", "Produce debug output", false, false);

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
    public static class AttributeInfo<T> implements ParameterInfo<T> {

        protected final Class<T> type;
        protected final String description;
        protected final boolean required;

        protected AttributeInfo(Class<T> type, String description, boolean required) {
            this.type = type;
            this.description = Objects.requireNonNull(description, "description is null");
            this.required = required;
        }

        /**
         * The attribute description.
         *
         * @return option description, never {@code null}
         */
        public final String description() {
            return description;
        }

        /**
         * The attribute required flag.
         *
         * @return required flag
         */
        public final boolean required() {
            return required;
        }

        @Override
        public final Class<T> type() {
            return type;
        }
    }

    /**
     * Meta model for the {@link Argument} annotation.
     * @param <T> mapped type
     */
    public static final class ArgumentInfo<T> extends AttributeInfo<T> {

        /**
         * Create a new argument info.
         *
         * @param type argument field type
         * @param description argument description
         * @param required argument required flag
         */
        public ArgumentInfo(Class<T> type, String description, boolean required) {
            super(type, description, required);
        }
    }

    /**
     * Meta model for the {@link Option} annotation.
     * @param <T> mapped type
     */
    public static class OptionInfo<T> extends AttributeInfo<T> {

        private final String name;
        private final boolean visible;

        /**
         * Create a new option info.
         *
         * @param type option field type
         * @param name option name
         * @param description option description
         * @param required option required flag
         */
        OptionInfo(Class<T> type, String name, String description, boolean required, boolean visible) {
            super(type, description, required);
            this.name = Objects.requireNonNull(name, "name is null");
            this.visible = visible;
        }

        /**
         * Create a new option info.
         *
         * @param type option field type
         * @param name option name
         * @param description option description
         * @param required option required flag
         */
        public OptionInfo(Class<T> type, String name, String description, boolean required) {
            this(type, name, description, required, /* visible */ true);
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
     * Meta model for repeatable {@link Option} value annotation.
     * @param <T> item type
     */
    public static final class RepeatableOptionInfo<T> extends OptionInfo<Collection<T>> {

        private final Class<T> paramType;

        /**
         * Create a new Repeatable option info.
         * @param paramType option field type parameter type
         * @param name option name
         * @param description option description
         * @param required option required flag
         */
        public RepeatableOptionInfo(Class<T> paramType, String name, String description, boolean required) {
            super(null, name, description, required);
            this.paramType = Objects.requireNonNull(paramType, "paramType is null");
        }

        /**
         * Get the parameter type.
         * @return type, never {@code null}
         */
        public Class<T> paramType() {
            return paramType;
        }
    }
}
