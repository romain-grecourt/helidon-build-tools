package io.helidon.build.cli.harness;

/**
 * Built-in usage command.
 */
final class UsageCommand extends CommandModel {

    static final String NAME = "";

    UsageCommand() {
        super(new CommandInfo(NAME, ""));
    }

    @Override
    boolean isMeta() {
        return true;
    }

    @Override
    public CommandExecution createExecution(CommandParser parser) {
        return this::execute;
    }

    private void execute(CommandContext context) {
        context.logInfo(String.format("\nUsage:\t %s [OPTIONS] COMMAND\n", context.name()));
        context.logInfo(context.description());
        context.logInfo("\nOptions:");
        context.logInfo("-D<name>=<value>     Define a system property");
        context.logInfo("--verbose            Produce verbose output");
        context.logInfo("--version            Print version information and quit");
        context.logInfo("\nCommands:");
        int maxCmdNameLength = 0;
        for (CommandModel command : context.allCommands()) {
            if (command.isMeta()) {
                continue;
            }
            int cmdNameLength = command.command().name().length();
            if (cmdNameLength > maxCmdNameLength) {
                maxCmdNameLength = cmdNameLength;
            }
        }
        if (maxCmdNameLength > 0) {
            int descColPos = maxCmdNameLength + 4 ;
            for (CommandModel command : context.allCommands()) {
                if (command.isMeta()) {
                    continue;
                }
                CommandInfo commandInfo = command.command();
                int curColPos = descColPos - commandInfo.name().length();
                String spacing = "";
                for (int i=0 ; i < curColPos ; i++) {
                    spacing += " ";
                }
                context.logInfo(String.format("  %s%s%s", commandInfo.name(), spacing, commandInfo.description()));
            }
        }
        context.logInfo(String.format("\nRun '%s COMMAND --help' for more information on a command.", context.name()));
    }
}
