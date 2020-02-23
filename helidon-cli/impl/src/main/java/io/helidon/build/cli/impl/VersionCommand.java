package io.helidon.build.cli.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandContext;
import io.helidon.build.cli.harness.CommandExecution;
import io.helidon.build.cli.harness.Creator;

/**
 * The {@code version} command.
 */
@Command(name = "version", description = "Print version information")
final class VersionCommand implements CommandExecution {

    private static final String CLI_VERSION_PROPS_RESOURCE = "version.properties";
    private static final String[] CLI_VERSION_PROP_NAMES = new String[] { "Version", "Revision", "Date"};
    private static final String CLI_KEY_PREFIX = "cli.";

    private final CommonOptions commonOptions;

    @Creator
    VersionCommand(CommonOptions commonOptions) {
        this.commonOptions = commonOptions;
    }

    @Override
    public void execute(CommandContext context) {
        InputStream is = VersionCommand.class.getResourceAsStream(CLI_VERSION_PROPS_RESOURCE);
        if (is == null) {
            throw new IllegalStateException("version.properties resource not found");
        }
        Properties props = new Properties();
        try {
            props.load(is);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        int maxPropNameLen = 0;
        for (int i=0 ; i < CLI_VERSION_PROP_NAMES.length ; i++) {
            int propNameLen = CLI_VERSION_PROP_NAMES[i].length();
            if (propNameLen > maxPropNameLen) {
                maxPropNameLen = propNameLen;
            }
        }
        if (maxPropNameLen > 0) {
            context.logInfo("\nCommand line tool:");
            int valueColPos = maxPropNameLen + 4;
            for (int i = 0; i < CLI_VERSION_PROP_NAMES.length; i++) {
                String propName = CLI_VERSION_PROP_NAMES[i];
                int curColPos = valueColPos - propName.length();
                String spacing = "";
                for (int y = 0; y < curColPos; y++) {
                    spacing += " ";
                }
                String propValue = getVersionProperty(props, CLI_KEY_PREFIX + propName.toLowerCase());
                context.logInfo(String.format("  %s%s%s", propName, spacing, propValue));
            }
        }

        context.logInfo("\nProject:");
        context.logInfo(String.format("  // TODO info from project, project=%s", commonOptions.project));
    }

    private static String getVersionProperty(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new IllegalStateException(key + " property not found");
        }
        return value;
    }
}
