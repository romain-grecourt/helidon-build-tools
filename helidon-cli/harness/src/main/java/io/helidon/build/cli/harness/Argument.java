package io.helidon.build.cli.harness;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * Describes a command argument.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PARAMETER)
public @interface Argument {

    /**
     * The option description.
     *
     * @return description
     */
    String description();

    /**
     * The required flag.
     *
     * @return {@code true} if optional, {@code false} if required
     */
    boolean required() default true;

    /**
     * Supported value types.
     */
    static final List<Class<?>> VALUE_TYPES = List.of(String.class, Integer.class, Boolean.class, File.class);
}
