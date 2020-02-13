package io.helidon.build.cli.harness;

import java.util.Objects;
import java.util.Optional;

/**
 * The command context.
 */
public final class CommandContext {

    private final CommandRegistry registry;
    private ExitCode exitCode = ExitCode.SUCCESS;
    private String message;

    CommandContext(CommandRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry is null");
    }

    /**
     * Exit codes.
     */
    public enum ExitCode {
        SUCCESS,
        WARNING,
        FAILURE
    };

    /**
     * Get the command registry.
     * @return command registry
     */
    public CommandRegistry registry() {
        return registry;
    }

    /**
     * Execute a command.
     * @param args raw arguments
     */
    public void execute(String ... args) {
        CommandRunner.execute(this, args);
    }

    /**
     * Get the action exit code.
     * @return exit code
     */
    ExitCode exitCode() {
        return exitCode;
    }

    /**
     * The action exit code.
     * @param exitCode exit code
     */
    public void exitCode(ExitCode exitCode) {
        this.exitCode = exitCode;
    }

    /**
     * Set the message message.
     * @param message action message
     */
    public void message(String message) {
        this.message = message;
    }

    /**
     * Get the action message.
     * @return optional of {@link String}
     */
    Optional<String> message() {
        return Optional.ofNullable(message);
    }

    /**
     * Mark this context action for a command not found erorr.
     * @param commandName command name
     */
    public void commandNotFound(String commandName) {
        exitCode = CommandContext.ExitCode.FAILURE;
        message = "Command not found: " + commandName;
    }
}
