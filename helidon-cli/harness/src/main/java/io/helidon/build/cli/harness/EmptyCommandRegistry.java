package io.helidon.build.cli.harness;

/**
 * The empty registry constant.
 */
import java.util.Optional;

/**
 * Empty registry.
 */
final class EmptyRegistry implements CommandRegistry {

    static CommandRegistry INSTANCE = new EmptyRegistry();

    @Override
    public String pkg() {
        return "";
    }

    @Override
    public Optional<CommandModel> get(String name) {
        return Optional.empty();
    }
}
