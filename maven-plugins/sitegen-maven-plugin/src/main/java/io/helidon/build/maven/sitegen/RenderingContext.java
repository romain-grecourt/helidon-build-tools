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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.helidon.build.common.SourcePath;
import io.helidon.build.common.VirtualFileSystem;
import io.helidon.build.maven.sitegen.freemarker.TemplateSession;
import io.helidon.build.maven.sitegen.models.Page;
import io.helidon.build.maven.sitegen.models.PageFilter;
import io.helidon.build.maven.sitegen.models.SourcePathFilter;
import io.helidon.build.maven.sitegen.models.StaticAsset;

import org.slf4j.LoggerFactory;

import static io.helidon.build.common.FileUtils.requireDirectory;
import static io.helidon.build.common.Strings.requireValid;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNull;

/**
 * Represents a site processing invocation.
 */
public class RenderingContext {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(RenderingContext.class);

    private final Site site;
    private final TemplateSession templateSession;
    private final Map<String, Page> pages;
    private final Path sourceDir;
    private final Path outputDir;
    private final List<SourcePath> sourcePaths;
    private final Map<String, String> assets;
    private final List<String> resolvedAssets;

    /**
     * Create a new instance.
     *
     * @param site      site
     * @param sourceDir source directory
     * @param outputDir output directory
     */
    @SuppressWarnings("resource")
    RenderingContext(Site site, Path sourceDir, Path outputDir) {
        this.site = requireNonNull(site, "site is null!");
        this.sourceDir = requireDirectory(sourceDir);
        requireNonNull(outputDir, "outputDir is null!");
        this.outputDir = VirtualFileSystem.create(outputDir).getPath("/");
        this.templateSession = TemplateSession.create();
        this.sourcePaths = SourcePath.scan(this.sourceDir);
        this.pages = createPages(sourcePaths, site.pages(), sourceDir, site.backend());
        this.assets = new HashMap<>();
        this.resolvedAssets = new ArrayList<>();
        for (StaticAsset asset : site.assets()) {
            for (SourcePath path : asset.resolve(sourcePaths)) {
                String source = path.asString(false);
                String target = asset.target();
                assets.put(source, target);
                Path targetPath = this.outputDir.resolve(target).resolve(source).normalize();
                resolvedAssets.add(targetPath.toString());
            }
        }
    }

    /**
     * Get the source directory.
     *
     * @return the source directory, never {@code null}
     */
    public Path sourceDir() {
        return sourceDir;
    }

    /**
     * Get the output directory.
     *
     * @return the source directory, never {@code null}
     */
    public Path outputDir() {
        return outputDir;
    }

    /**
     * Get the {@link TemplateSession} of this site processing invocation.
     *
     * @return the template session, never {@code null}
     */
    public TemplateSession templateSession() {
        return templateSession;
    }

    /**
     * Get all scanned pages.
     *
     * @return the scanned pages, never {@code null}
     */
    public Map<String, Page> pages() {
        return pages;
    }

    /**
     * Get the configured site.
     *
     * @return site
     */
    public Site site() {
        return site;
    }

    /**
     * Find a page with the given target path.
     *
     * @param route the target path to search
     * @return the page if found, {@code null} otherwise
     */
    @SuppressWarnings("unused")
    public Page pageForRoute(String route) {
        requireValid(route, "route is invalid!");
        for (Page page : pages.values()) {
            if (route.equals(page.target())) {
                return page;
            }
        }
        return null;
    }

    /**
     * Resolve a page with a relative path within the context of a document.
     *
     * @param page the document
     * @param path the path to resolve
     * @return resolved page or {@code null} if not found
     */
    public Page resolvePage(Page page, String path) {
        Path resolvedPath = resolvePath(page, path);
        String key = sourceDir.relativize(resolvedPath).toString();
        return pages.get(key);
    }

    /**
     * Resolve a relative path within the context of a document.
     *
     * @param page page
     * @param path the path to resolve
     * @return resolved path
     */
    public Path resolvePath(Page page, String path) {
        Path pageDir = pageDir(page.source());
        return pageDir.resolve(path).normalize();
    }

    /**
     * Resolve the page directory for the given document path.
     *
     * @param path document path
     * @return parent of the resolved path
     */
    public Path pageDir(String path) {
        Path pageSource = sourceDir.resolve(path);
        return pageSource.getParent().normalize();
    }

    /**
     * Get the resolved static assets.
     *
     * @return map of assets, key is the asset path, value is the target directory
     */
    public List<String> resolvedAssets() {
        return resolvedAssets;
    }

    /**
     * Copy the scanned static assets in the output directory.
     */
    public void copyStaticAssets() {
        for (Map.Entry<String, String> asset : assets.entrySet()) {
            try {
                String source = asset.getKey();
                String target = asset.getValue();
                Path targetDir = outputDir.resolve(target);
                Files.createDirectories(targetDir);
                copyResources(sourceDir.resolve(source), targetDir.resolve(source));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    /**
     * Process the rendering of all pages.
     *
     * @param pagesDir the directory where to generate the rendered files
     * @param ext      the file extension to use for the rendered files
     */
    public void processPages(Path pagesDir, String ext) {
        // TODO sort pages to render them in a predictable order
        for (Page page : pages.values()) {
            PageRenderer renderer = site.backend().renderer(page.sourceExt());
            renderer.process(page, this, pagesDir, ext);
        }
    }

    /**
     * Create pages.
     *
     * @param paths     a list of path to match
     * @param filters   a list of filters to apply
     * @param sourceDir the source directory containing the paths
     * @param backend   the backend to use for reading the metadata
     * @return map of pages indexed by relative source path
     */
    public static Map<String, Page> createPages(List<SourcePath> paths,
                                                List<PageFilter> filters,
                                                Path sourceDir,
                                                Backend backend) {

        requireNonNull(paths, "paths is null!");
        requireNonNull(filters, "filters is null!");
        List<SourcePath> filteredSourcePaths;
        if (filters.isEmpty()) {
            filteredSourcePaths = paths;
        } else {
            filteredSourcePaths = new ArrayList<>();
            for (SourcePathFilter filter : filters) {
                filteredSourcePaths.addAll(filter.resolve(paths));
            }
        }
        Map<String, Page> pages = new HashMap<>();
        for (SourcePath filteredPath : SourcePath.sort(filteredSourcePaths)) {
            String path = filteredPath.asString(false);
            if (pages.containsKey(path)) {
                throw new IllegalStateException("Source path " + path + "already included");
            }
            String ext = fileExt(path);
            Page.Metadata metadata = backend.renderer(ext).readMetadata(sourceDir.resolve(path));
            pages.put(path, Page.builder()
                                .source(path)
                                .ext(ext)
                                .target(removeFileExt(path))
                                .metadata(metadata)
                                .build());
        }
        return pages;
    }

    /**
     * Filter the given pages with the specified filters.
     *
     * @param pages    the pages to filter
     * @param includes include patterns
     * @param excludes exclude patterns
     * @return filtered pages
     */
    @SuppressWarnings("unused")
    public static List<Page> filterPages(Collection<Page> pages,
                                         Collection<String> includes,
                                         Collection<String> excludes) {

        requireNonNull(pages, "pages");
        Map<SourcePath, Page> sourcePaths = new HashMap<>();
        for (Page page : pages) {
            sourcePaths.put(new SourcePath(page.source()), page);
        }
        List<SourcePath> filteredSourcePaths = SourcePath.filter(sourcePaths.keySet(), includes, excludes);
        List<Page> filtered = new LinkedList<>();
        for (SourcePath sourcePath : SourcePath.sort(filteredSourcePaths)) {
            Page page = sourcePaths.get(sourcePath);
            if (page == null) {
                throw new IllegalStateException("Unable to get page for path: " + sourcePath.asString());
            }
            filtered.add(page);
        }
        return filtered;
    }

    /**
     * Copy static resources into the given output directory.
     *
     * @param resources the path to the resources
     * @param outputDir the target output directory where to copy the files
     */
    public static void copyResources(Path resources, Path outputDir) {
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
            throw new UncheckedIOException(ex);
        }
    }

    private static String fileExt(String filepath) {
        int index = filepath.lastIndexOf(".");
        return index < 0 ? null : filepath.substring(index + 1);
    }

    private static String removeFileExt(String filepath) {
        return filepath.substring(0, filepath.lastIndexOf("."));
    }
}
