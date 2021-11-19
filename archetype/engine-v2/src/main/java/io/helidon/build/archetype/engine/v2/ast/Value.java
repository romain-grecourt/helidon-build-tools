package io.helidon.build.archetype.engine.v2.ast;

import io.helidon.build.common.GenericType;

import java.util.List;

/**
 * Value.
 */
public interface Value {

    /**
     * Get the value type.
     *
     * @return type
     */
    GenericType<?> type();

    /**
     * Get this value as the given type.
     *
     * @param type type
     * @param <T>  actual type
     * @return instance as the given type
     * @throws ValueTypeException if this instance type does not match the given type
     */
    <T> T as(GenericType<T> type);

    /**
     * Get this value as a {@code string}.
     *
     * @return string
     */
    default String asString() {
        return as(ValueTypes.STRING);
    }

    /**
     * Get this value as a boolean.
     *
     * @return boolean
     */
    default Boolean asBoolean() {
        return as(ValueTypes.BOOLEAN);
    }

    /**
     * Get this value as an int.
     *
     * @return int
     */
    default Integer asInt() {
        return as(ValueTypes.INT);
    }

    /**
     * Get this value as a list.
     *
     * @return list
     */
    default List<String> asList() {
        return as(ValueTypes.STRING_LIST);
    }

    /**
     * Get this value as a read-only value.
     *
     * @return read-only value
     */
    default Value asReadOnly() {
        return this;
    }

    /**
     * Exception raised for unexpected type usages.
     */
    final class ValueTypeException extends IllegalStateException {

        /**
         * Create a new value type exception
         *
         * @param actual   the actual type
         * @param expected the unexpected type
         */
        ValueTypeException(GenericType<?> actual, GenericType<?> expected) {
            super(String.format("Cannot get a %s value as %s", actual, expected));
        }
    }
}
