package io.helidon.build.cli.harness;

import io.helidon.build.cli.harness.CommandModel.OptionInfo;
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
public class ExecTest {

    static final CommandRegistry REGISTRY = new TestCommandRegistry();
    static final String CLI_USAGE = resourceAsString("cli-usage.txt");
    static final String HELP_CMD_HELP = resourceAsString("help-cmd-help.txt");
    static final String SIMPLE_CMD_HELP = resourceAsString("simple-cmd-help.txt");
    static final CLIDefinition TEST_CLI = CLIDefinition.create("test-cli", "A test cli");

    static CommandContext ctx() {
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
        assertThat(exec("help", "simple"), is(equalTo(SIMPLE_CMD_HELP)));
        assertThat(exec("simple", "--help"), is(equalTo(SIMPLE_CMD_HELP)));
    }

    @Test
    public void testCmd() {
        assertThat(exec("simple"), is(equalTo("noop\n")));
        assertThat(exec("simple", "--foo"), is(equalTo("foo\n")));
        assertThat(exec("simple", "--bar"), is(equalTo("bar\n")));
    }

    @Test
    public void testCommonOptions() {
        assertThat(exec("common", "--key", "value"), is(equalTo("value\n")));
        assertThat(exec("common", "--foo", "--key", "value"), is(equalTo("foo: value\n")));
//        assertThat(exec("common"), is(equalTo("noop\n")));
        // TODO test missing required param --dir
    }

    private static final class TestCommandRegistry extends CommandRegistry {

        public TestCommandRegistry() {
            super(/* pkg */ "");
            register(new SimpleCommand());
            register(new CommandWithCommonOptions());
        }
    }

    private static final class SimpleCommand extends CommandModel {

        private static final OptionInfo<Boolean> FOO = new OptionInfo<>(Boolean.class, "foo", "Foo option", false);
        private static final OptionInfo<Boolean> BAR = new OptionInfo<>(Boolean.class, "bar", "Bar option", false);

        SimpleCommand() {
            super(new CommandInfo("simple", "A simple test command"));
            addParameter(FOO);
            addParameter(BAR);
        }

        @Override
        public CommandExecution createExecution(CommandParser parser) {
            return (ctx) -> {
                if (parser.resolve(FOO)) {
                    ctx.logInfo("foo");
                } else if (parser.resolve(BAR)) {
                    ctx.logInfo("bar");
                } else {
                    ctx.logInfo("noop");
                }
            };
        }
    }

    private static final class CommandWithCommonOptions extends CommandModel {

        private static final CommonOptionsInfo COMMON_OPTIONS = new CommonOptionsInfo();
        private static final OptionInfo<Boolean> FOO = new OptionInfo<>(Boolean.class, "foo", "Turn on foo mode", false);

        CommandWithCommonOptions() {
            super(new CommandInfo("common", "A test command with common options"));
            addParameter(COMMON_OPTIONS);
            addParameter(FOO);
        }

        @Override
        public CommandExecution createExecution(CommandParser parser) {
            return (ctx) -> {
                String key = COMMON_OPTIONS.getOrCreate(parser).key;
                if (parser.resolve(FOO)) {
                    ctx.logInfo("foo: " + key);
                } else {
                    ctx.logInfo(key);
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

        private static final OptionInfo<String> KEY_OPTION = new OptionInfo<>(String.class, "key", "key option", true);

        private CommonOptionsInfo() {
            super(CommonOptions.class);
            addParameter(KEY_OPTION);
        }

        @Override
        public CommonOptions create(CommandParser parser) {
            return new CommonOptions(parser.resolve(KEY_OPTION));
        }
    }
}
