/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.build.maven.stager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.build.common.SubstitutionVariables;
import io.helidon.build.common.SubstitutionVariables.NotFoundAction;
import io.helidon.build.common.xml.XMLElement;

/**
 * Stager configuration preprocessor.
 */
final class ConfigProcessor {

    private final XMLElement root;
    private final Path baseDir;
    private final Function<String, String> propertyResolver;

    private ConfigProcessor(XMLElement root, Path baseDir, Function<String, String> propertyResolver) {
        this.root = root;
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir").toAbsolutePath().normalize();
        this.propertyResolver = propertyResolver;
    }

    /**
     * Read and preprocess the stager configuration.
     * @param root XML root
     * @param baseDir base directory
     * @param propertyResolver property resolver
     * @return effective XMLElement
     */
    static XMLElement process(XMLElement root, Path baseDir, Function<String, String> propertyResolver) {
        validate(root);
        ConfigProcessor processor = new ConfigProcessor(root, baseDir, propertyResolver);
        return processor.process();
    }

    private XMLElement process() throws IllegalStateException {
        // use a copy
        XMLElement config = XMLElement.builder(root);

        // pre-process with a single traversal
        config.visit(new IncludeVisitor(config));

        // merge all top-level elements
        XMLElement.Builder directories = XMLElement.builder().name("directories");
        for (XMLElement variables : config.childrenAt("variables")) {
            variables.parent(directories);
            directories.children().add(variables);
        }
        for (XMLElement variables : config.childrenAt("directories", "variables")) {
            variables.parent(directories);
            directories.children().add(variables);
        }
        for (XMLElement directory : config.childrenAt("directories", "directory")) {
            directory.parent(directories);
            directories.children().add(directory);
        }
        return directories;
    }

    private static void validate(XMLElement root) {
        int previous = -1;
        String previousName = null;
        for (XMLElement child : root.children()) {
            int order = switch (child.name()) {
                case "properties" -> 0;
                case "variables" -> 1;
                case "include" -> 2;
                case "directories" -> 3;
                default -> -1;
            };
            if (order >= 0) {
                if (order < previous) {
                    throw new IllegalStateException(
                            "Invalid element order: <%s> must appear before <%s> in %s"
                                    .formatted(child.name(), previousName, child.location()));
                }
                previous = order;
                previousName = child.name();
            }
        }
    }

    private class IncludeVisitor implements XMLElement.Visitor {

        private final SubstitutionVariables substitution;
        private final Deque<IncludeFrame> includeFrames = new ArrayDeque<>();
        private final Map<String, String> properties = new HashMap<>();

        IncludeVisitor(XMLElement config) {
            this.substitution = SubstitutionVariables.of(NotFoundAction.AsIs, k -> {
                String value = properties.get(k);
                if (value == null) {
                    value = propertyResolver.apply(k);
                }
                return value;
            });
            addProperties(config);
        }

        @Override
        public void visitElement(XMLElement elt) {
            // interpolate
            elt.value(substitution.resolve(elt.value()));
            elt.attributes().replaceAll((key, value) -> substitution.resolve(value));

            // pre-process includes
            if (isInclude(elt)) {
                String source = resolveSource(elt);
                Path file = resolveFile(source, elt);
                XMLElement resolved = XMLElement.read(file, source, false);
                validate(resolved);
                addProperties(resolved);

                // add resolved elements as children of the "include" node
                // include node is "inlined" during post visit
                for (XMLElement e : resolved.children()) {
                    e.parent(elt);
                    elt.children().add(e);
                }
            }
        }

        @Override
        public void postVisitElement(XMLElement elt) {
            if (isInclude(elt)) {
                includeFrames.removeLast();

                // inline children and remove the "include" node
                List<XMLElement> siblings = elt.parent().children();
                int index = siblings.indexOf(elt);
                siblings.remove(index);
                List<XMLElement> children = elt.children();
                for (int i = 0; i < children.size(); i++) {
                    XMLElement e = children.get(i);
                    e.parent(elt.parent());
                    siblings.add(index + i, e);
                }
            }
        }

        String resolveSource(XMLElement elt) {
            String src = elt.attribute("src", null);
            if (src == null || src.isBlank()) {
                throw new IllegalStateException(
                        "Missing required 'src' attribute for include in " + elt.location());
            }
            String resolvedSrc = substitution.resolve(src);
            Path path = includePath(resolvedSrc);
            if (includeFrames.stream().anyMatch(it -> it.path.equals(path))) {
                throw new IllegalStateException(
                        "Include cycle detected: %s -> %s"
                                .formatted(includeChain(), elt.location()));
            }
            includeFrames.addLast(new IncludeFrame(elt, path));
            return path.toString();
        }

        Path includePath(String src) {
            IncludeFrame frame = includeFrames.peekLast();
            Path path = null;
            if (frame != null) {
                path = frame.path.getParent();
            }
            if (path == null) {
                path = Path.of("");
            }
            return path.resolve(src).normalize();
        }

        Path resolveFile(String source, XMLElement elt) {
            Path includeFile;
            Path path = Path.of(source);
            if (path.isAbsolute()) {
                includeFile = path.normalize();
            } else {
                includeFile = baseDir.resolve(path).normalize();
            }
            if (!Files.isRegularFile(includeFile) || !Files.isReadable(includeFile)) {
                throw new IllegalStateException(
                        "Missing or unreadable include '%s' referenced from %s"
                                .formatted(source, elt.location()));
            }
            return includeFile;
        }

        String includeChain() {
            return includeFrames.stream()
                    .map(it -> it.elt.location().toString())
                    .collect(Collectors.joining(" -> "));
        }

        void addProperties(XMLElement elt) {
            for (XMLElement property : elt.childrenAt("properties", "property")) {
                properties.put(property.attribute("name"), property.attribute("value"));
            }
        }

        static boolean isInclude(XMLElement elt) {
            if (!"include".equals(elt.name())) {
                return false;
            }
            XMLElement parent = elt.parent();
            if (parent == null) {
                return false;
            }
            while (parent.parent() != null) {
                if (!"include".equals(parent.name())) {
                    return false;
                }
                parent = parent.parent();
            }
            return true;
        }

        record IncludeFrame(XMLElement elt, Path path) {
        }
    }
}
