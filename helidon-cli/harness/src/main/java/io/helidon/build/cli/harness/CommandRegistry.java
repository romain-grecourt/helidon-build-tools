package io.helidon.build.cli.harness;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Registry of {@link CommandModel}, keyed by their name.
 */
public class CommandRegistry {

    private final Map<String, CommandModel> registry;
    private final String pkg;

    private CommandRegistry() {
        this.pkg = null;
        this.registry = Collections.emptyMap();
    }

    protected CommandRegistry(String pkg) {
        this.pkg = Objects.requireNonNull(pkg, "pkg is null");
        registry = new HashMap<>();
        // built-in commands
        register(new UsageCommand());
        register(new HelpCommand());
    }

    protected final void register(CommandModel model) {
        Objects.requireNonNull(model, "model is null");
        String name = model.command().name();
        if (registry.containsKey(name)) {
            throw new IllegalArgumentException("Command already registered for name: " + name);
        }
        registry.put(name, model);
    }

    /**
     * Get all the commands.
     * @return collection of all registered commands
     */
    public final Collection<CommandModel> all() {
        return registry.values();
    }

    /**
     * Get a command by name.
     * @param name command name
     * @return optional of {@link CommandModel}
     */
    public final Optional<CommandModel> get(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(registry.get(name));
    }

    /**
     * Get the package this command registry belongs to.
     * @return package name
     */
    public final String pkg() {
        return pkg;
    }

    /**
     * Load a {@link CommandRegistry} instance.
     * @param clazz a class to derive the package namespace the registry is associated with
     * @return command registry, never {@code null}
     */
    public static CommandRegistry load(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        String pkg = clazz.getPackageName();
        return ServiceLoader.load(CommandRegistry.class)
                .stream()
                .filter((r) -> pkg.equals(r.get().pkg()))
                .findFirst()
                .map(ServiceLoader.Provider::get)
                .orElse(new CommandRegistry());
    }
}
