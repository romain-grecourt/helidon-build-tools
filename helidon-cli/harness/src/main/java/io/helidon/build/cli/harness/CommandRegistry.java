package io.helidon.build.cli.harness;

import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;

/**
 * Registry of {@link CommandModel}, keyed by their name.
 */
public interface CommandRegistry {

    /**
     * Get the package this command registry belongs to.
     * @return package name
     */
    String namespace();

    /**
     * Get a command by name.
     * @param name command name
     * @return optional of {@link CommandModel}
     */
    public Optional<CommandModel> get(String name);

    /**
     * Load a {@link CommandRegistry} instance.
     * @param namespace package namespace the registry is associated with
     * @return 
     */
    static CommandRegistry load(String namespace) {
        Objects.requireNonNull(namespace, "namespace is null");
        return ServiceLoader.load(CommandRegistry.class)
                .stream()
                .filter((r) -> namespace.equals(r.get().namespace()))
                .findFirst()
                .map(Provider::get)
                .orElse(EmptyRegistry.INSTANCE);
    }
}
