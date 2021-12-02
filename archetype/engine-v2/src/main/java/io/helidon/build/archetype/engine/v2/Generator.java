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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.build.archetype.engine.v2.Context.ContextValue;
import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Output;
import io.helidon.build.archetype.engine.v2.ast.Output.Template;
import io.helidon.build.archetype.engine.v2.ast.Output.Transformation;
import io.helidon.build.archetype.engine.v2.ast.Output.Transformation.Replace;
import io.helidon.build.archetype.engine.v2.spi.TemplateSupport;
import io.helidon.build.common.PropertyEvaluator;
import io.helidon.build.common.SourcePath;

import static io.helidon.build.archetype.engine.v2.spi.TemplateSupportProvider.providerByName;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Output generator.
 */
public class Generator implements Output.Visitor<Context> {

    private final Map<String, TemplateSupport> templateSupports = new HashMap<>();
    private final Map<String, Transformation> transformations = new HashMap<>();

    private final Path outputDir;
    private final Block block;

    Generator(Block block, Path outputDir) {
        this.block = block;
        this.outputDir = outputDir;
    }

    @Override
    public VisitResult visitTransformation(Transformation transformation, Context arg) {
        // not doing a full traversal to get all transformations
        // assuming that transformations are declared before being used...
        transformations.putIfAbsent(transformation.id(), transformation);
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult visitFile(Output.File file, Context ctx) {
        copy(ctx.cwd().resolve(file.source()), outputDir.resolve(file.target()));
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult visitFiles(Output.Files files, Context ctx) {
        Path dir = ctx.cwd().resolve(files.directory());
        for (String resource : scan(files, ctx)) {
            Path source = dir.resolve(resource);
            Path target = outputDir.resolve(transformations(files, resource, ctx));
            copy(source, target);
        }
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult visitTemplates(Output.Templates templates, Context ctx) {
        Path dir = ctx.cwd().resolve(templates.directory());
        for (String resource : scan(templates, ctx)) {
            Path source = dir.resolve(resource);
            Path target = outputDir.resolve(transformations(templates, resource, ctx));
            render(source, target, templates.engine(), ctx, null);
        }
        return VisitResult.CONTINUE;
    }

    @Override
    public VisitResult visitTemplate(Template template, Context ctx) {
        Path source = ctx.cwd().resolve(template.source());
        Path target = outputDir.resolve(template.target());
        render(source, target, template.engine(), ctx, template);
        return VisitResult.CONTINUE;
    }

    private List<String> scan(Output.Files files, Context ctx) {
        Path dir = ctx.cwd().resolve(files.directory());
        List<SourcePath> resources = SourcePath.scan(dir);
        return SourcePath.filter(resources, files.includes(), files.excludes())
                         .stream()
                         .map(SourcePath::asString)
                         .collect(Collectors.toList());
    }

    private void render(Path source, Path target, String engine, Context ctx, Template extraScope) {
        TemplateSupport templateSupport = templateSupport(engine, ctx);
        try {
            Files.createDirectories(target.getParent());
            InputStream is = Files.newInputStream(source);
            OutputStream os = Files.newOutputStream(target);
            templateSupport.render(is, source.toAbsolutePath().toString(), UTF_8, os, extraScope);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void copy(Path source, Path target) {
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private String resolveVariable(String var, Context ctx) {
        ContextValue value = ctx.lookup(var);
        if (value == null) {
            throw new IllegalArgumentException("Unresolved variable: " + var);
        }
        return String.valueOf(value.unwrap());
    }

    private String transformations(Output.Files files, String path, Context ctx) {
        List<Transformation> transformations =
                files.transformations()
                     .stream()
                     .map(this.transformations::get)
                     .collect(Collectors.toList());
        String transformed = path;
        for (Transformation t : transformations) {
            for (Replace op : t.operations()) {
                String replacement = PropertyEvaluator.evaluate(op.replacement(), s -> resolveVariable(s, ctx));
                transformed = transformed.replaceAll(op.regex(), replacement);
            }
        }
        return transformed;
    }

    private TemplateSupport templateSupport(String engine, Context ctx) {
        return templateSupports.computeIfAbsent(engine, eng -> providerByName(eng).create(block, ctx));
    }
}
