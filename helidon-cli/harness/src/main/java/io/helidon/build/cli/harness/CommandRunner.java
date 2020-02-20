package io.helidon.build.cli.harness;

import java.util.Objects;
import java.util.Optional;

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
        parser.error().ifPresentOrElse(context::error,
                () -> parser.commandName()
                        .ifPresentOrElse((cmdName) -> context.command(cmdName)
                                // check for --help, force the help command if found
                                .flatMap((cmd) -> Optional.of(parser.resolve(HelpCommand.HELP_OPTION) ? new HelpCommand() : cmd))
                                .ifPresentOrElse((cmd) -> cmd.createExecution(parser).execute(context),
                                        () -> context.commandNotFoundError(cmdName)),
                                // not command name provided, print the usage
                                () -> new UsageCommand().createExecution(parser).execute(context)));
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
        context.runExitAction();
    }
}
