package io.helidon.build.cli.impl;

import java.io.File;

import io.helidon.build.cli.harness.Option;
import io.helidon.build.cli.harness.CommandFragment;
import io.helidon.build.cli.harness.Creator;

/**
 * Common options.
 */
@CommandFragment
final class CommonOptions {

    final boolean help;
    final File projectDir;

    @Creator
    CommonOptions(
            @Option(name = "help", description = "Print the help usage", required = false) boolean help,
            @Option(name = "project", description = "project directory", required = false) File projectDir) {

        this.help = help;
        this.projectDir = projectDir;
    }
}
