package io.helidon.build.cli.impl;

import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandContext;
import io.helidon.build.cli.harness.CommandExecution;
import io.helidon.build.cli.harness.CommandModel;
import io.helidon.build.cli.harness.Option;

/**
 * The {@code help} command.
 */
@Command(name = "help", description = "Get help")
public final class HelpCommand implements CommandExecution {

    private final CommonOptions commonOptions;

    @Option(name = "command", description = "command to get the help for")
    private final String command;

    public HelpCommand(CommonOptions commonOptions, String command) {
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
