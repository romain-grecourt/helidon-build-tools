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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import io.helidon.build.maven.sitegen.models.Header;
import io.helidon.build.maven.sitegen.models.Nav;
import io.helidon.build.maven.sitegen.models.PageFilter;
import io.helidon.build.maven.sitegen.models.SourcePathFilter;
import io.helidon.build.maven.sitegen.models.StaticAsset;
import io.helidon.build.maven.sitegen.models.WebResource;
import io.helidon.build.maven.sitegen.models.WebResource.Location;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import static io.helidon.build.maven.sitegen.TestHelper.*;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests config loading.
 */
@SuppressWarnings("ConstantConditions")
public class ConfigTest {

    private static void assertWebResource(WebResource actual,
                                          String expectedPath,
                                          String expectedHref,
                                          String name) {
        assertNotNull(actual, name);
        Assertions.assertEquals(Location.create(expectedPath, expectedHref), actual.location(), name);
    }

    private static Config loadConfig(String path) {
        InputStream is = ConfigTest.class.getClassLoader().getResourceAsStream(path);
        assertThat(is, is(not(nullValue())));
        Yaml yaml = new Yaml(new SafeConstructor());
        return Config.create(yaml.loadAs(new InputStreamReader(is), Object.class));
    }

    @Test
    public void testNavItem() {
        Config config = loadConfig("config/nav.yaml");

        Nav nav;
        Deque<Nav> stack = new ArrayDeque<>();

        nav = Nav.create(config);
        assertThat(nav, is(not(nullValue())));
        assertThat(nav.title(), is("Pet Project Documentation"));
        assertThat(nav.glyph(), is(not(nullValue())));
        assertThat(nav.glyph().type(), is("icon"));
        assertThat(nav.glyph().value(), is("import_contacts"));
        assertThat(nav.isRoot(), is(true));
        assertThat(nav.items().size(), is(3));
        stack.push(nav);

        nav = nav.items().get(0);
        assertThat(nav.title(), is("Main Documentation"));
        assertThat(nav.glyph(), is(nullValue()));
        assertThat(nav.pathprefix(), is("/about"));
        assertThat(nav.items().size(), is(3));
        stack.push(nav);

        nav = stack.peek().items().get(0);
        assertThat(nav.title(), is("About"));
        assertThat(nav.pathprefix(), is("/about"));
        assertThat(nav.glyph(), is(not(nullValue())));
        assertThat(nav.glyph().type(), is("icon"));
        assertThat(nav.glyph().value(), is("weekend"));

        nav = stack.peek().items().get(1);
        assertThat(nav.title(), is("Getting Started"));
        assertThat(nav.pathprefix(), is("/getting-started"));
        assertThat(nav.glyph(), is(not(nullValue())));
        assertThat(nav.glyph().type(), is("icon"));
        assertThat(nav.glyph().value(), is("weekend"));
        assertThat(nav.includes().size(), is(0));
        assertThat(nav.excludes(), hasItem("**/start*.adoc"));

        nav = stack.peek().items().get(2);
        assertThat(nav.title(), is("Let's code"));
        assertThat(nav.pathprefix(), is("/lets-code"));
        assertThat(nav.glyph(), is(not(nullValue())));
        assertThat(nav.glyph().type(), is("icon"));
        assertThat(nav.glyph().value(), is("weekend"));
        assertThat(nav.includes(), hasItem("**/*.adoc"));
        assertThat(nav.excludes().size(), is(0));

        stack.pop();
        assertThat(stack.isEmpty(), is(false));

        nav = stack.peek().items().get(1);
        assertThat(nav.title(), is("Extra Resources"));
        assertThat(nav.glyph(), is(nullValue()));
        assertThat(nav.items().size(), is(2));
        stack.push(nav);

        nav = stack.peek().items().get(0);
        assertThat(nav.title(), is("Google"));
        assertThat(nav.href(), is("https://google.com"));
        assertThat(nav.target(), is("_blank"));
        assertThat(nav.items().size(), is(0));

        nav = stack.peek().items().get(1);
        assertThat(nav.title(), is("Amazon"));
        assertThat(nav.href(), is("https://amazon.com"));
        assertThat(nav.target(), is("_blank"));
        assertThat(nav.items().size(), is(0));


        stack.pop();
        assertThat(stack.isEmpty(), is(false));

        nav = stack.peek().items().get(2);
        assertThat(nav.title(), is("GitHub"));
        assertThat(nav.href(), is("https://github.com"));
        assertThat(nav.target(), is("_blank"));
        assertThat(nav.items().size(), is(0));
    }

    @Test
    public void testLoadConfig() {

        // TODO add assertions for navigation: glyph, pathprefix
        Site site = Site.builder()
                        .config(loadConfig("config/basic.yaml"))
                        .build();
        assertNotNull(site, "site");

        // backend
        Backend backend = site.backend();
        assertNotNull(backend, "backend");
        assertString("basic", backend.getName(), "backend");

        SiteEngine engine = site.engine();
        assertNotNull(engine);
        assertNotNull(engine.asciidoc(), "engine.asciidoctor");
        assertNotNull(engine.freemarker(), "engine.freemarker");

        // asciidoctor imagesDir
        assertString("./images", engine.asciidoc().imagesDir(), "engine.asciidoctor.imagesdir");

        // asciidoctor libraries
        List<String> asciidoctorLibs = engine.asciidoc().libraries();
        assertList(1, asciidoctorLibs, "engine.asciidoctor.libraries");
        assertString("testlib", asciidoctorLibs.get(0), "engine.asciidoctor.libraries[0]");

        // asciidoctor attributes
        Map<String, Object> asciidoctorAttrs = engine.asciidoc().attributes();
        assertEquals(1, asciidoctorAttrs.size(), "engine.asciidoctor.attributes");
        assertEquals("alice", asciidoctorAttrs.get("bob"), "engine.asciidoctor.attributes[bob]");

        // freemarker directives
        Map<String, String> freemarkerDirectives = engine.freemarker().directives();
        assertEquals(1, freemarkerDirectives.size(), "engine.freemarker.directives");
        assertEquals("com.acme.foo.FooDirective", freemarkerDirectives.get("foo"), "engine.freemarker.directives[foo]");

        // freemarker model
        Map<String, String> freemarkerModel = engine.freemarker().model();
        assertEquals(1, freemarkerModel.size(), "engine.freemarker.model");
        assertEquals("value", freemarkerModel.get("key"), "engine.freemarker.model[foo]");

        // header
        Header header = site.header();
        assertNotNull(header, "header");

        // favicon
        assertWebResource(header.favicon(), "assets/images/favicon.ico", null, "header.favicon");

        // stylesheets
        List<WebResource> stylesheets = header.stylesheets();
        assertList(2, stylesheets, "header.stylesheets");
        assertWebResource(stylesheets.get(0), "assets/css/style.css", null, "header.stylesheets[0]");
        assertWebResource(stylesheets.get(1), null, "https://css.com/style.css", "header.stylesheets[1]");

        // scripts
        List<WebResource> scripts = header.scripts();
        assertList(2, scripts, "header.scripts");
        assertWebResource(scripts.get(0), "assets/js/script.js", null, "header.scripts[0]");
        assertWebResource(scripts.get(1), null, "https://js.com/script.js", "header.scripts[1]");

        // meta
        Map<String, String> meta = header.meta();
        assertNotNull(meta, "header.meta");
        assertEquals("a global description", meta.get("description"), "header.meta[description]");

        // static assets
        List<StaticAsset> assets = site.assets();
        assertList(1, assets, "assets");

        StaticAsset firstAsset = assets.get(0);
        assertEquals("/assets", firstAsset.target(), "assets[0].target");
        assertList(1, firstAsset.includes(), "assets[0].includes");
        assertEquals(System.getProperty("basedir", "") + "/assets/**", firstAsset.includes().get(0), "assets[0].includes[0]");
        assertList(1, firstAsset.excludes(), "assets[0].excludes");
        assertEquals("**/_*", firstAsset.excludes().get(0), "assets[0].excludes[0]");

        // pages
        List<PageFilter> pages = site.pages();
        assertList(1, pages, "pages");

        SourcePathFilter firstPageDef = pages.get(0);
        assertList(1, firstPageDef.includes(), "pages[0].includes");
        assertEquals("docs/**/*.adoc", firstPageDef.includes().get(0), "pages[0].includes[0]");
        assertList(1, firstPageDef.excludes(), "pages[0].excludes");
        assertEquals("**/_*", firstPageDef.excludes().get(0), "pages[0].excludes[0]");
    }

    @Test
    public void testLoadVuetifyConfig() {
        Site site = Site.builder()
                        .config(loadConfig("config/vuetify.yaml"))
                        .build();

        assertNotNull(site, "site");

        // backend
        Backend backend = site.backend();
        assertNotNull(backend, "backend");
        assertString("vuetify", backend.getName(), "backend.name");

        assertTrue(backend instanceof VuetifyBackend, "vuetify backend class");
        VuetifyBackend vbackend = (VuetifyBackend) backend;

        // homePage
        assertString("home.adoc", vbackend.home(), "homePage");

        // releases
        assertList(1, vbackend.releases(), "releases");
        assertString("1.0", vbackend.releases().get(0), "releases[0]");

        // navigation
        Nav navigation = vbackend.navigation();
        assertNotNull(navigation, "navigation");
        assertString("Pet Project Documentation", navigation.title(), "nav.title");

        List<Nav> topNavItems = navigation.items();
        assertList(3, topNavItems, "nav.items");

        Nav mainDocNavGroup = assertType(topNavItems.get(0), Nav.class, "nav.items[0]");
        assertEquals("Main Documentation", mainDocNavGroup.title(), "nav.items[0].title");
        assertEquals("/about", mainDocNavGroup.pathprefix(), "nav.items[0].pathprefix");

        List<Nav> mainDocNavItems = mainDocNavGroup.items();
        assertList(3, mainDocNavItems, "nav.items[0].items");

        Nav mainDoc1stItem = assertType(mainDocNavItems.get(0), Nav.class, "nav.items[0].items[0]");
        assertEquals("About", mainDoc1stItem.title(), "nav.items[0].items[0].title");
        assertEquals("/about", mainDoc1stItem.pathprefix(), "nav.items[0].items[0].pathprefix");

        Nav mainDoc2ndItem = assertType(mainDocNavItems.get(1), Nav.class, "nav.items[0].items[1]");
        assertEquals("Getting Started", mainDoc2ndItem.title(), "nav.items[0].items[1].title");
        assertEquals("/getting-started", mainDoc2ndItem.pathprefix(), "nav.items[0].items[1].pathprefix");

        Nav mainDoc3rdItem = assertType(mainDocNavItems.get(2), Nav.class, "nav.items[0].items[2]");
        assertEquals("Let's code", mainDoc3rdItem.title(), "nav.items[0].items[2].title");
        assertEquals("/lets-code", mainDoc3rdItem.pathprefix(), "nav.items[0].items[2].pathprefix");

        Nav extraResourcesNavGroup = assertType(topNavItems.get(1), Nav.class, "nav.items[1]");
        assertEquals("Extra Resources", extraResourcesNavGroup.title(), "nav.items[1].title");

        List<Nav> extraResourcesItems = extraResourcesNavGroup.items();
        assertList(2, extraResourcesItems, "nav.items[1].items");

        Nav google = assertType(extraResourcesItems.get(0), Nav.class, "nav.items[1].items[0]");
        assertEquals("Google", google.title(), "nav.items[1].items[0].title");
        assertEquals("https://google.com", google.href(), "nav.items[1].items[0].href");

        Nav amazon = assertType(extraResourcesItems.get(1), Nav.class, "nav.items[1].items[1]");
        assertEquals("Amazon", amazon.title(), "nav.items[1].items[1].title");
        assertEquals("https://amazon.com", amazon.href(), "nav.items[1].items[1].href");

        Nav githubNavLink = assertType(topNavItems.get(2), Nav.class, "nav.items[2]");
        assertEquals("Github", githubNavLink.title(), "nav.items[2].title");
        assertEquals("https://github.com", githubNavLink.href(), "nav.items[2].href");

        Map<String, String> options = vbackend.theme();
        assertNotNull(options, "backend.theme");

        assertEquals("#1976D2", options.get("primary"), "backend.theme.primary");
        assertEquals("#424242", options.get("secondary"), "backend.theme.secondary");
        assertEquals("#82B1FF", options.get("accent"), "backend.theme.accent");
        assertEquals("#FF5252", options.get("error"), "backend.theme.error");
        assertEquals("#2196F3", options.get("info"), "backend.theme.info");
        assertEquals("#4CAF50", options.get("success"), "backend.theme.success");
        assertEquals("#FFC107", options.get("warning"), "backend.theme.warning");
        assertEquals("true", options.get("toolbar.enabled"), "backend.theme.toolbar.enabled");
        assertEquals("true", options.get("navmenu.enabled"), "backend.theme.navmenu.enabled");
        assertEquals("true", options.get("navfooter.enabled"), "backend.theme.navfooter.enabled");
    }
}
