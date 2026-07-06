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

import io.helidon.build.common.xml.XMLElement;

/**
 * Config support.
 */
class ConfigHelper {

    private ConfigHelper() {
        // cannot be instantiated
    }

    /**
     * Compute the path of an element.
     *
     * @param elt element
     * @return path
     */
    static String path(XMLElement elt) {
        return subpath(null, elt);
    }

    /**
     * Compute the path of an element.
     *
     * @param begin element
     * @param end   element
     * @return path
     */
    static String subpath(XMLElement begin, XMLElement end) {
        StringBuilder sb = new StringBuilder();
        XMLElement e = end;
        while (e != null && e != begin) {
            String segment = e.name();
            int index = index(e);
            if (index >= 0) {
                segment += "[" + index + "]";
            }
            if (e != end) {
                segment += "/";
            }
            sb.insert(0, segment);
            e = e.parent();
        }
        return sb.toString();
    }

    /**
     * Compute the index among the siblings with the same name.
     *
     * @param elt element
     * @return index, or {@code -1} if there are no siblings
     */
    static int index(XMLElement elt) {
        int count = 0;
        if (elt.parent() != null) {
            List<XMLElement> elements = elt.parent().children(elt.name());
            if (elements.size() > 1) {
                for (XMLElement e : elements) {
                    if (e == elt) {
                        return count;
                    }
                    count++;
                }
            }
        }
        return -1;
    }
}
