package io.helidon.build.cli.harness;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Describes a command option.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PARAMETER)
public @interface Option {

    /**
     * The option name.
     *
     * @return option name
     */
    String name();

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
     * Name predicate to validate option names.
     */
    static final Predicate<String> NAME_PREDICATE = Pattern.compile("^[a-zA-Z0-9]{1,}[-]?[a-zA-Z0-9]{1,}$").asMatchPredicate();

    /**
     * Supported value types.
     */
    static final List<Class<?>> VALUE_TYPES = List.of(String.class, Integer.class, File.class);

    /**
     * Supported multi value types.
     */
    static final List<Class<?>> MULTI_TYPES = List.of(Collection.class, List.class);
}
