/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.build.maven.sitegen.asciidoctor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import io.helidon.build.maven.sitegen.RenderingException;
import io.helidon.build.maven.sitegen.SiteEngine;
import io.helidon.build.maven.sitegen.freemarker.FreemarkerEngine;
import io.helidon.build.maven.sitegen.freemarker.TemplateLoader;

import org.asciidoctor.ast.ContentNode;
import org.asciidoctor.ast.Cursor;
import org.asciidoctor.ast.PhraseNode;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.converter.AbstractConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.helidon.build.maven.sitegen.asciidoctor.CardBlockProcessor.BLOCK_LINK_TEXT;

/**
 * An asciidoctor converter that supports backends implemented with Freemarker.
 * <p>
 * The Freemarker templates are loaded from classpath, see {@link TemplateLoader}
 */
public class AsciidocConverter extends AbstractConverter<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsciidocConverter.class);

    private final FreemarkerEngine templateEngine;

    /**
     * Create a new instance of {@link AsciidocConverter}.
     *
     * @param backend the backend name
     * @param opts    the asciidoctor invocation options
     */
    public AsciidocConverter(String backend, Map<String, Object> opts) {
        super(backend, opts);
        templateEngine = SiteEngine.get(backend).freemarker();
    }

    @Override
    public String convert(ContentNode node, String transform, Map<Object, Object> opts) {
        if (node != null && node.getNodeName() != null) {
            String templateName;
            if (node.equals(node.getDocument())) {
                templateName = "document";
            } else if (node.isBlock()) {
                templateName = "block_" + node.getNodeName();
            } else {
                // detect phrase node for generated block links
                if (node.getNodeName().equals("inline_anchor")
                        && BLOCK_LINK_TEXT.equals(((PhraseNode) node).getText())) {

                    // store the link model as an attribute in the corresponding
                    // block
                    node.getParent()
                        .getParent()
                        .getAttributes()
                        .put("_link", node);
                    // the template for the block is responsible for rendering
                    // the link, discard the output
                    return "";
                }
                templateName = node.getNodeName();
            }
            LOGGER.debug("Rendering node: {}", node);
            try {
                return templateEngine.renderString(templateName, node);
            } catch (RenderingException ex) {
                if (ex instanceof RenderingException0) {
                    // only raise the underlying error
                    // don't represent the rendering stack
                    throw ex;
                }
                Cursor location = sourceLocation(node);
                String filename = location != null ? location.getPath() : "?";
                String lineno = location != null ? String.valueOf(location.getLineNumber()) : "?";
                throw new RenderingException0(String.format(
                        "An error occurred during rendering of '%s' at line %s", filename, lineno),
                        ex);
            }
        } else {
            return "";
        }
    }

    @Override
    public void write(String output, OutputStream out) throws IOException {
        out.write(output.getBytes());
    }

    private static Cursor sourceLocation(ContentNode node) {
        while (node != null) {
            if (node instanceof StructuralNode) {
                Cursor sourceLocation = ((StructuralNode) node).getSourceLocation();
                if (sourceLocation != null) {
                    return sourceLocation;
                }
            }
            node = node.getParent();
        }
        return null;
    }

    private static final class RenderingException0 extends RenderingException {
        private RenderingException0(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
