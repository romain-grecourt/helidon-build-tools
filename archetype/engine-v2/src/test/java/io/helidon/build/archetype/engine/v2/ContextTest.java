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

import io.helidon.build.archetype.engine.v2.ast.Value;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link Context}.
 */
public class ContextTest {

    @Test
    public void testLookup() {
        Context context = Context.create();
        context.push("foo", Value.create("foo-value"));

        Context.ContextValue value;

        value = context.lookup("foo");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("foo-value"));

        value = context.lookup("ROOT.foo");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("foo-value"));

        value = context.lookup("PARENT.foo");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("foo-value"));

        value = context.lookup("PARENT.PARENT.foo");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("foo-value"));

        value = context.lookup("PARENT.PARENT.PARENT.foo");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("foo-value"));
    }

    @Test
    public void testRelativeLookup() {
        Context context = Context.create();
        context.push("foo", Value.create("foo-value"));
        context.push("bar", Value.create("bar-value"));

        Context.ContextValue value;

        value = context.lookup("bar");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("bar-value"));

        value = context.lookup("ROOT.bar");
        assertThat(value, is(nullValue()));

        value = context.lookup("ROOT.foo");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("foo-value"));

        value = context.lookup("ROOT.foo.bar");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("bar-value"));

        value = context.lookup("PARENT.bar");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("bar-value"));

        value = context.lookup("PARENT.PARENT.bar");
        assertThat(value, is(nullValue()));

        value = context.lookup("PARENT.PARENT.PARENT.foo");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("foo-value"));

        value = context.lookup("PARENT.PARENT.PARENT.foo.bar");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("bar-value"));
    }

    @Test
    public void testPopInput() {
        Context context = Context.create();
        context.push("foo", Value.create("foo-value"));
        context.push("bar", Value.create("bar-value"));
        context.pop();

        Context.ContextValue value;

        value = context.lookup("bar");
        assertThat(value, is(nullValue()));

        value = context.lookup("foo");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("foo-value"));

        value = context.lookup("ROOT.foo");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("foo-value"));

        value = context.lookup("PARENT.foo");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("foo-value"));

        value = context.lookup("foo.bar");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("bar-value"));

        value = context.lookup("ROOT.foo.bar");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("bar-value"));

        value = context.lookup("PARENT.foo.bar");
        assertThat(value, is(notNullValue()));
        assertThat(value.asString(), is("bar-value"));
    }
}
