/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.build.maven.sitegen;

import java.nio.file.Path;

import io.helidon.build.maven.sitegen.models.Page.Metadata;
import io.helidon.build.maven.sitegen.asciidoctor.AsciidocPageRenderer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;

import static io.helidon.build.maven.sitegen.TestHelper.SOURCE_DIR_PREFIX;
import static io.helidon.build.maven.sitegen.TestHelper.assertString;
import static io.helidon.build.maven.sitegen.TestHelper.getFile;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@link Metadata}.
 */
public class PageMetadataTest {

    private static final Path SOURCE_DIR = getFile(SOURCE_DIR_PREFIX + "testmetadata").toPath();
    private static final String BACKEND_NAME = "dummy";
    private static AsciidocPageRenderer pageRenderer;

    @BeforeAll
    public static void init(){
        SiteEngine.register(BACKEND_NAME, SiteEngine.create());
        pageRenderer = AsciidocPageRenderer.create(BACKEND_NAME);
    }

    @AfterAll
    public static void cleanup(){
        SiteEngine.get(BACKEND_NAME).asciidoc().unregister();
        SiteEngine.deregister(BACKEND_NAME);
    }

    @Test
    public void testPageWithNoTitle(){
        try {
            readMetadata("no_title.adoc");
            fail("no_title.adoc is not a valid document");
        } catch (IllegalArgumentException ex) {
            // do nothing
        }
    }

    @Test
    public void testPageWithNoDescription(){
        Metadata m = null;
        try {
            m = readMetadata("no_description.adoc");
        } catch (Throwable ex) {
            fail("no_description.adoc is a valid document", ex);
        }
        assertString("This is a title", m.title(), "metadata.title");
        assertString("This is a title", m.h1(), "metadata.h1");
        assertNull(m.description(), "metadata.description");
    }

    @Test
    public void testPageWithTitleAndH1(){
        Metadata m = null;
        try {
            m = readMetadata("title_and_h1.adoc");
        } catch (Throwable ex) {
            fail("title_and_h1.adoc is a valid document", ex);
        }
        assertString("This is the document title", m.title(), "metadata.title");
        assertString("This is an h1 title", m.h1(), "metadata.h1");
    }

    @Test
    public void testPageWithDescription(){
        Metadata m = null;
        try {
            m = readMetadata("with_description.adoc");
        } catch (Throwable ex) {
            fail("with_description.adoc is a valid document", ex);
        }
        assertString("This is a description", m.description(), "metadata.description");
    }

    @Test
    public void testPageWithKeywords(){
        Metadata m = null;
        try {
            m = readMetadata("with_keywords.adoc");
        } catch (Throwable ex) {
            fail("with_keywords.adoc is a valid document", ex);
        }
        assertString("keyword1, keyword2, keyword3", m.keywords(), "metadata.keywords");
    }

    private static Metadata readMetadata(String filename){
        return pageRenderer.readMetadata(SOURCE_DIR.resolve(filename));
    }
}
