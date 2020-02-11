package io.helidon.build.cli.harness;

import java.util.HashMap;
import java.util.Optional;

/**
 * Base implementation of {@link CommandRegistry}.
 */
public abstract class BaseCommandRegistry implements CommandRegistry {

    protected final HashMap<String, CommandModel> registry = new HashMap<>();

    @Override
    public Optional<CommandModel> get(String name) {
        return Optional.ofNullable(registry.get(name));
    }
}
