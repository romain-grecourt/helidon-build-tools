/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.lsp.server.service.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;

import io.helidon.build.common.FileUtils;
import io.helidon.lsp.common.Dependency;
import io.helidon.lsp.server.management.MavenSupport;
import io.helidon.lsp.server.service.TextDocumentHandler;
import io.helidon.lsp.server.service.metadata.ConfigMetadata;
import io.helidon.lsp.server.service.metadata.ConfiguredType;
import io.helidon.lsp.server.service.metadata.MetadataProvider;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class CompletionTestBase {

    @Mock
    protected ConfigurationPropertiesService propertiesService;
    @Mock
    protected MavenSupport mavenSupport;
    @Spy
    protected MetadataProvider provider = MetadataProvider.instance();

    public void before() throws URISyntaxException {
        Map<String, ConfigMetadata> stringConfigMetadataMap = metadataForFile();
        Mockito.when(propertiesService.metadataForFile(any())).thenReturn(stringConfigMetadataMap);
    }

    protected CompletionItem completionItemByLabel(String label, List<CompletionItem> completion) {
        return completion.stream()
                .filter(item -> item.getLabel().equals(label))
                .findFirst().orElse(null);
    }

    protected List<CompletionItem> completionItems(Position position,
                                                   String fileName,
                                                   TextDocumentHandler handler) throws URISyntaxException {

        URL resource = getClass().getClassLoader().getResource(fileName);
        if (resource == null) {
            throw new IllegalArgumentException("Resource not found: " + fileName);
        }
        CompletionParams completionParams = new CompletionParams(
                new TextDocumentIdentifier(resource.toURI().toString()), position);
        return handler.completion(completionParams);
    }

    protected Map<String, ConfigMetadata> metadataForFile() {
        ConfigurationPropertiesService service = ConfigurationPropertiesService.instance();
        service.mavenSupport(mavenSupport);
        service.metadataProvider(provider);
        Set<Dependency> dependencies = dependencies();
        Mockito.when(mavenSupport.dependencies(any(Path.class))).thenReturn(dependencies);
        JsonReaderFactory readerFactory = Json.createReaderFactory(Map.of());
        for (Dependency dependency : dependencies) {
            try (InputStream is = new FileInputStream(dependency.path())) {
                JsonReader reader = readerFactory.createReader(is, StandardCharsets.UTF_8);
                List<ConfiguredType> configuredTypes = provider.processMetadataJson(reader.readArray());
                Mockito.doReturn(configuredTypes).when(provider).readMetadata(dependency.path());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return service.metadataForPom(Path.of("pom.xml"));
    }

    protected Set<Dependency> dependencies() {
        URL resource = getClass().getClassLoader().getResource("metadata");
        if (resource == null) {
            throw new IllegalStateException("Resource not found: metadata");
        }
        try (Stream<Path> paths = Files.list(FileUtils.pathOf(resource))) {
            return paths.filter(file -> file.getFileName().toString().endsWith(".json"))
                    .map(file -> new Dependency(null, null, null, null, null, file.toString()))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
