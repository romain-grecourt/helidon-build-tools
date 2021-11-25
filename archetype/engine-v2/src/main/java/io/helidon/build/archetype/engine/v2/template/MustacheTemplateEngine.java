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

package io.helidon.build.archetype.engine.v2.template;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Iteration;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.github.mustachejava.reflect.SimpleObjectHandler;
import com.github.mustachejava.util.GuardException;
import com.github.mustachejava.util.Wrapper;
import io.helidon.build.archetype.engine.v2.Context;
import io.helidon.build.archetype.engine.v2.Walker;
import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Model;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.Output;

/**
 * Implementation of the {@link TemplateEngine} for Mustache.
 */
public class MustacheTemplateEngine implements TemplateEngine {

    @Override
    public String name() {
        return "mustache";
    }

    @Override
    public void render(InputStream template,
                       String templateName,
                       Charset charset,
                       OutputStream target,
                       Object scope) {

        MustacheFactory factory = new DefaultMustacheFactory();
        Mustache mustache = factory.compile(new InputStreamReader(template), templateName);
        try (Writer writer = new OutputStreamWriter(target, charset)) {
            mustache.execute(writer, scope).flush();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static final class ModelVisitor implements Model.Visitor<Object, Context> {

        final String name;

        ModelVisitor(String name) {
            this.name = name;
        }
    }

    private static class WrapperImpl implements Wrapper,
                                                Node.Visitor<Context>,
                                                Block.Visitor<Object, Context>,
                                                Output.Visitor<Object, Context> {

        final ModelVisitor modelVisitor;
        final Context context;
        Object result;

        WrapperImpl(String name, Context context) {
            // this is a dot notation
            this.modelVisitor = new ModelVisitor(name);
            this.context = context;
        }

        @Override
        public Object call(List<Object> scopes) throws GuardException {
            for (Object scope : scopes) {
                if (scope instanceof Model) {
                    new Walker<>(this).walk((Model) scope, context);
                    if (result != null) {
                        return result;
                    }
                }
            }
            return null;
        }

        @Override
        public Node.VisitResult preVisitBlock(Block block, Context arg) {
            result = block.accept((Block.Visitor<Object, Context>) this, arg);
            if (result != null) {
                return Node.VisitResult.TERMINATE;
            }
            return Node.VisitResult.CONTINUE;
        }

        @Override
        public Object visitOutput(Output output, Context context) {
            return output.accept((Output.Visitor<Object, Context>) this, context);
        }
    }

    private static class ObjectHandlerImpl extends SimpleObjectHandler {

        @Override
        public Wrapper find(String name, List<Object> scopes) {
            // <value key="">
            // <map>
            return new WrapperImpl(name, null);
        }

        @Override
        public Writer iterate(Iteration iteration, Writer writer, Object object, List<Object> scopes) {
            // list
            return null;
        }
    }
}
