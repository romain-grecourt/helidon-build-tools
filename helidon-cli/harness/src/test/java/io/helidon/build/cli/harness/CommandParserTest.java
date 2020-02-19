package io.helidon.build.cli.harness;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;

/**
 * Test {@link CommandParser}.
 */
public class CommandParserTest {

    @Test
    public void testTrim() {
        CommandParser parser = CommandParser.create("  cli  ", " --foo ", "   bar ");
        assertThat(parser.commandName().isPresent(), is(true));
        assertThat(parser.commandName().get(), is("cli"));
        assertThat(parser.error().isEmpty(), is(true));
        assertThat(parser.params().size(), is(1));
        assertThat(parser.params(), hasKey("foo"));
        assertThat(parser.params().get("foo"), is(instanceOf(CommandParser.KeyValueParam.class)));
        assertThat(((CommandParser.KeyValueParam)parser.params().get("foo")).value, is("bar"));
    }

    @Test
    public void testUpperCase() {
        CommandParser parser = CommandParser.create("CLI", "--HELP");
        assertThat(parser.commandName().isPresent(), is(true));
        assertThat(parser.error().isEmpty(), is(true));
        assertThat(parser.commandName().get(), is("cli"));
        assertThat(parser.params().size(), is(1));
        assertThat(parser.params(), hasKey("help"));
        assertThat(parser.params().get("help"), is(instanceOf(CommandParser.FlagParam.class)));

        parser = CommandParser.create("cLi", "--HeLp");
        assertThat(parser.commandName().isPresent(), is(true));
        assertThat(parser.error().isEmpty(), is(true));
        assertThat(parser.commandName().get(), is("cli"));
        assertThat(parser.params().size(), is(1));
        assertThat(parser.params(), hasKey("help"));
        assertThat(parser.params().get("help"), is(instanceOf(CommandParser.FlagParam.class)));

        parser = CommandParser.create("cLi", "--fOo", "bAR");
        assertThat(parser.commandName().isPresent(), is(true));
        assertThat(parser.error().isEmpty(), is(true));
        assertThat(parser.commandName().get(), is("cli"));
        assertThat(parser.params().size(), is(1));
        assertThat(parser.params(), hasKey("foo"));
        assertThat(parser.params().get("foo"), is(instanceOf(CommandParser.KeyValueParam.class)));
        assertThat(((CommandParser.KeyValueParam)parser.params().get("foo")).value, is("bar"));
    }

    @Test
    public void testInvalidCommandNames() {
        CommandParser parser = CommandParser.create("-cli", "--help");
        assertThat(parser.commandName().isEmpty(), is(true));
        assertThat(parser.error().isPresent(), is(true));
        assertThat(parser.error().get(), is(CommandParser.INVALID_COMMAND_NAME + ": -cli"));

        parser = CommandParser.create("cli-", "--help");
        assertThat(parser.commandName().isEmpty(), is(true));
        assertThat(parser.error().isPresent(), is(true));
        assertThat(parser.error().get(), is(CommandParser.INVALID_COMMAND_NAME + ": cli-"));

        parser = CommandParser.create("great-cli", "--help");
        assertThat(parser.commandName().isEmpty(), is(true));
        assertThat(parser.error().isPresent(), is(true));
        assertThat(parser.error().get(), is(CommandParser.INVALID_COMMAND_NAME + ": great-cli"));

        parser = CommandParser.create("great_cli", "--help");
        assertThat(parser.commandName().isEmpty(), is(true));
        assertThat(parser.error().isPresent(), is(true));
        assertThat(parser.error().get(), is(CommandParser.INVALID_COMMAND_NAME + ": great_cli"));
    }

    @Test
    public void testInvalidOptionName() {
        CommandParser parser = CommandParser.create("cli", "---help");
        assertThat(parser.commandName().isPresent(), is(true));
        assertThat(parser.commandName().get(), is("cli"));
        assertThat(parser.error().isPresent(), is(true));
        assertThat(parser.error().get(), is(CommandParser.INVALID_OPTION_NAME + ": -help"));

        parser = CommandParser.create("cli", "--help-");
        assertThat(parser.commandName().isPresent(), is(true));
        assertThat(parser.commandName().get(), is("cli"));
        assertThat(parser.error().isPresent(), is(true));
        assertThat(parser.error().get(), is(CommandParser.INVALID_OPTION_NAME + ": help-"));

        parser = CommandParser.create("cli", "--please_help");
        assertThat(parser.commandName().isPresent(), is(true));
        assertThat(parser.commandName().get(), is("cli"));
        assertThat(parser.error().isPresent(), is(true));
        assertThat(parser.error().get(), is(CommandParser.INVALID_OPTION_NAME + ": please_help"));

        parser = CommandParser.create("cli", "--please-help");
        assertThat(parser.commandName().isPresent(), is(true));
        assertThat(parser.commandName().get(), is("cli"));
        assertThat(parser.params().size(), is(1));
        assertThat(parser.params(), hasKey("please-help"));
        assertThat(parser.params().get("please-help"), is(instanceOf(CommandParser.FlagParam.class)));
    }

    @Test
    public void testRepeatingOption() {
        CommandParser parser = CommandParser.create("cli", "--foo", "--foo", "bar");
        assertThat(parser.commandName().isPresent(), is(true));
        assertThat(parser.commandName().get(), is("cli"));
        assertThat(parser.error().isPresent(), is(true));
        assertThat(parser.error().get(), is(CommandParser.INVALID_REPEATING_OPTION + ": foo"));

        parser = CommandParser.create("cli", "--foo", "1", "--foo");
        assertThat(parser.commandName().isPresent(), is(true));
        assertThat(parser.commandName().get(), is("cli"));
        assertThat(parser.error().isPresent(), is(true));
        assertThat(parser.error().get(), is(CommandParser.INVALID_REPEATING_OPTION + ": foo"));

        parser = CommandParser.create("cli", "--foo", "bar1", "--foo", "bar2");
        assertThat(parser.commandName().isPresent(), is(true));
        assertThat(parser.commandName().get(), is("cli"));
        assertThat(parser.error().isEmpty(), is(true)); 
        assertThat(parser.params().size(), is(1));
        assertThat(parser.params(), hasKey("foo"));
        assertThat(parser.params().get("foo"), is(instanceOf(CommandParser.KeyValuesParam.class)));
        assertThat(((CommandParser.KeyValuesParam) parser.params().get("foo")).values, contains("bar1", "bar2"));
    }
}
