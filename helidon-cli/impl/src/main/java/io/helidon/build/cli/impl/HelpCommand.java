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
        context.registry().get(command)
                .ifPresentOrElse(this::printHelp, () -> context.commandNotFound(command));
    }

    private void printHelp(CommandModel model) {
        // TODO
    }
}
