package io.helidon.build.cli.harness;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code help} command.
 */
class HelpCommand extends CommandModel {

    private final ArgumentInfo<String> commandArg;

    HelpCommand() {
        super(new CommandInfo("help", "Help about the command"));
        commandArg = new ArgumentInfo<>(String.class, "command", false);
        addParameter(commandArg);
    }

    @Override
    boolean visible() {
        return false;
    }

    @Override
    public final CommandExecution createExecution(CommandParser parser) {
        return new HelpCommandExecution(parser);
    }

    private final class HelpCommandExecution implements CommandExecution {

        final CommandParser parser;

        HelpCommandExecution(CommandParser parser) {
            this.parser = parser;
        }

        private Optional<String> commandName() {
            return Optional.ofNullable(parser.resolve(commandArg))
                // if the help command is forced because of --help, the actual command arg is the original command name
                .or(() -> parser.commandName().map((command) -> "help".equals(command) ? null : command))
                // if --help is found at this point, this is help about the help command
                .or(() -> Optional.ofNullable(parser.resolve(HELP_OPTION) ? "help" : null));
        }

        @Override
        public void execute(CommandContext context) {
            commandName().ifPresentOrElse(
                    // execute
                    (commandName) -> this.doExecute(context, commandName),
                    // just help, print usage
                    () -> context.execute());
        }

        private void doExecute(CommandContext context, String commandName) {
            context.command(commandName).ifPresentOrElse(
                    // execute
                    (command) -> this.doExecute(context, command),
                    // command name is not found
                    () -> context.commandNotFoundError(commandName));
        }

        private String optionDescription(NamedOptionInfo<?> option) {
            String desc = option.description();
            if (option instanceof KeyValueInfo && !((KeyValueInfo) option).required()) {
                Object defaultValue = ((KeyValueInfo<?>) option).defaultValue();
                if (defaultValue != null) {
                    desc += " (default: " + defaultValue + ")";
                }
            }
            return desc;
        }

        private void doExecute(CommandContext context, CommandModel model) {
            Map<String, String> options = new LinkedHashMap<>();
            options.putAll(UsageCommand.GLOBAL_OPTIONS);
            String usage = "";
            String argument = "";
            for (ParameterInfo<?> param : model.parameters()) {
                if (!param.visible()) {
                    continue;
                }
                if (!usage.isEmpty()) {
                    usage += " ";
                }
                if (param instanceof ArgumentInfo) {
                    argument = ((ArgumentInfo) param).usage();
                } else if (param instanceof OptionInfo) {
                    usage += ((OptionInfo) param).usage();
                }
                if (param instanceof NamedOptionInfo) {
                    NamedOptionInfo<?> option = (NamedOptionInfo<?>) param;
                    options.put("--" + option.name(), optionDescription(option));
                } else if (param instanceof CommandFragmentInfo) {
                    for (ParameterInfo<?> fragmentParam : ((CommandFragmentInfo) param).parameters()) {
                        if (fragmentParam instanceof NamedOptionInfo) {
                            NamedOptionInfo<?> fragmentOption = (NamedOptionInfo<?>) fragmentParam;
                            usage += fragmentOption.usage();
                            options.put("--" + fragmentOption.name(), optionDescription(fragmentOption));
                        }
                    }
                }
            }
            if (!argument.isEmpty()) {
                usage += (usage.isEmpty() ? argument : (" " + argument));
            }
            context.logInfo(String.format("\nUsage:\t%s %s [OPTIONS] %s\n", context.cli().name(), model.command().name(), usage));
            context.logInfo(model.command().description());
            context.logInfo("\nOptions:");
            context.logInfo(UsageCommand.describeOptions(options));
        }
    }
}
