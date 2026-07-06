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

import java.util.List;

import org.junit.jupiter.api.Test;

import static io.helidon.build.common.xml.XMLElement.read;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test {@link ConfigDiff}.
 */
class ConfigDiffTest {

    @Test
    void valueTest() {
        List<ConfigDiff> diffs = ConfigDiff.diff(read("<a><b>c</b></a>"), read("<a><b>not-c</b></a>"));
        assertThat(diffs.size(), is(1));
        assertThat(diffs.get(0).asString(), is("b was 'c' but is now 'not-c'"));
    }

    @Test
    void attributeTest() {
        List<ConfigDiff> diffs;

        diffs = ConfigDiff.diff(read("<a b=\"c\"/>"), read("<a b=\"not-c\"/>"));
        assertThat(diffs.size(), is(1));
        assertThat(diffs.get(0).asString(), is("@b was 'c' but is now 'not-c'"));

        diffs = ConfigDiff.diff(read("<a b=\"c\" d=\"e\"/>"), read("<a b=\"not-c\" d=\"not-e\"/>"));
        assertThat(diffs.size(), is(2));
        assertThat(diffs.get(0).asString(), is("@b was 'c' but is now 'not-c'"));
        assertThat(diffs.get(1).asString(), is("@d was 'e' but is now 'not-e'"));
    }

    @Test
    void addTest() {
        List<ConfigDiff> diffs;

        diffs = ConfigDiff.diff(read("<a></a>"), read("<a><b>c</b></a>"));
        assertThat(diffs.size(), is(1));
        assertThat(diffs.get(0).asString(), is("b has been added"));

        diffs = ConfigDiff.diff(read("<a><b>c</b></a>"), read("<a><b>c</b><b>d</b></a>"));
        assertThat(diffs.size(), is(1));
        assertThat(diffs.get(0).asString(), is("b[1] has been added"));
    }

    @Test
    void removeTest() {
        List<ConfigDiff> diffs;

        diffs = ConfigDiff.diff(read("<a><b>c</b></a>"), read("<a></a>"));
        assertThat(diffs.size(), is(1));
        assertThat(diffs.get(0).asString(), is("b has been removed"));

        diffs = ConfigDiff.diff(read("<a><b>c</b><d>e</d></a>"), read("<a><b>c</b></a>"));
        assertThat(diffs.size(), is(1));
        assertThat(diffs.get(0).asString(), is("d has been removed"));
    }

    @Test
    void mixedChangesTest() {
        List<ConfigDiff> diffs;

        diffs = ConfigDiff.diff(read("<a><b>c</b></a>"), read("<a><d>e</d></a>"));
        assertThat(diffs.size(), is(2));
        assertThat(diffs.get(0).asString(), is("b has been removed"));
        assertThat(diffs.get(1).asString(), is("d has been added"));

        diffs = ConfigDiff.diff(read("<a><b>c</b><d>e</d></a>"), read("<a><d>e</d></a>"));
        assertThat(diffs.size(), is(1));
        assertThat(diffs.get(0).asString(), is("b has been removed"));

        diffs = ConfigDiff.diff(read("<a><b>c</b><d>e</d></a>"), read("<a><d>e</d><f>g</f><h>i</h></a>"));
        assertThat(diffs.size(), is(3));
        assertThat(diffs.get(0).asString(), is("b has been removed"));
        assertThat(diffs.get(1).asString(), is("f has been added"));
        assertThat(diffs.get(2).asString(), is("h has been added"));
    }
}
