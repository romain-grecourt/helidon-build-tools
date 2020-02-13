package io.helidon.build.cli.harness;

import java.util.Objects;

/**
 * Command runner.
 */
public final class CommandRunner {

    private final CommandParser parser;
    private final CommandContext context;

    private CommandRunner(CommandContext context, String[] args) {
        this.context = Objects.requireNonNull(context, "context is null");
        this.parser = CommandParser.create(args);
    }

    /**
     * Execute the command.
     */
    public void execute() {
        context.registry().get(parser.commandName().orElse(""))
                .ifPresentOrElse(this::executeCommand, this::commandNotFound);
    }

    private void executeCommand(CommandModel model) {
        model.createExecution(parser).execute(context);
    }

    private void commandNotFound() {
        context.exitCode(CommandContext.ExitCode.FAILURE);
        context.message("Command not found: " + parser.commandName());
    }

    /**
     * Create a new {@link CommandRunner instance}.
     *
     * @param context command context
     * @param args raw command line arguments
     */
    public static void execute(CommandContext context, String... args) {
        new CommandRunner(context, args).execute();
    }

    /**
     * Create a new {@link CommandRunner instance}.
     * @param namespace package namespace
     * @param args raw command line arguments
     */
    public static void execute(String namespace, String ... args) {
        CommandRegistry registry = CommandRegistry.load(namespace);
        execute(new CommandContext(registry), args);
    }
}
