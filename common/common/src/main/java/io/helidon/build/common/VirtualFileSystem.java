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

package io.helidon.build.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.ProviderMismatchException;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterators.spliteratorUnknownSize;

/**
 * Virtual file system that provides a pseudo chroot.
 */
@SuppressWarnings("NullableProblems")
public class VirtualFileSystem extends FileSystem {

    private static final VFSProvider PROVIDER = new VFSProvider();

    private final Path internalRoot;
    private final VPath root = new VPath(this, "/");
    private volatile boolean isOpen;

    /**
     * Create a new virtual filesystem using the given path as the root.
     *
     * @param path path
     * @return file system
     */
    public static FileSystem create(Path path) {
        return new VirtualFileSystem(path);
    }

    private VirtualFileSystem(Path internalRoot) {
        this.internalRoot = internalRoot;
        this.isOpen = true;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public void close() {
        cleanup();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void finalize() {
        cleanup();
    }

    @Override
    public FileSystemProvider provider() {
        return PROVIDER;
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singleton(root);
    }

    @Override
    public Path getPath(String first, String... more) {
        if (more.length == 0) {
            if (isWithinBounds(internalRoot.resolve(first))) {
                return new VPath(this, first);
            } else {
                throw new InvalidVirtualPathException(this, first);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(first);
        for (String s : more) {
            if (!s.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('/');
                }
                sb.append(s);
            }
        }
        String path = sb.toString();
        if (isWithinBounds(internalRoot.resolve(path))) {
            return new VPath(this, path);
        }
        throw new InvalidVirtualPathException(this, path);
    }

    @Override
    public final boolean isReadOnly() {
        return true;
    }

    @Override
    public final UserPrincipalLookupService getUserPrincipalLookupService() {
        return internalRoot.getFileSystem().getUserPrincipalLookupService();
    }

    @Override
    public final WatchService newWatchService() throws IOException {
        return internalRoot.getFileSystem().newWatchService();
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndInput) {
        return internalRoot.getFileSystem().getPathMatcher(syntaxAndInput);
    }

    @Override
    public final Iterable<FileStore> getFileStores() {
        return internalRoot.getFileSystem().getFileStores();
    }

    @Override
    public final Set<String> supportedFileAttributeViews() {
        return internalRoot.getFileSystem().supportedFileAttributeViews();
    }

    @Override
    public final String toString() {
        return "virtual:" + internalRoot.toUri();
    }

    @Override
    public final String getSeparator() {
        return "/";
    }

    private VPath relativize(VPath path, VPath other) {
        Path otherInternal = internal(other);
        if (isWithinBounds(otherInternal)) {
            otherInternal = internalRoot;
        }
        return new VPath(this, internal(path).relativize(otherInternal).toString(), true);
    }

    private String normalize(String path) {
        return internalRoot.relativize(internalRoot.resolve(path).normalize()).toString();
    }

    private boolean isWithinBounds(Path path) {
        return path.startsWith(internalRoot);
    }

    private synchronized void cleanup() {
        if (isOpen) {
            isOpen = false;
        }
    }

    private Path internal(VPath path) {
        return internalRoot.resolve(path.toString());
    }

    private Path relative0(VPath path) {
        return internalRoot.relativize(internalRoot.resolve(path.toString()));
    }

    private FileSystemProvider provider0() {
        return internalRoot.getFileSystem().provider();
    }

    private static final class InvalidVirtualPathException extends InvalidPathException {

        InvalidVirtualPathException(VirtualFileSystem fs, String input) {
            super(input, "Not within virtual root: " + fs.internalRoot);
        }
    }

    private static final class VPath implements Path {

        final VirtualFileSystem fs;
        final String path;
        final boolean normalized;

        VPath(VirtualFileSystem fs, String path) {
            this.fs = fs;
            this.path = path;
            this.normalized = false;
        }

        VPath(VirtualFileSystem fs, String path, boolean normalized) {
            this.fs = fs;
            this.path = normalized ? path : fs.normalize(path);
            this.normalized = normalized;
        }

        @Override
        public VPath getRoot() {
            return isAbsolute() ? fs.root : null;
        }

        @Override
        public VPath getFileName() {
            if (path.isEmpty()) {
                return this;
            }
            if (path.length() == 1 && path.charAt(0) == '/') {
                return null;
            }
            int off = path.lastIndexOf('/');
            if (off == -1) {
                return this;
            }
            return new VPath(fs, path.substring(off + 1), true);
        }

        @Override
        public VPath getParent() {
            Path parent = fs.relative0(this).getParent();
            if (parent == null) {
                return null;
            } else {
                if (fs.isWithinBounds(parent)) {
                    return new VPath(fs, parent.toString());
                }
                return fs.root;
            }
        }

        @Override
        public int getNameCount() {
            return fs.relative0(this).getNameCount();
        }

        @Override
        public VPath getName(int index) {
            return new VPath(fs, fs.relative0(this).getName(index).toString());
        }

        @Override
        public VPath subpath(int beginIndex, int endIndex) {
            return new VPath(fs, fs.relative0(this).subpath(beginIndex, endIndex).toString());
        }

        @Override
        public VPath toRealPath(LinkOption... options) throws IOException {
            Path internal = fs.internal(this).toRealPath(options).toAbsolutePath().normalize();
            if (!fs.isWithinBounds(internal)) {
                throw new InvalidVirtualPathException(fs, internal.toString());
            }
            return new VPath(fs, fs.internalRoot.relativize(internal).toString(), true);
        }

        @Override
        public VPath toAbsolutePath() {
            return isAbsolute() ? this : new VPath(fs, "/" + path, true);
        }

        @Override
        public URI toUri() {
            try {
                return new URI("virtual:",
                        String.format("%s!%s", fs.internalRoot.toUri(), fs.relative0(this)), null);
            } catch (URISyntaxException ex) {
                throw new AssertionError(ex);
            }
        }

        @Override
        public VPath relativize(Path other) {
            final VPath o = VPath.unwrap(other);
            if (o.equals(this)) {
                return new VPath(fs, "", true);
            }
            if (path.isEmpty()) {
                return o;
            }
            if (fs != o.fs || isAbsolute() != o.isAbsolute()) {
                throw new IllegalArgumentException("Incorrect filesystem or path: " + other);
            }
            return fs.relativize(this, (VPath) other);
        }

        @Override
        public VirtualFileSystem getFileSystem() {
            return fs;
        }

        @Override
        public boolean isAbsolute() {
            return !path.isEmpty() && path.charAt(0) == '/';
        }

        @Override
        public VPath resolve(Path other) {
            final VPath o = VPath.unwrap(other);
            if (this.path.isEmpty() || o.isAbsolute()) {
                return o;
            }
            if (o.path.isEmpty()) {
                return this;
            }
            Path internal = fs.relative0(this).resolve(other.toString());
            if (!fs.isWithinBounds(internal)) {
                throw new InvalidVirtualPathException(fs, internal.toString());
            }
            return new VPath(fs, internal.toString(), true);
        }

        @Override
        public Path resolveSibling(Path other) {
            Objects.requireNonNull(other, "other");
            Path parent = getParent();
            return (parent == null) ? other : parent.resolve(other);
        }

        @Override
        public boolean startsWith(Path other) {
            if (!(Objects.requireNonNull(other) instanceof VPath)) {
                return false;
            }
            return fs.internal(this).startsWith(fs.internal((VPath) other));
        }

        @Override
        public boolean endsWith(Path other) {
            if (!(Objects.requireNonNull(other) instanceof VPath)) {
                return false;
            }
            return fs.internal(this).endsWith(fs.internal((VPath) other));
        }

        @Override
        public VPath resolve(String other) {
            return resolve(fs.getPath(other));
        }

        @Override
        public Path resolveSibling(String other) {
            return resolveSibling(fs.getPath(other));
        }

        @Override
        public boolean startsWith(String other) {
            return startsWith(fs.getPath(other));
        }

        @Override
        public boolean endsWith(String other) {
            return endsWith(fs.getPath(other));
        }

        @Override
        public VPath normalize() {
            return normalized ? this : new VPath(fs, path, true);
        }

        @Override
        public String toString() {
            return path;
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof VPath && this.path.equals(((VPath) obj).path);
        }

        @Override
        public int compareTo(Path other) {
            return path.compareTo(VPath.unwrap(other).path);
        }

        @Override
        public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
                throws IOException {
            return fs.internal(this).register(watcher, events, modifiers);
        }

        @Override
        public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
            return fs.internal(this).register(watcher, events);
        }

        @Override
        public File toFile() {
            return fs.internal(this).toFile();
        }

        @Override
        public Iterator<Path> iterator() {
            return new Iterator<>() {
                private int i = 0;

                @Override
                public boolean hasNext() {
                    return (i < getNameCount());
                }

                @Override
                public Path next() {
                    if (i < getNameCount()) {
                        Path result = getName(i);
                        i++;
                        return result;
                    } else {
                        throw new NoSuchElementException();
                    }
                }

                @Override
                public void remove() {
                    throw new ReadOnlyFileSystemException();
                }
            };
        }

        private static VPath unwrap(Path path) {
            Objects.requireNonNull(path, "path");
            if (!(path instanceof VPath)) {
                throw new ProviderMismatchException();
            }
            return (VPath) path;
        }
    }

    private static final class VFSProvider extends FileSystemProvider {

        @Override
        public String getScheme() {
            return "virtual";
        }

        @Override
        public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
            checkUri(uri);
            return new VirtualFileSystem(Path.of(uri.getPath()));
        }

        @Override
        public Path getPath(URI uri) {
            checkUri(uri);
            String schemeSpecificPart = uri.getSchemeSpecificPart();
            int index = schemeSpecificPart.indexOf('!');
            URI virtualUri = URI.create(schemeSpecificPart.substring(0, index));
            String path = uri.getScheme();
            if (path == null || path.charAt(0) != '/') {
                throw new IllegalArgumentException("Invalid path component");
            }
            VirtualFileSystem fs = new VirtualFileSystem(Path.of(virtualUri));
            return fs.getPath(path);
        }

        @Override
        public FileSystem getFileSystem(URI uri) {
            checkUri(uri);
            String schemeSpecificPart = uri.getSchemeSpecificPart();
            int index = schemeSpecificPart.indexOf('!');
            URI fileUri = URI.create(schemeSpecificPart.substring(0, index));
            return new VirtualFileSystem(Path.of(fileUri));
        }

        @Override
        public void checkAccess(Path path, AccessMode... modes) throws IOException {
            VPath vpath = VPath.unwrap(path);
            vpath.fs.provider0().checkAccess(vpath.fs.internal(vpath), modes);
        }

        @Override
        public Path readSymbolicLink(Path link) throws IOException {
            VPath vlink = VPath.unwrap(link);
            Path targetPath = Files.readSymbolicLink(vlink.fs.relative0(vlink));
            if (!vlink.fs.isWithinBounds(targetPath)) {
                throw new InvalidVirtualPathException(vlink.fs, targetPath.toString());
            }
            return new VPath(vlink.fs, vlink.fs.internalRoot.relativize(targetPath.toAbsolutePath()).toString());
        }

        @Override
        public void copy(Path src, Path target, CopyOption... options) throws IOException {
            VPath vsrc = VPath.unwrap(src);
            vsrc.fs.provider0().copy(vsrc.fs.internal(vsrc), vsrc.fs.internal(VPath.unwrap(target)), options);
        }

        @Override
        public void createDirectory(Path path, FileAttribute<?>... attrs) throws IOException {
            VPath vpath = VPath.unwrap(path);
            vpath.fs.provider0().createDirectory(vpath.fs.internal(vpath), attrs);
        }

        @Override
        public void delete(Path path) throws IOException {
            VPath vpath = VPath.unwrap(path);
            vpath.fs.provider0().delete(vpath.fs.internal(vpath));
        }

        @Override
        public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
            VPath vpath = VPath.unwrap(path);
            return vpath.fs.provider0().getFileAttributeView(vpath.fs.internal(vpath), type, options);
        }

        @Override
        public FileStore getFileStore(Path path) throws IOException {
            VPath vpath = VPath.unwrap(path);
            return vpath.fs.provider0().getFileStore(vpath.fs.internalRoot);
        }

        @Override
        public boolean isHidden(Path path) throws IOException {
            VPath vpath = VPath.unwrap(path);
            return vpath.fs.provider0().isHidden(vpath.fs.internal(vpath));
        }

        @Override
        public boolean isSameFile(Path path, Path other) throws IOException {
            VPath vpath = VPath.unwrap(path);
            return vpath.fs.provider0()
                           .isSameFile(vpath.fs.internal(VPath.unwrap(path)),
                                   vpath.fs.internal(VPath.unwrap(other)));
        }

        @Override
        public void move(Path src, Path target, CopyOption... options) throws IOException {
            VPath vsrc = VPath.unwrap(src);
            vsrc.fs.provider0().move(vsrc.fs.internal(vsrc), vsrc.fs.internal(VPath.unwrap(target)), options);
        }

        @Override
        public AsynchronousFileChannel newAsynchronousFileChannel(Path path,
                                                                  Set<? extends OpenOption> options,
                                                                  ExecutorService exec,
                                                                  FileAttribute<?>... attrs) throws IOException {
            VPath vpath = VPath.unwrap(path);
            return vpath.fs.provider0().newAsynchronousFileChannel(vpath.fs.internal(vpath), options, exec, attrs);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
                throws IOException {
            VPath vpath = VPath.unwrap(path);
            return vpath.fs.provider0().newByteChannel(vpath.fs.internal(vpath), options, attrs);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path path, DirectoryStream.Filter<? super Path> filter)
                throws IOException {
            VPath vpath = VPath.unwrap(path);
            Iterator<Path> it = vpath.fs.provider0().newDirectoryStream(vpath.fs.internal(vpath), filter).iterator();
            Stream<Path> stream = StreamSupport.stream(spliteratorUnknownSize(it, Spliterator.ORDERED), false);
            return new DirectoryStream<>() {
                @Override
                public Iterator<Path> iterator() {
                    return stream.map(p -> (Path) new VPath(vpath.fs, vpath.fs.internalRoot.relativize(p).toString()))
                                 .iterator();
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
                throws IOException {
            VPath vpath = VPath.unwrap(path);
            return vpath.fs.provider0().newFileChannel(vpath.fs.internal(vpath), options, attrs);
        }

        @Override
        public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
            VPath vpath = VPath.unwrap(path);
            return vpath.fs.provider0().newInputStream(vpath.fs.internal(vpath), options);
        }

        @Override
        public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
            VPath vpath = VPath.unwrap(path);
            return vpath.fs.provider0().newOutputStream(vpath.fs.internal(vpath), options);
        }

        @Override
        public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
                throws IOException {
            VPath vpath = VPath.unwrap(path);
            return vpath.fs.provider0().readAttributes(vpath.fs.internal(vpath), type, options);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attribute, LinkOption... options)
                throws IOException {
            VPath vpath = VPath.unwrap(path);
            return vpath.fs.provider0().readAttributes(vpath.fs.internal(vpath), attribute, options);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            VPath vpath = VPath.unwrap(path);
            vpath.fs.provider0().setAttribute(vpath.fs.internal(vpath), attribute, value, options);
        }

        private void checkUri(URI uri) {
            if (!uri.getScheme().equalsIgnoreCase(getScheme())) {
                throw new IllegalArgumentException("URI does not match this provider");
            }
            if (uri.getAuthority() != null) {
                throw new IllegalArgumentException("Authority component present");
            }
            if (uri.getQuery() != null) {
                throw new IllegalArgumentException("Query component present");
            }
            if (uri.getFragment() != null) {
                throw new IllegalArgumentException("Fragment component present");
            }
        }
    }
}
