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

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import javax.xml.bind.JAXB;

import io.helidon.build.userflow.UserFlow.Property;
import io.helidon.build.userflow.UserFlow.Selector;
import io.helidon.build.userflow.UserFlow.Step;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.nullValue;

/**
 * Tests {@link UserFlow}.
 */
public class UserFlowTest {

    /**
     * Test marshalling.
     * @param args not used
     */
    public static void main(String[] args) {
        UserFlow flow = new UserFlow();
        List<Step> rootSteps = new ArrayList<>();
        Property gluten = new Property();
        gluten.setId("gluten");
        gluten.setText("Are you alergic to gluten");
        rootSteps.add(gluten);
        Selector lunchSelector = new Selector();
        lunchSelector.setText("Select a lunch type");
        List<Property> lunchChoices = new ArrayList<>();
        Property mexican = new Property();
        mexican.setId("mexican");
        mexican.setDefault(true);
        mexican.setText("Mexican");
        lunchChoices.add(mexican);
        Property italian = new Property();
        italian.setId("italian");
        italian.setText("Italian");
        lunchChoices.add(italian);
        lunchSelector.setChoices(lunchChoices);
        Selector mealSelector = new Selector();
        mealSelector.setText("Is it lunch or diner");
        Property lunch = new Property();
        lunch.setId("lunch");
        List<Step> lunchSteps = new ArrayList<>();
        lunchSteps.add(lunchSelector);
        lunch.setSteps(lunchSteps);
        List<Property> mealChoices = new ArrayList<>();
        mealChoices.add(lunch);
        mealSelector.setChoices(mealChoices);
        rootSteps.add(mealSelector);
        flow.setSteps(rootSteps);
        JAXB.marshal(flow, System.out);
    }

    /**
     * Assert a property selector step.
     * @param prop property to process
     * @param numSteps number of steps in the property
     * @param text select text
     * @param numChoices number of selector choices
     * @return choices
     */
    private static List<Property> selectorStep(Property prop, int numSteps, String text, int numChoices) {
        assertThat(prop.getSteps(), is(not(nullValue())));
        assertThat(prop.getSteps().size(), is(numSteps));
        assertThat(prop.getSteps().get(0), is(instanceOf(Selector.class)));
        Selector selector = (Selector) prop.getSteps().get(0);
        assertThat(selector.getText(), is(text));
        assertThat(selector.getChoices(), is(not(nullValue())));
        assertThat(selector.getChoices().size(), is(numChoices));
        return selector.getChoices();
    }

    /**
     * Assert a choice.
     * @param prop property to process
     * @param id property id
     * @param isdefault property default attribute
     */
    private static void assertChoice(Property prop, String id, boolean isdefault) {
        assertThat(prop.getId(), is(id));
        assertThat(prop.isDefault(), is(isdefault));
    }

    @Test
    public void testCreate() throws IOException {
        UserFlow flow = UserFlow.create(UserFlowTest.class.getResource("what-to-eat.xml").openStream());
        List<Step> rootSteps = flow.getSteps();
        assertThat(rootSteps, is(not(nullValue())));
        assertThat(rootSteps.size(), is(2));
        assertThat(rootSteps.get(0), is(instanceOf(Property.class)));
        Property gluten = (Property) rootSteps.get(0);
        assertThat(gluten.getId(), is("gluten"));
        assertThat(gluten.getText(), is("Are you alergic to gluten"));
        assertThat(gluten.isDefault(), is(not(true)));
        assertThat(rootSteps.get(1), is(instanceOf(Selector.class)));
        Selector meal = (Selector) rootSteps.get(1);
        assertThat(meal.getText(), is("Is it lunch or diner"));
        List<Property> mealChoices = meal.getChoices();
        assertThat(mealChoices, is(not(nullValue())));
        assertThat(mealChoices.size(), is(2));
        assertChoice(mealChoices.get(0), "lunch", /* default */ false);
        List<Property> lunchChoices = selectorStep(mealChoices.get(0), 1, "Select a lunch type", 2);
        assertChoice(lunchChoices.get(0), "mexican", /* default */ true);
        List<Property> mexicanChoices = selectorStep(lunchChoices.get(0), 3, "Select a mexican dish", 3);
        assertChoice(mexicanChoices.get(0), "carnitas", /* default */ true);
        assertChoice(mexicanChoices.get(1), "burrito", /* default */ false);
        assertChoice(mexicanChoices.get(2), "quesadilla", /* default */ false);
        assertThat(mexicanChoices.get(2).getUnlessProperties(), contains(gluten));
        assertThat(lunchChoices.get(0).getSteps().get(1), is(instanceOf(Property.class)));
        Property mexicanBeans = (Property) lunchChoices.get(0).getSteps().get(1);
        assertThat(mexicanBeans.getId(), is("beans"));
        assertThat(mexicanBeans.getText(), is("Do you want beans"));
        assertThat(mexicanBeans.isDefault(), is(true));
        List<Property> mexicanBeansChoices = selectorStep(mexicanBeans, 1, "Select beans type", 2);
        assertChoice(mexicanBeansChoices.get(0), "red", /* default */ true);
        assertChoice(mexicanBeansChoices.get(1), "black", /* default */ false);
        Property guacamole = (Property) lunchChoices.get(0).getSteps().get(2);
        assertThat(guacamole.getId(), is("guacamole"));
        assertThat(guacamole.getText(), is("Do you want guacamole"));
        assertThat(guacamole.getIfProperties(), contains(gluten));
        List<Property> italianChoices = selectorStep(lunchChoices.get(1), 1, "Select an italian dish", 3);
        assertChoice(italianChoices.get(0), "pizza", /* default */ true);
        assertChoice(italianChoices.get(1), "pasta", /* default */ false);
        assertChoice(italianChoices.get(2), "bruschetta", /* default */ false);
        assertThat(italianChoices.get(2).getUnlessProperties(), contains(gluten));
        assertChoice(mealChoices.get(1), "diner", /* default */ false);
        List<Property> dinerChoices = selectorStep(mealChoices.get(1), 1, "Select a diner type", 2);
        assertThat(dinerChoices.get(0).getId(), is("french"));
        List<Property> frenchChoices = selectorStep(dinerChoices.get(0), 1, "Select a french dish", 2);
        assertChoice(frenchChoices.get(0), "snails", /* default */ true);
        assertChoice(frenchChoices.get(1), "frogs", /* default */ false);
        List<Property> japaneseChoices = selectorStep(dinerChoices.get(1), 1, "Select a japanese dish", 2);
        assertChoice(japaneseChoices.get(0), "sushi", /* default */ true);
        assertChoice(japaneseChoices.get(1), "yakiniku", /* default */ false);
    }
}
