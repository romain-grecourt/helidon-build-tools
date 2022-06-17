/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import io.helidon.build.maven.sitegen.asciidoctor.AsciidocConverter;

import org.slf4j.LoggerFactory;

import static io.helidon.build.common.FileUtils.pathOf;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * A helper class to help with class-path resources.
 */
public abstract class Helper {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Helper.class);

    /**
     * Load a resource directory as a {@link java.nio.file.Path} instance.
     *
     * @param resourcePath the resource path to load
     * @return the created path instance
     * @throws URISyntaxException    if the resource URL cannot be converted to a =
     *                               URI
     * @throws IOException           if an error occurred during {@link FileSystem}
     *                               creation
     * @throws IllegalStateException if the resource path is not found, or if
     *                               the URI scheme is not <code>jar</code> or <code>file</code>
     */
    public static Path loadResourceDirAsPath(String resourcePath)
            throws URISyntaxException, IOException, IllegalStateException {

        // get classloader resource URL
        URL templatesDirURL = AsciidocConverter.class.getResource(resourcePath);
        if (templatesDirURL == null) {
            throw new IllegalStateException("resource not found: " + resourcePath);
        }
        // convert URL to Path
        return pathOf(templatesDirURL.toURI());
    }

    /**
     * Return the {@code String} from an {@link Object} instance.
     *
     * @param obj the instance to convert
     * @return the string value if given a {@code String} instance, or null
     * otherwise
     */
    public static String asString(Object obj) {
        if (!(obj instanceof String)) {
            return null;
        } else {
            return (String) obj;
        }
    }

    /**
     * Copy static resources into the given output directory.
     *
     * @param resources the path to the resources
     * @param outputDir the target output directory where to copy the files
     * @throws IOException if an error occurred during processing
     */
    public static void copyResources(Path resources, Path outputDir) throws IOException {
        try {
            Files.walkFileTree(resources, new FileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!Files.isDirectory(file)) {
                        String targetRelativePath = resources.relativize(file).toString();
                        Path targetPath = outputDir.resolve(targetRelativePath);
                        Files.createDirectories(targetPath.getParent());
                        LOGGER.debug("Copying static resource: {} to {}", targetRelativePath, targetPath);
                        Files.copy(file, targetPath, REPLACE_EXISTING);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException ex) {
                    LOGGER.error("Error while copying static resource: {} - {}", file.getFileName(), ex.getMessage());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            throw new RenderingException("An error occurred during static resource processing ", ex);
        }
    }

    /**
     * Enforce that a given instance is non {@code null}.
     *
     * @param instance the instance to check
     * @param name     the name of the instance used for the exception message
     * @param <T>      arg type
     * @return arg
     * @throws IllegalArgumentException if the supplied instance is {@code null}
     */
    public static <T> T requireNonNull(T instance, String name) throws IllegalArgumentException {
        if (instance == null) {
            throw new IllegalArgumentException(name + " is null");
        }
        return instance;
    }

    /**
     * Enforce that a given instance is non {@code null} and of the given type.
     *
     * @param obj  the instance to check
     * @param type required type
     * @param name the name of the instance used for the exception message
     * @param <T>  required type
     * @return supplied instance casted as the required type
     * @throws IllegalArgumentException if the supplied instance is {@code null} or not of the required type
     */
    public static <T> T requireType(Object obj, Class<T> type, String name) {
        if (obj != null) {
            if (type.isInstance(obj)) {
                return type.cast(obj);
            }
            throw new IllegalArgumentException(name + " is not a " + type.getSimpleName());
        }
        throw new IllegalArgumentException(name + " is null");
    }

    /**
     * Enforce that a given instance is of the given type.
     *
     * @param obj          the instance to check
     * @param type         required type
     * @param defaultValue default value
     * @param name         the name of the instance used for the exception message
     * @param <T>          required type
     * @return supplied instance casted as the required type
     * @throws IllegalArgumentException if the supplied instance is not of the required type
     */
    public static <T> T requireType(Object obj, Class<T> type, T defaultValue, String name) {
        if (obj != null) {
            if (type.isInstance(obj)) {
                return type.cast(obj);
            }
            throw new IllegalArgumentException(name + " is not a " + type.getSimpleName());
        }
        return defaultValue;
    }

    /**
     * Verify that a given {@link String} instance is non {@code null} and non-empty.
     *
     * @param str  the instance to check
     * @param name the name of the instance used for the exception message
     * @return supplied instance
     * @throws IllegalArgumentException if the supplied instance is {@code null} or empty
     */
    public static String requireValidString(String str, String name) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException(name + " is null or empty");
        }
        return str;
    }

    /**
     * Verify that a given {@link Path} instance is non {@code null} and exists.
     *
     * @param file the instance to check
     * @param name the name of the instance used for the exception message
     * @return supplied instance
     * @throws IllegalArgumentException if the supplied instance is {@code null} or does not exist
     */
    @SuppressWarnings("UnusedReturnValue")
    public static Path requireExistentFile(Path file, String name) {
        if (file == null) {
            throw new IllegalArgumentException(name + "is null");
        }
        if (!Files.exists(file)) {
            throw new IllegalArgumentException(file.toAbsolutePath() + " does not exist");
        }
        return file;
    }

    /**
     * Verify that a given {@link Path} instance is non {@code null}, exists and is a file.
     *
     * @param file the instance to check
     * @param name the name of the instance used for the exception message
     * @return supplied instance
     * @throws IllegalArgumentException if the supplied instance is {@code null}, does not exist, or is not a file
     */
    public static Path requireValidFile(Path file, String name) {
        requireExistentFile(file, name);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(file.toAbsolutePath() + " is not a file");
        }
        return file;
    }

    /**
     * Verify that a given {@link Path} instance is non {@code null}, exists and is a directory.
     *
     * @param dir  the instance to check
     * @param name the name of the instance used for the exception message
     * @return file
     * @throws IllegalArgumentException if the supplied instance is {@code null}, does not exist, or is not a directory
     */
    public static Path requireValidDirectory(Path dir, String name) {
        requireExistentFile(dir, name);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException(dir.toAbsolutePath() + " is not a directory");
        }
        return dir;
    }

    /**
     * Get the file extension in the given file path.
     *
     * @param filepath the file path with an extension
     * @return the file extension
     */
    public static String fileExtension(String filepath) {
        int index = filepath.lastIndexOf(".");
        return index < 0 ? null : filepath.substring(index + 1);
    }

    /**
     * Replace the file extension in the given file path.
     *
     * @param filepath the file path to use
     * @param ext      the new file extension
     * @return the filepath with the new extension
     */
    public static String replaceFileExt(String filepath, String ext) {
        String path = filepath;
        path = path.substring(0, path.lastIndexOf("."));
        return path + ext;
    }

    /**
     * Get the relative path for a given source file within the source directory.
     *
     * @param sourceDir the source directory
     * @param source    the source file
     * @return the relative path of the source file
     */
    public static String relativePath(Path sourceDir, Path source) {
        return sourceDir.relativize(source).toString()
                        // force UNIX style path on Windows
                        .replace("\\", "/");
    }
}
