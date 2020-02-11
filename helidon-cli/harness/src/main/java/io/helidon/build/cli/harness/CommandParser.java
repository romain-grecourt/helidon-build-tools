package io.helidon.build.cli.harness;

/**
 * Command parser.
 */
public final class CommandParser {

    private final String[] rawArgs;

    /**
     * Create a new command parser.
     * @param rawArgs raw command line arguments
     */
    CommandParser(String[] rawArgs) {
        this.rawArgs = rawArgs;
    }

    /**
     * Parse the command line arguments.
     */
    void parse() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    String commandName() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
