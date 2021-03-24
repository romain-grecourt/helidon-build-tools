package io.helidon.build.util;

import java.util.HashMap;
import java.util.Objects;
import java.util.Properties;

/**
 * Utilities to create a {@link HashMap} from {@link Properties}.
 */
public class PropertiesMap extends HashMap<String, String> {

    /**
     * Create a new instance.
     * @param properties properties
     */
    public PropertiesMap(Properties properties) {
        Objects.requireNonNull(properties);
        properties.stringPropertyNames().forEach(k -> put(k, properties.getProperty(k)));
    }
}
