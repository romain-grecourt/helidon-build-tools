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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.mustachejava.Binding;
import com.github.mustachejava.Code;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.TemplateContext;
import com.github.mustachejava.reflect.SimpleObjectHandler;
import com.github.mustachejava.util.Wrapper;
import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.spi.TemplateSupport;

/**
 * Implementation of the {@link TemplateSupport} for Mustache.
 */
public class MustacheSupport implements TemplateSupport {

    private final MergedModel scope;
    private final DefaultMustacheFactory factory;
    private final Map<String, Mustache> cache;

    /**
     * Create a new instance.
     *
     * @param block   block
     * @param context context
     */
    MustacheSupport(Block block, Context context) {
        factory = new DefaultMustacheFactory();
        factory.setObjectHandler(new ModelHandler());
        scope = MergedModel.resolve(block, context);
        cache = new HashMap<>();
    }

    @Override
    public void render(InputStream template, String name, Charset charset, OutputStream os) {
        Mustache mustache = cache.computeIfAbsent(name, n -> factory.compile(new InputStreamReader(template), name));
        try (Writer writer = new OutputStreamWriter(os, charset)) {
            Writer result = mustache.execute(writer, scope);
            if (result != null) {
                result.flush();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static class ModelHandler extends SimpleObjectHandler {

        @Override
        public Binding createBinding(String name, TemplateContext tc, Code code) {
            return scopes -> find(name, null).call(scopes);
        }

        @Override
        public Wrapper find(String name, List<Object> ignore) {
            return scopes -> {
                Object result = null;
                if (!scopes.isEmpty()) {
                    Object scope = scopes.get(scopes.size() - 1);
                    if (scope instanceof MergedModel) {
                        result = ((MergedModel) scope).get(name);
                    }
                }
                if (result == null) {
                    throw new RuntimeException(String.format("Unresolved model value: '%s'", name));
                }
                return result;
            };
        }

        @Override
        public String stringify(Object object) {
            if (object instanceof MergedModel) {
                return ((MergedModel) object).asString();
            }
            throw new IllegalStateException("Only instances of type " + MergedModel.class + "are supported");
        }
    }
}
