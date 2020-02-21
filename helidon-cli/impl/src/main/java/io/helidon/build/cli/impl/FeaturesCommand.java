package io.helidon.build.cli.impl;

import java.util.Collection;

import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandContext;
import io.helidon.build.cli.harness.CommandExecution;
import io.helidon.build.cli.harness.Creator;
import io.helidon.build.cli.harness.Option;

/**
 * The {@code features} command.
 */
@Command(name = "features", description = "List or add features to the project")
public final class FeaturesCommand implements CommandExecution {

    private final CommonOptions commonOptions;
    private final Collection<String> add;
    private final boolean list;
    private final boolean all;

    @Creator
    FeaturesCommand(
            CommonOptions commonOptions,
            @Option(name = "add", description = "Add features to the project", required = false) Collection<String> add,
            @Option(name = "list", description = "List the features used in the project", required = false) boolean list,
            @Option(name = "all", description = "List all available features", required = false) boolean all) {

        this.commonOptions = commonOptions;
        this.add = add;
        this.list = list;
        this.all = all;
    }

    @Override
    public void execute(CommandContext context) {
        context.logInfo(String.format("\n// TODO exec features, project=%s, add=%s, list=%s, all=%s",
                commonOptions.project, add, String.valueOf(list), String.valueOf(all)));
    }
}
