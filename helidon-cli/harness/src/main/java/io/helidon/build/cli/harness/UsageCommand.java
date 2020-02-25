package io.helidon.build.cli.harness;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Built-in usage command.
 */
final class UsageCommand extends CommandModel {

    private static final String BEGIN_SPACING = "  ";
    private static final String COL_SPACING = "    ";

    static final String NAME = "";
    static final Map<String, String> GLOBAL_OPTIONS = createGlobalOptionsMap();

    UsageCommand() {
        super(new CommandInfo(NAME, ""));
    }

    @Override
    boolean visible() {
        return false;
    }

    @Override
    public CommandExecution createExecution(CommandParser parser) {
        return this::execute;
    }

    private static Map<String, String> createGlobalOptionsMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("-D<name>=<value>", "Define a system property");
        map.put("--verbose", CommandModel.VERBOSE_OPTION.description);
        map.put("--debug", CommandModel.DEBUG_OPTION.description);
        return map;
    }

    /**
     * Print a map of options in two aligned columns.
     * @param map map of options
     * @return rendered description
     */
    static String describeOptions(Map<String, String> map) {
        int maxOptNameLength = 0;
        for (String opt : map.keySet()) {
            int optNameLength = opt.length();
            if (optNameLength > maxOptNameLength) {
                maxOptNameLength = optNameLength;
            }
        }
        String out = "";
        Iterator<Entry<String, String>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, String> optEntry = it.next();
            String optName = optEntry.getKey();
            int curColPos = maxOptNameLength - optName.length();
            String colSpacing = "";
            for (int i = 0; i < curColPos; i++) {
                colSpacing += " ";
            }
            out += BEGIN_SPACING + optName + colSpacing + COL_SPACING + optEntry.getValue();
            if (it.hasNext()) {
                out += "\n";
            }
        }
        return out;
    }

    private void execute(CommandContext context) {
        context.logInfo(String.format("\nUsage:\t%s [OPTIONS] COMMAND\n", context.cli().name()));
        context.logInfo(context.cli().description());
        context.logInfo("\nOptions:");
        context.logInfo(describeOptions(GLOBAL_OPTIONS));
        context.logInfo("\nCommands:");
        int maxCmdNameLength = 0;
        for (CommandModel command : context.allCommands()) {
            int cmdNameLength = command.command().name().length();
            if (cmdNameLength > maxCmdNameLength) {
                maxCmdNameLength = cmdNameLength;
            }
        }
        if (maxCmdNameLength > 0) {
            int descColPos = maxCmdNameLength + 4 ;
            for (CommandModel command : context.allCommands()) {
                CommandInfo commandInfo = command.command();
                int curColPos = descColPos - commandInfo.name().length();
                String spacing = "";
                for (int i=0 ; i < curColPos ; i++) {
                    spacing += " ";
                }
                context.logInfo(String.format("  %s%s%s", commandInfo.name(), spacing, commandInfo.description()));
            }
        }
        context.logInfo(String.format("\nRun '%s COMMAND --help' for more information on a command.", context.cli().name()));
    }
}
