package io.helidon.build.cli;

import io.helidon.build.cli.harness.Command;
import java.io.File;

/**
 * The global options.
 */
public final class CommonOptions {

    @Command.Option(name = "--help", description = "Print the help usage")
    private final boolean help;

    @Command.Option(name = "--project", description = "project directory")
    private final File projectDir;

    public CommonOptions(boolean help, File projectDir) {
        this.help = help;
        this.projectDir = projectDir;
    }
}
