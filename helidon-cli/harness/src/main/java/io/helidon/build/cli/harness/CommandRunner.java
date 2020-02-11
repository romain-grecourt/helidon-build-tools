package io.helidon.build.cli.harness;

/**
 * Command runner.
 */
public final class CommandRunner {

    private final CommandParser parser;
    private final CommandRegistry registry;
    private final CommandContext context;

    private CommandRunner(String namespace, String[] args) {
        this.parser = new CommandParser(args);
        this.registry = CommandRegistry.load(namespace);
        this.context = new CommandContext(registry);
    }

    /**
     * Execute the command.
     */
    public void execute() {
        parser.parse();
        registry.get(parser.commandName())
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
     * @param namespace package namespace
     * @param args raw command line arguments
     */
    public static void execute(String namespace, String[] args) {
        new CommandRunner(namespace, args).execute();
    }
}
