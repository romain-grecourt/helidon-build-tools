/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.build.common;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import io.helidon.build.common.test.utils.TestFiles;

import org.junit.jupiter.api.Test;

import static io.helidon.build.common.Strings.normalizeNewLines;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.isSymbolicLink;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.readString;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link VirtualFileSystem}.
 */
class VirtualFileSystemTest {

    private static final Path ROOT = TestFiles.targetDir(VirtualFileSystemTest.class)
                                              .resolve("test-classes/vfs")
                                              .normalize();


    private static FileSystem vfs() {
        return VirtualFileSystem.create(ROOT);
    }

    private static Path echo(Path path, String msg) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(path);
        writer.write(msg);
        writer.flush();
        writer.close();
        return path;
    }

    @Test
    void testRoot() {
        FileSystem fs = vfs();
        Iterator<Path> it = fs.getRootDirectories().iterator();
        assertThat(it.hasNext(), is(true));
        assertThat(it.next().toString(), is("/"));
        assertThat(it.hasNext(), is(false));
        assertThat(fs.getPath("/").toAbsolutePath().toString(), is("/"));
        assertThat(fs.getPath("").toAbsolutePath().toString(), is("/"));
        //noinspection ResultOfMethodCallIgnored
        InvalidPathException ex = assertThrows(InvalidPathException.class, () -> fs.getPath("../../").toAbsolutePath());
        assertThat(ex.getMessage(), startsWith("Not within virtual root"));
    }

    @Test
    void testNormalize() {
        FileSystem fs = vfs();
        Path red = fs.getPath("red");
        assertThat(red.resolve("./blue").toString(), is("red/./blue"));
        assertThat(red.resolve("./blue").normalize().toString(), is("red/blue"));
        assertThat(red.resolve("blue/../green").toString(), is("red/blue/../green"));
        assertThat(red.resolve("blue/../green").normalize().toString(), is("red/green"));
    }

    @Test
    void testRelativize() {
        FileSystem fs = vfs();
        Path red = fs.getPath("red");
        Path green = red.resolve("blue/green");
        assertThat(red.relativize(green).toString(), is("blue/green"));
        assertThat(green.relativize(red).toString(), is("../.."));
    }

    @Test
    void testGetRoot() {
        assertThat(vfs().getPath("red/blue/green").toAbsolutePath().getRoot().toString(), is("/"));
    }

    @Test
    void testGetParent() {
        Path green = vfs().getPath("red/blue/green");
        assertThat(green.getParent().toString(), is("red/blue"));
        assertThat(green.getParent().getParent().toString(), is("red"));
        assertThat(green.getParent().getParent().getParent(), is(nullValue()));
    }

    @Test
    void testGetFileName() {
        FileSystem fs = vfs();
        Path green = fs.getPath("red/blue/green");
        assertThat(green.getFileName().toString(), is("green"));
        Path blue = fs.getPath("red/blue/green/..");
        assertThat(blue.getFileName().toString(), is(".."));
        assertThat(blue.normalize().getFileName().toString(), is("blue"));
    }

    @Test
    void testNameCount() {
        FileSystem fs = vfs();
        Path green = fs.getPath("red/blue/green");
        assertThat(green.getNameCount(), is(3));
    }

    @Test
    void testGetName() {
        FileSystem fs = vfs();
        Path green = fs.getPath("red/blue/green");
        assertThat(green.getName(0).toString(), is("red"));
        assertThat(green.getName(1).toString(), is("blue"));
        assertThat(green.getName(2).toString(), is("green"));
    }

    @Test
    void testSubPath() {
        FileSystem fs = vfs();
        Path green = fs.getPath("red/blue/green");
        assertThat(green.subpath(0, 1).toString(), is("red"));
        assertThat(green.subpath(0, 2).toString(), is("red/blue"));
        assertThat(green.subpath(1, 2).toString(), is("blue"));
        assertThat(green.subpath(1, 3).toString(), is("blue/green"));
        assertThat(green.subpath(0, 3).toString(), is("red/blue/green"));
    }

    @Test
    void testToRealPath() throws IOException {
        FileSystem fs = VirtualFileSystem.create(TestFiles.targetDir(VirtualFileSystemTest.class)
                                                          .resolve("../src/test/resources/vfs")
                                                          .normalize());
        assertThat(fs.getPath("green").toRealPath().toString(), is("blue"));
    }

    @Test
    void testToUri() {
        assertThat(vfs().getPath("green").toUri().toString(),
                is(String.format("virtual:file://%s/!green", ROOT)));
    }

    @Test
    void testIsAbsolute() {
        Path green = vfs().getPath("green");
        assertThat(green.isAbsolute(), is(false));
        assertThat(green.toAbsolutePath().isAbsolute(), is(true));
        Path red = green.resolve("red");
        assertThat(red.isAbsolute(), is(false));
        assertThat(red.toAbsolutePath().isAbsolute(), is(true));
        assertThat(green.relativize(red).isAbsolute(), is(false));
    }

    @Test
    void testStartsWith() {
        FileSystem fs = vfs();
        Path green = fs.getPath("green");
        Path blue = fs.getPath("green/blue");
        Path red = fs.getPath("green/blue/red");
        assertThat(blue.startsWith(green), is(true));
        assertThat(red.startsWith(green), is(true));
        assertThat(red.startsWith(blue), is(true));
        assertThat(blue.startsWith("green"), is(true));
        assertThat(red.startsWith("green"), is(true));
        assertThat(red.startsWith("green/blue"), is(true));
    }

    @Test
    void testEndsWith() {
        FileSystem fs = vfs();
        Path green = fs.getPath("green");
        Path blue = fs.getPath("green/blue");
        Path red = fs.getPath("green/blue/red");
        assertThat(green.endsWith("green"), is(true));
        assertThat(blue.endsWith("blue"), is(true));
        assertThat(blue.endsWith("green/blue"), is(true));
        assertThat(red.endsWith("red"), is(true));
        assertThat(red.endsWith("blue/red"), is(true));
        assertThat(red.endsWith("green/blue/red"), is(true));
        assertThat(red.endsWith(red.getFileName()), is(true));
        assertThat(red.endsWith(red.subpath(2, 3)), is(true));
    }

    @Test
    void testResolveSibling() {
        FileSystem fs = vfs();
        Path green = fs.getPath("green/blue");
        assertThat(green.resolveSibling("red").toString(), is("green/red"));
    }

    @Test
    void testToFile() {
        FileSystem fs = vfs();
        assertThat(fs.getPath("blue").toFile().getAbsolutePath(), is(ROOT.resolve("blue").toFile().getAbsolutePath()));
    }

    @Test
    void testWalk() throws IOException {
        FileSystem fs = vfs();
        List<String> paths = Files.walk(fs.getPath("/")).map(Path::toString).collect(Collectors.toList());
        assertThat(paths, hasItems("/", "dir1", "dir1/file.txt", "blue", "green"));
    }

    @Test
    void testInputStream() throws IOException {
        FileSystem fs = vfs();
        InputStream is = newInputStream(fs.getPath("dir1/file.txt"));
        assertThat(normalizeNewLines(new String(is.readAllBytes(), UTF_8)), is("foo\n"));
    }

    @Test
    void testOutputStream() throws IOException {
        FileSystem fs = vfs();
        Path path = echo(fs.getPath("dir1/testOutputStream.txt"), "foo\n");
        assertThat(normalizeNewLines(readString(path)), is("foo\n"));
    }

    @Test
    void testSymbolicLink() {
        FileSystem fs = VirtualFileSystem.create(TestFiles.targetDir(VirtualFileSystemTest.class)
                                                          .resolve("../src/test/resources/vfs")
                                                          .normalize());
        assertThat(isSymbolicLink(fs.getPath("green")), is(true));
    }

    @Test
    void testDelete() throws IOException {
        FileSystem fs = vfs();
        Path path = Files.createTempFile(fs.getPath("dir1"), "testDelete", null);
        assertThat(Files.exists(path), is(true));
        Files.delete(path);
        assertThat(Files.exists(path), is(false));
    }

    @Test
    void testCopy() throws IOException {
        FileSystem fs = vfs();
        Path source = echo(fs.getPath("dir1/testCopy.txt"), "bar\n");
        Path target = fs.getPath("dir1/testCopy-2.txt");
        Files.copy(source, target, REPLACE_EXISTING);
        assertThat(Files.exists(source), is(true));
        assertThat(Files.exists(target), is(true));
        assertThat(normalizeNewLines(readString(target)), is("bar\n"));
    }

    @Test
    void testMove() throws IOException {
        FileSystem fs = vfs();
        Path source = echo(fs.getPath("dir1/testMove.txt"), "bar\n");
        Path target = fs.getPath("dir1/testMove-src.txt");
        Files.move(source, target, REPLACE_EXISTING);
        assertThat(Files.exists(source), is(false));
        assertThat(Files.exists(target), is(true));
        assertThat(normalizeNewLines(readString(target)), is("bar\n"));
    }

    @Test
    void testIsHidden() throws IOException {
        FileSystem fs = vfs();
        Path path1 = echo(fs.getPath("dir1/.testIsHidden.txt"), "bar\n");
        assertThat(Files.isHidden(path1), is(true));
        Path path2 = echo(fs.getPath("dir1/testIsHidden.txt"), "bar\n");
        assertThat(Files.isHidden(path2), is(false));
    }

    @Test
    void testResolveAbsolute() throws IOException {
        Path root =  vfs().getPath("/");
        Path path = root.resolve("/dir1/file.txt");
        assertThat(normalizeNewLines(readString(path)), is("foo\n"));
        assertThat(normalizeNewLines(readString(path.getParent().resolve("/blue"))), is("blue\n"));
    }

    @Test
    void testWriteOutOfBounds() {
        Path root =  vfs().getPath("/");
        InvalidPathException ex = assertThrows(InvalidPathException.class, () -> echo(root.resolve("/../test.txt"), "bad\n"));
        assertThat(ex.getMessage(), startsWith("Not within virtual root"));
    }

    @Test
    void testReadOutOfBounds() {
        Path root =  vfs().getPath("/");
        InvalidPathException ex = assertThrows(InvalidPathException.class, () -> readString(root.resolve("/../test.txt")));
        assertThat(ex.getMessage(), startsWith("Not within virtual root"));
    }
}
