package io.helidon.build.cli.harness;

/**
 * Top level usage command model.
 */
final class UsageCommand extends CommandModel {

    static final String NAME = "_usage";

    UsageCommand() {
        super(new CommandInfo(NAME, ""));
    }

    @Override
    public CommandExecution createExecution(CommandParser parser) {
        return new CommandExecutionImpl();
    }

    private static final class CommandExecutionImpl implements CommandExecution {

        @Override
        public void execute(CommandContext context) {
            System.out.println("Commands:");
            for (CommandModel commandModel : context.allCommands()) {
                CommandInfo commandInfo = commandModel.command();
                if ("".equals(commandInfo.name())) {
                    continue;
                }
                System.out.println("  " + commandModel.command().name());
            }
        }
    }
}
