package io.helidon.build.cli.harness;

import java.util.Objects;

import io.helidon.build.cli.harness.CommandParser.CommandParserException;

/**
 * Command runner.
 */
public final class CommandRunner {

    private final CommandParser parser;
    private final CommandContext context;

    private CommandRunner(CommandContext context, String[] args) {
        this.context = Objects.requireNonNull(context, "context is null");
        this.parser = CommandParser.create(args == null ? new String[0] : args);
    }

    /**
     * Execute the command.
     */
    public void execute() {
        // TODO set system properties
        try {
            parser.error().ifPresentOrElse(context::error, this::doExecute);
        } catch (CommandParserException ex) {
            context.error(ex.getMessage());
        }
    }

    /**
     * Execute the current command from the parser.
     */
    private void doExecute() {
        parser.commandName().ifPresentOrElse(this::doExecuteCommandName, this::printUsage);
    }

    /**
     * Execute the command represented by the given name.
     * @param command command name
     */
    private void doExecuteCommandName(String command) {
        context.command(command).map(this::mapHelp)
                .ifPresentOrElse(this::doExecuteCommand, () -> context.commandNotFoundError(command));
    }

    /**
     * Execute the given {@link CommandModel} instance.
     * @param command command to execute
     */
    private void doExecuteCommand(CommandModel command) {
        if (parser.resolve(CommandModel.VERBOSE_OPTION)) {
            context.verbosity(CommandContext.Verbosity.VERBOSE);
        }
        if (parser.resolve(CommandModel.DEBUG_OPTION)) {
            context.verbosity(CommandContext.Verbosity.DEBUG);
        }
        command.createExecution(parser).execute(context);
    }

    /**
     * Resolve the {@code --help} option and return the {@code help} command if found.
     * @param command fallback command
     * @return {@code help} command if the {@code --help} option is provided, otherwise the supplied fallback command
     */
    private CommandModel mapHelp(CommandModel command) {
        return parser.resolve(CommandModel.HELP_OPTION) ? new HelpCommand() : command;
    }

    /**
     * No command provided, print the usage.
     */
    private void printUsage() {
        new UsageCommand().createExecution(parser).execute(context);
    }

    /**
     * Execute a sub-command.
     *
     * @param context command context
     * @param args raw command line arguments
     */
    static void execute(CommandContext context, String... args) {
        new CommandRunner(context, args).execute();
    }

    /**
     * Execute a command.
     * @param cli CLI definition
     * @param clazz class used to derive the package of the sub-commands
     * @param args raw command line arguments
     */
    public static void execute(CLIDefinition cli, Class clazz, String... args) {
        CommandRegistry registry = CommandRegistry.load(clazz);
        CommandContext context = CommandContext.create(registry, cli);
        CommandRunner.execute(context, args);
        context.exitAction().run();
    }
}
