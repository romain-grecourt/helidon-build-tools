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

import java.util.List;

/**
 * Archetype driver.
 */
public interface Driver {

    /**
     * Step event.
     *
     * @param info step info (label, help)
     */
    void onStep(Info info);

    /**
     * Prompt for a text value.
     *
     * @param prompt prompt text
     * @param info   input info (label, help)
     * @return user input (response from the user)
     */
    String promptText(String prompt, Info info);

    /**
     * Prompt for a boolean value.
     *
     * @param prompt prompt text
     * @param info   input info (label, help)
     * @return user input (response from the user)
     */
    boolean promptBoolean(String prompt, Info info);

    /**
     * Prompt a "one-of" option selection.
     *
     * @param prompt  prompt text
     * @param info    input info (label, help)
     * @param options the list of options to select from
     * @return user input (the value of the chosen option)
     */
    String promptEnum(String prompt, Info info, List<Option> options);

    /**
     * Prompt for an "any-of" option selection.
     *
     * @param prompt  prompt text
     * @param info    input info (label, help)
     * @param options the list of options to select from
     * @return user input (the values of the chosen options)
     */
    List<String> promptList(String prompt, Info info, List<Option> options);

    /**
     * Info.
     */
    class Info {

        private final String label;
        private final String help;

        /**
         * Create a new instance.
         *
         * @param label label
         * @param help  help
         */
        Info(String label, String help) {
            this.label = label;
            this.help = help;
        }

        /**
         * Get the label.
         *
         * @return label
         */
        public String label() {
            return label;
        }

        /**
         * Get the help.
         *
         * @return help
         */
        public String help() {
            return help;
        }
    }

    /**
     * Selectable option.
     */
    final class Option extends Info {

        private final String value;

        /**
         * Create a new instance.
         *
         * @param value option value
         * @param label label
         * @param help  help
         */
        Option(String value, String label, String help) {
            super(label, help);
            this.value = value;
        }

        /**
         * Get the option value.
         *
         * @return value
         */
        public String value() {
            return value;
        }
    }
}
