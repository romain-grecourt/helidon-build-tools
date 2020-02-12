package io.helidon.build.cli.harness;

import java.util.LinkedList;
import java.util.Map;

/**
 * Command parser.
 */
public final class CommandParser {

    private final String[] rawArgs;
    private boolean parsed;
    private String commandName;
    private Map<String, LinkedList<String>> globalOptions;
    private Map<String, LinkedList<String>> options;

    /**
     * Create a new command parser.
     * @param rawArgs raw command line arguments
     */
    CommandParser(String[] rawArgs) {
        this.rawArgs = rawArgs;
    }

    enum State {
        GLOBAL,
        LOCAL
    }

    /**
     * Parse the command line arguments.
     */
    void parse() {
        if (!parsed) {
            State state = State.GLOBAL;
            for (int i=0 ; i < rawArgs.length ; i++) {
                String arg = rawArgs[i];
                if (arg == null || arg.isEmpty()) {
                    continue;
                }
                if (arg.charAt(0) == '-') {
                    String optionName = arg.substring(1);
                    if (!optionName.isEmpty() && optionName.charAt(0) == '-') {
                        optionName = optionName.substring(1);
                    }
                    if (!Option.NAME_PREDICATE.test(optionName)) {
                        throw new IllegalArgumentException("Invalid option name: " + optionName);
                    }
                    if (i + 1 < rawArgs.length) {
                        String value = rawArgs[i + 1];
                        if (value.charAt(0) != '-') {
                            LinkedList<String> values = options.get(optionName);
                            if (values == null) {
                                values = new LinkedList<>();
                                // key values option
                                options.put(optionName, values);
                            }
                            values.add(value);
                            continue;
                        }
                    }
                    // flag
                    options.put(arg, new LinkedList<>());
                } else {
                    if (state == State.GLOBAL) {
                        if (!Command.NAME_PREDICATE.test(arg)) {
                            throw new IllegalArgumentException("Invalid command name: " + arg);
                        }
                        commandName = arg;
                        state = State.LOCAL;
                    } else {
                        
                    }
                }
            }
        }
    }

    // TODO sketch-up a pseudo grammar
    // TODO model different kinds of options (flag, key-value, key-values, argument, arguments)

    String commandName() {
        return commandName;
    }
}
