package io.helidon.build.cli.impl;

import io.helidon.build.cli.harness.Argument;
import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandContext;
import io.helidon.build.cli.harness.CommandExecution;
import io.helidon.build.cli.harness.CommandModel;
import io.helidon.build.cli.harness.Creator;

/**
 * The {@code help} command.
 */
@Command(name = "help", description = "Get help")
final class HelpCommand implements CommandExecution {

    private final CommonOptions commonOptions;
    private final String command;

    @Creator
    HelpCommand(CommonOptions commonOptions,
            @Argument(description = "command to get the help for") String command) {

        this.commonOptions = commonOptions;
        this.command = command;
    }

    @Override
    public void execute(CommandContext context) {
        new HelpExecution(context, command).run();
    }

    private static final class HelpExecution {

        private final CommandContext context;
        private final String command;

        HelpExecution(CommandContext context, String command) {
            this.context = context;
            this.command = command;
        }

        void run() {
            context.command(command)
                    .ifPresentOrElse(this::printHelp, this::commandNotFound);
        }

        void commandNotFound() {
            System.out.println("Command not found: " + command);
        }

        void printHelp(CommandModel model) {
            System.out.println("TODO describe: " + command);
        }
    }

}
