package io.helidon.build.cli.harness;

import java.util.Objects;

/**
 * CLI definition.
 */
public final class CLIDefinition {

    private final String name;
    private final String description;

    private CLIDefinition(String name, String description) {
        this.name = Objects.requireNonNull(name, "name is null");
        this.description = Objects.requireNonNull(description, "description is null");
    }

    /**
     * The CLI name.
     *
     * @return name
     */
    public String name() {
        return name;
    }

    /**
     * The CLI description.
     *
     * @return description
     */
    public String description() {
        return description;
    }

    /**
     * Create a new CLI definition.
     * @param name CLI name
     * @param description CLI description
     * @return CLI definition, never {@code null}
     */
    public static CLIDefinition create(String name, String description) {
        return new CLIDefinition(name, description);
    }
}