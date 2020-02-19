package io.helidon.build.cli.harness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test the built-in commands and options.
 */
public class BuiltInTest {

    static final CommandRegistry REGISTRY = new TestCommandRegistry();
    static final String CLI_USAGE = resourceAsString("cli-usage.txt");
    static final String HELP_CMD_HELP = resourceAsString("help-cmd-help.txt");
    static final String TEST_CMD_HELP = resourceAsString("test-cmd-help.txt");

    static CommandContext ctx() {
        return CommandContext.create(REGISTRY, "test-cli", "A test cli");
    }

    static String resourceAsString(String name) {
        InputStream is = BuiltInTest.class.getResourceAsStream(name);
        try {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    static String exec(CommandContext ctx, String... args) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream stdout = System.out;
        try {
            System.setOut(new PrintStream(baos));
            CommandRunner.execute(ctx, args);
        } finally {
            System.setOut(stdout);
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    static String exec(String... args) {
        return exec(ctx(), args);
    }

    @Test
    public void testUsage() {
        assertThat(exec("--help"), is(equalTo(CLI_USAGE)));
        assertThat(exec("help"), is(equalTo(CLI_USAGE)));
        assertThat(exec(), is(equalTo(CLI_USAGE)));
    }

    @Test
    public void testHelp() {
        assertThat(exec("help", "--help"), is(equalTo(HELP_CMD_HELP)));
        assertThat(exec("help", "help"), is(equalTo(HELP_CMD_HELP)));
        assertThat(exec("help", "test"), is(equalTo(TEST_CMD_HELP)));
        assertThat(exec("test", "--help"), is(equalTo(TEST_CMD_HELP)));
    }

    private static final class TestCommandRegistry extends CommandRegistry {

        public TestCommandRegistry() {
            super("");
            register(new TestCommand());
        }
    }

    private static final class TestCommand extends CommandModel {

        TestCommand() {
            super(new CommandInfo("test", "A test command"));
            addParameter(new OptionInfo<>(String.class, "clean", "Clean the directory", true));
            addParameter(new OptionInfo<>(String.class, "debug", "Turn on debug mode", true));
        }

        @Override
        public CommandExecution createExecution(CommandParser parser) {
            return (ctx) -> {};
        }
    }
}
