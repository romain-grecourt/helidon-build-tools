package io.helidon.build.cli.impl;

import io.helidon.build.cli.harness.CLIDefinition;
import io.helidon.build.cli.harness.CommandRunner;

/**
 * Main entry point for the CLI.
 */
public class Main {

    private static final CLIDefinition CLI_DEFINITION = CLIDefinition.create("helidon", "Helidon Project command line tool");

    /**
     * Execute the command.
     * @param args raw command line arguments
     */
    public static void main(String[] args) {
        CommandRunner.execute(CLI_DEFINITION, Main.class, args);
    }
}
