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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.mustachejava.Binding;
import com.github.mustachejava.Code;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Iteration;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.TemplateContext;
import com.github.mustachejava.reflect.SimpleObjectHandler;
import com.github.mustachejava.util.Wrapper;
import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Model.MergeableModel;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.Output;
import io.helidon.build.archetype.engine.v2.ast.Statement;
import io.helidon.build.archetype.engine.v2.spi.TemplateSupport;

/**
 * Implementation of the {@link TemplateSupport} for Mustache.
 */
public class MustacheSupport implements TemplateSupport {

    private final DefaultMustacheFactory factory;
    private final Map<String, Mustache> cache;

    public MustacheSupport(Context context) {
        factory = new DefaultMustacheFactory();
        factory.setObjectHandler(new ModelHandler(context));
        cache = new HashMap<>();
    }

    @Override
    public void render(InputStream template, String name, Charset charset, OutputStream os, Block scope) {
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

    private static class ModelResolver extends Controller
            implements Output.Visitor<Void, Void>, Model.Visitor<Void, Void> {

        final Map<String, Model> models = new HashMap<>();
        final Deque<String> names = new ArrayDeque<>();
        final Deque<Integer[]> indexes = new ArrayDeque<>();

        private Void visitModel(MergeableModel model) {
            String name = model.key();
            if (name == null) {
                if(indexes.isEmpty()) {
                    throw new IllegalStateException("Index not set");
                }
                name = "[" + (indexes.peek()[0]++) + "]";
            }
            String fqn = names.isEmpty() ? name : names.peek() + "." + name;
            names.push(fqn);
            models.put(fqn, model);
            return null;
        }

        @Override
        public Void visitList(Model.List list, Void arg) {
            visitModel(list);
            indexes.push(new Integer[]{0});
            return null;
        }

        @Override
        public Void visitMap(Model.Map map, Void arg) {
            return visitModel(map);
        }

        @Override
        public Void visitValue(Model.Value value, Void arg) {
            return visitModel(value);
        }

        @Override
        public Void visitModel(Model model, Void arg) {
            return model.accept((Model.Visitor<Void, Void>) this, arg);
        }

        @Override
        public Void visitOutput(Output output, Void arg) {
            return output.accept((Output.Visitor<Void, Void>) this, arg);
        }

        @Override
        public Node.VisitResult postVisitBlock(Block block, Context arg) {
            if (block instanceof Model.List) {
                indexes.pop();
            }
            if (block instanceof Model) {
                names.pop();
            }
            return Node.VisitResult.CONTINUE;
        }
    }

    private static class ModelHandler extends SimpleObjectHandler {

        // foo -> Model.List
        // foo.[0] -> Model.Map
        // foo.[0].name -> Model.Value
        // foo.[0].version -> Model.Value
        // foo.[1]-> Model.Map
        // foo.[1].name -> Model.Value
        // foo.[1].version -> Model.Value

        final Map<String, Model> cache;
        final Context context;
        final Deque<String> names = new ArrayDeque<>();
        boolean initialized;

        ModelHandler(Context context) {
            this.context = context;
            this.cache = new HashMap<>();
        }

        @Override
        public Binding createBinding(final String name, TemplateContext tc, Code code) {
            return scopes -> {
                    String fqn = names.isEmpty() ? name : names.peek() + "." + name;
                    return find(fqn, scopes).call(scopes);
            };
        }

        @Override
        public Wrapper find(String name, List<Object> scopes) {
            return scs -> {

                // initialized is false, we traverse and populate the map

                // foo -> Model.List

                // foo.[0] -> Model.Map
                // foo.[0].name -> Model.Value
                // foo.[0].version -> Model.Value
                // foo.[1]-> Model.Map
                // foo.[1].name -> Model.Value
                // foo.[1].version -> Model.Value

                if (!initialized) {
                    Object scope = scopes.get(scopes.size() - 1);
                    if (scope instanceof Block) {
                        ModelResolver resolver = new ModelResolver();
                        Walker.walk(resolver, (Block) scope, context);
                        cache.putAll(resolver.models);
                        initialized = true;
                    }
                }
                return cache.get(name);
            };
        }

        @Override
        public String stringify(Object object) {
            if (object instanceof Model.Value) {
                return ((Model.Value) object).value();
            }
            return null;
        }

        @Override
        public Writer iterate(Iteration iteration, Writer writer, Object object, List<Object> scopes) {
            if (object instanceof Model.List) {
                Model.List list = (Model.List) object;
                String key = list.key();
                if (key != null) {
                    names.push(names.isEmpty() ? key : names.peek() + "." + key);
                }
                int i = 0;
                for (Statement next : list.statements()) {
                    names.push(names.peek() + "." + "[" + i++ + "]");
                    writer = iteration.next(writer, next, scopes);
                    names.pop();
                }
            }
            // TODO investigate iterating over a map entries
            //  OR fail
            return writer;
        }
    }
}
