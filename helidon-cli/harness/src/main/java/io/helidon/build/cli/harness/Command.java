package io.helidon.build.cli.harness;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Describes a command.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Command {

    /**
     * The command name.
     * @return command name
     */
    String name();

    /**
     * The command description.
     * @return command description
     */
    String description();

    /**
     * Name predicate to validate command names.
     */
    static final Predicate<String> NAME_PREDICATE = Pattern.compile("^[a-zA-Z0-9]{1,}$").asMatchPredicate();
}
