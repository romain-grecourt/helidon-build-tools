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
import java.util.stream.Collectors;

/**
 * Prompt of the one or more values from the list.
 */
public class ListPrompt extends Prompt<List<String>> {

//    private final String min;
//    private final String max;
//    private final List<Option> options = new ArrayList<>();
//
//    private ListPrompt(Builder builder) {
//        super(builder);
//        this.max = builder.max;
//        this.min = builder.min;
//        this.options.addAll(builder.options);
//    }
//
//    /**
//     * Create a new builder.
//     *
//     * @return a new builder
//     */
//    public static ListPrompt.Builder builder() {
//        return new ListPrompt.Builder();
//    }
//
//    @Override
//    public List<String> accept(Prompter prompter) {
//        return prompter.prompt(this);
//    }
//
//    @Override
//    public DescriptorNodes.ContextNode<?> acceptAndConvert(Prompter prompter, String path) {
//        return new DescriptorNodes.ContextListNode(path, prompter.prompt(this));
//    }
//
//    public static class Builder extends Prompt.Builder<ListPrompt, ListPrompt.Builder> {
//
//        private String min;
//        private String max;
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
//        /**
//         * Set the minimum.
//         *
//         * @param min min
//         * @return Builder
//         */
//        public Builder min(String min) {
//            this.min = min;
//            return this;
//        }
//
//        /**
//         * Set the maximum.
//         *
//         * @param max max
//         * @return Builder
//         */
//        public Builder max(String max) {
//            this.max = max;
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
//                throw new IllegalArgumentException("UserInputAST must contain a child note");
//            }
//            if (userInput.children().get(0) instanceof DescriptorNodes.InputListNode) {
//                DescriptorNodes.InputListNode inputList = (DescriptorNodes.InputListNode) userInput.children().get(0);
//
//                initFields(userInput);
//                min = inputList.descriptor().min();
//                max = inputList.descriptor().max();
//                options.addAll(
//                        inputList.children().stream()
//                                .filter(ch -> ch instanceof DescriptorNodes.InputOptionNode)
//                                .map(ch -> (DescriptorNodes.InputOptionNode) ch)
//                                .map(o -> new Option(o.descriptor().label(), o.value(), o.descriptor().help()))
//                                .collect(Collectors.toList())
//                );
//
//                return this;
//            }
//            throw new IllegalArgumentException(
//                    String.format(
//                            "Incorrect type of the child node in the UserInputAST instance. Must be - %s. Actual - %s.",
//                            ListPrompt.class.getName(),
//                            userInput.children().get(0).getClass().getName()
//                    )
//            );
//        }
//
//        @Override
//        public ListPrompt build() {
//            return new ListPrompt(this);
//        }
//    }
}
