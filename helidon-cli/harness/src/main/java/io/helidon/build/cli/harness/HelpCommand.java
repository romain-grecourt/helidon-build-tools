package io.helidon.build.cli.harness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@code help} command.
 */
class HelpCommand extends CommandModel {

    static OptionInfo<Boolean> HELP_OPTION = new OptionInfo<>(Boolean.class, "help", "Display help information", false);
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
        return Optional.ofNullable(parser.resolve(commandArg))
                // if the help command is forced because of --help, the actual command arg is the original command name
                .or(() -> parser.commandName().map((cmd) -> "help".equals(cmd) ? null : cmd))
                // if --help is found at this point, this is help about the help command
                .or(() -> Optional.ofNullable(parser.resolve(HELP_OPTION) ? "help" : null))
                // execute
                .map((cmdName) -> (CommandExecution) (ctx) -> ctx.command(cmdName)
                .ifPresentOrElse((cmd) -> this.execute(ctx, cmd), () -> ctx.commandNotFoundError(cmdName)))
                // just help, print the usage
                .orElse((CommandExecution) (ctx) -> ctx.execute());
    }

    private void execute(CommandContext context, CommandModel model) {
        List<OptionInfo> options = new ArrayList<>();
        String argument = "";
        int maxOptNameLength = 0;
        for (ParameterInfo<?> param : model.parameters()) {
            if (param.equals(HELP_OPTION)) {
                continue;
            }
            if (param instanceof ArgumentInfo) {
                argument = " " + ((ArgumentInfo) param).description;
            } else if (param instanceof OptionInfo) {
                int optNameLength = ((OptionInfo) param).name().length();
                if (optNameLength > maxOptNameLength) {
                    maxOptNameLength = optNameLength;
                }
                options.add((OptionInfo) param);
            }
        }
        context.logInfo(String.format("\nUsage:\t%s %s [OPTIONS]%s\n", context.cli().name(), model.command().name(), argument));
        context.logInfo(model.command().description());
        if (maxOptNameLength > 0) {
            int descColPos = maxOptNameLength + 4;
            context.logInfo("\nOptions:");
            for (OptionInfo<?> option : options) {
                int curColPos = descColPos - option.name().length();
                String spacing = "";
                for (int i=0 ; i < curColPos ; i++) {
                    spacing += " ";
                }
                context.logInfo(String.format(" --%s%s%s", option.name(), spacing, option.description));
            }
        }
    }
}
