package io.helidon.build.cli.harness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import io.helidon.build.cli.harness.CommandContext.ExitStatus;
import io.helidon.build.cli.harness.CommandModel.KeyValueInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test the built-in commands and options.
 */
public class ExecTest {

    static final CommandRegistry REGISTRY = new TestCommandRegistry();
    static final String CLI_USAGE = resourceAsString("cli-usage.txt");
    static final String HELP_CMD_HELP = resourceAsString("help-cmd-help.txt");
    static final String SIMPLE_CMD_HELP = resourceAsString("simple-cmd-help.txt");
    static final CLIDefinition TEST_CLI = CLIDefinition.create("test-cli", "A test cli");

    static CommandContext context() {
        return CommandContext.create(REGISTRY, TEST_CLI);
    }

    static String resourceAsString(String name) {
        InputStream is = ExecTest.class.getResourceAsStream(name);
        try {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    static String exec(CommandContext context, String... args) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream stdout = System.out;
        try {
            System.setOut(new PrintStream(baos));
            CommandRunner.execute(context, args);
        } finally {
            System.setOut(stdout);
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    static String exec(String... args) {
        return exec(context(), args);
    }

    @Test
    public void testUsage() {
        assertThat(exec("--help"), is(CLI_USAGE));
        assertThat(exec("help"), is(CLI_USAGE));
        assertThat(exec(), is(CLI_USAGE));
    }

    @Test
    public void testHelp() {
        assertThat(exec("help", "--help"), is(HELP_CMD_HELP));
        assertThat(exec("help", "help"), is(HELP_CMD_HELP));
        assertThat(exec("help", "simple"), is(SIMPLE_CMD_HELP));
        assertThat(exec("simple", "--help"), is(SIMPLE_CMD_HELP));
    }

    @Test
    public void testCmd() {
        assertThat(exec("simple"), is("noop\n"));
        assertThat(exec("simple", "--foo"), is("foo\n"));
        assertThat(exec("simple", "--bar"), is("bar\n"));
    }

    @Test
    public void testCommonOptions() {
        assertThat(exec("common", "--key", "value"), is("value\n"));
        assertThat(exec("common", "--foo", "--key", "value"), is(equalTo("foo: value\n")));
        CommandContext context = context();
        exec(context, "common");
        assertThat(context.exitAction().status, is(ExitStatus.FAILURE));
        assertThat(context.exitAction().message, is("Missing required option: key"));
    }

    private static final class TestCommandRegistry extends CommandRegistry {

        public TestCommandRegistry() {
            super(/* pkg */ "");
            register(new SimpleCommand());
            register(new CommandWithCommonOptions());
        }
    }

    private static final class SimpleCommand extends CommandModel {

        private static final FlagInfo FOO = new FlagInfo("foo", "Foo option");
        private static final FlagInfo BAR = new FlagInfo("bar", "Bar option");

        SimpleCommand() {
            super(new CommandInfo("simple", "A simple test command"));
            addParameter(FOO);
            addParameter(BAR);
        }

        @Override
        public CommandExecution createExecution(CommandParser parser) {
            return (context) -> {
                if (parser.resolve(FOO)) {
                    context.logInfo("foo");
                } else if (parser.resolve(BAR)) {
                    context.logInfo("bar");
                } else {
                    context.logInfo("noop");
                }
            };
        }
    }

    private static final class CommandWithCommonOptions extends CommandModel {

        private static final CommonOptionsInfo COMMON_OPTIONS = new CommonOptionsInfo();
        private static final FlagInfo FOO = new FlagInfo("foo", "Turn on foo mode");

        CommandWithCommonOptions() {
            super(new CommandInfo("common", "A test command with common options"));
            addParameter(COMMON_OPTIONS);
            addParameter(FOO);
        }

        @Override
        public CommandExecution createExecution(CommandParser parser) {
            return (context) -> {
                String key = COMMON_OPTIONS.resolve(parser).key;
                if (parser.resolve(FOO)) {
                    context.logInfo("foo: " + key);
                } else {
                    context.logInfo(key);
                }
            };
        }
    }

    private static final class CommonOptions {

        final String key;

        CommonOptions(String key) {
            this.key = key;
        }
    }

    private static final class CommonOptionsInfo extends CommandParameters.CommandFragmentInfo<CommonOptions> {

        private static final KeyValueInfo<String> KEY_OPTION = new KeyValueInfo<>(String.class, "key", "key option", null);

        private CommonOptionsInfo() {
            super(CommonOptions.class);
            addParameter(KEY_OPTION);
        }

        @Override
        public CommonOptions resolve(CommandParser parser) {
            return new CommonOptions(parser.resolve(KEY_OPTION));
        }
    }
}
