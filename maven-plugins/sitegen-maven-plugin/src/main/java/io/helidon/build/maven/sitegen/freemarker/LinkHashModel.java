/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

package io.helidon.build.maven.sitegen.freemarker;

import java.util.Map;

import io.helidon.build.maven.sitegen.models.Link;
import io.helidon.build.maven.sitegen.models.Page;

import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import org.asciidoctor.ast.PhraseNode;

/**
 * Link hash model.
 */
public final class LinkHashModel implements TemplateHashModel {

    private final ObjectWrapper objectWrapper;

    /**
     * Create a new instance.
     *
     * @param objectWrapper object wrapper
     */
    LinkHashModel(ObjectWrapper objectWrapper) {
        this.objectWrapper = objectWrapper;
    }

    /**
     * Create a new link helper.
     *
     * @param node the node representing the link
     * @return the link helper or {@code null} if the provided node is {@code null}
     */
    @SuppressWarnings("unchecked")
    public Link link(PhraseNode node) {
        if (node == null) {
            return null;
        }
        Map<String, Object> docAttrs = node.getDocument().getAttributes();
        Map<String, Object> nodeAttrs = node.getAttributes();
        return Link.builder()
                   .pages((Map<String, Page>) docAttrs.get("pages"))
                   .page((Page) docAttrs.get("page"))
                   .path((String) nodeAttrs.get("path"))
                   .refId((String) nodeAttrs.get("refid"))
                   .fragment((String) nodeAttrs.get("fragment"))
                   .title((String) nodeAttrs.get("title"))
                   .target(node.getTarget())
                   .window((String) nodeAttrs.get("window"))
                   .type(node.getType())
                   .text(node.getText())
                   .id(node.getId())
                   .build();
    }

    @Override
    public TemplateModel get(String key) throws TemplateModelException {
        // return method model if method name found for key
        if (SimpleMethodModel.hasMethodWithName(this, key)) {
            return new SimpleMethodModel(objectWrapper, this, key);
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
