package io.helidon.build.archetype.engine.v2.ast;

import io.helidon.build.common.GenericType;
import io.helidon.build.common.Pair;

import java.util.List;

/**
 * All supported value types.
 */
public final class ValueTypes {

    /**
     * String.
     */
    public static final GenericType<String> STRING = GenericType.create(String.class);

    /**
     * Boolean.
     */
    public static final GenericType<Boolean> BOOLEAN = GenericType.create(Boolean.class);

    /**
     * Integer.
     */
    public static final GenericType<Integer> INT = GenericType.create(Integer.class);

    /**
     * List of strings.
     */
    public static final GenericType<List<String>> STRING_LIST = new GenericType<>() {};

    /**
     * List of string pairs.
     */
    public static final GenericType<List<Pair<String, String>>> STRING_PAIR_LIST = new GenericType<>() {};

    private ValueTypes() {
    }
}
