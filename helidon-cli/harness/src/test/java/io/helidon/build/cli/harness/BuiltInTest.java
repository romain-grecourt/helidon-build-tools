package io.helidon.build.cli.harness;

import org.junit.jupiter.api.Test;

/**
 * Test the built-in commands and options.
 */
public class BuiltInTest {

    static final CommandRegistry REGISTRY = new TestCommandRegistry();

    private static CommandContext ctx() {
        return CommandContext.create(REGISTRY, "test-cli", "A test cli");
    }

    @Test
    public void testUsage() {
        CommandContext ctx = ctx();
        CommandRunner.execute(ctx, "--help");
        CommandRunner.execute(ctx, "help");
    }

    @Test
    public void testHelp() {
        CommandContext ctx = ctx();
        CommandRunner.execute(ctx, "help --help");
        CommandRunner.execute(ctx, "help test");
    }

    private static final class TestCommandRegistry extends CommandRegistry {

        public TestCommandRegistry() {
            super("");
            register(new TestCommand());
        }
    }

    private static final class TestCommand extends CommandModel {

        public TestCommand() {
            super(new CommandInfo("test", "A test command"));
            addParameter(new OptionInfo<>(String.class, "clean", "Clean the directory", true, Option.Scope.ANY));
            addParameter(new OptionInfo<>(String.class, "debug", "Turn on debug mode", true, Option.Scope.ANY));
        }

        @Override
        public CommandExecution createExecution(CommandParser parser) {
            return (ctx) -> {};
        }
    }
}
