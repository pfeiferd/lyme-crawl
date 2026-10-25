package de.hsheilbronn.lymecrawl;

import java.util.Optional;
import java.util.Properties;

/** Typed access to a {@link Properties} set; blank values count as "not set". */
public class PropertyReader {

    private final Properties properties;

    public PropertyReader(Properties properties) {
        this.properties = properties;
    }

    public Optional<String> string(String key) {
        String v = properties.getProperty(key);
        return v == null || v.isBlank() ? Optional.empty() : Optional.of(v.strip());
    }

    public int integer(String key, int defaultValue) {
        return string(key).map(v -> {
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(key + " is not a number: " + v);
            }
        }).orElse(defaultValue);
    }

    public boolean bool(String key, boolean defaultValue) {
        return string(key).map(v -> {
            if (v.equalsIgnoreCase("true")) return true;
            if (v.equalsIgnoreCase("false")) return false;
            throw new IllegalArgumentException(key + " must be true or false: " + v);
        }).orElse(defaultValue);
    }
}
