/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.build.common.Strings;
import io.helidon.build.maven.stager.VariableValue.ListValue;
import io.helidon.build.maven.stager.VariableValue.MapValue;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.reflect.ReflectionObjectHandler;
import com.github.mustachejava.util.Wrapper;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/**
 * Render a mustache template.
 */
final class TemplateTask extends StagingTask {

    private final String source;
    private final List<Variable> variables;

    TemplateTask(ActionIterators iterators, Map<String, String> attrs, List<Variable> variables) {
        super("template", null, iterators, attrs);
        this.source = Strings.requireValid(attrs.get("source"), "source is required");
        this.variables = variables;
    }

    @Override
    protected void doExecute(StagingContext ctx, Path dir, Map<String, String> vars) throws IOException {
        String resolvedTarget = resolveVar(target(), vars);
        String resolvedSource = resolveVar(source, vars);
        Path sourceFile = ctx.resolve(resolvedSource);
        if (!Files.exists(sourceFile)) {
            throw new IllegalStateException(sourceFile + " does not exist");
        }
        Path targetFile = dir.resolve(resolvedTarget).normalize();
        ctx.ensureDirectory(targetFile.getParent());
        try (Reader reader = Files.newBufferedReader(sourceFile);
                Writer writer = Files.newBufferedWriter(targetFile, CREATE, TRUNCATE_EXISTING)) {

            Map<String, Object> scope = templateScope(variables);
            DefaultMustacheFactory factory = new DefaultMustacheFactory();
            factory.setObjectHandler(new TemplateObjectHandler());
            factory.compile(reader, resolvedSource)
                    .execute(writer, scope)
                    .flush();
        }
    }

    String source() {
        return source;
    }

    List<Variable> variables() {
        return variables;
    }

    private static Map<String, Object> templateScope(List<Variable> variables) {
        Map<String, VariableValue> values = new LinkedHashMap<>();
        for (Variable variable : variables) {
            values.merge(variable.name(), variable.value(), (l, r) -> l.merge(variable.name(), r));
        }
        Map<String, Object> scope = new LinkedHashMap<>();
        values.forEach((name, value) -> scope.put(name, mapValue(value)));
        return scope;
    }

    private static Object mapValue(VariableValue value) {
        if (value instanceof ListValue lv) {
            List<VariableValue> values = lv.values();
            List<Object> list = new ArrayList<>(values.size());
            for (VariableValue e : values) {
                list.add(mapValue(e));
            }
            return new DecoratedCollection(list);
        }
        if (value instanceof MapValue mv) {
            Map<String, VariableValue> values = mv.values();
            Map<String, Object> map = new LinkedHashMap<>();
            values.forEach((k, v) -> map.put(k, mapValue(v)));
            return new DecoratedMap(map);
        }
        return value.unwrap();
    }

    static class DecoratedCollection extends AbstractCollection<Object> {

        private final List<Object> elements;

        DecoratedCollection(List<Object> delegate) {
            elements = new ArrayList<>(delegate.size());
            Iterator<Object> it = delegate.iterator();
            for (int i = 0; it.hasNext(); i++) {
                Object next = it.next();
                elements.add(new Element(next, i, i == 0, !it.hasNext()));
            }
        }

        @Override
        public Iterator<Object> iterator() {
            return elements.iterator();
        }

        @Override
        public int size() {
            return elements.size();
        }

        record Element(Object value, int index, boolean first, boolean last) {

            @Override
            public String toString() {
                return String.valueOf(value);
            }
        }
    }

    static class DecoratedMap extends AbstractMap<String, Object> {

        private final Set<Entry<String, Object>> entries = new LinkedHashSet<>();

        DecoratedMap(Map<String, Object> delegate) {
            Set<EntryImpl> decorated = new LinkedHashSet<>();
            Iterator<Entry<String, Object>> it = delegate.entrySet().iterator();
            for (int i = 0; it.hasNext(); i++) {
                Entry<String, Object> entry = it.next();
                decorated.add(new EntryImpl(entry.getKey(), entry.getValue(), i, i == 0, !it.hasNext()));
            }
            entries.add(Map.entry("entries", decorated));
            entries.addAll(delegate.entrySet());
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return entries;
        }

        record EntryImpl(String key, Object value, int index, boolean first, boolean last)
                implements Map.Entry<String, Object> {

            @Override
            public String getKey() {
                return key;
            }

            @Override
            public Object getValue() {
                return value;
            }

            @Override
            public Object setValue(Object value) {
                throw new UnsupportedOperationException();
            }
        }
    }

    static final class TemplateObjectHandler extends ReflectionObjectHandler {

        static final String ARGUMENT = "\"((?:\\\\.|[^\"\\\\])*)\"|'((?:\\\\.|[^'\\\\])*)'|([^\\s\"']\\S*)";
        static final Pattern REPLACE_PATTERN = Pattern.compile("replace(?:\\s+(?:" + ARGUMENT + ")){3}\\s*");
        static final Pattern ARGUMENT_PATTERN = Pattern.compile(ARGUMENT);

        @Override
        public Wrapper find(String name, List<Object> scopes) {
            if (name.startsWith("replace ")) {
                if (REPLACE_PATTERN.matcher(name).matches()) {
                    Argument[] args = parse(name);
                    return sc -> call(args, sc);
                } else {
                    throw new IllegalArgumentException("replace requires exactly 3 arguments");
                }
            }
            return super.find(name, scopes);
        }

        String call(Argument[] args, List<Object> scopes) {
            String value = resolve(args[0], scopes);
            String target = resolve(args[1], scopes);
            String replacement = resolve(args[2], scopes);
            return value.replace(target, replacement);
        }

        String resolve(Argument arg, List<Object> scopes) {
            if (arg.literal) {
                return arg.value;
            }
            Object resolved = find(arg.value, scopes).call(scopes);
            return resolved == null ? "" : stringify(coerce(resolved));
        }

        static Argument[] parse(String source) {
            Argument[] args = new Argument[3];
            Matcher matcher = ARGUMENT_PATTERN.matcher(source.substring("replace".length()));
            for (int i = 0; matcher.find() && i < 3; i++) {
                if (matcher.group(1) != null) {
                    args[i] = new Argument(unescape(matcher.group(1)), true);
                } else if (matcher.group(2) != null) {
                    args[i] = new Argument(unescape(matcher.group(2)), true);
                } else {
                    args[i] = new Argument(matcher.group(3), false);
                }
            }
            return args;
        }

        static String unescape(String source) {
            StringBuilder sb = new StringBuilder(source.length());
            for (int i = 0; i < source.length(); i++) {
                char ch = source.charAt(i);
                if (ch == '\\' && i + 1 < source.length()) {
                    ch = source.charAt(++i);
                }
                sb.append(ch);
            }
            return sb.toString();
        }

        record Argument(String value, boolean literal) {
        }
    }
}
