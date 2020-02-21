package io.helidon.build.cli.impl;

import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandContext;
import io.helidon.build.cli.harness.CommandExecution;
import io.helidon.build.cli.harness.Creator;
import io.helidon.build.cli.harness.Option;

/**
 * The {@code init} command.
 */
@Command(name = "init", description = "Generate a new project")
public final class InitCommand implements CommandExecution {

    private final CommonOptions commonOptions;
    private final boolean batch;
    private final boolean mp;
    private final boolean se;
    private final boolean maven;
    private final boolean gradle;

    @Creator
    InitCommand(
            CommonOptions commonOptions,
            @Option(name = "mp", description = "Generate a Helidon MP project", required = false) boolean mp,
            @Option(name = "se", description = "Generate a Helidon SE project", required = false) boolean se,
            @Option(name = "maven", description = "Use a Maven as build system", required = false) boolean maven,
            @Option(name = "gradle", description = "Use a Gradle as build system", required = false) boolean gradle,
            @Option(name = "batch", description = "Non iteractive, user input is passes as system properties", required = false) boolean batch) {

        this.commonOptions = commonOptions;
        this.mp = mp;
        this.se = se;
        this.maven = maven;
        this.gradle = gradle;
        this.batch = batch;
    }

    @Override
    public void execute(CommandContext context) {
        context.logInfo(String.format("\n TODO exec init, project=%s, mp=%s, se=%s, maven=%s, gradle=%s, batch=%s",
                commonOptions.project, String.valueOf(mp), String.valueOf(se), String.valueOf(maven), String.valueOf(gradle),
                String.valueOf(batch)));
    }
}
