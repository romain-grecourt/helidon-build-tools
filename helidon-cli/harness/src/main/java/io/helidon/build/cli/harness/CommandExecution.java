package io.helidon.build.cli.harness;

/**
 * A command execution.
 */
@FunctionalInterface
public interface CommandExecution {

    /**
     * Execute the command.
     * @param context command context
     */
    void execute(CommandContext context);
}
