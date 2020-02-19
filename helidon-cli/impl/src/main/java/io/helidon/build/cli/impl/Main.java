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
        CommandRegistry registry = CommandRegistry.load(Main.class);
        CommandContext context = CommandContext.create(registry, "helidon", "Java framework for writing microservices");
        CommandRunner.execute(context, args);
        context.runExitAction();
    }
}
