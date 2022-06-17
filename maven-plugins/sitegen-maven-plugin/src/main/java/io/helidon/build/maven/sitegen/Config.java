/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.build.maven.sitegen;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.build.common.SubstitutionVariables;

/**
 * Simple config.
 */
@SuppressWarnings("unused")
public final class Config {

    /**
     * Config mapping exception.
     */
    public static class MappingException extends RuntimeException {

        private MappingException(Class<?> actual, Class<?> expected) {
            super(String.format("Cannot get a %s as %s", actual.getSimpleName(), expected.getSimpleName()));
        }

        private MappingException(Throwable cause) {
            super(cause);
        }
    }

    private final Object value;
    private final Config parent;
    private final SubstitutionVariables substitutions;

    private Config(Object value, Map<String, String> properties) {
        this.value = value;
        this.parent = null;
        this.substitutions = SubstitutionVariables.of(properties);
    }

    private Config(Object value, Config parent) {
        this.value = value;
        this.parent = parent;
        this.substitutions = parent.substitutions;
    }

    /**
     * Get the parent node.
     *
     * @return parent node
     */
    public Config parent() {
        return parent;
    }

    /**
     * Test if this is a root config node.
     *
     * @return {@code true} if root, {@code false} otherwise
     */
    public boolean isRoot() {
        return parent == this;
    }

    /**
     * Get a config node.
     *
     * @param key key
     * @return node
     */
    public Config get(String key) {
        Object v = value != null ? ((Map<?, ?>) value).get(key) : null;
        return new Config(v, this);
    }

    /**
     * Test if this node contains the given key.
     *
     * @param key key
     * @return {@code true} if the node contains the key, {@code false} otherwise
     */
    public boolean containsKey(String key) {
        if (value instanceof Map) {
            return ((Map<?, ?>) value).containsKey(key);
        }
        return false;
    }

    /**
     * Get this config node as an optional.
     *
     * @return optional
     */
    public Optional<Config> asOptional() {
        return value == null ? Optional.empty() : Optional.of(this);
    }

    /**
     * Get the value as string.
     *
     * @return optional
     */
    public Optional<String> asString() {
        return as(String.class);
    }

    /**
     * Map the value using a mapping function.
     *
     * @param mapper mapping function
     * @param <T>    requested type
     * @return optional
     */
    public <T> Optional<T> as(Function<Object, T> mapper) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(mapper.apply(value));
        } catch (Throwable ex) {
            throw new MappingException(ex);
        }
    }

    /**
     * Map the value to a given type.
     *
     * @param type requested type
     * @param <T>  requested type
     * @return optional
     */
    public <T> Optional<T> as(Class<T> type) {
        return as(o -> cast(o, type));
    }

    /**
     * Map the value to a list of nodes.
     *
     * @return optional
     */
    public Optional<List<Config>> asNodeList() {
        return asList(e -> new Config(e, this));
    }

    /**
     * Map the value to a list of object.
     *
     * @return optional
     */
    public Optional<List<Object>> asList() {
        return asList(Function.identity());
    }

    /**
     * Map the value to a list of a given type.
     *
     * @param type requested type
     * @param <T>  requested type
     * @return optional
     */
    public <T> Optional<List<T>> asList(Class<T> type) {
        return asList(e -> cast(e, type));
    }

    /**
     * Map the value to a list using a mapping function.
     *
     * @param mapper mapping function
     * @param <T>    requested type
     * @return optional
     */
    public <T> Optional<List<T>> asList(Function<Object, T> mapper) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof List) {
            return Optional.of(((List<?>) value).stream()
                                                .map(mapper)
                                                .collect(Collectors.toList()));
        }
        throw new MappingException(value.getClass(), List.class);
    }

    /**
     * Map the value to a map using a mapping function.
     *
     * @param mapper mapping function
     * @param <T>    requested type
     * @return optional
     */
    public <T> Optional<Map<String, T>> asMap(Function<Object, T> mapper) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Map) {
            return Optional.of(((Map<?, ?>) value)
                    .entrySet()
                    .stream()
                    .map(e -> Map.entry(e.getKey().toString(), mapper.apply(e.getValue())))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
        throw new MappingException(value.getClass(), Map.class);
    }

    /**
     * Map the value to a map of object.
     *
     * @return optional
     */
    public Optional<Map<String, Object>> asMap() {
        return asMap(Function.identity());
    }

    /**
     * Map the value to a map of a given type.
     *
     * @param type requested type
     * @param <T>  requested type
     * @return optional
     */
    public <T> Optional<Map<String, T>> asMap(Class<T> type) {
        return asMap(e -> cast(e, type));
    }

    /**
     * Create a new instance.
     *
     * @param value      underlying value
     * @param properties substitution properties
     * @return new instance
     */
    public static Config create(Object value, Map<String, String> properties) {
        return new Config(value, properties);
    }

    /**
     * Create a new instance.
     *
     * @param value underlying value
     * @return new instance
     */
    public static Config create(Object value) {
        return new Config(value, Map.of());
    }

    private static <T> T cast(Object value, Class<T> type) {
        if (value != null) {
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            throw new MappingException(value.getClass(), type);
        }
        return null;
    }
}
