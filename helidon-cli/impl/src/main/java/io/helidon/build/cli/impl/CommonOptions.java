package io.helidon.build.cli.impl;

import java.io.File;

import io.helidon.build.cli.harness.Option;
import io.helidon.build.cli.harness.OptionName;
import io.helidon.build.cli.harness.CommandFragment;

/**
 * Common options.
 */
@CommandFragment
public final class CommonOptions {

    @Option(name = "help", description = "Print the help usage")
    final boolean help;

    @Option(name = "project", description = "project directory")
    final File projectDir;

    public CommonOptions(boolean help, @OptionName("project") File projectDir) {
        this.help = help;
        this.projectDir = projectDir;
    }
}
