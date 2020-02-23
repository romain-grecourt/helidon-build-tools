package io.helidon.build.cli.impl;

import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandContext;
import io.helidon.build.cli.harness.CommandExecution;
import io.helidon.build.cli.harness.Creator;
import io.helidon.build.cli.harness.Option.Flag;

/**
 * The {@code build} command.
 */
@Command(name = "build", description = "Build the application")
public final class BuildCommand implements CommandExecution {

    private final CommonOptions commonOptions;
    private final boolean clean;
    private final boolean nativeMode;
    private final boolean jlinkMode;

    @Creator
    BuildCommand(
            CommonOptions commonOptions,
            @Flag(name = "clean", description = "Perform a clean before the build") boolean clean,
            @Flag(name = "native", description = "Build a native binary using GraalVM native-image") boolean nativeMode,
            @Flag(name = "jlink", description = "Build a jlink image") boolean jlinkMode) {

        this.commonOptions = commonOptions;
        this.clean = clean;
        this.nativeMode = nativeMode;
        this.jlinkMode = jlinkMode;
    }

    @Override
    public void execute(CommandContext context) {
        context.logInfo(String.format("\n// TODO exec build, project=%s, clean=%s",
                commonOptions.project, String.valueOf(clean)));
    }
}
