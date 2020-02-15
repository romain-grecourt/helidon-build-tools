package io.helidon.build.cli.impl;

import io.helidon.build.cli.harness.CommandContext;
import io.helidon.build.cli.harness.CommandRegistry;
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
        // TODO add an annotation for CLI with name and description, as well as global options
        // load registry using the @CLI annotated class
        // Or, use a builder class...
        // configure system properties support etc.
        CommandRegistry registry = CommandRegistry.load(Main.class);
        CommandContext context = CommandContext.create(registry, "helidon", "Java framework for writing microservices");
        CommandRunner.execute(context, args);
    }
}
