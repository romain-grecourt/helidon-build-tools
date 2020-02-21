package io.helidon.build.cli.harness;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * The command context.
 */
public final class CommandContext {

    private final CLIDefinition cli;
    private final Logger logger;
    private final Properties properties;
    private final CommandRegistry registry;
    private final LogHandler logHandler;
    private ExitAction exitAction;

    private CommandContext(CommandContext parent) {
        this.cli = parent.cli;
        this.properties = parent.properties;
        this.logger = parent.logger;
        this.registry = parent.registry;
        this.logHandler = parent.logHandler;
        this.exitAction = new ExitAction();
    }

    private CommandContext(CommandRegistry registry, CLIDefinition cli) {
        this.cli = Objects.requireNonNull(cli, "cli is null");
        this.properties = new Properties();
        this.logger = Logger.getAnonymousLogger();
        this.logger.setUseParentHandlers(false);
        this.logHandler = new LogHandler();
        this.logger.addHandler(logHandler);
        this.registry = Objects.requireNonNull(registry, "registry is null");
        this.exitAction = new ExitAction();
    }

    /**
     * Exit status.
     */
    public static enum ExitStatus {
        /**
         * Success exit status.
         */
        SUCCESS,

        /**
         * Warning exit status.
         */
        WARNING,

        /**
         * Failure exit status.
         */
        FAILURE;

        /**
         * Test if this status is worse than the given one.
         * @param status status to compare with
         * @return {@code true} if worse, {@code false} if not
         */
        public boolean isWorse(ExitStatus status) {
            return ordinal() > status.ordinal();
        }
    }

    public final class ExitAction {

        final ExitStatus status;
        final String message;

        private ExitAction() {
            this.status = ExitStatus.SUCCESS;
            this.message = null;
        }

        ExitAction(ExitStatus status, String message) {
            this.status = Objects.requireNonNull(status, "exit status is null");
            this.message = Objects.requireNonNull(message, "message is null");
        }

        /**
         * Run the exit sequence for this action.
         * <b>WARNING:</b> This method invokes {@link System#exit(int)}.
         */
        public void run() {
            switch (exitAction.status) {
                case FAILURE:
                    if (message != null && !message.isEmpty()) {
                        CommandContext.this.logError(message);
                    }
                    System.exit(1);
                case WARNING:
                    if (message != null && !message.isEmpty()) {
                        CommandContext.this.logWarning(message);
                    }
                default:
                    System.exit(0);
            }
        }
    }

    /**
     * Get the CLI definition
     * @return CLI definition, never {@code null}
     */
    public CLIDefinition cli() {
        return cli;
    }

    /**
     * Get a command model by name.
     * @param name command name
     * @return optional of command model, never {@code null}
     */
    public Optional<CommandModel> command(String name) {
        return registry.get(name);
    }

    /**
     * Get all commands.
     * @return collection of command models, never {@code null}
     */
    public Collection<CommandModel> allCommands() {
        return registry.all();
    }

    /**
     * Get the system properties.
     * @return properties, never {@code null}
     */
    public Properties properties() {
        return properties;
    }

    /**
     * Get the logger for this context.
     * @return logger, never {@code null}
     */
    public Logger Logger() {
        return logger;
    }

    /**
     * Log an INFO message.
     * @param message INFO message to log
     */
    public void logInfo(String message) {
        logger.log(Level.INFO, message);
    }

    /**
     * Log a WARNING message.
     * @param message WARNING message to log
     */
    public void logWarning(String message) {
        logger.log(Level.WARNING, message);
    }

    /**
     * Log a SEVERE message.
     * @param message SEVERE message to log
     */
    public void logError(String message) {
        logger.log(Level.SEVERE, message);
    }

    /**
     * Log a FINE message.
     * @param message FINE message to log
     */
    public void logVerbose(String message) {
        logger.log(Level.FINE, message);
    }

    /**
     * Log a FINEST message.
     * @param message FINEST message to log
     */
    public void logDebug(String message) {
        logger.log(Level.FINEST, message);
    }

    /**
     * Execute a nested command.
     * @param args raw arguments
     */
    public void execute(String... args) {
        CommandContext context = new CommandContext(this);
        CommandRunner.execute(context, args);
        this.exitAction = context.exitAction;
    }

    /**
     * Set the error message if not already set.
     * @param status
     * @param message error message
     */
    public void exitAction(ExitStatus status, String message) {
        if (status.isWorse(exitAction.status)) {
            exitAction = new ExitAction(status, message);
            logError(message);
        }
    }

    /**
     * Get the exit action.
     * @return exit action, never {@code null}
     */
    public ExitAction exitAction() {
        return exitAction;
    }

    /**
     * Enable verbose mode.
     * @param verbose verbose value
     */
    void verbosity(Verbosity verbosity) {
        this.logHandler.verbosity = verbosity;
    }

    /**
     * Set the exit action to {@link ExitStatus#FAILURE} with a command not found error message.
     *
     * @param command command name
     */
    void commandNotFoundError(String command) {
        error("Command not found: " + command);
    }

    /**
     * Set the exit action to {@link ExitStatus#FAILURE} with the given error message.
     * @param message error message
     */
    void error(String message) {
        exitAction(ExitStatus.FAILURE, message);
    }

    /**
     * Create a new command context.
     * @param registry command registry
     * @param cliDef CLI definition
     * @return command context, never {@code null}
     */
    public static CommandContext create(CommandRegistry registry, CLIDefinition cliDef) {
        return new CommandContext(registry, cliDef);
    }

    /**
     * Verbosity levels.
     */
    static enum Verbosity {
        NORMAL,
        VERBOSE,
        DEBUG
    }

    /**
     * Custom log handler to print the message to {@code stdout} and {@code stderr}.
     */
    private static final class LogHandler extends Handler {

        Verbosity verbosity = Verbosity.NORMAL;

        @Override
        public void publish(LogRecord record) {
            Level level = record.getLevel();
            if (level == Level.INFO) {
                System.out.println(record.getMessage());
            } else if (level == Level.WARNING || level == Level.SEVERE) {
                System.err.println(record.getMessage());
            } else if ((level == Level.CONFIG || level == Level.FINE)
                    && (verbosity == Verbosity.VERBOSE || verbosity == Verbosity.DEBUG)) {
                System.out.println(record.getMessage());
            } else if (verbosity == Verbosity.DEBUG) {
                System.out.println(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }
    }
}
