package io.helidon.build.cli.impl;

import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandContext;
import io.helidon.build.cli.harness.CommandExecution;
import io.helidon.build.cli.harness.Creator;
import io.helidon.build.cli.harness.Option.Flag;
import io.helidon.build.cli.harness.Option.KeyValue;

/**
 * The {@code init} command.
 */
@Command(name = "init", description = "Generate a new project")
public final class InitCommand implements CommandExecution {

    private final CommonOptions commonOptions;
    private final boolean batch;
    private final Flavor flavor;
    private final Build build;

    enum Flavor {
        MP,
        SE
    }

    enum Build {
        MAVEN,
        GRADLE
    }

    @Creator
    InitCommand(
            CommonOptions commonOptions,
            @KeyValue(name = "flavor", description = "Helidon flavor SE|MP") Flavor flavor,
            @KeyValue(name = "build", description = "Build type MAVEN|GRADLE") Build build,
            @Flag(name = "batch", description = "Non iteractive, user input is passes as system properties") boolean batch) {

        this.commonOptions = commonOptions;
        this.flavor = flavor;
        this.build = build;
        this.batch = batch;
    }

    @Override
    public void execute(CommandContext context) {
        context.logInfo(String.format("\n TODO exec init, project=%s, flavor=%s, build=%s, batch=%s",
                commonOptions.project, String.valueOf(flavor), String.valueOf(build), String.valueOf(batch)));
    }
}
