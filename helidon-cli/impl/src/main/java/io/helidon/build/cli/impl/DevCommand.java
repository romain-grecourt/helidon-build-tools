package io.helidon.build.cli.impl;

import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandContext;
import io.helidon.build.cli.harness.CommandExecution;
import io.helidon.build.cli.harness.Creator;
import io.helidon.build.cli.harness.Option.Flag;

/**
 * The {@code dev} command.
 */
@Command(name = "dev", description = "Continuous application development")
public final class DevCommand implements CommandExecution {

    private final CommonOptions commonOptions;
    private final boolean clean;

    @Creator
    DevCommand(
            CommonOptions commonOptions,
            @Flag(name = "clean", description = "Perform a clean before the first build") boolean clean) {

        this.commonOptions = commonOptions;
        this.clean = clean;
    }

    @Override
    public void execute(CommandContext context) {
        context.logInfo(String.format("\n// TODO exec dev, project=%s, clean=%s",
                commonOptions.project, String.valueOf(clean)));
    }
}
