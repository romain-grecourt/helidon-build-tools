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

    private final String name;
    private final String description;
    private final Logger logger;
    private final Properties properties;
    private final CommandRegistry registry;
    private boolean error;

    private CommandContext(CommandRegistry registry, String name, String description) {
        this.name = Objects.requireNonNull(name, "name is null");
        this.description = Objects.requireNonNull(description, "description is null");
        this.properties = new Properties();
        this.logger = Logger.getAnonymousLogger();
        this.logger.setUseParentHandlers(false);
        this.logger.addHandler(new LogHandler());
        this.registry = Objects.requireNonNull(registry, "registry is null");
    }

    /**
     * Get the CLI name.
     * @return name, never {@code null}
     */
    public String name() {
        return name;
    }

    /**
     * Get the CLI description.
     * @return description, never {@code null}
     */
    public String description() {
        return description;
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
    public void logDebug(String message) {
        logger.log(Level.FINE, message);
    }

    /**
     * Set the error message if not already set.
     * @param message error message
     */
    public void error(String message) {
        if (!error) {
            error = true;
            logError(message);
        }
    }

    /**
     * Mark this context action for a command not found error.
     * @param commandName command name
     */
    void commandNotFound(String commandName) {
        error("Command not found: " + commandName);
    }

    /**
     * Execute a command.
     * @param args raw arguments
     */
    public void execute(String... args) {
        CommandRunner.execute(this, args);
    }

    /**
     * Perform the system sequence.
     */
    public void exit() {
        if (error) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

    /**
     * Create a new command context.
     * @param registry command registry
     * @param name CLI name
     * @param description CLI description
     * @return command context, never {@code null}
     */
    public static CommandContext create(CommandRegistry registry, String name, String description) {
        return new CommandContext(registry, name, description);
    }

    private static final class LogHandler extends Handler {

        @Override
        public void publish(LogRecord record) {
            System.out.println(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }
    }
}
