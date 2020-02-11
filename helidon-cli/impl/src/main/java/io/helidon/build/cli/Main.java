package io.helidon.build.cli;

import io.helidon.build.cli.harness.CommandRunner;

/**
 * Main entry point for the command line interface.
 */
public class Main {

    /**
     * Execute the command.
     * @param args raw command line arguments
     */
    public static void main(String[] args) {
        CommandRunner.execute(Main.class.getPackage().getName(), args);
    }
}
