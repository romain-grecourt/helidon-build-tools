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

/**
 * Prompt of the text value.
 */
public class TextPrompt extends Prompt<String> {

//    private final String placeHolder;
//
//    private TextPrompt(
//            String stepLabel,
//            String stepHelp,
//            String help,
//            String label,
//            String name,
//            String def,
//            String prompt,
//            String placeHolder,
//            boolean optional,
//            boolean canBeGenerated
//    ) {
//        super(stepLabel, stepHelp, help, label, name, def, prompt, optional, canBeGenerated);
//        this.placeHolder = placeHolder;
//    }
//
//    /**
//     * Get the placeholder (default value).
//     *
//     * @return placeholder
//     */
//    public String placeHolder() {
//        return placeHolder;
//    }
//
//    /**
//     * Create a new builder.
//     *
//     * @return a new builder
//     */
//    public static Builder builder() {
//        return new Builder();
//    }
//
//    @Override
//    public String accept(Prompter prompter) {
//        return prompter.prompt(this);
//    }
//
//    @Override
//    public DescriptorNodes.ContextNode acceptAndConvert(Prompter prompter, String path) {
//        String value = prompter.prompt(this);
//        DescriptorNodes.ContextTextNode result = new DescriptorNodes.ContextTextNode(path);
//        result.value(value);
//        return result;
//    }
//
//    public static class Builder extends Prompt.Builder<TextPrompt, Builder> {
//
//        private String placeHolder;
//
//        /**
//         * Set the placeholder.
//         *
//         * @param placeHolder placeholder
//         * @return Builder
//         */
//        public Builder placeHolder(String placeHolder) {
//            this.placeHolder = placeHolder;
//            return this;
//        }
//
//        @Override
//        public Builder instance() {
//            return this;
//        }
//
//        @Override
//        public Builder userInputAST(UserInputNode userInputAST) {
//            if (userInputAST.children().isEmpty()) {
//                throw new IllegalArgumentException("UserInputAST must contain a child note");
//            }
//            if (userInputAST.children().get(0) instanceof DescriptorNodes.InputTextNode) {
//                DescriptorNodes.InputTextNode inputTextAST = (DescriptorNodes.InputTextNode) userInputAST.children().get(0);
//
//                initFields(userInputAST);
//                placeHolder = inputTextAST.placeHolder();
//
//                return this;
//            }
//            throw new IllegalArgumentException(
//                    String.format(
//                            "Incorrect type of the child node in the UserInputAST instance. Must be - %s. Actual - %s.",
//                            DescriptorNodes.InputTextNode.class.getName(),
//                            userInputAST.children().get(0).getClass().getName()
//                    )
//            );
//        }
//
//        @Override
//        public TextPrompt build() {
//            return new TextPrompt(
//                    stepLabel(),
//                    stepHelp(),
//                    help(),
//                    label(),
//                    name(),
//                    defaultValue(),
//                    prompt(),
//                    placeHolder,
//                    optional(),
//                    canBeGenerated()
//            );
//        }
//    }

}
