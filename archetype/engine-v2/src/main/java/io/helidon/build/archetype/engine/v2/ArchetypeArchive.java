/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.build.archetype.engine.v2;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.build.common.VirtualFileSystem;

/**
 * Archetype archive.
 */
public final class ArchetypeArchive implements Closeable {

    private final FileSystem fileSystem;

    private ArchetypeArchive(Builder builder) {
        this.fileSystem = builder.fileSystem;
    }

    /**
     * Get a {@link Path} relative to the root directory in the archetype.
     *
     * @param path path
     * @return Path
     */
    public Path path(String path) {
        return fileSystem.getPath(path);
    }

    /**
     * Get all the paths in the archetype.
     *
     * @return List of paths
     */
    public List<String> path() {
        try (Stream<Path> stream = Files.walk(fileSystem.getRootDirectories().iterator().next())) {
            return stream
                    .filter(file -> !Files.isDirectory(file))
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void close() throws IOException {
        fileSystem.close();
    }

    /**
     * Create a new builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Archetype archive builder.
     */
    public static final class Builder {

        FileSystem fileSystem;

        private Builder() {
        }

        /**
         * Set the path of the archive file system.
         *
         * @param path path
         * @return this builder
         */
        public Builder path(Path path) {
            try {
                this.fileSystem = FileSystems.newFileSystem(path, null);
                return this;
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        /**
         * Set the path of the directory to create a virtual file system.
         *
         * @param path path
         * @return this builder
         */
        public Builder dir(Path path) {
            this.fileSystem = VirtualFileSystem.create(path);
            return this;
        }

        /**
         * Build the instance.
         *
         * @return archetype archive
         */
        public ArchetypeArchive build() {
            return new ArchetypeArchive(this);
        }
    }
}
