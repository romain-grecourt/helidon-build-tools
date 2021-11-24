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

import io.helidon.build.archetype.engine.v2.ast.Constant;
import io.helidon.build.archetype.engine.v2.ast.Input;
import io.helidon.build.archetype.engine.v2.ast.ValueTypes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

import static io.helidon.build.common.ansi.AnsiTextStyles.Bold;
import static io.helidon.build.common.ansi.AnsiTextStyles.BoldBlue;

/**
 * Prompter that uses CLI for input/output.
 */
public class Prompter implements Input.Visitor<Void, Context> {

    private final InputStream in;
    private String lastLabel;

    /**
     * Create a new prompter.
     *
     * @param in input stream to use for reading user input
     */
    public Prompter(InputStream in) {
        this.in = in;
    }

    @Override
    public Void visitText(Input.Text input, Context context) {
        try {
            printLabel(input);
            String defaultText = input.defaultValue().map(BoldBlue::apply).orElse(null);
            String response = prompt("Enter text", defaultText);
            if (response == null || response.trim().length() == 0) {
                context.pushInput(input.name(), input.defaultValue().map(Constant::create).orElse(null));
            } else {
                context.pushInput(input.name(), Constant.create(response));
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Void visitEnum(Input.Enum input, Context context) {
        while (true) {
            try {
                printLabel(input);
                printOptions(input);

                int defaultIndex = input.defaultIndex();
                String defaultText = defaultIndex != -1
                        ? BoldBlue.apply(String.format("%s", defaultIndex + 1))
                        : null;

                String response = prompt("Enter selection", defaultText);
                lastLabel = input.label();
                if ((response == null || response.trim().length() == 0)) {
                    if (defaultIndex >= 0) {
                        context.pushInput(input.name(), Constant.create(input.options().get(defaultIndex).value()));
                        return null;
                    }
                } else {
                    int index = Integer.parseInt(response.trim());
                    if (index > 0 && index <= input.options().size()) {
                        context.pushInput(input.name(), Constant.create(input.options().get(index - 1).value()));
                        return null;
                    }
                }
            } catch (NumberFormatException e) {
                // TODO print error message
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Void visitList(Input.List input, Context context) {
        while (true) {
            try {
                String question = "Enter selection (one or more numbers separated by the spaces)";
                printLabel(input);
                printOptions(input);

                List<Integer> defaultIndexes = input.defaultIndexes();
                String defaultText = defaultIndexes.size() > 0
                        ? BoldBlue.apply(String.format("%s",
                        defaultIndexes.stream().map(i -> (i + 1) + "").collect(Collectors.joining(", "))))
                        : null;

                String response = prompt(question, defaultText);

                lastLabel = input.label();
                if (response == null || response.trim().length() == 0) {
                    if (!defaultIndexes.isEmpty()) {
                        context.pushInput(input.name(), Constant.create(input.defaultValue(), ValueTypes.STRING_LIST));
                        return null;
                    }
                } else {
                    context.pushInput(input.name(), Constant.create(input.parseResponse(response), ValueTypes.STRING_LIST));
                    return null;
                }
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                // TODO print error message
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Void visitBoolean(Input.Boolean input, Context context) {
        while (true) {
            try {
                printLabel(input);
                boolean defaultValue = input.defaultValue();
                String defaultValueText = BoldBlue.apply(String.format("%s", defaultValue ? "yes" : "no"));
                String question = String.format("%s (yes/no)", Bold.apply(input.prompt()));
                String response = prompt(question, defaultValueText);
                if (response == null || response.trim().length() == 0) {
                    context.pushInput(input.name(), Constant.create(defaultValue, ValueTypes.BOOLEAN));
                    return null;
                }
                boolean value;
                switch (response.trim().toLowerCase()) {
                    case "y":
                    case "yes":
                        value = true;
                        break;
                    case "n":
                    case "no":
                        value = false;
                        break;
                    default:
                        continue;
                }
                context.pushInput(input.name(), Constant.create(value, ValueTypes.BOOLEAN));
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void printLabel(Input input) {
        String label = input.label();
        if (label != null && !label.equals(lastLabel)) {
            System.out.println(Bold.apply(label));
        }
        System.out.println(Bold.apply(input.prompt()));
    }

    private static void printOptions(Input.Options input) {
        List<Input.Option> options = input.options();
        int index = 0;
        for (Input.Option option : options) {
            String o = BoldBlue.apply(String.format("  (%d) %s ", index + 1, option.value()));
            String optionText = option.label() != null && !option.label().isBlank()
                    ? String.format("%s | %s", o, option.label())
                    : o;
            System.out.println(optionText);
            index++;
        }
    }

    private String prompt(String prompt, String defaultText) throws IOException {
        String prompText = defaultText != null
                ? String.format("%s (Default: %s): ", prompt, defaultText)
                : String.format("%s: ", prompt);
        System.out.print(Bold.apply(prompText));
        System.out.flush();
        return new BufferedReader(new InputStreamReader(in)).readLine();
    }
}
