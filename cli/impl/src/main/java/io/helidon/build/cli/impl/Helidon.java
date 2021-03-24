package io.helidon.build.cli.impl;

import io.helidon.build.cli.harness.CommandLineInterface;
import io.helidon.build.cli.harness.CommandRunner;

/**
 * Helidon CLI definition and entry-point.
 */
@CommandLineInterface(
        name = "helidon",
        description = "Helidon Project command line tool",
        commands = {
                BuildCommand.class,
                DevCommand.class,
                InfoCommand.class,
                InitCommand.class,
                VersionCommand.class
        })
public final class Helidon {

    /**
     * Execute the command.
     *
     * @param args raw command line arguments
     */
    public static void main(String[] args) {
        CommandRunner.builder()
                     .args(args)
                     .optionLookup(Config.userConfig()::property)
                     .cliClass(Helidon.class)
                     .build()
                     .initProxy()
                     .execute()
                     .runExitAction();
    }
}
