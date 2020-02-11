package io.helidon.build.cli.harness;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
     * Describes a command option.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.FIELD)
    static @interface Option {

        /**
         * The option name.
         * @return option name
         */
        String name();

        /**
         * The option description.
         * @return description
         */
        String description();

        /**
         * The required flag.
         * @return {@code true} if optional, {@code false} if required
         */
        boolean required() default true;
    }
}
