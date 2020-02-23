package io.helidon.build.cli.harness;

import java.util.ArrayList;
import java.util.List;
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

        private void doExecute(CommandContext context, CommandModel model) {
            List<NamedOptionInfo> options = new ArrayList<>();
            String argument = "";
            int maxOptNameLength = 0;
            for (ParameterInfo<?> param : model.parameters()) {
                if (!param.visible()) {
                    continue;
                }
                if (param instanceof ArgumentInfo) {
                    argument = " " + ((ArgumentInfo) param).description;
                } else if (param instanceof NamedOptionInfo) {
                    int optNameLength = ((NamedOptionInfo) param).name().length();
                    if (optNameLength > maxOptNameLength) {
                        maxOptNameLength = optNameLength;
                    }
                    options.add((NamedOptionInfo) param);
                } else if (param instanceof CommandFragmentInfo) {
                    for (ParameterInfo<?> fragmentParam : ((CommandFragmentInfo) param).parameters()) {
                        if (fragmentParam instanceof NamedOptionInfo) {
                            int optNameLength = ((NamedOptionInfo) fragmentParam).name().length();
                            if (optNameLength > maxOptNameLength) {
                                maxOptNameLength = optNameLength;
                            }
                            options.add((NamedOptionInfo) fragmentParam);
                        }
                    }
                }
            }
            context.logInfo(String.format("\nUsage:\t%s %s [OPTIONS]%s\n", context.cli().name(), model.command().name(), argument));
            context.logInfo(model.command().description());
            if (maxOptNameLength > 0) {
                int descColPos = maxOptNameLength + 4;
                context.logInfo("\nOptions:");
                for (NamedOptionInfo<?> option : options) {
                    int curColPos = descColPos - option.name().length();
                    String spacing = "";
                    for (int i = 0; i < curColPos; i++) {
                        spacing += " ";
                    }
                    context.logInfo(String.format("  --%s%s%s", option.name(), spacing, option.description));
                }
            }
        }
    }
}
