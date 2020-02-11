package io.helidon.build.cli.harness;

import java.util.Map;

/**
 * Command model.
 */
public interface CommandModel {

    /**
     * Get the command for this model.
     * @return {@link Command}
     */
    CommandInfo command();

    /**
     * Get the options for this model.
     * @return map of {@link Command.Option} keyed by name.
     */
    Map<String, OptionInfo> options();

    /**
     * Create a {@link CommandExecution} for this model.
     * @param parser command parser
     * @return new {@link CommandExecution} instance
     */
    CommandExecution createExecution(CommandParser parser);

    /**
     * Meta model for the {@link Command} annotation.
     */
    static class CommandInfo {

        private final String name;
        private final String description;

        /**
         * Create a new command info.
         * @param name command name.
         * @param description command description
         */
        public CommandInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }

        /**
         * The command name.
         * @return command name
         */
        public String name() {
            return name;
        }

        /**
         * The command description.
         * @return command description
         */
        public String description() {
            return description;
        }
    }

    /**
     * Meta model for the {@link Option} annotation.
     */
    static class OptionInfo {

        private final String name;
        private final String description;
        private final boolean required;

        /**
         * Create a new option info.
         * @param name option name
         * @param description option description
         * @param required option required flag
         */
        public OptionInfo(String name, String description, boolean required) {
            this.name = name;
            this.description = description;
            this.required = required;
        }

        /**
         * The option name.
         * @return option name
         */
        public String name() {
            return name;
        }

        /**
         * The option description.
         * @return option description
         */
        public String description() {
            return description;
        }

        /**
         * The option required flag.
         * @return required flag
         */
        public boolean required() {
            return required;
        }
    }
}
