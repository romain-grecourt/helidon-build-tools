package io.helidon.build.cli;

import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandContext;
import io.helidon.build.cli.harness.CommandExecution;

/**
 * The {@code dev} command.
 */
public final class DevCommand implements CommandExecution {

    private final CommonOptions commonOptions;

    @Command.Option(name = "--clean", description = "clean before build")
    private final boolean clean;

    public DevCommand(CommonOptions commonOptions, boolean clean) {
        this.commonOptions = commonOptions;
        this.clean = clean;
    }

    @Override
    public void execute(CommandContext context) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
