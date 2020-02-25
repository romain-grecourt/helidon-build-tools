package io.helidon.build.cli.impl;

import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandContext;
import io.helidon.build.cli.harness.CommandExecution;
import io.helidon.build.cli.harness.Creator;
import io.helidon.build.cli.harness.Option.Flag;
import io.helidon.build.cli.harness.Option.KeyValue;

/**
 * The {@code build} command.
 */
@Command(name = "build", description = "Build the application")
public final class BuildCommand implements CommandExecution {

    private final CommonOptions commonOptions;
    private final boolean clean;
    private final BuildMode buildMode;

    enum BuildMode {
        PLAIN,
        NATIVE,
        JLINK
    }

    @Creator
    BuildCommand(
            CommonOptions commonOptions,
            @Flag(name = "clean", description = "Perform a clean before the build") boolean clean,
            @KeyValue(name = "mode", description = "Build mode", defaultValue = "PLAIN") BuildMode buildMode) {

        this.commonOptions = commonOptions;
        this.clean = clean;
        this.buildMode = buildMode;
    }

    @Override
    public void execute(CommandContext context) {
        context.logInfo(String.format("\n// TODO exec build, project=%s, clean=%s, buildMode=%s",
                commonOptions.project, String.valueOf(clean), buildMode));
    }
}
