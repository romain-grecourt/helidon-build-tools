package io.helidon.build.cli.impl;

import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandContext;
import io.helidon.build.cli.harness.CommandExecution;
import io.helidon.build.cli.harness.Creator;
import io.helidon.build.cli.harness.Option;

/**
 * The {@code dev} command.
 */
@Command(name = "dev", description = "dev !")
public final class DevCommand implements CommandExecution {

    private final CommonOptions commonOptions;
    private final boolean clean;

    @Creator
    DevCommand(
            CommonOptions commonOptions,
            @Option(name = "clean", description = "clean before build") boolean clean) {

        this.commonOptions = commonOptions;
        this.clean = clean;
    }

    @Override
    public void execute(CommandContext context) {
    }
}
