package io.helidon.build.cli.harness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@code help} command.
 */
class HelpCommand extends CommandModel {

    static OptionInfo<Boolean> HELP_OPTION = new OptionInfo<>(Boolean.class, "help", "Display help information", false, Option.Scope.ANY);
    private final ArgumentInfo<String> commandArg;

    HelpCommand() {
        super(new CommandInfo("help", "Help about the command"));
        commandArg = new ArgumentInfo<>(String.class, "command", false);
        addParameter(commandArg);
    }

    @Override
    boolean isMeta() {
        return true;
    }

    @Override
    public final CommandExecution createExecution(CommandParser parser) {
        final String cmdName = Optional.ofNullable(parser.resolve(commandArg)).orElse(UsageCommand.NAME);
        return (ctx) -> ctx.command(cmdName).ifPresentOrElse((cmd) -> this.execute(ctx, cmd), () -> ctx.commandNotFound(cmdName));
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
        context.logInfo(String.format("\nUsage:\t %s %s [OPTIONS]%s\n", context.name(), model.command().name(), argument));
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
