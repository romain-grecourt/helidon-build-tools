/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.build.userflow;

import java.io.InputStream;
import java.util.List;
import javax.xml.bind.JAXB;
import javax.xml.bind.annotation.XmlAccessOrder;
import javax.xml.bind.annotation.XmlAccessorOrder;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * User flow model.
 */
@XmlRootElement(name = "userflow")
public final class UserFlow {

    /**
     * Create a new user flow instance from a descriptor input stream.
     * @param is input stream
     * @return UserFlow
     */
    public static UserFlow create(InputStream is) {
        return JAXB.unmarshal(is, UserFlow.class);
    }

    private List<Step> steps;

    @XmlElements({
        @XmlElement(name = "property", type = Property.class),
        @XmlElement(name = "select", type = Selector.class)
    })
    public List<Step> getSteps() {
        return steps;
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps;
    }

    @XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
    @XmlType(propOrder = {"text", "ifProperties", "unlessProperties"})
    public static abstract class Step {

        private List<Property> ifProperties;
        private List<Property> unlessProperties;
        private String text;

        @XmlIDREF
        @XmlAttribute(name = "if")
        public List<Property> getIfProperties() {
            return ifProperties;
        }

        public void setIfProperties(List<Property> ifProperties) {
            this.ifProperties = ifProperties;
        }

        @XmlIDREF
        @XmlAttribute(name = "unless")
        public List<Property> getUnlessProperties() {
            return unlessProperties;
        }

        public void setUnlessProperties(List<Property> unlessProperties) {
            this.unlessProperties = unlessProperties;
        }

        @XmlAttribute(name = "text", required = true)
        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    @XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
    @XmlType(propOrder = {"id", "default", "steps"})
    public static final class Property extends Step {

        private String id;
        private List<Step> steps;
        private Boolean isdefault;

        @XmlID
        @XmlAttribute(name = "id", required = true)
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @XmlElements({
            @XmlElement(name = "property", type = Property.class),
            @XmlElement(name = "select", type = Selector.class)
        })
        public List<Step> getSteps() {
            return steps;
        }

        public void setSteps(List<Step> steps) {
            this.steps = steps;
        }

        @XmlAttribute(name = "default")
        @XmlJavaTypeAdapter(BooleanAdapter.class)
        public Boolean isDefault() {
            if (isdefault == null) {
                return false;
            }
            return isdefault;
        }

        public void setDefault(Boolean isdefault) {
            this.isdefault = isdefault;
        }
    }

    public static final class Selector extends Step {

        private List<Property> choices;

        @XmlElement(name = "property")
        public List<Property> getChoices() {
            return choices;
        }

        public void setChoices(List<Property> choices) {
            this.choices = choices;
        }
    }

    public static class BooleanAdapter extends XmlAdapter<String, Boolean> {

        @Override
        public Boolean unmarshal(String s) throws Exception {
            return "yes".equals(s);
        }

        @Override
        public String marshal(Boolean b) throws Exception {
            if (b != null && b) {
                return "yes";
            }
            return "no";
        }
    }
}
