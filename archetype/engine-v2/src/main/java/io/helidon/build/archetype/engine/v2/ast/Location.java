package io.helidon.build.archetype.engine.v2.ast;

import java.nio.file.Paths;

/**
 * Location of the elements in the tree.
 */
public final class Location {

    private final String currentDirectory;
    private final String scriptDirectory;

    private Location(String currentDirectory, String scriptDirectory) {
        this.currentDirectory = currentDirectory;
        this.scriptDirectory = scriptDirectory;
    }

    /**
     * Path to the current working directory relative to the archetype root directory.
     *
     * @return current directory
     */
    public String currentDirectory() {
        return currentDirectory;
    }

    /**
     * Path to the directory of the current descriptor script, relative to the archetype root directory.
     *
     * @return script directory
     */
    public String scriptDirectory() {
        return scriptDirectory;
    }

    /**
     * Create a new location.
     *
     * @param currentDirectory current directory
     * @param scriptDirectory  script directory
     * @return location
     */
    public static Location create(String currentDirectory, String scriptDirectory) {
        return new Location(currentDirectory, scriptDirectory);
    }

    /**
     * Create a new empty location.
     *
     * @return location
     */
    public static Location create() {
        return new Location("", "");
    }
}
