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

package io.helidon.build.archetype.engine.v2.prompter;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Prompt of the one value from the enum.
 */
public class EnumPrompt extends Prompt<String> {

//    private final List<Option> options = new ArrayList<>();
//
//    private EnumPrompt(Builder builder) {
//        super(builder);
//        this.options.addAll(builder.options);
//    }
//
//    /**
//     * Get the options.
//     *
//     * @return options
//     */
//    public List<Option> options() {
//        return options;
//    }
//
//    /**
//     * Create a new builder.
//     *
//     * @return a new builder
//     */
//    public static EnumPrompt.Builder builder() {
//        return new EnumPrompt.Builder();
//    }
//
//    @Override
//    public String accept(Prompter prompter) {
//        return prompter.prompt(this);
//    }
//
//    @Override
//    public ContextNode<?> acceptAndConvert(Prompter prompter, String path) {
//        return new ContextEnumNode(path, prompter.prompt(this));
//    }
//
//    public static class Builder extends Prompt.Builder<EnumPrompt, Builder> {
//
//        private List<Option> options = new ArrayList<>();
//
//        /**
//         * Set the options.
//         *
//         * @param options list of options
//         * @return Builder
//         */
//        public Builder options(List<Option> options) {
//            if (options != null) {
//                this.options = options;
//            }
//            return this;
//        }
//
//        @Override
//        public Builder instance() {
//            return this;
//        }
//
//        @Override
//        public Builder userInputAST(UserInputNode userInput) {
//            if (userInput.children().isEmpty()) {
//                throw new IllegalArgumentException("UserInputNode must contain a child");
//            }
//            if (userInput.children().get(0) instanceof InputEnumNode) {
//                InputEnumNode inputEnum = (InputEnumNode) userInput.children().get(0);
//                initFields(userInput);
//                options.addAll(
//                        inputEnum.children()
//                                 .stream()
//                                 .filter(ch -> ch instanceof InputOptionNode)
//                                 .map(ch -> (InputOptionNode) ch)
//                                 .map(o -> new Option(o.descriptor().label(), o.value(), o.descriptor().help()))
//                                 .collect(toList()));
//                return this;
//            }
//            throw new IllegalArgumentException(String.format(
//                    "Incorrect child type, expected: %s, actual: %s",
//                    EnumPrompt.class.getName(),
//                    userInput.children().get(0).getClass().getName()));
//        }
//
//        @Override
//        public EnumPrompt build() {
//            return new EnumPrompt(this);
//        }
//    }
}
