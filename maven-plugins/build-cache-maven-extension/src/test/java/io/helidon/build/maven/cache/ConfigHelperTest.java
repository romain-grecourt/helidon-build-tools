/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
package io.helidon.build.maven.cache;

import io.helidon.build.common.xml.XMLElement;

import org.junit.jupiter.api.Test;

import static io.helidon.build.common.xml.XMLElement.read;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link ConfigHelper}.
 */
class ConfigHelperTest {

    @Test
    void testRoot() {
        XMLElement node = read("<a/>");
        assertThat(ConfigHelper.index(node), is(-1));
        assertThat(ConfigHelper.path(node), is("a"));
    }

    @Test
    void testNoSiblings() {
        XMLElement node = read("<a><b><c>d</c></b></a>").children().get(0).children().get(0);
        assertThat(ConfigHelper.index(node), is(-1));
        assertThat(ConfigHelper.path(node), is("a/b/c"));
    }

    @Test
    void testSiblings() {
        var nodes = read("<a><b><c>d</c><c>e</c></b></a>").children().get(0).children();
        XMLElement node0 = nodes.get(0);
        assertThat(ConfigHelper.index(node0), is(0));
        assertThat(ConfigHelper.path(node0), is("a/b/c[0]"));
        XMLElement node1 = nodes.get(1);
        assertThat(ConfigHelper.index(node1), is(1));
        assertThat(ConfigHelper.path(node1), is("a/b/c[1]"));
    }

    @Test
    void testSubPath() {
        XMLElement a = read("<a><b><c><d/></c></b></a>");
        XMLElement b = a.children().get(0);
        XMLElement c = b.children().get(0);
        XMLElement d = c.children().get(0);
        assertThat(ConfigHelper.subpath(c, d), is("d"));
        assertThat(ConfigHelper.subpath(b, d), is("c/d"));
        assertThat(ConfigHelper.subpath(a, d), is("b/c/d"));
        assertThat(ConfigHelper.subpath(null, d), is("a/b/c/d"));
    }
}
